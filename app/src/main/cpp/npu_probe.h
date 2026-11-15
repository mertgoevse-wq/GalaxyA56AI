#pragma once

#include <string>

namespace galaxya56 {

// Executes a tiny ADD graph through Android NNAPI on the vendor accelerator
// (NPU/DSP) when one is exposed, and returns a JSON report with device names,
// feature levels, the chosen device, output values and latency.
std::string nnapiProbeJson();

} // namespace galaxya56
