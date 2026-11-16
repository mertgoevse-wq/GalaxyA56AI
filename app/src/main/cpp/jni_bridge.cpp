#include <jni.h>

#include <android/log.h>

#include <string>

#include "enn_engine.h"
#include "hardware_detector.h"
#include "image_engine.h"
#include "llama_engine.h"
#include "npu_probe.h"

#define LOG_TAG "A56AI-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

std::string jstringToString(JNIEnv* env, jstring value) {
    if (!value) return "";
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string out(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return out;
}

jstring stringToJstring(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

std::string escapeJson(const std::string& in) {
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

} // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_galaxya56_ai_inference_LocalInference_nativeHardwareJson(JNIEnv* env, jobject) {
    return stringToJstring(env, galaxya56::hardwareJson());
}

JNIEXPORT jstring JNICALL
Java_com_galaxya56_ai_inference_LocalInference_nativeLoadModel(
        JNIEnv* env, jobject, jstring path, jint nCtx, jint nThreads, jint gpuLayers) {
    const std::string modelPath = jstringToString(env, path);
    LOGI("Loading %s (ctx=%d threads=%d gpuLayers=%d)", modelPath.c_str(), nCtx, nThreads, gpuLayers);
    return stringToJstring(env,
        galaxya56::LlamaEngine::instance().loadModel(modelPath, nCtx, nThreads, gpuLayers));
}

JNIEXPORT jstring JNICALL
Java_com_galaxya56_ai_inference_LocalInference_nativeUnload(JNIEnv* env, jobject) {
    return stringToJstring(env, galaxya56::LlamaEngine::instance().unload());
}

JNIEXPORT jstring JNICALL
Java_com_galaxya56_ai_inference_LocalInference_nativeModelInfo(JNIEnv* env, jobject) {
    return stringToJstring(env, galaxya56::LlamaEngine::instance().infoJson());
}

JNIEXPORT jstring JNICALL
Java_com_galaxya56_ai_inference_LocalInference_nativeApplyLora(
        JNIEnv* env, jobject, jstring path, jfloat scale) {
    return stringToJstring(env,
        galaxya56::LlamaEngine::instance().applyLora(jstringToString(env, path), scale));
}

JNIEXPORT jstring JNICALL
Java_com_galaxya56_ai_inference_LocalInference_nativeClearLoras(JNIEnv* env, jobject) {
    return stringToJstring(env, galaxya56::LlamaEngine::instance().clearLoras());
}

JNIEXPORT jstring JNICALL
Java_com_galaxya56_ai_inference_LocalInference_nativeGenerate(
        JNIEnv* env, jobject, jstring prompt, jint maxTokens, jfloat temperature,
        jfloat topP, jint topK, jfloat repeatPenalty, jobject callback) {
    galaxya56::GenParams params;
    params.maxTokens = maxTokens;
    params.temperature = temperature;
    params.topP = topP;
    params.topK = topK;
    params.repeatPenalty = repeatPenalty;

    jclass callbackClass = nullptr;
    jmethodID onToken = nullptr;
    if (callback) {
        callbackClass = env->GetObjectClass(callback);
        onToken = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    }

    auto emit = [&](const std::string& piece) {
        if (!callback || !onToken) return;
        jstring jpiece = env->NewStringUTF(piece.c_str());
        if (!jpiece) return;
        env->CallVoidMethod(callback, onToken, jpiece);
        env->DeleteLocalRef(jpiece);
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
    };

    galaxya56::GenResult result = galaxya56::LlamaEngine::instance().generate(
        jstringToString(env, prompt), params, emit);

    if (callbackClass) env->DeleteLocalRef(callbackClass);

    std::string json = "{\"ok\":" + std::string(result.ok ? "true" : "false") +
        ",\"text\":\"" + escapeJson(result.text) + "\"" +
        ",\"tokens\":" + std::to_string(result.tokens) +
        ",\"promptMs\":" + std::to_string(result.promptMs) +
        ",\"ms\":" + std::to_string(result.genMs) +
        ",\"stopped\":" + (result.stopped ? "true" : "false") +
        ",\"contextFull\":" + (result.contextFull ? "true" : "false") +
        ",\"error\":\"" + escapeJson(result.error) + "\"}";
    return stringToJstring(env, json);
}

JNIEXPORT void JNICALL
Java_com_galaxya56_ai_inference_LocalInference_nativeStop(JNIEnv*, jobject) {
    galaxya56::LlamaEngine::instance().cancel();
}

JNIEXPORT jstring JNICALL
Java_com_galaxya56_ai_inference_LocalInference_nativeBackend(JNIEnv* env, jobject) {
#ifdef GALAXYA56_HAS_VULKAN
    return stringToJstring(env, "llama.cpp b6390 · CPU/NEON + Vulkan (ARM64)");
#else
    return stringToJstring(env, "llama.cpp b6390 (CPU/NEON, ARM64)");
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_galaxya56_ai_inference_LocalInference_nativeNnapiProbe(JNIEnv* env, jobject) {
    const std::string json = galaxya56::nnapiProbeJson();
    LOGI("NNAPI probe: %s", json.c_str());
    return stringToJstring(env, json);
}

JNIEXPORT jstring JNICALL
Java_com_galaxya56_ai_inference_LocalInference_nativeEnnProbe(JNIEnv* env, jobject) {
    const std::string json = galaxya56::EnnEngine::instance().probeJson();
    LOGI("ENN probe: %s", json.c_str());
    return stringToJstring(env, json);
}

JNIEXPORT jstring JNICALL
Java_com_galaxya56_ai_inference_LocalInference_nativeEnnBenchmark(
        JNIEnv* env, jobject, jstring path) {
    const std::string modelPath = jstringToString(env, path);
    LOGI("ENN benchmark: %s", modelPath.c_str());
    return stringToJstring(env,
        galaxya56::EnnEngine::instance().benchmarkJson(modelPath));
}

JNIEXPORT jstring JNICALL
Java_com_galaxya56_ai_inference_LocalInference_nativeEnnUpscale(
        JNIEnv* env, jobject, jstring inputPath, jstring outputPath, jstring modelPath) {
    const std::string in = jstringToString(env, inputPath);
    const std::string out = jstringToString(env, outputPath);
    const std::string model = jstringToString(env, modelPath);
    LOGI("ENN upscale: %s -> %s (model %s)", in.c_str(), out.c_str(), model.c_str());
    return stringToJstring(env,
        galaxya56::EnnEngine::instance().upscaleJson(in, out, model));
}

JNIEXPORT jstring JNICALL
Java_com_galaxya56_ai_inference_LocalInference_nativeImageLoad(
        JNIEnv* env, jobject, jstring path, jint threads, jstring backendMode) {
    const std::string modelPath = jstringToString(env, path);
    const std::string backend = jstringToString(env, backendMode);
    LOGI("Loading diffusion model %s (threads=%d backend=%s)", modelPath.c_str(), threads, backend.c_str());
    return stringToJstring(env,
        galaxya56::ImageEngine::instance().loadModel(modelPath, threads, backend));
}

JNIEXPORT jstring JNICALL
Java_com_galaxya56_ai_inference_LocalInference_nativeImageUnload(JNIEnv* env, jobject) {
    return stringToJstring(env, galaxya56::ImageEngine::instance().unload());
}

JNIEXPORT jstring JNICALL
Java_com_galaxya56_ai_inference_LocalInference_nativeImageStatus(JNIEnv* env, jobject) {
    return stringToJstring(env, galaxya56::ImageEngine::instance().statusJson());
}

JNIEXPORT jstring JNICALL
Java_com_galaxya56_ai_inference_LocalInference_nativeImageGenerate(
        JNIEnv* env, jobject, jstring prompt, jstring negativePrompt, jint width, jint height,
        jint steps, jfloat cfg, jboolean vaeTiling, jlong seed, jstring sampler,
        jstring loraPath, jfloat loraScale, jstring outputPath) {
    galaxya56::ImageGenParams params;
    params.prompt = jstringToString(env, prompt);
    params.negativePrompt = jstringToString(env, negativePrompt);
    params.width = width;
    params.height = height;
    params.steps = steps;
    params.cfg = cfg;
    params.vaeTiling = vaeTiling == JNI_TRUE;
    params.seed = seed;
    params.sampler = jstringToString(env, sampler);
    params.loraPath = jstringToString(env, loraPath);
    params.loraScale = loraScale;
    params.outputPath = jstringToString(env, outputPath);
    return stringToJstring(env, galaxya56::ImageEngine::instance().generate(params));
}

JNIEXPORT void JNICALL
Java_com_galaxya56_ai_inference_LocalInference_nativeImageCancel(JNIEnv*, jobject) {
    galaxya56::ImageEngine::instance().cancel();
}

} // extern "C"
