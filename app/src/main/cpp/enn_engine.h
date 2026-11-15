#pragma once

#include <string>

namespace galaxya56 {

/**
 * Samsung ENN (Exynos Neural Network) runtime.
 *
 * The public ENN API is shipped by Samsung as part of the vendor image
 * (libenn_public_api_cpp.so, listed in /vendor/etc/public.libraries.txt).
 * We load it dynamically so no proprietary header or binary is vendored into
 * this repository. Models are .nnc files compiled by Samsung's ENN compiler.
 */
class EnnEngine {
public:
    static EnnEngine& instance();

    /** Initializes ENN and reports framework version / NPU preference state. */
    std::string probeJson();

    /** Opens a .nnc model, allocates its buffers, executes once, checks output. */
    std::string benchmarkJson(const std::string& modelPath);

    /** 4x NPU upscale with a MIDAS-compatible model (256x256 -> 1024x1024). */
    std::string upscaleJson(const std::string& inputPath, const std::string& outputPath,
                            const std::string& modelPath);

private:
    EnnEngine() = default;
};

} // namespace galaxya56
