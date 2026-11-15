#pragma once

#include <string>

namespace galaxya56 {

// Returns a JSON object describing the device. Implemented with Linux/procfs,
// Android system properties, Vulkan enumeration and NNAPI device discovery.
std::string hardwareJson();

} // namespace galaxya56
