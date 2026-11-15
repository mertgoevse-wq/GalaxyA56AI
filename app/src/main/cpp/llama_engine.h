#pragma once

#include <functional>
#include <string>

namespace galaxya56 {

struct GenParams {
    int maxTokens = 512;
    float temperature = 0.7f;
    float topP = 0.9f;
    int topK = 40;
    float repeatPenalty = 1.1f;
};

struct GenResult {
    bool ok = false;
    bool stopped = false;
    bool contextFull = false;
    std::string error;
    std::string text;
    int tokens = 0;
    long long promptMs = 0;
    long long genMs = 0;
};

class LlamaEngine {
public:
    static LlamaEngine& instance();

    std::string loadModel(const std::string& path, int nCtx, int nThreads);
    std::string unload();
    bool isLoaded();
    std::string infoJson();

    std::string applyLora(const std::string& path, float scale);
    std::string clearLoras();

    GenResult generate(const std::string& prompt, const GenParams& params,
                       const std::function<void(const std::string&)>& onToken);
    void cancel();

private:
    LlamaEngine() = default;
};

} // namespace galaxya56
