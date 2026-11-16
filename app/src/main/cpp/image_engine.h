#pragma once

#include <string>

namespace galaxya56 {

struct ImageGenParams {
    std::string prompt;
    std::string negativePrompt;
    int width = 512;
    int height = 512;
    int steps = 20;
    float cfg = 7.0f;
    bool vaeTiling = false;
    long long seed = -1;
    std::string sampler = "euler_a";
    std::string loraPath;
    float loraScale = 0.8f;
    std::string controlImagePath;
    float controlStrength = 0.9f;
    std::string controlType = "none";
    bool controlCanny = false;
    std::string outputPath;
};

class ImageEngine {
public:
    static ImageEngine& instance();

    std::string loadModel(const std::string& path, int threads, const std::string& backendMode);
    std::string unload();
    std::string statusJson();
    std::string generate(const ImageGenParams& params);
    std::string loadControlNet(const std::string& path);
    std::string unloadControlNet();
    void cancel();

private:
    ImageEngine() = default;
};

} // namespace galaxya56
