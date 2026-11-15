#include "hardware_detector.h"

#include <android/log.h>
#include <dlfcn.h>
#include <sys/system_properties.h>
#include <sys/sysinfo.h>
#include <unistd.h>
#include <vulkan/vulkan.h>

#include <algorithm>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <map>
#include <sstream>
#include <string>
#include <vector>

#define LOG_TAG "A56AI-HW"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace galaxya56 {
namespace {

std::string prop(const char* key) {
    char value[PROP_VALUE_MAX] = {0};
    __system_property_get(key, value);
    return std::string(value);
}

std::string readFile(const std::string& path) {
    std::ifstream f(path);
    if (!f.is_open()) return "";
    std::stringstream ss;
    ss << f.rdbuf();
    return ss.str();
}

std::string jsonEscape(const std::string& in) {
    std::string out;
    out.reserve(in.size() + 8);
    for (char c : in) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if (static_cast<unsigned char>(c) < 0x20) {
                    char buf[8];
                    snprintf(buf, sizeof(buf), "\\u%04x", c);
                    out += buf;
                } else {
                    out += c;
                }
        }
    }
    return out;
}

struct CpuStats {
    int cores = 0;
    int perf = 0;
    int eff = 0;
    double maxGhz = 0.0;
    std::string arch;
};

CpuStats detectCpu() {
    CpuStats s;
    s.cores = static_cast<int>(sysconf(_SC_NPROCESSORS_CONF));
    if (s.cores <= 0) s.cores = 8;

    std::string info = readFile("/proc/cpuinfo");
    std::map<std::string, int> parts;
    {
        std::istringstream stream(info);
        std::string line;
        while (std::getline(stream, line)) {
            if (line.rfind("CPU part", 0) == 0) {
                auto pos = line.find(':');
                if (pos != std::string::npos) {
                    std::string v = line.substr(pos + 1);
                    v.erase(0, v.find_first_not_of(" \t"));
                    parts[v]++;
                }
            } else if (line.rfind("CPU architecture", 0) == 0 && s.arch.empty()) {
                auto pos = line.find(':');
                if (pos != std::string::npos) {
                    s.arch = line.substr(pos + 1);
                    s.arch.erase(0, s.arch.find_first_not_of(" \t"));
                }
            }
        }
    }

    // ARM MIDR part numbers for big/medium vs little cores.
    const std::vector<std::string> big = {"0xd0b", "0xd0d", "0xd4b", "0xd4d", "0xd4e", "0xd47",
                                          "0xd81", "0xd82", "0xd85", "0xd87"};
    for (auto& entry : parts) {
        bool isBig = std::find(big.begin(), big.end(), entry.first) != big.end();
        if (isBig) s.perf += entry.second;
        else s.eff += entry.second;
    }
    if (s.perf == 0) {
        // Unknown MIDR encoding: assume the first half of the clusters are performance cores.
        s.perf = s.cores / 2;
        s.eff = s.cores - s.perf;
    }

    for (int i = 0; i < s.cores; ++i) {
        std::string khz = readFile("/sys/devices/system/cpu/cpu" + std::to_string(i) +
                                   "/cpufreq/cpuinfo_max_freq");
        if (khz.empty()) continue;
        try {
            double ghz = std::stod(khz) / 1000000.0;
            s.maxGhz = std::max(s.maxGhz, ghz);
        } catch (...) {}
    }
    if (s.maxGhz <= 0.0) s.maxGhz = 2.4;
    if (s.arch.empty()) s.arch = "aarch64";
    return s;
}

struct GpuStats {
    bool available = false;
    std::string name;
    std::string vendor;
    int apiVersion = 0;
    int driverVersion = 0;
    bool rayTracing = false;
    bool integerDot = false;
};

uint32_t major(uint32_t v) { return VK_VERSION_MAJOR(v); }
uint32_t minor(uint32_t v) { return VK_VERSION_MINOR(v); }

GpuStats detectGpu() {
    GpuStats g;
    VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
    app.pApplicationName = "GalaxyA56AI";
    app.apiVersion = VK_API_VERSION_1_1;

    VkInstanceCreateInfo ci{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
    ci.pApplicationInfo = &app;

    VkInstance instance = VK_NULL_HANDLE;
    if (vkCreateInstance(&ci, nullptr, &instance) != VK_SUCCESS || instance == VK_NULL_HANDLE) {
        LOGI("Vulkan instance creation failed");
        return g;
    }

    uint32_t count = 0;
    vkEnumeratePhysicalDevices(instance, &count, nullptr);
    std::vector<VkPhysicalDevice> devices(count);
    if (count > 0) vkEnumeratePhysicalDevices(instance, &count, devices.data());

    for (auto dev : devices) {
        VkPhysicalDeviceProperties p{};
        vkGetPhysicalDeviceProperties(dev, &p);

        std::string name = p.deviceName;
        std::string lower = name;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        // Skip software rasterizers when a real GPU is present.
        if (lower.find("swiftshader") != std::string::npos) continue;

        g.available = true;
        g.name = name;
        g.apiVersion = static_cast<int>(p.apiVersion);
        g.driverVersion = static_cast<int>(p.driverVersion);

        switch (p.vendorID) {
            case 0x5143: g.vendor = "Qualcomm"; break;
            case 0x13B5: g.vendor = "ARM"; break;
            case 0x1002: g.vendor = "AMD (Samsung Xclipse)"; break;
            case 0x1010: g.vendor = "Imagination"; break;
            case 0x10DE: g.vendor = "NVIDIA"; break;
            case 0x8086: g.vendor = "Intel"; break;
            default: g.vendor = "vendor:" + std::to_string(p.vendorID); break;
        }

        uint32_t extCount = 0;
        vkEnumerateDeviceExtensionProperties(dev, nullptr, &extCount, nullptr);
        std::vector<VkExtensionProperties> exts(extCount);
        if (extCount > 0) vkEnumerateDeviceExtensionProperties(dev, nullptr, &extCount, exts.data());
        for (auto& e : exts) {
            std::string n = e.extensionName;
            if (n.find("ray_tracing") != std::string::npos) g.rayTracing = true;
            if (n == "VK_KHR_shader_integer_dot_product") g.integerDot = true;
        }
        break;
    }
    vkDestroyInstance(instance, nullptr);
    return g;
}

struct NpuStats {
    bool available = false;
    std::string names;
    int deviceCount = 0;
};

NpuStats detectNpu() {
    NpuStats n;
    void* lib = dlopen("libneuralnetworks.so", RTLD_NOW);
    if (!lib) return n;

    using GetCountFn = int (*)(uint32_t*);
    using GetDeviceFn = int (*)(uint32_t, void**);
    using GetNameFn = int (*)(void*, const char**);
    using GetTypeFn = int (*)(void*, int32_t*);

    auto getCount = reinterpret_cast<GetCountFn>(dlsym(lib, "ANeuralNetworks_getDeviceCount"));
    auto getDevice = reinterpret_cast<GetDeviceFn>(dlsym(lib, "ANeuralNetworks_getDevice"));
    auto getName = reinterpret_cast<GetNameFn>(dlsym(lib, "ANeuralNetworksDevice_getName"));
    auto getType = reinterpret_cast<GetTypeFn>(dlsym(lib, "ANeuralNetworksDevice_getType"));

    if (!getCount) {
        dlclose(lib);
        return n;
    }

    uint32_t count = 0;
    if (getCount(&count) == 0 && count > 0) {
        n.available = true;
        n.deviceCount = static_cast<int>(count);
        for (uint32_t i = 0; i < count; ++i) {
            void* dev = nullptr;
            if (getDevice && getDevice(i, &dev) == 0 && dev) {
                std::string name = "device";
                if (getName) {
                    const char* nn = nullptr;
                    if (getName(dev, &nn) == 0 && nn) name = nn;
                }
                if (getType) {
                    int32_t type = 0;
                    if (getType(dev, &type) == 0) {
                        name += type == 4 ? " [accelerator]" : (type == 3 ? " [gpu]" : (type == 2 ? " [cpu]" : " [other]"));
                    }
                }
                if (!n.names.empty()) n.names += ", ";
                n.names += name;
            }
        }
    }
    dlclose(lib);
    return n;
}

float detectThermal(float* throttleOut) {
    float maxTemp = 0.0f;
    for (int i = 0; i < 40; ++i) {
        std::string type = readFile("/sys/class/thermal/thermal_zone" + std::to_string(i) + "/type");
        if (type.empty()) continue;
        std::string raw = readFile("/sys/class/thermal/thermal_zone" + std::to_string(i) + "/temp");
        if (raw.empty()) continue;
        try {
            float t = std::stof(raw);
            if (t > 1000.0f) t /= 1000.0f;
            if (t >= 5.0f && t <= 150.0f) maxTemp = std::max(maxTemp, t);
        } catch (...) {}
    }
    *throttleOut = 85.0f;
    return maxTemp;
}

void ramStats(uint64_t* totalMb, uint64_t* availMb) {
    struct sysinfo si {};
    if (sysinfo(&si) == 0) {
        *totalMb = static_cast<uint64_t>(si.totalram) * si.mem_unit / (1024ULL * 1024ULL);
    }
    std::string mem = readFile("/proc/meminfo");
    std::istringstream stream(mem);
    std::string line;
    uint64_t availKb = 0;
    while (std::getline(stream, line)) {
        if (line.rfind("MemAvailable:", 0) == 0) {
            std::sscanf(line.c_str(), "MemAvailable: %llu", (unsigned long long*)&availKb);
            break;
        }
    }
    *availMb = availKb / 1024ULL;
}

} // namespace

std::string hardwareJson() {
    CpuStats cpu = detectCpu();
    GpuStats gpu = detectGpu();
    NpuStats npu = detectNpu();

    float throttle = 85.0f;
    float temp = detectThermal(&throttle);

    uint64_t totalMb = 0, availMb = 0;
    ramStats(&totalMb, &availMb);

    std::string soc = prop("ro.soc.model");
    if (soc.empty()) soc = prop("ro.hardware");
    if (soc.empty()) soc = prop("ro.board.platform");

    // Samsung Exynos NPU throughput (marketing TOPS, INT8).
    int topsInt8 = 0, topsFp16 = 0, topsFp8 = 0;
    std::string socLower = soc;
    std::transform(socLower.begin(), socLower.end(), socLower.begin(), ::tolower);
    if (socLower.find("s5e8855") != std::string::npos || socLower.find("exynos 1580") != std::string::npos) {
        topsInt8 = 14; topsFp16 = 7; topsFp8 = 14;
    } else if (socLower.find("s5e8845") != std::string::npos || socLower.find("exynos 1480") != std::string::npos) {
        topsInt8 = 15; topsFp16 = 7;
    } else if (npu.available) {
        topsInt8 = 10; topsFp16 = 5;
    }

    std::ostringstream json;
    json << "{";
    json << "\"device\":\"" << jsonEscape(prop("ro.product.model")) << "\"";
    json << ",\"manufacturer\":\"" << jsonEscape(prop("ro.product.manufacturer")) << "\"";
    json << ",\"soc\":\"" << jsonEscape(soc) << "\"";
    json << ",\"android\":\"" << jsonEscape(prop("ro.build.version.release")) << "\"";
    json << ",\"api\":" << (prop("ro.build.version.sdk").empty() ? 0 : std::stoi(prop("ro.build.version.sdk")));
    json << ",\"cpuCores\":" << cpu.cores;
    json << ",\"cpuPerf\":" << cpu.perf;
    json << ",\"cpuEff\":" << cpu.eff;
    json << ",\"cpuMaxGhz\":" << cpu.maxGhz;
    json << ",\"arch\":\"" << jsonEscape(cpu.arch) << "\"";
    json << ",\"gpu\":\"" << jsonEscape(gpu.name) << "\"";
    json << ",\"gpuVendor\":\"" << jsonEscape(gpu.vendor) << "\"";
    json << ",\"gpuVulkan\":" << (gpu.available ? "true" : "false");
    json << ",\"gpuApi\":\"" << major(gpu.apiVersion) << "." << minor(gpu.apiVersion) << "\"";
    json << ",\"gpuRayTracing\":" << (gpu.rayTracing ? "true" : "false");
    json << ",\"gpuIntDot\":" << (gpu.integerDot ? "true" : "false");
    json << ",\"npu\":" << (npu.available ? "true" : "false");
    json << ",\"npuDevices\":" << npu.deviceCount;
    json << ",\"npuNames\":\"" << jsonEscape(npu.names) << "\"";
    json << ",\"npuTopsInt8\":" << topsInt8;
    json << ",\"npuTopsFp16\":" << topsFp16;
    json << ",\"npuTopsFp8\":" << topsFp8;
    json << ",\"ramTotalMb\":" << totalMb;
    json << ",\"ramAvailMb\":" << availMb;
    json << ",\"thermalC\":" << temp;
    json << ",\"throttleC\":" << throttle;
    json << ",\"llama\":true";
    json << "}";
    return json.str();
}

} // namespace galaxya56
