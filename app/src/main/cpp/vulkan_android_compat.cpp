// Android's libvulkan.so stub for API < 33 does not export vkResetQueryPool even
// though Vulkan 1.2 headers declare it. ggml-vulkan references it through
// vk::DispatchLoaderStatic, so provide a forwarder that resolves the real entry
// point at runtime (and degrades to a no-op when the loader is older).
#include <vulkan/vulkan.h>

extern "C" VKAPI_ATTR void VKAPI_CALL vkResetQueryPool(
    VkDevice device, VkQueryPool queryPool, uint32_t firstQuery, uint32_t queryCount) {
    static PFN_vkResetQueryPool resolved = nullptr;
    if (!resolved) {
        auto getDeviceProcAddr =
            reinterpret_cast<PFN_vkGetDeviceProcAddr>(vkGetDeviceProcAddr);
        if (!getDeviceProcAddr) return;
        resolved = reinterpret_cast<PFN_vkResetQueryPool>(
            getDeviceProcAddr(device, "vkResetQueryPool"));
    }
    if (resolved) resolved(device, queryPool, firstQuery, queryCount);
}
