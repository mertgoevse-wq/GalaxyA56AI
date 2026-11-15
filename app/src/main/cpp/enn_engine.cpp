#include "enn_engine.h"

#include <android/log.h>
#include <dlfcn.h>

#include <algorithm>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

#define STB_IMAGE_IMPLEMENTATION
#include "third_party/stable-diffusion.cpp/thirdparty/stb_image.h"
#define STB_IMAGE_RESIZE_IMPLEMENTATION
#include "third_party/stable-diffusion.cpp/thirdparty/stb_image_resize.h"
#include "third_party/stable-diffusion.cpp/thirdparty/stb_image_write.h"

#define LOG_TAG "A56AI-ENN"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace galaxya56 {
namespace {

// ABI-compatible mirrors of the public ENN NDK types (enn_api-type_ndk_v1.h).
using EnnModelId = uint64_t;
using EnnReturn = int32_t;

struct EnnBuffer {
    void* va;
    uint32_t size;
    uint32_t offset;
};

struct NumberOfBuffersInfo {
    uint32_t n_in_buf;
    uint32_t n_out_buf;
};

struct EnnBufferInfo {
    bool is_able_to_update;
    uint32_t n;
    uint32_t width;
    uint32_t height;
    uint32_t channel;
    uint32_t size;
    uint32_t buffer_type;
    const char* label;
};

enum EnnBufDir {
    ENN_DIR_IN = 0,
    ENN_DIR_OUT = 1,
    ENN_DIR_EXT = 2,
};

enum EnnMetaType {
    ENN_META_VERSION_FRAMEWORK = 110,
    ENN_META_VERSION_COMMIT = 111,
    ENN_META_VERSION_MODEL_COMPILER_NNC = 120,
    ENN_META_VERSION_MODEL_COMPILER_NPU = 121,
    ENN_META_VERSION_UNIFIED_FW = 131,
    ENN_META_VERSION_NPU_FW = 132,
};

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

const char* returnName(EnnReturn rc) {
    switch (rc) {
        case 0: return "success";
        case 1: return "failed";
        case 2: return "io";
        case 3: return "invalid_argument";
        case 4: return "filtered";
        case 5: return "out_of_memory";
        case 6: return "size";
        case 10: return "timeout_enn";
        case 11: return "timeout_dd";
        case 12: return "timeout_fw";
        case 13: return "timeout_hw_not_recovered";
        case 14: return "timeout_hw_recovered";
        case 15: return "service_null";
        case 16: return "resource_busy";
        case 255: return "not_supported";
        default: return "unknown";
    }
}

/**
 * Minimal loader for Samsung's public ENN C++ API. Symbol names are stable
 * (Itanium ABI); the vendor library is a public Android library, so dlopen by
 * soname works without shipping anything proprietary in the APK.
 */
struct EnnApi {
    void* handle = nullptr;
    bool loaded = false;
    bool initialized = false;

    using FnInitialize = EnnReturn (*)();
    using FnDeinitialize = EnnReturn (*)();
    using FnOpenModel = EnnReturn (*)(const char*, EnnModelId*);
    using FnCloseModel = EnnReturn (*)(EnnModelId);
    using FnAllocateAllBuffers = EnnReturn (*)(EnnModelId, EnnBuffer***, NumberOfBuffersInfo*, int, bool);
    using FnReleaseBuffers = EnnReturn (*)(EnnBuffer**, int32_t);
    using FnExecuteModel = EnnReturn (*)(EnnModelId, int);
    using FnGetBuffersInfo = EnnReturn (*)(NumberOfBuffersInfo*, EnnModelId);
    using FnGetBufferInfoByIndex = EnnReturn (*)(EnnBufferInfo*, EnnModelId, int, uint32_t);
    using FnGetMetaInfo = EnnReturn (*)(int, EnnModelId, char*);
    using FnSetPerfMode = EnnReturn (*)(uint32_t);
    using FnGetPerfMode = EnnReturn (*)(uint32_t*);

    FnInitialize initialize = nullptr;
    FnDeinitialize deinitialize = nullptr;
    FnOpenModel openModel = nullptr;
    FnCloseModel closeModel = nullptr;
    FnAllocateAllBuffers allocateAllBuffers = nullptr;
    FnReleaseBuffers releaseBuffers = nullptr;
    FnExecuteModel executeModel = nullptr;
    FnGetBuffersInfo getBuffersInfo = nullptr;
    FnGetBufferInfoByIndex getBufferInfoByIndex = nullptr;
    FnGetMetaInfo getMetaInfo = nullptr;
    FnSetPerfMode setPerfMode = nullptr;
    FnGetPerfMode getPerfMode = nullptr;

    bool load(std::string& error) {
        if (loaded) return true;

        // The vendor libs live in /vendor/lib64 and are listed as public
        // Android libraries. Try both the soname (app namespace) and the
        // absolute vendor path (some Samsung builds only expose the latter).
        const char* userCandidates[] = {
            "libenn_user.samsung_slsi.so",
            "/vendor/lib64/libenn_user.samsung_slsi.so",
        };
        for (const char* name : userCandidates) {
            void* user = dlopen(name, RTLD_NOW | RTLD_GLOBAL);
            if (user) {
                LOGI("ENN user lib loaded: %s", name);
                break;
            }
            const char* dlerr = dlerror();
            LOGE("ENN user lib failed (%s): %s", name, dlerr ? dlerr : "?");
        }

        const char* candidates[] = {
            "libenn_public_api_cpp.so",
            "/vendor/lib64/libenn_public_api_cpp.so",
            "libenn_public_api_cpp_lib.so",
            "/vendor/lib64/libenn_public_api_cpp_lib.so",
        };
        for (const char* name : candidates) {
            handle = dlopen(name, RTLD_NOW | RTLD_LOCAL);
            if (handle) {
                LOGI("ENN public API loaded: %s", name);
                break;
            }
            const char* dlerr = dlerror();
            LOGE("ENN public API failed (%s): %s", name, dlerr ? dlerr : "?");
            error = dlerr ? dlerr : "libenn_public_api_cpp.so nicht ladbar";
        }
        if (!handle) {
            LOGE("ENN dlopen failed: %s", error.c_str());
            return false;
        }

        auto sym = [&](const char* name) -> void* { return dlsym(handle, name); };
        initialize = reinterpret_cast<FnInitialize>(sym("_ZN3enn3api13EnnInitializeEv"));
        deinitialize = reinterpret_cast<FnDeinitialize>(sym("_ZN3enn3api15EnnDeinitializeEv"));
        openModel = reinterpret_cast<FnOpenModel>(sym("_ZN3enn3api12EnnOpenModelEPKcPm"));
        closeModel = reinterpret_cast<FnCloseModel>(sym("_ZN3enn3api13EnnCloseModelEm"));
        allocateAllBuffers = reinterpret_cast<FnAllocateAllBuffers>(
            sym("_ZN3enn3api21EnnAllocateAllBuffersEmPPP10_ennBufferP20_NumberOfBuffersInfoib"));
        releaseBuffers = reinterpret_cast<FnReleaseBuffers>(
            sym("_ZN3enn3api17EnnReleaseBuffersEPP10_ennBufferi"));
        executeModel = reinterpret_cast<FnExecuteModel>(sym("_ZN3enn3api15EnnExecuteModelEmi"));
        getBuffersInfo = reinterpret_cast<FnGetBuffersInfo>(
            sym("_ZN3enn3api17EnnGetBuffersInfoEP20_NumberOfBuffersInfom"));
        getBufferInfoByIndex = reinterpret_cast<FnGetBufferInfoByIndex>(
            sym("_ZN3enn3api23EnnGetBufferInfoByIndexEP14_ennBufferInfom14_enn_buf_dir_ej"));
        getMetaInfo = reinterpret_cast<FnGetMetaInfo>(
            sym("_ZN3enn3api14EnnGetMetaInfoE19_enn_meta_type_id_emPc"));
        setPerfMode = reinterpret_cast<FnSetPerfMode>(
            sym("_ZN3enn3api24EnnSetPreferencePerfModeEj"));
        getPerfMode = reinterpret_cast<FnGetPerfMode>(
            sym("_ZN3enn3api24EnnGetPreferencePerfModeEPj"));

        if (!initialize || !deinitialize || !openModel || !closeModel ||
            !allocateAllBuffers || !releaseBuffers || !executeModel ||
            !getBuffersInfo || !getBufferInfoByIndex || !getMetaInfo) {
            error = "ENN-Symbole unvollständig (API-Version inkompatibel)";
            LOGE("%s", error.c_str());
            dlclose(handle);
            handle = nullptr;
            return false;
        }
        loaded = true;
        return true;
    }
};

EnnApi& api() {
    static EnnApi instance;
    return instance;
}

std::mutex& ennMutex() {
    static std::mutex mutex;
    return mutex;
}

std::string metaString(EnnApi& a, int type, EnnModelId model = 0) {
    char buffer[256];
    std::memset(buffer, 0, sizeof(buffer));
    EnnReturn rc = a.getMetaInfo(type, model, buffer);
    if (rc != 0 || buffer[0] == '\0') return "";
    std::string value(buffer);
    if (value == "Failed") return "";
    return value;
}

std::string bufferInfoJson(EnnApi& a, EnnModelId model, int dir, uint32_t index) {
    EnnBufferInfo info{};
    EnnReturn rc = a.getBufferInfoByIndex(&info, model, dir, index);
    std::ostringstream json;
    if (rc != 0) {
        json << "{\"ok\":false,\"code\":" << rc << ",\"error\":\"" << returnName(rc) << "\"}";
        return json.str();
    }
    json << "{\"ok\":true,\"n\":" << info.n
         << ",\"width\":" << info.width
         << ",\"height\":" << info.height
         << ",\"channel\":" << info.channel
         << ",\"size\":" << info.size
         << ",\"type\":" << info.buffer_type
         << ",\"label\":\"" << jsonEscape(info.label ? info.label : "") << "\"}";
    return json.str();
}

uint64_t fnv1a(const uint8_t* data, size_t size) {
    uint64_t hash = 1469598103934665603ULL;
    for (size_t i = 0; i < size; ++i) {
        hash ^= data[i];
        hash *= 1099511628211ULL;
    }
    return hash;
}

size_t nonZeroBytes(const uint8_t* data, size_t size) {
    size_t count = 0;
    for (size_t i = 0; i < size; ++i) {
        if (data[i] != 0) ++count;
    }
    return count;
}

} // namespace

EnnEngine& EnnEngine::instance() {
    static EnnEngine engine;
    return engine;
}

std::string EnnEngine::probeJson() {
    std::lock_guard<std::mutex> lock(ennMutex());
    EnnApi& a = api();
    std::string error;
    if (!a.load(error)) {
        std::ostringstream json;
        json << "{\"ok\":false,\"available\":false,\"error\":\"" << jsonEscape(error) << "\"}";
        return json.str();
    }

    if (!a.initialized) {
        EnnReturn rc = a.initialize();
        if (rc != 0) {
            std::ostringstream json;
            json << "{\"ok\":false,\"available\":false,\"stage\":\"initialize\",\"code\":" << rc
                 << ",\"error\":\"" << returnName(rc) << "\"}";
            LOGE("ENN initialize failed: %s (%d)", returnName(rc), rc);
            return json.str();
        }
        a.initialized = true;
    }

    const std::string framework = metaString(a, ENN_META_VERSION_FRAMEWORK);
    const std::string commit = metaString(a, ENN_META_VERSION_COMMIT);
    const std::string unified = metaString(a, ENN_META_VERSION_UNIFIED_FW);
    const std::string npuFw = metaString(a, ENN_META_VERSION_NPU_FW);
    uint32_t perfMode = 0;
    EnnReturn perfRc = a.getPerfMode ? a.getPerfMode(&perfMode) : 255;

    LOGI("ENN available: framework=%s commit=%s unified=%s npu=%s perf=%u",
         framework.c_str(), commit.c_str(), unified.c_str(), npuFw.c_str(), perfMode);

    std::ostringstream json;
    json << "{\"ok\":true,\"available\":true"
         << ",\"framework\":\"" << jsonEscape(framework) << "\""
         << ",\"commit\":\"" << jsonEscape(commit) << "\""
         << ",\"unifiedFw\":\"" << jsonEscape(unified) << "\""
         << ",\"npuFw\":\"" << jsonEscape(npuFw) << "\""
         << ",\"perfMode\":" << perfMode
         << ",\"perfModeReadOk\":" << (perfRc == 0 ? "true" : "false")
         << "}";
    return json.str();
}

std::string EnnEngine::benchmarkJson(const std::string& modelPath) {
    std::lock_guard<std::mutex> lock(ennMutex());
    EnnApi& a = api();
    std::string error;
    if (!a.load(error)) {
        std::ostringstream json;
        json << "{\"ok\":false,\"available\":false,\"error\":\"" << jsonEscape(error) << "\"}";
        return json.str();
    }

    if (!a.initialized) {
        EnnReturn rc = a.initialize();
        if (rc != 0) {
            std::ostringstream json;
            json << "{\"ok\":false,\"stage\":\"initialize\",\"code\":" << rc
                 << ",\"error\":\"" << returnName(rc) << "\"}";
            return json.str();
        }
        a.initialized = true;
    }

    if (a.setPerfMode) {
        a.setPerfMode(7); // ENN_PREF_MODE_PERFORMANCE
    }

    auto loadStart = std::chrono::steady_clock::now();
    EnnModelId model = 0;
    EnnReturn rc = a.openModel(modelPath.c_str(), &model);
    auto loadEnd = std::chrono::steady_clock::now();
    const long long loadMs =
        std::chrono::duration_cast<std::chrono::milliseconds>(loadEnd - loadStart).count();
    if (rc != 0 || model == 0) {
        LOGE("ENN openModel failed: %s (%d) path=%s", returnName(rc), rc, modelPath.c_str());
        std::ostringstream json;
        json << "{\"ok\":false,\"stage\":\"open_model\",\"code\":" << rc
             << ",\"error\":\"" << returnName(rc) << "\",\"path\":\""
             << jsonEscape(modelPath) << "\"}";
        return json.str();
    }

    const std::string nnc = metaString(a, ENN_META_VERSION_MODEL_COMPILER_NNC, model);
    const std::string npu = metaString(a, ENN_META_VERSION_MODEL_COMPILER_NPU, model);

    NumberOfBuffersInfo info{};
    rc = a.getBuffersInfo(&info, model);
    if (rc != 0 || info.n_in_buf == 0 || info.n_out_buf == 0) {
        a.closeModel(model);
        std::ostringstream json;
        json << "{\"ok\":false,\"stage\":\"buffers_info\",\"code\":" << rc
             << ",\"error\":\"" << returnName(rc) << "\"}";
        return json.str();
    }

    EnnBuffer** buffers = nullptr;
    rc = a.allocateAllBuffers(model, &buffers, &info, 0, true);
    if (rc != 0 || !buffers) {
        a.closeModel(model);
        std::ostringstream json;
        json << "{\"ok\":false,\"stage\":\"allocate\",\"code\":" << rc
             << ",\"error\":\"" << returnName(rc) << "\"}";
        return json.str();
    }

    // Deterministic, non-trivial input so a real execution produces a
    // non-zero output instead of an all-zero buffer.
    size_t inputBytes = 0;
    for (uint32_t i = 0; i < info.n_in_buf; ++i) {
        EnnBuffer* buffer = buffers[i];
        if (!buffer || !buffer->va) continue;
        uint8_t* bytes = static_cast<uint8_t*>(buffer->va);
        for (uint32_t k = 0; k < buffer->size; ++k) {
            bytes[k] = static_cast<uint8_t>((k * 31u + i * 17u) & 0xFFu);
        }
        inputBytes += buffer->size;
    }

    auto execStart = std::chrono::steady_clock::now();
    rc = a.executeModel(model, 0);
    auto execEnd = std::chrono::steady_clock::now();
    const long long execMs =
        std::chrono::duration_cast<std::chrono::milliseconds>(execEnd - execStart).count();

    size_t outputBytes = 0;
    uint64_t checksum = 1469598103934665603ULL;
    size_t nonZero = 0;
    for (uint32_t i = 0; i < info.n_out_buf; ++i) {
        EnnBuffer* buffer = buffers[info.n_in_buf + i];
        if (!buffer || !buffer->va) continue;
        const uint8_t* bytes = static_cast<const uint8_t*>(buffer->va);
        checksum ^= fnv1a(bytes, buffer->size);
        nonZero += nonZeroBytes(bytes, buffer->size);
        outputBytes += buffer->size;
    }

    const std::string inInfo = bufferInfoJson(a, model, ENN_DIR_IN, 0);
    const std::string outInfo = bufferInfoJson(a, model, ENN_DIR_OUT, 0);

    a.releaseBuffers(buffers, static_cast<int32_t>(info.n_in_buf + info.n_out_buf));
    a.closeModel(model);

    LOGI("ENN run rc=%s (%d) load=%lldms exec=%lldms in=%zuB out=%zuB nonzero=%zu",
         returnName(rc), rc, loadMs, execMs, inputBytes, outputBytes, nonZero);

    std::ostringstream json;
    json << "{\"ok\":" << (rc == 0 ? "true" : "false")
         << ",\"stage\":\"execute\",\"code\":" << rc
         << ",\"error\":\"" << (rc == 0 ? "" : returnName(rc)) << "\""
         << ",\"model\":\"" << jsonEscape(modelPath) << "\""
         << ",\"nnc\":\"" << jsonEscape(nnc) << "\""
         << ",\"npuCompiler\":\"" << jsonEscape(npu) << "\""
         << ",\"loadMs\":" << loadMs
         << ",\"execMs\":" << execMs
         << ",\"nIn\":" << info.n_in_buf
         << ",\"nOut\":" << info.n_out_buf
         << ",\"inputBytes\":" << inputBytes
         << ",\"outputBytes\":" << outputBytes
         << ",\"nonZero\":" << nonZero
         << ",\"checksum\":" << checksum
         << ",\"input\":" << inInfo
         << ",\"output\":" << outInfo
         << "}";
    return json.str();
}

std::string EnnEngine::upscaleJson(const std::string& inputPath, const std::string& outputPath,
                                  const std::string& modelPath) {
    std::lock_guard<std::mutex> lock(ennMutex());
    EnnApi& a = api();
    std::string error;
    if (!a.load(error)) {
        return "{\"ok\":false,\"error\":\"" + jsonEscape(error) + "\"}";
    }
    if (!a.initialized) {
        EnnReturn rc = a.initialize();
        if (rc != 0) {
            return "{\"ok\":false,\"stage\":\"initialize\",\"code\":" + std::to_string(rc) +
                   ",\"error\":\"" + returnName(rc) + "\"}";
        }
        a.initialized = true;
    }
    if (a.setPerfMode) a.setPerfMode(7);

    constexpr int kIn = 256;
    constexpr int kOut = 1024;

    int width = 0, height = 0, channels = 0;
    unsigned char* src = stbi_load(inputPath.c_str(), &width, &height, &channels, 3);
    if (!src || width <= 0 || height <= 0) {
        if (src) stbi_image_free(src);
        return R"({"ok":false,"stage":"decode","error":"Eingabebild nicht lesbar"})";
    }

    std::vector<unsigned char> resized(static_cast<size_t>(kIn) * kIn * 3);
    const int resizeOk = stbir_resize_uint8(src, width, height, 0, resized.data(), kIn, kIn, 0, 3);
    stbi_image_free(src);
    if (!resizeOk) {
        return R"({"ok":false,"stage":"resize","error":"Resize auf 256x256 fehlgeschlagen"})";
    }

    std::vector<float> input(static_cast<size_t>(kIn) * kIn * 3);
    const size_t plane = static_cast<size_t>(kIn) * kIn;
    for (int c = 0; c < 3; ++c) {
        for (int y = 0; y < kIn; ++y) {
            for (int x = 0; x < kIn; ++x) {
                const size_t pixel = static_cast<size_t>(y) * kIn + x;
                input[c * plane + pixel] = resized[pixel * 3 + c] / 255.0f;
            }
        }
    }

    // MIDAS outputs a residual (residual_value=0.7 in midas_config.json),
    // which is added to a bicubic 4x base image.
    std::vector<unsigned char> baseResized(static_cast<size_t>(kOut) * kOut * 3);
    if (!stbir_resize_uint8(resized.data(), kIn, kIn, 0, baseResized.data(), kOut, kOut, 0, 3)) {
        return R"({"ok":false,"stage":"resize_base","error":"Basis-Resize fehlgeschlagen"})";
    }

    auto loadStart = std::chrono::steady_clock::now();
    EnnModelId model = 0;
    EnnReturn rc = a.openModel(modelPath.c_str(), &model);
    const long long loadMs = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - loadStart).count();
    if (rc != 0 || model == 0) {
        return "{\"ok\":false,\"stage\":\"open_model\",\"code\":" + std::to_string(rc) +
               ",\"error\":\"" + returnName(rc) + "\"}";
    }

    NumberOfBuffersInfo info{};
    rc = a.getBuffersInfo(&info, model);
    if (rc != 0 || info.n_in_buf == 0 || info.n_out_buf == 0) {
        a.closeModel(model);
        return "{\"ok\":false,\"stage\":\"buffers_info\",\"code\":" + std::to_string(rc) + "}";
    }

    EnnBuffer** buffers = nullptr;
    rc = a.allocateAllBuffers(model, &buffers, &info, 0, true);
    if (rc != 0 || !buffers || !buffers[0] || !buffers[info.n_in_buf]) {
        if (buffers) a.releaseBuffers(buffers, static_cast<int32_t>(info.n_in_buf + info.n_out_buf));
        a.closeModel(model);
        return "{\"ok\":false,\"stage\":\"allocate\",\"code\":" + std::to_string(rc) + "}";
    }

    const size_t inputBytes = input.size() * sizeof(float);
    const size_t outputBytes = static_cast<size_t>(kOut) * kOut * 3 * sizeof(float);
    if (!buffers[0]->va || buffers[0]->size < inputBytes ||
        !buffers[info.n_in_buf]->va || buffers[info.n_in_buf]->size < outputBytes) {
        const std::string detail = "Buffer zu klein: in=" + std::to_string(buffers[0]->size) +
            " out=" + std::to_string(buffers[info.n_in_buf]->size);
        a.releaseBuffers(buffers, static_cast<int32_t>(info.n_in_buf + info.n_out_buf));
        a.closeModel(model);
        return "{\"ok\":false,\"stage\":\"buffer_size\",\"error\":\"" + jsonEscape(detail) + "\"}";
    }

    std::memcpy(buffers[0]->va, input.data(), inputBytes);
    auto execStart = std::chrono::steady_clock::now();
    rc = a.executeModel(model, 0);
    const long long execMs = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - execStart).count();
    if (rc != 0) {
        a.releaseBuffers(buffers, static_cast<int32_t>(info.n_in_buf + info.n_out_buf));
        a.closeModel(model);
        return "{\"ok\":false,\"stage\":\"execute\",\"code\":" + std::to_string(rc) +
               ",\"error\":\"" + returnName(rc) + "\"}";
    }

    const float* output = static_cast<const float*>(buffers[info.n_in_buf]->va);
    float minValue = 1e9f;
    float maxValue = -1e9f;
    double sum = 0.0;
    for (size_t i = 0; i < static_cast<size_t>(kOut) * kOut * 3; ++i) {
        minValue = std::min(minValue, output[i]);
        maxValue = std::max(maxValue, output[i]);
        sum += output[i];
    }
    LOGI("ENN upscale residual: min=%f max=%f mean=%f", minValue, maxValue,
         sum / static_cast<double>(static_cast<size_t>(kOut) * kOut * 3));
    constexpr float kResidual = 0.7f;
    std::vector<unsigned char> rgb(static_cast<size_t>(kOut) * kOut * 3);
    const size_t outPlane = static_cast<size_t>(kOut) * kOut;
    for (int c = 0; c < 3; ++c) {
        for (int y = 0; y < kOut; ++y) {
            for (int x = 0; x < kOut; ++x) {
                const size_t pixel = static_cast<size_t>(y) * kOut + x;
                const float base = baseResized[pixel * 3 + c] / 255.0f;
                const float value = std::clamp(base + output[c * outPlane + pixel] * kResidual,
                                               0.0f, 1.0f);
                rgb[pixel * 3 + c] = static_cast<unsigned char>(value * 255.0f + 0.5f);
            }
        }
    }

    const bool written = stbi_write_png(outputPath.c_str(), kOut, kOut, 3, rgb.data(), kOut * 3) != 0;
    const uint64_t checksum = fnv1a(reinterpret_cast<const uint8_t*>(output), outputBytes);
    a.releaseBuffers(buffers, static_cast<int32_t>(info.n_in_buf + info.n_out_buf));
    a.closeModel(model);

    if (!written) {
        return R"({"ok":false,"stage":"encode","error":"PNG konnte nicht geschrieben werden"})";
    }

    LOGI("ENN upscale done: %dx%d -> %dx%d in %lld ms (load %lld ms)",
         width, height, kOut, kOut, execMs, loadMs);
    std::ostringstream json;
    json << "{\"ok\":true,\"path\":\"" << jsonEscape(outputPath) << "\""
         << ",\"inputWidth\":" << width << ",\"inputHeight\":" << height
         << ",\"width\":" << kOut << ",\"height\":" << kOut
         << ",\"loadMs\":" << loadMs << ",\"ms\":" << execMs
         << ",\"checksum\":" << checksum << "}";
    return json.str();
}

} // namespace galaxya56
