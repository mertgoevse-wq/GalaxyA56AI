package com.galaxya56.ai.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Minimal H.264/MP4 writer for the on-device AnimateDiff/Wan frames.
 *
 * Android encoders disagree about their preferred YUV layout, so this class
 * asks the selected encoder for its colour formats and feeds either an
 * android.media.Image (COLOR_FormatYUV420Flexible) or NV12. Tune/try-catch is
 * deliberate: a missing encoder becomes a normal video error in the UI, not a
 * crash in the middle of a long diffusion job.
 */
object Mp4Encoder {

    data class Result(val ok: Boolean, val path: String = "", val error: String = "")

    fun encode(frames: List<File>, output: File, fps: Int): Result {
        if (frames.isEmpty()) return Result(false, error = "Keine Videoframes vorhanden")
        val first = BitmapFactory.decodeFile(frames.first().absolutePath)
            ?: return Result(false, error = "Erster Frame ist kein lesbares Bild")
        val width = first.width and 0x7FFFFFFE
        val height = first.height and 0x7FFFFFFE
        first.recycle()
        if (width < 16 || height < 16) return Result(false, error = "Frame ist zu klein")

        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()

        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var track = -1
        return try {
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec = encoder
            val capabilities = encoder.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val supportsFlexible = capabilities.colorFormats.contains(
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            format.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                if (supportsFlexible) MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                else MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            )
            format.setInteger(MediaFormat.KEY_BIT_RATE, width * height * 3)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, fps.coerceAtLeast(1))
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val out = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = out
            val info = MediaCodec.BufferInfo()

            fun drain(endOfStream: Boolean) {
                var idle = 0
                while (true) {
                    val index = encoder.dequeueOutputBuffer(info, if (endOfStream) 20_000 else 0)
                    when {
                        index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) return
                            // After queueing EOS, keep draining until the encoder
                            // emits its final sample; returning here drops the
                            // last frame (7 of 8 frames in the first test).
                            if (++idle > 1500) error("Encoder lieferte kein EOS")
                        }
                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (muxerStarted) error("Encoder-Format wechselt mehrfach")
                            track = out.addTrack(encoder.outputFormat)
                            out.start()
                            muxerStarted = true
                        }
                        index >= 0 -> {
                            val buffer: ByteBuffer = encoder.getOutputBuffer(index)
                                ?: error("Encoder lieferte keinen Ausgabepuffer")
                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                info.size = 0
                            }
                            if (info.size > 0) {
                                if (!muxerStarted || track < 0) {
                                    error("Encoder-Muxer wurde nicht gestartet")
                                }
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                out.writeSampleData(track, buffer, info)
                            }
                            encoder.releaseOutputBuffer(index, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        }
                    }
                }
            }

            var frameIndex = 0
            for (frame in frames) {
                val bitmap = BitmapFactory.decodeFile(frame.absolutePath)
                    ?: error("Frame ${frame.name} ist nicht lesbar")
                val scaled = if (bitmap.width == width && bitmap.height == height) {
                    bitmap
                } else {
                    Bitmap.createScaledBitmap(bitmap, width, height, true).also { bitmap.recycle() }
                }

                var inputIndex = encoder.dequeueInputBuffer(20_000)
                while (inputIndex < 0) {
                    drain(false)
                    inputIndex = encoder.dequeueInputBuffer(20_000)
                }
                val image = if (supportsFlexible) encoder.getInputImage(inputIndex) else null
                if (image != null) {
                    fillFlexible(image, scaled)
                    image.close()
                } else {
                    val buffer = encoder.getInputBuffer(inputIndex)
                        ?: error("Encoder lieferte keinen Eingabepuffer")
                    buffer.clear()
                    buffer.put(toNv12(scaled))
                }
                scaled.recycle()

                val pts = frameIndex * 1_000_000L / fps.coerceAtLeast(1)
                encoder.queueInputBuffer(inputIndex, 0, width * height * 3 / 2, pts, 0)
                frameIndex++
                drain(false)
            }

            var eosIndex = encoder.dequeueInputBuffer(20_000)
            while (eosIndex < 0) {
                drain(false)
                eosIndex = encoder.dequeueInputBuffer(20_000)
            }
            encoder.queueInputBuffer(
                eosIndex, 0, 0,
                frameIndex * 1_000_000L / fps.coerceAtLeast(1),
                MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
            drain(true)

            encoder.stop()
            if (muxerStarted) out.stop()
            out.release()
            muxer = null
            encoder.release()
            codec = null
            Result(true, output.absolutePath)
        } catch (t: Throwable) {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { if (muxerStarted) muxer?.stop() }
            runCatching { muxer?.release() }
            Result(false, error = t.message ?: "MP4-Export fehlgeschlagen")
        }
    }

    private fun fillFlexible(image: android.media.Image, bitmap: Bitmap) {
        val width = image.width
        val height = image.height
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = argb[y * width + x]
                val r = (color shr 16) and 0xff
                val g = (color shr 8) and 0xff
                val b = color and 0xff
                val luma = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yPlane.buffer.put(
                    y * yPlane.rowStride + x * yPlane.pixelStride,
                    luma.coerceIn(0, 255).toByte()
                )
            }
        }
        val cw = (width + 1) / 2
        val ch = (height + 1) / 2
        for (y in 0 until ch) {
            for (x in 0 until cw) {
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                for (dy in 0..1) {
                    for (dx in 0..1) {
                        val px = x * 2 + dx
                        val py = y * 2 + dy
                        if (px < width && py < height) {
                            val color = argb[py * width + px]
                            r += (color shr 16) and 0xff
                            g += (color shr 8) and 0xff
                            b += color and 0xff
                            count++
                        }
                    }
                }
                if (count == 0) continue
                r /= count
                g /= count
                b /= count
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                uPlane.buffer.put(
                    y * uPlane.rowStride + x * uPlane.pixelStride,
                    u.coerceIn(0, 255).toByte()
                )
                vPlane.buffer.put(
                    y * vPlane.rowStride + x * vPlane.pixelStride,
                    v.coerceIn(0, 255).toByte()
                )
            }
        }
    }

    /** NV12 (Y plane followed by interleaved U,V), for semi-planar encoders. */
    private fun toNv12(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)
        val ySize = width * height
        val out = ByteArray(ySize + 2 * (width / 2) * (height / 2))
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = argb[y * width + x]
                val r = (color shr 16) and 0xff
                val g = (color shr 8) and 0xff
                val b = color and 0xff
                out[y * width + x] = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16)
                    .coerceIn(0, 255).toByte()
            }
        }
        var offset = ySize
        for (y in 0 until height step 2) {
            for (x in 0 until width step 2) {
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                for (dy in 0..1) {
                    for (dx in 0..1) {
                        val px = x + dx
                        val py = y + dy
                        if (px < width && py < height) {
                            val color = argb[py * width + px]
                            r += (color shr 16) and 0xff
                            g += (color shr 8) and 0xff
                            b += color and 0xff
                            count++
                        }
                    }
                }
                r /= count.coerceAtLeast(1)
                g /= count.coerceAtLeast(1)
                b /= count.coerceAtLeast(1)
                out[offset++] = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128)
                    .coerceIn(0, 255).toByte()
                out[offset++] = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128)
                    .coerceIn(0, 255).toByte()
            }
        }
        return out
    }
}
