package com.galaxya56.ai.inference

import org.json.JSONObject

/**
 * Real device capabilities, produced by the native probe (Vulkan enumeration,
 * NNAPI device discovery, procfs, Android system properties).
 */
data class HardwareInfo(
    val device: String = "",
    val manufacturer: String = "",
    val soc: String = "",
    val android: String = "",
    val api: Int = 0,
    val cpuCores: Int = 0,
    val cpuPerf: Int = 0,
    val cpuEff: Int = 0,
    val cpuMaxGhz: Double = 0.0,
    val arch: String = "",
    val gpu: String = "",
    val gpuVendor: String = "",
    val gpuVulkan: Boolean = false,
    val gpuApi: String = "",
    val gpuRayTracing: Boolean = false,
    val gpuIntDot: Boolean = false,
    val npu: Boolean = false,
    val npuDevices: Int = 0,
    val npuNames: String = "",
    val npuTopsInt8: Int = 0,
    val npuTopsFp16: Int = 0,
    val npuTopsFp8: Int = 0,
    val ramTotalMb: Long = 0,
    val ramAvailMb: Long = 0,
    val thermalC: Float = 0f,
    val throttleC: Float = 85f,
    val llama: Boolean = false
) {
    val cpuSummary: String
        get() = "$cpuCores Kerne (${cpuPerf}P + ${cpuEff}E) · ${"%.2f".format(cpuMaxGhz)} GHz"

    val npuSummary: String
        get() = if (npu) {
            val tops = if (npuTopsInt8 > 0) " · ~$npuTopsInt8 TOPS INT8" else ""
            "Verfügbar$tops"
        } else "Nicht erkannt"

    val thermalWarning: Boolean get() = throttleC > 0 && thermalC >= throttleC * 0.9f

    companion object {
        fun parse(json: String): HardwareInfo {
            return try {
                val o = JSONObject(json)
                HardwareInfo(
                    device = o.optString("device"),
                    manufacturer = o.optString("manufacturer"),
                    soc = o.optString("soc"),
                    android = o.optString("android"),
                    api = o.optInt("api"),
                    cpuCores = o.optInt("cpuCores"),
                    cpuPerf = o.optInt("cpuPerf"),
                    cpuEff = o.optInt("cpuEff"),
                    cpuMaxGhz = o.optDouble("cpuMaxGhz", 0.0),
                    arch = o.optString("arch"),
                    gpu = o.optString("gpu"),
                    gpuVendor = o.optString("gpuVendor"),
                    gpuVulkan = o.optBoolean("gpuVulkan"),
                    gpuApi = o.optString("gpuApi"),
                    gpuRayTracing = o.optBoolean("gpuRayTracing"),
                    gpuIntDot = o.optBoolean("gpuIntDot"),
                    npu = o.optBoolean("npu"),
                    npuDevices = o.optInt("npuDevices"),
                    npuNames = o.optString("npuNames"),
                    npuTopsInt8 = o.optInt("npuTopsInt8"),
                    npuTopsFp16 = o.optInt("npuTopsFp16"),
                    npuTopsFp8 = o.optInt("npuTopsFp8"),
                    ramTotalMb = o.optLong("ramTotalMb"),
                    ramAvailMb = o.optLong("ramAvailMb"),
                    thermalC = o.optDouble("thermalC", 0.0).toFloat(),
                    throttleC = o.optDouble("throttleC", 85.0).toFloat(),
                    llama = o.optBoolean("llama")
                )
            } catch (t: Throwable) {
                HardwareInfo()
            }
        }
    }
}
