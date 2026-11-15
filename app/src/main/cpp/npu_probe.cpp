#include "npu_probe.h"

#include <android/NeuralNetworks.h>
#include <android/log.h>
#include <dlfcn.h>

#include <chrono>
#include <cstdio>
#include <cstring>
#include <sstream>
#include <string>
#include <vector>

#define LOG_TAG "A56AI-NPU"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace galaxya56 {
namespace {

std::string escape(const std::string& in) {
    std::string out;
    for (char c : in) {
        if (c == '"' || c == '\\') out += '\\';
        if (c == '\n') { out += "\\n"; continue; }
        out += c;
    }
    return out;
}

const char* deviceTypeName(int32_t type) {
    switch (type) {
        case ANEURALNETWORKS_DEVICE_ACCELERATOR: return "accelerator";
        case ANEURALNETWORKS_DEVICE_GPU: return "gpu";
        case ANEURALNETWORKS_DEVICE_CPU: return "cpu";
        case ANEURALNETWORKS_DEVICE_OTHER: return "other";
        default: return "unknown";
    }
}

struct NnApi {
    void* handle = nullptr;

    using FnGetDeviceCount = void (*)(uint32_t*);
    using FnGetDevice = int (*)(uint32_t, ANeuralNetworksDevice**);
    using FnDeviceGetName = int (*)(const ANeuralNetworksDevice*, const char**);
    using FnDeviceGetType = int (*)(const ANeuralNetworksDevice*, int32_t*);
    using FnDeviceGetFeatureLevel = int (*)(const ANeuralNetworksDevice*, int64_t*);
    using FnModelCreate = int (*)(ANeuralNetworksModel**);
    using FnModelAddOperand = int (*)(ANeuralNetworksModel*, const ANeuralNetworksOperandType*);
    using FnModelSetOperandValue = int (*)(ANeuralNetworksModel*, int32_t, const void*, size_t);
    using FnModelAddOperation = int (*)(ANeuralNetworksModel*, ANeuralNetworksOperationType,
                                        uint32_t, const uint32_t*, uint32_t, const uint32_t*);
    using FnModelIdentify = int (*)(ANeuralNetworksModel*, uint32_t, const uint32_t*,
                                    uint32_t, const uint32_t*);
    using FnModelFinish = int (*)(ANeuralNetworksModel*);
    using FnModelFree = void (*)(ANeuralNetworksModel*);
    using FnCompilationCreate = int (*)(ANeuralNetworksModel*, ANeuralNetworksCompilation**);
    using FnCompilationCreateForDevices = int (*)(ANeuralNetworksModel*,
                                                  const ANeuralNetworksDevice* const*,
                                                  uint32_t, ANeuralNetworksCompilation**);
    using FnCompilationSetPreference = int (*)(ANeuralNetworksCompilation*, int32_t);
    using FnCompilationFinish = int (*)(ANeuralNetworksCompilation*);
    using FnCompilationFree = void (*)(ANeuralNetworksCompilation*);
    using FnExecutionCreate = int (*)(ANeuralNetworksCompilation*, ANeuralNetworksExecution**);
    using FnExecutionSetInput = int (*)(ANeuralNetworksExecution*, int32_t,
                                        const ANeuralNetworksOperandType*, const void*, size_t);
    using FnExecutionSetOutput = int (*)(ANeuralNetworksExecution*, int32_t,
                                         const ANeuralNetworksOperandType*, void*, size_t);
    using FnExecutionCompute = int (*)(ANeuralNetworksExecution*);
    using FnExecutionStartCompute = int (*)(ANeuralNetworksExecution*, ANeuralNetworksEvent**);
    using FnEventWait = int (*)(ANeuralNetworksEvent*);
    using FnEventFree = void (*)(ANeuralNetworksEvent*);
    using FnExecutionFree = void (*)(ANeuralNetworksExecution*);

    FnGetDeviceCount getDeviceCount = nullptr;
    FnGetDevice getDevice = nullptr;
    FnDeviceGetName deviceGetName = nullptr;
    FnDeviceGetType deviceGetType = nullptr;
    FnDeviceGetFeatureLevel deviceGetFeatureLevel = nullptr;
    FnModelCreate modelCreate = nullptr;
    FnModelAddOperand modelAddOperand = nullptr;
    FnModelSetOperandValue modelSetOperandValue = nullptr;
    FnModelAddOperation modelAddOperation = nullptr;
    FnModelIdentify modelIdentify = nullptr;
    FnModelFinish modelFinish = nullptr;
    FnModelFree modelFree = nullptr;
    FnCompilationCreate compilationCreate = nullptr;
    FnCompilationCreateForDevices compilationCreateForDevices = nullptr;
    FnCompilationSetPreference compilationSetPreference = nullptr;
    FnCompilationFinish compilationFinish = nullptr;
    FnCompilationFree compilationFree = nullptr;
    FnExecutionCreate executionCreate = nullptr;
    FnExecutionSetInput executionSetInput = nullptr;
    FnExecutionSetOutput executionSetOutput = nullptr;
    FnExecutionCompute executionCompute = nullptr;
    FnExecutionStartCompute executionStartCompute = nullptr;
    FnEventWait eventWait = nullptr;
    FnEventFree eventFree = nullptr;
    FnExecutionFree executionFree = nullptr;

    bool load() {
        handle = dlopen("libneuralnetworks.so", RTLD_NOW);
        if (!handle) return false;
#define LOAD(field, symbol) \
    field = reinterpret_cast<decltype(field)>(dlsym(handle, symbol)); \
    if (!field) return false
        LOAD(getDeviceCount, "ANeuralNetworks_getDeviceCount");
        LOAD(getDevice, "ANeuralNetworks_getDevice");
        LOAD(deviceGetName, "ANeuralNetworksDevice_getName");
        LOAD(deviceGetType, "ANeuralNetworksDevice_getType");
        LOAD(deviceGetFeatureLevel, "ANeuralNetworksDevice_getFeatureLevel");
        LOAD(modelCreate, "ANeuralNetworksModel_create");
        LOAD(modelAddOperand, "ANeuralNetworksModel_addOperand");
        LOAD(modelSetOperandValue, "ANeuralNetworksModel_setOperandValue");
        LOAD(modelAddOperation, "ANeuralNetworksModel_addOperation");
        LOAD(modelIdentify, "ANeuralNetworksModel_identifyInputsAndOutputs");
        LOAD(modelFinish, "ANeuralNetworksModel_finish");
        LOAD(modelFree, "ANeuralNetworksModel_free");
        LOAD(compilationCreate, "ANeuralNetworksCompilation_create");
        LOAD(compilationCreateForDevices, "ANeuralNetworksCompilation_createForDevices");
        LOAD(compilationSetPreference, "ANeuralNetworksCompilation_setPreference");
        LOAD(compilationFinish, "ANeuralNetworksCompilation_finish");
        LOAD(compilationFree, "ANeuralNetworksCompilation_free");
        LOAD(executionCreate, "ANeuralNetworksExecution_create");
        LOAD(executionSetInput, "ANeuralNetworksExecution_setInput");
        LOAD(executionSetOutput, "ANeuralNetworksExecution_setOutput");
        LOAD(executionCompute, "ANeuralNetworksExecution_compute");
        LOAD(executionStartCompute, "ANeuralNetworksExecution_startCompute");
        LOAD(eventWait, "ANeuralNetworksEvent_wait");
        LOAD(eventFree, "ANeuralNetworksEvent_free");
        LOAD(executionFree, "ANeuralNetworksExecution_free");
#undef LOAD
        return true;
    }
};

} // namespace

std::string nnapiProbeJson() {
    std::ostringstream json;
    json << "{";

    NnApi api;
    if (!api.load()) {
        json << "\"ok\":false,\"error\":\"libneuralnetworks.so nicht ladbar\"}";
        return json.str();
    }

    uint32_t count = 0;
    api.getDeviceCount(&count);

    struct DeviceInfo {
        ANeuralNetworksDevice* device = nullptr;
        std::string name;
        int32_t type = ANEURALNETWORKS_DEVICE_UNKNOWN;
        int64_t featureLevel = 0;
    };
    std::vector<DeviceInfo> devices;
    ANeuralNetworksDevice* chosen = nullptr;
    std::string chosenName;
    int32_t chosenType = ANEURALNETWORKS_DEVICE_UNKNOWN;

    json << "\"ok\":true,\"devices\":[";
    for (uint32_t i = 0; i < count; ++i) {
        ANeuralNetworksDevice* dev = nullptr;
        if (api.getDevice(i, &dev) != ANEURALNETWORKS_NO_ERROR || !dev) continue;
        const char* name = nullptr;
        int32_t type = ANEURALNETWORKS_DEVICE_UNKNOWN;
        int64_t level = 0;
        api.deviceGetName(dev, &name);
        api.deviceGetType(dev, &type);
        api.deviceGetFeatureLevel(dev, &level);
        DeviceInfo info{dev, name ? name : "?", type, level};
        devices.push_back(info);
        if (i > 0) json << ",";
        json << "{\"name\":\"" << escape(info.name) << "\",\"type\":\"" << deviceTypeName(type)
             << "\",\"featureLevel\":" << level << "}";
        if (!chosen && type == ANEURALNETWORKS_DEVICE_ACCELERATOR) {
            chosen = dev;
            chosenName = info.name;
            chosenType = type;
        }
    }
    json << "]";
    if (!chosen) {
        for (const auto& d : devices) {
            if (d.type == ANEURALNETWORKS_DEVICE_GPU) {
                chosen = d.device;
                chosenName = d.name;
                chosenType = d.type;
                break;
            }
        }
    }
    if (!chosen && !devices.empty()) {
        chosen = devices.front().device;
        chosenName = devices.front().name;
        chosenType = devices.front().type;
    }
    json << ",\"deviceCount\":" << devices.size();
    json << ",\"chosen\":\"" << escape(chosenName) << "\"";
    json << ",\"chosenType\":\"" << deviceTypeName(chosenType) << "\"";

    if (!chosen) {
        json << ",\"ok\":false,\"error\":\"Kein NNAPI-Gerät verfügbar\"}";
        return json.str();
    }

    alignas(16) const float a[4] = {1.0f, 2.0f, 3.0f, 4.0f};
    alignas(16) const float b[4] = {10.0f, 20.0f, 30.0f, 40.0f};
    alignas(16) float out[4] = {0, 0, 0, 0};
    uint32_t dims[1] = {4};
    ANeuralNetworksOperandType tensorType{};
    tensorType.type = ANEURALNETWORKS_TENSOR_FLOAT32;
    tensorType.dimensionCount = 1;
    tensorType.dimensions = dims;
    ANeuralNetworksOperandType activationType{};
    activationType.type = ANEURALNETWORKS_INT32;
    activationType.dimensionCount = 0;
    activationType.dimensions = nullptr;
    int32_t fusedNone = ANEURALNETWORKS_FUSED_NONE;

    ANeuralNetworksModel* model = nullptr;
    std::string stage = "create";
    int rc = api.modelCreate(&model);
    stage = "addOperand0";
    if (rc == ANEURALNETWORKS_NO_ERROR) rc = api.modelAddOperand(model, &tensorType);
    stage = "addOperand1";
    if (rc == ANEURALNETWORKS_NO_ERROR) rc = api.modelAddOperand(model, &tensorType);
    stage = "addActivation";
    if (rc == ANEURALNETWORKS_NO_ERROR) rc = api.modelAddOperand(model, &activationType);
    stage = "addOperandOutput";
    if (rc == ANEURALNETWORKS_NO_ERROR) rc = api.modelAddOperand(model, &tensorType);
    stage = "setActivation";
    if (rc == ANEURALNETWORKS_NO_ERROR) {
        rc = api.modelSetOperandValue(model, 2, &fusedNone, sizeof(fusedNone));
    }
    uint32_t inputs[3] = {0, 1, 2};
    uint32_t outputs[1] = {3};
    stage = "addOperation";
    if (rc == ANEURALNETWORKS_NO_ERROR) {
        rc = api.modelAddOperation(model, ANEURALNETWORKS_ADD, 3, inputs, 1, outputs);
    }
    stage = "identify";
    if (rc == ANEURALNETWORKS_NO_ERROR) {
        rc = api.modelIdentify(model, 2, inputs, 1, outputs);
    }
    stage = "finish";
    if (rc == ANEURALNETWORKS_NO_ERROR) rc = api.modelFinish(model);
    if (rc != ANEURALNETWORKS_NO_ERROR) {
        if (model) api.modelFree(model);
        json << ",\"ok\":false,\"stage\":\"" << stage << "\",\"error\":\"Modellaufbau fehlgeschlagen (Code " << rc << ")\"}";
        return json.str();
    }

    ANeuralNetworksCompilation* compilation = nullptr;
    rc = api.compilationCreate(model, &compilation);
    if (rc == ANEURALNETWORKS_NO_ERROR) {
        api.compilationSetPreference(compilation, ANEURALNETWORKS_PREFER_SUSTAINED_SPEED);
        rc = api.compilationFinish(compilation);
    }
    if (rc != ANEURALNETWORKS_NO_ERROR) {
        if (compilation) api.compilationFree(compilation);
        api.modelFree(model);
        json << ",\"ok\":false,\"error\":\"Kompilierung fehlgeschlagen (Code " << rc << ")\"}";
        return json.str();
    }

    ANeuralNetworksExecution* execution = nullptr;
    stage = "executionCreate";
    rc = api.executionCreate(compilation, &execution);
    stage = "setInput0";
    if (rc == ANEURALNETWORKS_NO_ERROR) {
        rc = api.executionSetInput(execution, 0, &tensorType, a, sizeof(a));
    }
    stage = "setInput1";
    if (rc == ANEURALNETWORKS_NO_ERROR) {
        rc = api.executionSetInput(execution, 1, &tensorType, b, sizeof(b));
    }
    stage = "setOutput";
    if (rc == ANEURALNETWORKS_NO_ERROR) {
        rc = api.executionSetOutput(execution, 3, &tensorType, out, sizeof(out));
    }
    stage = "compute";
    auto start = std::chrono::steady_clock::now();
    if (rc == ANEURALNETWORKS_NO_ERROR && api.executionCompute) {
        rc = api.executionCompute(execution);
    } else if (rc == ANEURALNETWORKS_NO_ERROR && api.executionStartCompute) {
        ANeuralNetworksEvent* event = nullptr;
        rc = api.executionStartCompute(execution, &event);
        if (rc == ANEURALNETWORKS_NO_ERROR && event) {
            rc = api.eventWait(event);
            if (api.eventFree) api.eventFree(event);
        }
    }
    auto end = std::chrono::steady_clock::now();
    double ms = std::chrono::duration<double, std::milli>(end - start).count();

    json << ",\"latencyMs\":" << ms;
    if (rc == ANEURALNETWORKS_NO_ERROR) {
        json << ",\"output\":[" << out[0] << "," << out[1] << "," << out[2] << "," << out[3] << "]";
        json << ",\"correct\":" << ((out[0] == 11.0f && out[1] == 22.0f &&
                                     out[2] == 33.0f && out[3] == 44.0f) ? "true" : "false");
    } else {
        json << ",\"ok\":false,\"stage\":\"" << stage << "\",\"error\":\"Ausfuehrung fehlgeschlagen (Code " << rc << ")\"";
    }

    if (execution) api.executionFree(execution);
    api.compilationFree(compilation);
    api.modelFree(model);
    json << "}";
    LOGI("NNAPI probe: %s", json.str().c_str());
    return json.str();
}

} // namespace galaxya56
