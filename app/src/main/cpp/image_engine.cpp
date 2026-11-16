#include "image_engine.h"

#include <android/log.h>
#include <stable-diffusion.h>

#include <algorithm>
#include <atomic>
#include <cctype>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "third_party/stable-diffusion.cpp/thirdparty/stb_image_write.h"

#define LOG_TAG "A56AI-IMAGE"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace galaxya56 {
namespace {

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

const char* statusName(sd_status_t status) {
    switch (status) {
        case SD_STATUS_OK: return "ok";
        case SD_STATUS_INVALID_ARGUMENT: return "invalid_argument";
        case SD_STATUS_UNKNOWN_KEY: return "unknown_key";
        case SD_STATUS_TYPE_MISMATCH: return "type_mismatch";
        case SD_STATUS_OUT_OF_RANGE: return "out_of_range";
        case SD_STATUS_UNSUPPORTED: return "unsupported";
        case SD_STATUS_OUT_OF_MEMORY: return "out_of_memory";
        case SD_STATUS_LOAD_FAILED: return "load_failed";
        case SD_STATUS_GENERATION_FAILED: return "generation_failed";
        case SD_STATUS_BUSY: return "busy";
        case SD_STATUS_CANCELLED: return "cancelled";
        default: return "unknown";
    }
}

std::string lastError() {
    const char* error = sd_get_last_error();
    return error ? std::string(error) : std::string("unbekannter Fehler");
}

// stable-diffusion.cpp picks the first registered accelerator. On Samsung
// devices that is NNAPI, which is unavailable to normal apps (HIDL is hidden).
// Prefer the Vulkan device explicitly; fall back to the library default.
std::string preferredBackend() {
    const size_t needed = sd_list_devices(nullptr, 0);
    if (needed == 0) return {};
    std::string devices(needed + 1, '\0');
    if (sd_list_devices(devices.data(), devices.size()) == 0) return {};
    devices.resize(std::strlen(devices.c_str()));
    LOGI("SD devices: %s", devices.c_str());

    size_t start = 0;
    while (start < devices.size()) {
        const size_t end = devices.find('\n', start);
        const std::string line = devices.substr(start, end == std::string::npos ? std::string::npos : end - start);
        const size_t tab = line.find('\t');
        const std::string name = line.substr(0, tab);
        if (name.find("Vulkan") != std::string::npos) return name;
        if (end == std::string::npos) break;
        start = end + 1;
    }
    return {};
}

struct State {
    std::mutex mutex;
    sd_ctx_t* ctx = nullptr;
    std::string path;
    int threads = 4;
    std::string version = "unbekannt";
    std::string backend = "unbekannt";
    std::atomic<bool> cancelRequested{false};
    std::atomic<int> progressStep{0};
    std::atomic<int> progressSteps{0};
};

State& state() {
    static State s;
    return s;
}

void sdLogCallback(sd_log_level_t level, const char* text, void* /*data*/) {
    const int priority = level == SD_LOG_ERROR ? ANDROID_LOG_ERROR
                       : level == SD_LOG_WARN  ? ANDROID_LOG_WARN
                       : level == SD_LOG_INFO  ? ANDROID_LOG_INFO
                                               : ANDROID_LOG_DEBUG;
    __android_log_print(priority, "A56AI-SD", "%s", text ? text : "");
}

void progressCallback(int step, int steps, float /*time*/, void* /*data*/) {
    State& s = state();
    s.progressStep.store(step);
    s.progressSteps.store(steps);
    if (step == 0 || step == steps || step % 2 == 0) {
        LOGI("SD progress %d/%d", step, steps);
    }
    if (s.cancelRequested.load()) {
        // sd_cancel_generation is invoked by ImageEngine::cancel() from the UI
        // thread; the callback only surfaces progress.
    }
}

} // namespace

ImageEngine& ImageEngine::instance() {
    static ImageEngine engine;
    return engine;
}

std::string ImageEngine::loadModel(const std::string& path, int threads, const std::string& backendMode) {
    State& s = state();
    std::lock_guard<std::mutex> lock(s.mutex);

    if (s.ctx) {
        sd_ctx_free(s.ctx);
        s.ctx = nullptr;
    }

    auto start = std::chrono::steady_clock::now();
    sd_set_log_callback(sdLogCallback, nullptr);

    // stable-diffusion.cpp has no NNAPI/ENN backend. NPU/auto requests are
    // therefore resolved as Vulkan first, then CPU. This keeps every failure
    // recoverable and logs exactly which stage ran.
    const std::string vulkan = preferredBackend();
    std::vector<std::string> attempts;
    if (backendMode == "cpu") {
        attempts.push_back("");
    } else {
        if (!vulkan.empty()) attempts.push_back(vulkan);
        attempts.push_back("");
    }
    if (backendMode == "npu") {
        LOGI("SD NPU direct request denied: no NNAPI/ENN diffusion backend; trying GPU then CPU");
    }

    std::string lowered = path;
    std::transform(lowered.begin(), lowered.end(), lowered.begin(),
                   [](unsigned char c) { return static_cast<char>(std::tolower(c)); });

    sd_ctx_t* ctx = nullptr;
    sd_status_t rc = SD_STATUS_LOAD_FAILED;
    std::string error;
    std::string backendLabel;
    bool fallback = false;

    for (size_t attempt = 0; attempt < attempts.size(); ++attempt) {
        const std::string requested = attempts[attempt];
        sd_ctx_opt_t* opt = sd_ctx_opt_create();
        if (!opt) {
            error = "Optik konnte nicht erzeugt werden";
            break;
        }
        rc = SD_STATUS_OK;
        if (!requested.empty()) {
            rc = sd_opt_set_string(opt, "backend", requested.c_str());
            LOGI("SD attempt backend=%s set rc=%d err=%s", requested.c_str(), (int)rc, sd_get_last_error());
        } else {
            LOGI("SD attempt backend=CPU/default");
        }
        sd_status_t seg_rc = sd_opt_set_bool(opt, "disable_segmented_compute", true);
        LOGI("SD disable_segmented_compute rc=%d err=%s", (int)seg_rc, sd_get_last_error());
        if (rc == SD_STATUS_OK) rc = sd_opt_set_string(opt, "model_path", path.c_str());
        if (rc == SD_STATUS_OK) rc = sd_opt_set_int(opt, "n_threads", threads > 0 ? threads : 4);
        if (rc == SD_STATUS_OK) rc = sd_opt_set_bool(opt, "enable_mmap", true);
        if (rc == SD_STATUS_OK && lowered.find("turbo") != std::string::npos) {
            sd_status_t pred_rc = sd_opt_set_string(opt, "prediction", "v");
            LOGI("SD turbo checkpoint: force v-prediction rc=%d err=%s", (int)pred_rc, sd_get_last_error());
        }
        ctx = nullptr;
        if (rc == SD_STATUS_OK) rc = sd_ctx_create(opt, &ctx);
        error = rc == SD_STATUS_OK ? "" : lastError();
        sd_opt_free(opt);
        if (rc == SD_STATUS_OK && ctx) {
            backendLabel = requested.empty() ? "CPU (NEON)" : requested;
            fallback = attempt > 0;
            break;
        }
        if (ctx) { sd_ctx_free(ctx); ctx = nullptr; }
        LOGE("SD load attempt %zu failed: %s", attempt + 1, error.c_str());
    }

    if (rc != SD_STATUS_OK || !ctx) {
        LOGE("SD load failed after %zu attempt(s): %s", attempts.size(), error.c_str());
        std::ostringstream json;
        json << "{\"ok\":false,\"status\":\"" << statusName(rc) << "\",\"error\":\""
             << jsonEscape(error) << "\",\"requestedBackend\":\"" << jsonEscape(backendMode) << "\"}";
        return json.str();
    }

    s.ctx = ctx;
    s.path = path;
    s.threads = threads;
    s.backend = backendLabel;
    const char* version = sd_get_model_version_name(ctx);
    s.version = version ? version : "unbekannt";
    sd_set_progress_callback(progressCallback, nullptr);

    auto end = std::chrono::steady_clock::now();
    const long long ms = std::chrono::duration_cast<std::chrono::milliseconds>(end - start).count();
    LOGI("SD model loaded: %s (%s) on %s in %lld ms (fallback=%d)", s.version.c_str(), path.c_str(), s.backend.c_str(), ms, fallback ? 1 : 0);

    std::ostringstream json;
    json << "{\"ok\":true,\"model\":\"" << jsonEscape(s.version)
         << "\",\"path\":\"" << jsonEscape(path)
         << "\",\"threads\":" << s.threads
         << ",\"loadMs\":" << ms
         << ",\"backend\":\"" << jsonEscape(s.backend)
         << "\",\"requestedBackend\":\"" << jsonEscape(backendMode)
         << "\",\"fallback\":" << (fallback ? "true" : "false")
         << ",\"npuDirect\":false"
         << ",\"imageSupport\":" << (sd_ctx_supports_image_generation(ctx) ? "true" : "false")
         << ",\"videoSupport\":" << (sd_ctx_supports_video_generation(ctx) ? "true" : "false")
         << "}";
    return json.str();
}

std::string ImageEngine::unload() {
    State& s = state();
    std::lock_guard<std::mutex> lock(s.mutex);
    if (s.ctx) {
        sd_ctx_free(s.ctx);
        s.ctx = nullptr;
    }
    s.path.clear();
    s.version = "unbekannt";
    s.backend = "unbekannt";
    return R"json({"ok":true})json";
}

std::string ImageEngine::statusJson() {
    State& s = state();
    std::lock_guard<std::mutex> lock(s.mutex);
    std::ostringstream json;
    json << "{\"loaded\":" << (s.ctx ? "true" : "false")
         << ",\"path\":\"" << jsonEscape(s.path)
         << "\",\"model\":\"" << jsonEscape(s.version)
         << "\",\"backend\":\"" << jsonEscape(s.backend)
         << "\",\"threads\":" << s.threads
         << ",\"progressStep\":" << s.progressStep.load()
         << ",\"progressSteps\":" << s.progressSteps.load() << "}";
    return json.str();
}

std::string ImageEngine::generate(const ImageGenParams& params) {
    State& s = state();
    std::lock_guard<std::mutex> lock(s.mutex);
    if (!s.ctx) {
        return R"json({"ok":false,"error":"Kein Diffusionsmodell geladen"})json";
    }

    s.cancelRequested.store(false);
    s.progressStep.store(0);
    s.progressSteps.store(0);

    sd_img_gen_opt_t* opt = sd_img_gen_opt_create();
    if (!opt) {
        return R"json({"ok":false,"error":"Generierungsoptionen konnten nicht erzeugt werden"})json";
    }

    sd_status_t rc = sd_opt_set_string(opt, "prompt", params.prompt.c_str());
    if (rc == SD_STATUS_OK && !params.negativePrompt.empty()) {
        rc = sd_opt_set_string(opt, "negative_prompt", params.negativePrompt.c_str());
    }
    if (rc == SD_STATUS_OK) rc = sd_opt_set_int(opt, "width", params.width);
    if (rc == SD_STATUS_OK) rc = sd_opt_set_int(opt, "height", params.height);
    if (rc == SD_STATUS_OK) rc = sd_opt_set_int(opt, "sample.steps", params.steps);
    if (rc == SD_STATUS_OK && !params.sampler.empty()) {
        rc = sd_opt_set_string(opt, "sample.method", params.sampler.c_str());
    }
    if (rc == SD_STATUS_OK) rc = sd_opt_set_float(opt, "sample.guidance.txt_cfg", params.cfg);
    if (rc == SD_STATUS_OK) rc = sd_opt_set_bool(opt, "vae_tiling.enabled", params.vaeTiling);
    if (rc == SD_STATUS_OK) rc = sd_opt_set_int(opt, "seed", params.seed);
    if (rc == SD_STATUS_OK) rc = sd_opt_set_int(opt, "batch_count", 1);
    if (rc == SD_STATUS_OK && !params.loraPath.empty()) {
        sd_lora_t lora{};
        lora.is_high_noise = false;
        lora.multiplier = params.loraScale;
        lora.path = params.loraPath.c_str();
        rc = sd_opt_set_loras(opt, "loras", &lora, 1);
        LOGI("SD image LoRA %s scale=%.2f rc=%d", params.loraPath.c_str(), params.loraScale, (int)rc);
    }
    LOGI("SD generate backend=%s sampler=%s steps=%d cfg=%.2f seed=%lld", s.backend.c_str(), params.sampler.c_str(), params.steps, params.cfg, params.seed);

    sd_result_t* result = nullptr;
    const auto start = std::chrono::steady_clock::now();
    if (rc == SD_STATUS_OK) rc = sd_generate_image(s.ctx, opt, &result);
    const auto end = std::chrono::steady_clock::now();
    const long long ms = std::chrono::duration_cast<std::chrono::milliseconds>(end - start).count();
    const std::string error = rc == SD_STATUS_OK ? "" : lastError();
    sd_opt_free(opt);

    if (rc != SD_STATUS_OK || !result) {
        if (result) sd_result_free(result);
        LOGE("SD generation failed: %s", error.c_str());
        std::ostringstream json;
        json << "{\"ok\":false,\"status\":\"" << statusName(rc) << "\",\"ms\":" << ms
             << ",\"error\":\"" << jsonEscape(error) << "\"}";
        return json.str();
    }

    size_t written = 0;
    int outWidth = 0;
    int outHeight = 0;
    const size_t count = sd_result_get_image_count(result);
    for (size_t i = 0; i < count; ++i) {
        const sd_tensor_t* image = sd_result_get_image(result, i);
        if (!image || sd_tensor_type(image) != SD_TENSOR_U8 || sd_tensor_rank(image) != 3) continue;
        const int64_t channels = sd_tensor_dim(image, 0);
        const int64_t width = sd_tensor_dim(image, 1);
        const int64_t height = sd_tensor_dim(image, 2);
        if (channels != 3 || width <= 0 || height <= 0) continue;

        const size_t expected = static_cast<size_t>(width) * height * 3;
        const void* pixels = sd_tensor_data(image);
        if (!pixels || sd_tensor_byte_size(image) < expected) continue;

        std::string path = params.outputPath;
        if (count > 1) {
            const size_t dot = path.find_last_of('.');
            const std::string suffix = "_" + std::to_string(i);
            path = dot == std::string::npos ? path + suffix : path.substr(0, dot) + suffix + path.substr(dot);
        }
        if (!stbi_write_png(path.c_str(), static_cast<int>(width), static_cast<int>(height), 3,
                            pixels, static_cast<int>(width) * 3)) {
            if (result) sd_result_free(result);
            return R"json({"ok":false,"error":"PNG konnte nicht geschrieben werden"})json";
        }
        outWidth = static_cast<int>(width);
        outHeight = static_cast<int>(height);
        ++written;
    }

    sd_result_free(result);

    if (written == 0) {
        return R"json({"ok":false,"error":"Modell lieferte kein RGB-Bild"})json";
    }

    LOGI("SD image done: %dx%d in %lld ms", outWidth, outHeight, ms);
    std::ostringstream json;
    json << "{\"ok\":true,\"images\":" << written
         << ",\"width\":" << outWidth << ",\"height\":" << outHeight
         << ",\"ms\":" << ms
         << ",\"seed\":" << params.seed
         << ",\"backend\":\"" << jsonEscape(s.backend)
         << "\",\"sampler\":\"" << jsonEscape(params.sampler)
         << "\",\"lora\":\"" << jsonEscape(params.loraPath)
         << "\",\"path\":\"" << jsonEscape(params.outputPath)
         << "\"}";
    LOGI("SD result JSON (%zu chars): %s", json.str().size(), json.str().c_str());
    return json.str();
}

void ImageEngine::cancel() {
    State& s = state();
    s.cancelRequested.store(true);
    if (s.ctx) sd_cancel_generation(s.ctx, SD_CANCEL_ALL);
}

} // namespace galaxya56
