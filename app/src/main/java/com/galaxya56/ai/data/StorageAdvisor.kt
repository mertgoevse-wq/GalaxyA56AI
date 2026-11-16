package com.galaxya56.ai.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.os.storage.StorageManager
import java.io.File

data class SwapInfo(
    val totalMb: Long,
    val usedMb: Long,
    val kind: String
)

data class StorageReport(
    val ramTotalMb: Long,
    val ramAvailMb: Long,
    val appFreeMb: Long,
    val zramMb: Long,
    val swaps: List<SwapInfo>,
    val removableVolumes: List<String>,
    val modelDir: String
) {
    val swapSummary: String
        get() = if (swaps.isEmpty()) "kein aktiver Swap sichtbar"
        else swaps.joinToString(" · ") { "${it.kind} ${it.usedMb}/${it.totalMb} MB" }
}

/**
 * Reads the real kernel view of memory pressure. An app cannot enable ZRAM or
 * swap without root; this object only reports what the ROM already provides
 * and points to the paths that actually help (mmap, smaller models, USB).
 */
object StorageAdvisor {

    fun inspect(context: Context): StorageReport {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        val appDir = context.getExternalFilesDir(null) ?: context.filesDir
        val freeMb = runCatching {
            StatFs(appDir.absolutePath).availableBytes / (1024L * 1024L)
        }.getOrDefault(0L)

        val swaps = runCatching {
            File("/proc/swaps").readLines().drop(1).mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size < 5) return@mapNotNull null
                val total = parts[2].toLongOrNull()?.div(1024L) ?: 0L
                val used = parts[3].toLongOrNull()?.div(1024L) ?: 0L
                SwapInfo(totalMb = total, usedMb = used, kind = parts[0].substringAfterLast('/'))
            }
        }.getOrDefault(emptyList())

        val zramMb = runCatching {
            val bytes = File("/sys/block/zram0/disksize").readText().trim().toLong()
            bytes / (1024L * 1024L)
        }.getOrDefault(0L)

        val volumes = runCatching {
            val manager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            manager.storageVolumes.mapNotNull { volume ->
                if (!volume.isRemovable) return@mapNotNull null
                val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    volume.directory?.absolutePath
                } else null
                val label = volume.getDescription(context)
                if (dir.isNullOrBlank()) "$label (über USB-C/Ordner importieren)" else "$label · $dir"
            }
        }.getOrDefault(emptyList())

        return StorageReport(
            ramTotalMb = memory.totalMem / (1024L * 1024L),
            ramAvailMb = memory.availMem / (1024L * 1024L),
            appFreeMb = freeMb,
            zramMb = zramMb,
            swaps = swaps,
            removableVolumes = volumes,
            modelDir = File(appDir, "models").absolutePath
        )
    }
}
