package com.galaxya56.ai.data

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

data class PsytranceResult(
    val file: File,
    val seconds: Int,
    val bpm: Int,
    val seed: Long
)

/**
 * Deterministic, fully local Progressive-Psytrance renderer.
 *
 * This is deliberately a DSP engine, not a neural music model: it gives the
 * A56 a working long-form music path (4+ minutes, 44.1 kHz stereo WAV) while
 * the large ACE-Step/MusicGen checkpoints remain catalogue-only. The
 * arrangement follows the genre: rolling 16th bass, offbeat kick, shuffled
 * hats, a Phrygian lead, a breakdown and a second drop.
 */
object PsytranceEngine {

    private const val SAMPLE_RATE = 44_100
    private const val TWO_PI = 2.0 * PI

    fun generate(
        output: File,
        prompt: String,
        bpm: Int,
        minutes: Float,
        seed: Long,
        onProgress: (Int) -> Unit = {}
    ): PsytranceResult {
        require(minutes > 0f) { "Dauer muss positiv sein" }
        val safeBpm = bpm.coerceIn(90, 180)
        val seconds = (minutes * 60f).toInt().coerceIn(30, 480)
        val totalFrames = seconds * SAMPLE_RATE
        val beat = 60.0 / safeBpm
        val bar = beat * 4.0
        val step = beat / 4.0
        val totalBars = (seconds / bar).toInt().coerceAtLeast(8)
        val random = Random(seed xor prompt.hashCode().toLong())
        val root = 55.0 // A1
        val scale = intArrayOf(0, 3, 5, 7, 10, 12, 15) // A minor / Phrygian colour
        val bassPattern = intArrayOf(0, 0, 0, 7, 0, 0, 3, 0, 0, 0, 7, 0, 5, 0, 3, 0)
        val leadPattern = intArrayOf(0, 7, 3, 10, 12, 7, 15, 3, 5, 12, 7, 10, 3, 15, 7, 12)
        val chordProgression = arrayOf(
            intArrayOf(0, 3, 7),
            intArrayOf(0, 5, 8),
            intArrayOf(-2, 3, 7),
            intArrayOf(0, 3, 10)
        )

        output.parentFile?.mkdirs()
        val frames = ShortArray(SAMPLE_RATE * 2) // one second of stereo interleaved
        var delayIndex = 0
        val delayLine = FloatArray(SAMPLE_RATE / 2)
        var noiseState = seed.toInt().takeIf { it != 0 } ?: 0x1234567

        FileOutputStream(output).use { raw ->
            val out = BufferedOutputStream(raw, 1 shl 20)
            writeWavHeader(out, totalFrames)
            var frame = 0
            while (frame < totalFrames) {
                val count = min(SAMPLE_RATE, totalFrames - frame)
                for (i in 0 until count) {
                    val t = (frame + i).toDouble() / SAMPLE_RATE

                    noiseState = noiseState xor (noiseState shl 13)
                    noiseState = noiseState xor (noiseState ushr 17)
                    noiseState = noiseState xor (noiseState shl 5)
                    val noise = (noiseState and 0x7fffffff) / 1073741824.0 - 1.0

                    val barPos = t / bar
                    val barIndex = floor(barPos).toInt()
                    val barFrac = barPos - barIndex
                    val beatPos = t / beat
                    val beatIndex = floor(beatPos).toInt()
                    val beatFrac = beatPos - beatIndex
                    val stepPos = t / step
                    val stepIndex = floor(stepPos).toInt()
                    val stepFrac = stepPos - stepIndex
                    val section = sectionAt(barIndex.toDouble() / totalBars)

                    val kickTime = beatFrac * beat
                    val kickEnv = if (section != Section.BREAKDOWN || beatFrac < 0.12) {
                        exp(-kickTime * 18.0)
                    } else 0.0
                    val kickPitch = 42.0 + 90.0 * exp(-kickTime * 32.0)
                    val kick = sin(TWO_PI * kickPitch * kickTime) * kickEnv * 0.72

                    val bassGate = when (section) {
                        Section.INTRO -> if (stepIndex % 4 == 0) 1.0 else 0.0
                        Section.BREAKDOWN -> if (stepIndex % 8 == 0) 0.45 else 0.0
                        else -> 1.0
                    }
                    val bassNote = bassPattern[stepIndex % bassPattern.size]
                    val bassFreq = root * 2.0.pow(bassNote / 12.0)
                    val bassEnv = exp(-stepFrac * step * 13.0) * bassGate
                    val bassPhase = TWO_PI * bassFreq * t
                    val bassSaw = 2.0 * (bassPhase / TWO_PI - floor(bassPhase / TWO_PI + 0.5))
                    val duck = if (section == Section.BREAKDOWN) 1.0 else 1.0 - 0.55 * exp(-kickTime * 9.0)
                    val bass = (0.68 * sin(bassPhase) + 0.32 * bassSaw) * bassEnv * duck * 0.34

                    val leadGate = when (section) {
                        Section.INTRO, Section.OUTRO -> 0.0
                        Section.BREAKDOWN -> if (stepIndex % 8 == 0) 1.0 else 0.25
                        else -> 1.0
                    }
                    val leadNote = leadPattern[(stepIndex + barIndex / 2) % leadPattern.size]
                    val leadFreq = root * 4.0 * 2.0.pow(scale[leadNote % scale.size] / 12.0)
                    val leadEnv = exp(-stepFrac * step * 7.0) * leadGate
                    val leadPhase = TWO_PI * leadFreq * t
                    val leadSaw = 2.0 * (leadPhase / TWO_PI - floor(leadPhase / TWO_PI + 0.5))
                    val lead = leadSaw * leadEnv * 0.16

                    val hatGate = if (stepIndex % 4 == 2) 1.0 else 0.0
                    val hatEnv = exp(-stepFrac * step * 55.0) * hatGate
                    val hat = noise * hatEnv * 0.075

                    val clapGate = if (beatIndex % 4 == 1 || beatIndex % 4 == 3) 1.0 else 0.0
                    val clapEnv = exp(-beatFrac * beat * 26.0) * clapGate
                    val clap = noise * clapEnv * 0.10

                    val chord = chordProgression[barIndex % chordProgression.size]
                    val padEnv = exp(-barFrac * 1.5)
                    var pad = 0.0
                    for (note in chord) {
                        val freq = root * 2.0 * 2.0.pow(note / 12.0)
                        pad += sin(TWO_PI * freq * t) * 0.06
                    }
                    pad *= padEnv * when (section) {
                        Section.BREAKDOWN -> 1.7
                        Section.INTRO -> 0.8
                        else -> 1.0
                    }

                    val riser = if (section == Section.BUILD && barIndex >= (totalBars * 0.18).toInt()) {
                        val progress = (barIndex - totalBars * 0.18) / max(1.0, totalBars * 0.07)
                        noise * 0.08 * progress.coerceIn(0.0, 1.0) * (0.5 + 0.5 * sin(TWO_PI * 4.0 * t))
                    } else 0.0

                    val dry = kick + bass + lead + hat + clap + pad + riser
                    val delayed = delayLine[delayIndex]
                    delayLine[delayIndex] = (dry + delayed * 0.30).toFloat()
                    delayIndex = (delayIndex + 1) % delayLine.size

                    val mono = tanh((dry + delayed * 0.24) * 1.35)
                    val left = tanh((mono + delayed * 0.06) * 0.92)
                    val right = tanh((mono - delayed * 0.045) * 0.92)
                    val idx = i * 2
                    frames[idx] = (left.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
                    frames[idx + 1] = (right.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
                }
                out.write(shortsToBytes(frames, count * 2))
                frame += count
                if (frame % (SAMPLE_RATE * 5) == 0) onProgress((frame * 100L / totalFrames).toInt())
            }
            out.flush()
            onProgress(100)
        }
        return PsytranceResult(output, seconds, safeBpm, seed)
    }

    private enum class Section { INTRO, BUILD, MAIN, BREAKDOWN, OUTRO }

    private fun sectionAt(position: Double): Section = when {
        position < 0.09 -> Section.INTRO
        position < 0.24 -> Section.BUILD
        position < 0.56 -> Section.MAIN
        position < 0.68 -> Section.BREAKDOWN
        position < 0.92 -> Section.MAIN
        else -> Section.OUTRO
    }

    private fun writeWavHeader(out: BufferedOutputStream, frames: Int) {
        val dataBytes = frames.toLong() * 4L
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt((36L + dataBytes).toInt())
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1)
        header.putShort(2)
        header.putInt(SAMPLE_RATE)
        header.putInt(SAMPLE_RATE * 4)
        header.putShort(4)
        header.putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataBytes.toInt())
        out.write(header.array())
    }

    private fun shortsToBytes(samples: ShortArray, count: Int): ByteArray {
        val bytes = ByteArray(count * 2)
        var j = 0
        for (i in 0 until count) {
            val value = samples[i].toInt()
            bytes[j++] = (value and 0xff).toByte()
            bytes[j++] = ((value shr 8) and 0xff).toByte()
        }
        return bytes
    }
}
