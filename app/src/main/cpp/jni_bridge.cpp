#include <jni.h>

#include <android/log.h>

#include <string>

#include "hardware_detector.h"
#include "llama_engine.h"

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
        JNIEnv* env, jobject, jstring path, jint nCtx, jint nThreads) {
    const std::string modelPath = jstringToString(env, path);
    LOGI("Loading %s (ctx=%d threads=%d)", modelPath.c_str(), nCtx, nThreads);
    return stringToJstring(env,
        galaxya56::LlamaEngine::instance().loadModel(modelPath, nCtx, nThreads));
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
    return stringToJstring(env, "llama.cpp b6390 (CPU/NEON, ARM64)");
}

} // extern "C"
