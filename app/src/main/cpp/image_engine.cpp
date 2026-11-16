#include "image_engine.h"

#include <android/log.h>
#include <stable-diffusion.h>

#include <algorithm>
#include <atomic>
#include <cctype>
#include <chrono>
#include <cstdio>
#include <limits.h>
#include <cstring>
#include <mutex>
#include <sstream>
#include <string>
#include <sys/stat.h>
#include <vector>

#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "third_party/stable-diffusion.cpp/thirdparty/stb_image_write.h"
// stb_image has its implementation in enn_engine.cpp; only declarations here.
#include "third_party/stable-diffusion.cpp/thirdparty/stb_image.h"

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
    bool controlNetLoaded = false;
    std::string controlNetPath;
    bool videoReady = false;
    std::string motionModulePath;
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

std::string ImageEngine::loadModel(const std::string& path, int threads, const std::string& backendMode,
                                   const std::string& motionModulePath) {
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
        if (rc == SD_STATUS_OK && !motionModulePath.empty()) {
            rc = sd_opt_set_string(opt, "motion_module_path", motionModulePath.c_str());
            LOGI("SD motion module attempt path=%s rc=%d err=%s",
                 motionModulePath.c_str(), (int)rc, sd_get_last_error());
        }
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
    s.controlNetLoaded = false;
    s.controlNetPath.clear();
    s.motionModulePath = motionModulePath;
    s.videoReady = sd_ctx_supports_video_generation(ctx);
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
         << ",\"controlNet\":false"
         << ",\"motionModulePath\":\"" << jsonEscape(motionModulePath) << "\""
         << ",\"imageSupport\":" << (sd_ctx_supports_image_generation(ctx) ? "true" : "false")
         << ",\"videoSupport\":" << (s.videoReady ? "true" : "false")
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
    s.controlNetLoaded = false;
    s.controlNetPath.clear();
    s.videoReady = false;
    s.motionModulePath.clear();
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
         << ",\"controlNet\":" << (s.controlNetLoaded ? "true" : "false")
         << ",\"controlNetPath\":\"" << jsonEscape(s.controlNetPath) << "\""
         << ",\"videoReady\":" << (s.videoReady ? "true" : "false")
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
    sd_tensor_t* controlTensor = nullptr;
    sd_tensor_t* processedControl = nullptr;
    std::string setupError;
    if (rc == SD_STATUS_OK && !params.controlImagePath.empty()) {
        if (!s.controlNetLoaded) {
            setupError = "ControlNet ist nicht geladen";
            rc = SD_STATUS_INVALID_ARGUMENT;
        } else {
            int cw = 0, ch = 0, cn = 0;
            unsigned char* rgb = stbi_load(params.controlImagePath.c_str(), &cw, &ch, &cn, 3);
            if (!rgb || cw <= 0 || ch <= 0) {
                setupError = "Control-Bild konnte nicht gelesen werden: " + params.controlImagePath;
                rc = SD_STATUS_INVALID_ARGUMENT;
                if (rgb) stbi_image_free(rgb);
            } else {
                const size_t pixels = static_cast<size_t>(cw) * ch;
                std::vector<unsigned char> planar(pixels * 3);
                for (int y = 0; y < ch; ++y) {
                    for (int x = 0; x < cw; ++x) {
                        const size_t src = (static_cast<size_t>(y) * cw + x) * 3;
                        const size_t dst = static_cast<size_t>(y) * cw + x;
                        planar[dst] = rgb[src];
                        planar[pixels + dst] = rgb[src + 1];
                        planar[pixels * 2 + dst] = rgb[src + 2];
                    }
                }
                stbi_image_free(rgb);
                const int64_t shape[3] = {3, cw, ch};
                controlTensor = sd_tensor_create(SD_TENSOR_U8, 3, shape, planar.data(), planar.size());
                if (!controlTensor) {
                    setupError = "Control-Tensor konnte nicht erzeugt werden";
                    rc = SD_STATUS_OUT_OF_MEMORY;
                } else if (params.controlCanny || params.controlType == "canny") {
                    const sd_status_t cannyRc = sd_preprocess_canny(
                        controlTensor, 0.08f, 0.08f, 0.8f, 1.0f, false, &processedControl);
                    if (cannyRc != SD_STATUS_OK || !processedControl) {
                        setupError = "Canny-Vorverarbeitung fehlgeschlagen: " + lastError();
                        rc = cannyRc == SD_STATUS_OK ? SD_STATUS_GENERATION_FAILED : cannyRc;
                    } else {
                        LOGI("SD control Canny: %dx%d", cw, ch);
                    }
                }
                if (rc == SD_STATUS_OK) {
                    rc = sd_opt_set_tensor(opt, "control_image", processedControl ? processedControl : controlTensor);
                    if (rc == SD_STATUS_OK) rc = sd_opt_set_float(opt, "control_strength", params.controlStrength);
                    if (rc == SD_STATUS_OK && params.controlType != "none") {
                        rc = sd_opt_set_string(opt, "control_type", params.controlType.c_str());
                    }
                    LOGI("SD control image=%s strength=%.2f type=%s canny=%d rc=%d",
                         params.controlImagePath.c_str(), params.controlStrength,
                         params.controlType.c_str(), params.controlCanny ? 1 : 0, (int)rc);
                }
            }
        }
    }
    LOGI("SD generate backend=%s sampler=%s steps=%d cfg=%.2f seed=%lld", s.backend.c_str(), params.sampler.c_str(), params.steps, params.cfg, params.seed);

    sd_result_t* result = nullptr;
    const auto start = std::chrono::steady_clock::now();
    if (rc == SD_STATUS_OK) rc = sd_generate_image(s.ctx, opt, &result);
    const auto end = std::chrono::steady_clock::now();
    const long long ms = std::chrono::duration_cast<std::chrono::milliseconds>(end - start).count();
    const std::string error = rc == SD_STATUS_OK ? "" : (setupError.empty() ? lastError() : setupError);
    if (processedControl) sd_tensor_free(processedControl);
    if (controlTensor) sd_tensor_free(controlTensor);
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
         << "\",\"controlImage\":\"" << jsonEscape(params.controlImagePath)
         << "\",\"controlStrength\":" << params.controlStrength
         << ",\"controlType\":\"" << jsonEscape(params.controlType)
         << "\",\"path\":\"" << jsonEscape(params.outputPath)
         << "\"}";
    LOGI("SD result JSON (%zu chars): %s", json.str().size(), json.str().c_str());
    return json.str();
}

std::string ImageEngine::generateVideo(const VideoGenParams& params) {
    State& s = state();
    std::lock_guard<std::mutex> lock(s.mutex);
    if (!s.ctx) {
        return R"json({"ok":false,"error":"Kein Diffusionsmodell geladen"})json";
    }
    if (!s.videoReady) {
        return R"json({"ok":false,"error":"Dieses Checkpoint hat keinen Video-Pfad. Fuer AnimateDiff ein SD1.5-Modell plus Motion-Modul laden."})json";
    }

    s.cancelRequested.store(false);
    s.progressStep.store(0);
    s.progressSteps.store(0);

    sd_vid_gen_opt_t* opt = sd_vid_gen_opt_create();
    if (!opt) {
        return R"json({"ok":false,"error":"Video-Optionen konnten nicht erzeugt werden"})json";
    }

    sd_status_t rc = sd_opt_set_string(opt, "prompt", params.prompt.c_str());
    if (rc == SD_STATUS_OK && !params.negativePrompt.empty()) {
        rc = sd_opt_set_string(opt, "negative_prompt", params.negativePrompt.c_str());
    }
    if (rc == SD_STATUS_OK) rc = sd_opt_set_int(opt, "width", params.width);
    if (rc == SD_STATUS_OK) rc = sd_opt_set_int(opt, "height", params.height);
    if (rc == SD_STATUS_OK) rc = sd_opt_set_int(opt, "video_frames", params.frames);
    if (rc == SD_STATUS_OK) rc = sd_opt_set_int(opt, "fps", params.fps);
    if (rc == SD_STATUS_OK) rc = sd_opt_set_int(opt, "sample.steps", params.steps);
    if (rc == SD_STATUS_OK && !params.sampler.empty()) {
        rc = sd_opt_set_string(opt, "sample.method", params.sampler.c_str());
    }
    if (rc == SD_STATUS_OK) rc = sd_opt_set_float(opt, "sample.guidance.txt_cfg", params.cfg);
    if (rc == SD_STATUS_OK) rc = sd_opt_set_int(opt, "seed", params.seed);
    if (rc == SD_STATUS_OK) rc = sd_opt_set_bool(opt, "vae_tiling.enabled", true);
    if (rc == SD_STATUS_OK && !params.loraPath.empty()) {
        sd_lora_t lora{};
        lora.is_high_noise = false;
        lora.multiplier = params.loraScale;
        lora.path = params.loraPath.c_str();
        rc = sd_opt_set_loras(opt, "loras", &lora, 1);
    }

    LOGI("SD video generate backend=%s sampler=%s frames=%d steps=%d cfg=%.2f seed=%lld",
         s.backend.c_str(), params.sampler.c_str(), params.frames, params.steps, params.cfg, params.seed);
    sd_result_t* result = nullptr;
    const auto start = std::chrono::steady_clock::now();
    if (rc == SD_STATUS_OK) rc = sd_generate_video(s.ctx, opt, &result);
    const auto end = std::chrono::steady_clock::now();
    const long long ms = std::chrono::duration_cast<std::chrono::milliseconds>(end - start).count();
    const std::string error = rc == SD_STATUS_OK ? "" : lastError();
    sd_opt_free(opt);

    if (rc != SD_STATUS_OK || !result) {
        if (result) sd_result_free(result);
        LOGE("SD video generation failed: %s", error.c_str());
        std::ostringstream json;
        json << "{\"ok\":false,\"status\":\"" << statusName(rc) << "\",\"ms\":" << ms
             << ",\"error\":\"" << jsonEscape(error) << "\"}";
        return json.str();
    }

    const sd_video_t* video = sd_result_get_video(result);
    const sd_tensor_t* tensor = video ? video->data : nullptr;
    if (!tensor || sd_tensor_type(tensor) != SD_TENSOR_U8 || sd_tensor_rank(tensor) != 4) {
        const std::string why = "Modell lieferte keinen U8-Videotensor";
        sd_result_free(result);
        return "{\"ok\":false,\"error\":\"" + jsonEscape(why) + "\"}";
    }

    const int64_t channels = sd_tensor_dim(tensor, 0);
    const int64_t width = sd_tensor_dim(tensor, 1);
    const int64_t height = sd_tensor_dim(tensor, 2);
    const int64_t frameCount = sd_tensor_dim(tensor, 3);
    const size_t expected = static_cast<size_t>(channels) * width * height * frameCount;
    const void* data = sd_tensor_data(tensor);
    if (channels < 3 || width <= 0 || height <= 0 || frameCount <= 0 ||
        !data || sd_tensor_byte_size(tensor) < expected) {
        sd_result_free(result);
        return R"json({"ok":false,"error":"Ungueltige Videotensor-Form"})json";
    }

    if (mkdir(params.outputDir.c_str(), 0700) != 0) {
        struct stat st{};
        if (stat(params.outputDir.c_str(), &st) != 0 || !S_ISDIR(st.st_mode)) {
            sd_result_free(result);
            return R"json({"ok":false,"error":"Video-Ausgabeordner konnte nicht angelegt werden"})json";
        }
    }

    const auto* pixels = static_cast<const uint8_t*>(data);
    // sd_result_get_video packs whole frames one after another: [T][H][W][C].
    // The public shape is [C,W,H,T], but sd::Image data is HWC-interleaved
    // and the stride between frames is C*W*H.
    const size_t frameStride = static_cast<size_t>(channels) * width * height;
    int written = 0;
    for (int64_t t = 0; t < frameCount; ++t) {
        std::vector<uint8_t> rgb(static_cast<size_t>(width) * height * 3);
        for (int64_t y = 0; y < height; ++y) {
            for (int64_t x = 0; x < width; ++x) {
                const size_t dst = (static_cast<size_t>(y) * width + x) * 3;
                for (int64_t c = 0; c < 3; ++c) {
                    const size_t src = static_cast<size_t>(t) * frameStride +
                        (static_cast<size_t>(y) * width + x) * channels + c;
                    rgb[dst + c] = pixels[src];
                }
            }
        }
        char framePath[PATH_MAX];
        snprintf(framePath, sizeof(framePath), "%s/frame_%04lld.png", params.outputDir.c_str(),
                 static_cast<long long>(t));
        if (!stbi_write_png(framePath, static_cast<int>(width), static_cast<int>(height), 3,
                            rgb.data(), static_cast<int>(width) * 3)) {
            sd_result_free(result);
            return R"json({"ok":false,"error":"Video-Frame konnte nicht geschrieben werden"})json";
        }
        ++written;
    }
    const int outFps = video->fps > 0 ? video->fps : params.fps;
    sd_result_free(result);

    LOGI("SD video done: %dx%d, %d frames, %d fps in %lld ms", (int)width, (int)height,
         written, outFps, ms);
    std::ostringstream json;
    json << "{\"ok\":true,\"width\":" << width << ",\"height\":" << height
         << ",\"frames\":" << written << ",\"fps\":" << outFps
         << ",\"ms\":" << ms << ",\"seed\":" << params.seed
         << ",\"dir\":\"" << jsonEscape(params.outputDir) << "\"}";
    return json.str();
}

void ImageEngine::cancel() {
    State& s = state();
    s.cancelRequested.store(true);
    if (s.ctx) sd_cancel_generation(s.ctx, SD_CANCEL_ALL);
}


std::string ImageEngine::loadControlNet(const std::string& path) {
    State& s = state();
    std::lock_guard<std::mutex> lock(s.mutex);
    if (!s.ctx) return R"json({"ok":false,"error":"Kein Diffusionsmodell geladen"})json";
    if (path.empty()) {
        if (s.ctx) sd_ctx_unload_control_net(s.ctx);
        s.controlNetLoaded = false;
        s.controlNetPath.clear();
        return R"json({"ok":true})json";
    }
    if (!sd_ctx_load_control_net(s.ctx, path.c_str())) {
        const std::string error = lastError();
        LOGE("ControlNet load failed: %s", error.c_str());
        return "{\"ok\":false,\"error\":\"" + jsonEscape(error) + "\"}";
    }
    s.controlNetLoaded = true;
    s.controlNetPath = path;
    LOGI("ControlNet loaded: %s", path.c_str());
    return "{\"ok\":true,\"path\":\"" + jsonEscape(path) + "\"}";
}

std::string ImageEngine::unloadControlNet() {
    State& s = state();
    std::lock_guard<std::mutex> lock(s.mutex);
    if (s.ctx) sd_ctx_unload_control_net(s.ctx);
    s.controlNetLoaded = false;
    s.controlNetPath.clear();
    return R"json({"ok":true})json";
}

} // namespace galaxya56
