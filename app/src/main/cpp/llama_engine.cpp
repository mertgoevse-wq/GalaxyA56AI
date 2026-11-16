#include "llama_engine.h"
#include "ggml-backend.h"

#include "llama.h"

#include <android/log.h>
#include <sys/stat.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstring>
#include <mutex>
#include <sstream>
#include <vector>

#define LOG_TAG "A56AI-LLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace galaxya56 {
namespace {

bool fileExists(const std::string& path) {
    struct stat st {};
    return stat(path.c_str(), &st) == 0 && S_ISREG(st.st_mode);
}

std::string jsonEscape(const std::string& in) {
    std::string out;
    out.reserve(in.size() + 16);
    for (unsigned char c : in) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if (c < 0x20) {
                    char buf[8];
                    snprintf(buf, sizeof(buf), "\\u%04x", c);
                    out += buf;
                } else {
                    out += static_cast<char>(c);
                }
        }
    }
    return out;
}

// Emits only complete UTF-8 sequences, so Kotlin always receives valid text.
class Utf8Sink {
public:
    explicit Utf8Sink(std::function<void(const std::string&)> cb) : cb_(std::move(cb)) {}

    void push(const char* data, size_t len) {
        buffer_.append(data, len);
        size_t cut = completePrefix(buffer_);
        if (cut > 0) {
            cb_(buffer_.substr(0, cut));
            buffer_.erase(0, cut);
        }
    }

    void flush() {
        if (!buffer_.empty()) {
            cb_(buffer_);
            buffer_.clear();
        }
    }

private:
    static size_t completePrefix(const std::string& s) {
        if (s.empty()) return 0;
        size_t i = s.size();
        size_t back = 0;
        while (i > 0 && (static_cast<unsigned char>(s[i - 1]) & 0xC0) == 0x80 && back < 3) {
            --i;
            ++back;
        }
        if (i == 0) return s.size();
        unsigned char lead = static_cast<unsigned char>(s[i - 1]);
        size_t need = 0;
        if ((lead & 0x80) == 0) need = 1;
        else if ((lead & 0xE0) == 0xC0) need = 2;
        else if ((lead & 0xF0) == 0xE0) need = 3;
        else if ((lead & 0xF8) == 0xF0) need = 4;
        else return s.size(); // invalid lead byte: don't hold back forever

        size_t have = s.size() - (i - 1);
        if (have >= need) return s.size();
        return i - 1;
    }

    std::function<void(const std::string&)> cb_;
    std::string buffer_;
};

struct State {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    const llama_vocab* vocab = nullptr;
    std::vector<llama_adapter_lora*> loras;
    std::mutex mutex;
    std::atomic<bool> cancel{false};
    int nCtx = 0;
    int nThreads = 4;
    std::string description;
};

State g;

void unloadLocked() {
    if (g.ctx) {
        llama_free(g.ctx);
        g.ctx = nullptr;
    }
    if (g.model) {
        llama_model_free(g.model);
        g.model = nullptr;
    }
    g.loras.clear();
    g.vocab = nullptr;
    g.nCtx = 0;
    g.description.clear();
}

void logCallback(enum ggml_log_level level, const char* text, void*) {
    if (level == GGML_LOG_LEVEL_ERROR) {
        LOGE("%s", text);
    } else if (level == GGML_LOG_LEVEL_WARN) {
        LOGI("%s", text);
    }
}

} // namespace

LlamaEngine& LlamaEngine::instance() {
    static LlamaEngine engine;
    return engine;
}

std::string LlamaEngine::loadModel(const std::string& path, int nCtx, int nThreads, int gpuLayers) {
    std::lock_guard<std::mutex> lock(g.mutex);
    unloadLocked();

    if (!fileExists(path)) {
        return R"json({"ok":false,"error":"Modelldatei nicht gefunden"})json";
    }

    // Keep the probe result across loads: std::call_once must not guard
    // function-local values that are re-initialized on every call.
    static int gpuDevices = 0;
    static std::string gpuDeviceName;
    static std::once_flag once;
    std::call_once(once, [&] {
        llama_log_set(logCallback, nullptr);
        llama_backend_init();
        const size_t devCount = ggml_backend_dev_count();
        for (size_t i = 0; i < devCount; ++i) {
            ggml_backend_dev_t dev = ggml_backend_dev_get(i);
            if (!dev) continue;
            if (ggml_backend_dev_type(dev) == GGML_BACKEND_DEVICE_TYPE_GPU) {
                ++gpuDevices;
                if (gpuDeviceName.empty()) {
                    const char* desc = ggml_backend_dev_description(dev);
                    gpuDeviceName = desc ? desc : "GPU";
                }
            }
        }
        LOGI("Backend devices: %zu, GPU: %d (%s)", devCount, gpuDevices, gpuDeviceName.c_str());
    });

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = gpuLayers;
    mparams.use_mmap = true;
    mparams.use_mlock = false;

    g.model = llama_model_load_from_file(path.c_str(), mparams);
    if (!g.model) {
        return R"json({"ok":false,"error":"GGUF konnte nicht geladen werden (Format/Varianten pruefen)"})json";
    }

    g.vocab = llama_model_get_vocab(g.model);

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(std::max(512, nCtx));
    cparams.n_batch = 256;
    cparams.n_ubatch = 256;
    cparams.n_threads = std::max(1, nThreads);
    cparams.n_threads_batch = std::max(1, nThreads);

    g.ctx = llama_init_from_model(g.model, cparams);
    std::string backend = gpuLayers > 0
        ? (gpuDevices > 0 ? "CPU+Vulkan " + gpuDeviceName : "CPU (kein Vulkan-Geraet)")
        : "CPU (NEON)";
    if (!g.ctx && gpuLayers > 0) {
        // GPU init can fail (driver, VRAM, unsupported op): fall back to CPU only.
        llama_model_free(g.model);
        g.model = nullptr;
        mparams.n_gpu_layers = 0;
        g.model = llama_model_load_from_file(path.c_str(), mparams);
        if (g.model) {
            g.vocab = llama_model_get_vocab(g.model);
            g.ctx = llama_init_from_model(g.model, cparams);
            backend = "CPU (Vulkan-Init fehlgeschlagen)";
        }
    }
    if (!g.ctx) {
        unloadLocked();
        return R"json({"ok":false,"error":"Kontext konnte nicht erstellt werden (zu wenig RAM?)"})json";
    }

    char desc[512] = {0};
    llama_model_desc(g.model, desc, sizeof(desc));
    g.description = desc;
    g.nCtx = static_cast<int>(llama_n_ctx(g.ctx));
    g.nThreads = nThreads;

    std::ostringstream json;
    json << "{\"ok\":true,\"description\":\"" << jsonEscape(g.description) << "\""
         << ",\"params\":" << llama_model_n_params(g.model)
         << ",\"sizeBytes\":" << llama_model_size(g.model)
         << ",\"context\":" << g.nCtx
         << ",\"threads\":" << g.nThreads
         << ",\"backend\":\"" << jsonEscape(backend) << "\""
         << ",\"gpuLayers\":" << gpuLayers
         << ",\"gpuDevices\":" << gpuDevices
         << ",\"gpuDevice\":\"" << jsonEscape(gpuDeviceName) << "\""
         << ",\"layers\":" << llama_model_n_layer(g.model) << "}";
    LOGI("Model loaded: %s", g.description.c_str());
    return json.str();
}

std::string LlamaEngine::unload() {
    std::lock_guard<std::mutex> lock(g.mutex);
    unloadLocked();
    return R"json({"ok":true})json";
}

bool LlamaEngine::isLoaded() {
    std::lock_guard<std::mutex> lock(g.mutex);
    return g.ctx != nullptr;
}

std::string LlamaEngine::infoJson() {
    std::lock_guard<std::mutex> lock(g.mutex);
    std::ostringstream json;
    json << "{\"loaded\":" << (g.ctx ? "true" : "false")
         << ",\"description\":\"" << jsonEscape(g.description) << "\""
         << ",\"context\":" << g.nCtx
         << ",\"threads\":" << g.nThreads
         << ",\"loras\":" << g.loras.size() << "}";
    return json.str();
}

std::string LlamaEngine::applyLora(const std::string& path, float scale) {
    std::lock_guard<std::mutex> lock(g.mutex);
    if (!g.ctx || !g.model) {
        return R"json({"ok":false,"error":"Kein Modell geladen"})json";
    }
    if (!fileExists(path)) {
        return R"json({"ok":false,"error":"LoRA-Datei nicht gefunden"})json";
    }
    llama_adapter_lora* adapter = llama_adapter_lora_init(g.model, path.c_str());
    if (!adapter) {
        return R"json({"ok":false,"error":"LoRA konnte nicht geladen werden (GGUF-Adapter erforderlich)"})json";
    }
    if (llama_set_adapter_lora(g.ctx, adapter, scale) != 0) {
        return R"json({"ok":false,"error":"LoRA passt nicht zum Modell"})json";
    }
    g.loras.push_back(adapter);
    std::ostringstream json;
    json << "{\"ok\":true,\"scale\":" << scale << ",\"count\":" << g.loras.size() << "}";
    return json.str();
}

std::string LlamaEngine::clearLoras() {
    std::lock_guard<std::mutex> lock(g.mutex);
    if (g.ctx) {
        llama_clear_adapter_lora(g.ctx);
    }
    g.loras.clear();
    return R"json({"ok":true,"count":0})json";
}

void LlamaEngine::cancel() {
    g.cancel.store(true);
}

GenResult LlamaEngine::generate(const std::string& prompt, const GenParams& params,
                                const std::function<void(const std::string&)>& onToken) {
    std::lock_guard<std::mutex> lock(g.mutex);
    GenResult result;

    if (!g.ctx || !g.model || !g.vocab) {
        result.error = "Kein Modell geladen";
        return result;
    }

    g.cancel.store(false);

    auto clock = [] { return std::chrono::steady_clock::now(); };
    auto millis = [](auto a, auto b) {
        return std::chrono::duration_cast<std::chrono::milliseconds>(b - a).count();
    };

    const int nCtx = g.nCtx > 0 ? g.nCtx : 2048;
    int maxNew = params.maxTokens;
    if (maxNew <= 0 || maxNew > nCtx) maxNew = std::max(16, nCtx / 2);
    const int maxPrompt = std::max(16, nCtx - maxNew - 8);

    // Chat templates already carry their control tokens; adding BOS again would
    // duplicate it (Llama 3 / Gemma). Raw prompts still get BOS.
    const bool templatePrompt = prompt.rfind("<|", 0) == 0 ||
                                prompt.rfind("<start_of_turn>", 0) == 0 ||
                                prompt.rfind("<bos>", 0) == 0 ||
                                prompt.rfind("[INST]", 0) == 0;
    const bool addSpecial = !templatePrompt;

    std::vector<llama_token> tokens(prompt.size() + 16);
    int32_t n = llama_tokenize(g.vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                               tokens.data(), static_cast<int32_t>(tokens.size()), addSpecial, true);
    if (n < 0) {
        tokens.resize(-n);
        n = llama_tokenize(g.vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                           tokens.data(), static_cast<int32_t>(tokens.size()), addSpecial, true);
    }
    if (n <= 0) {
        result.error = "Prompt konnte nicht tokenisiert werden";
        return result;
    }
    tokens.resize(n);

    if (n > maxPrompt) {
        tokens.erase(tokens.begin(), tokens.begin() + (n - maxPrompt));
    }

    const auto promptStart = clock();
    const int nBatch = 256;
    for (size_t i = 0; i < tokens.size(); i += nBatch) {
        const int32_t chunk = static_cast<int32_t>(std::min<size_t>(nBatch, tokens.size() - i));
        llama_batch batch = llama_batch_get_one(tokens.data() + i, chunk);
        if (llama_decode(g.ctx, batch) != 0) {
            result.error = "Prompt-Verarbeitung fehlgeschlagen (Kontext zu gross?)";
            return result;
        }
    }
    result.promptMs = millis(promptStart, clock());

    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler* sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(64, params.repeatPenalty, 0.0f, 0.0f));
    if (params.topK > 0) llama_sampler_chain_add(sampler, llama_sampler_init_top_k(params.topK));
    if (params.topP > 0.0f && params.topP < 1.0f) {
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(params.topP, 1));
    }
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(std::max(0.01f, params.temperature)));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    Utf8Sink sink(onToken);
    const auto genStart = clock();
    int generated = 0;
    int used = static_cast<int>(tokens.size());

    while (generated < maxNew) {
        if (g.cancel.load()) {
            result.stopped = true;
            break;
        }
        const llama_token id = llama_sampler_sample(sampler, g.ctx, -1);
        if (llama_vocab_is_eog(g.vocab, id)) {
            break;
        }
        char piece[512];
        const int32_t pieceLen = llama_token_to_piece(g.vocab, id, piece, sizeof(piece), 0, true);
        if (pieceLen > 0) {
            sink.push(piece, static_cast<size_t>(pieceLen));
            result.text.append(piece, static_cast<size_t>(pieceLen));
        }
        ++generated;

        llama_batch batch = llama_batch_get_one(const_cast<llama_token*>(&id), 1);
        if (llama_decode(g.ctx, batch) != 0) {
            result.contextFull = true;
            break;
        }
        if (++used >= nCtx - 1) {
            result.contextFull = true;
            break;
        }
    }

    sink.flush();
    llama_sampler_free(sampler);

    result.tokens = generated;
    result.genMs = millis(genStart, clock());
    result.ok = true;
    return result;
}

} // namespace galaxya56
