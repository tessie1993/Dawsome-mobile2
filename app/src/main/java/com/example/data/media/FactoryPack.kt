package com.example.data.media

import android.content.Context
import com.example.synth.engine.WireProtocol
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * The generated factory starter library (spec P1 §12: the browser's factory
 * content). Eight one-shot WAVs are SYNTHESIZED deterministically on first
 * run into `filesDir/factory/v1/` — no binaries in the repo or APK, and the
 * files are real WAVs the native decoder reads like any user sample.
 *
 * Identity discipline: `fileId = fnv1a64("factory/v1/<name>")` — the
 * LIBRARY-RELATIVE path, so the id is stable across devices and installs
 * while the absolute path may differ (WireProtocol fileId rule).
 *
 * Format: 44.1 kHz mono 16-bit PCM. The engine's SampleCache conforms to the
 * device rate once at load (decision D5), so the generation rate is a
 * storage choice, not a playback constraint.
 *
 * [ensureInstalled] is idempotent and thread-safe; call it off the main
 * thread (it writes ~1 MB of audio on the first run only). A `.complete`
 * marker gates regeneration; bumping [PACK_DIR] versions the whole pack.
 */
/** One installed factory sample, ready for the browser and the engine. */
data class FactorySample(
    val id: String,          // library-relative path ("factory/v1/kick_808.wav")
    val name: String,        // display
    val path: String,        // absolute filesystem path for the engine
    val fileId: Long,        // fnv1a64(id)
    val rootNote: Int,       // MIDI root for melodic playback (60 = as-is)
    val target: FactoryPack.Target,
    val tags: List<String>,
)

object FactoryPack {

    const val SAMPLE_RATE = 44100
    private const val PACK_DIR = "factory/v1"
    private const val MARKER = ".complete"

    /** Where a factory sample wants to land, for one-tap assignment. */
    enum class Target { DRUM_PAD, MELODIC }

    private val _samples = MutableStateFlow<List<FactorySample>>(emptyList())
    val samples: StateFlow<List<FactorySample>> = _samples

    private val lock = Any()

    fun ensureInstalled(context: Context) {
        synchronized(lock) {
            if (_samples.value.isNotEmpty()) return
            val dir = File(context.filesDir, PACK_DIR)
            val specs = packSpecs()
            val marker = File(dir, MARKER)
            // A real WAV is header (44) + audio; length > 44 rejects the
            // zero-length husks a crash-window rename can leave (cycle-3).
            val complete = marker.exists() &&
                specs.all { File(dir, it.fileName).length() > 44L }
            if (!complete) {
                dir.mkdirs()
                for (spec in specs) writeWavMono16(File(dir, spec.fileName), spec.render())
                marker.writeText("1")
            }
            _samples.value = specs.map { spec ->
                val rel = "$PACK_DIR/${spec.fileName}"
                FactorySample(
                    id = rel,
                    name = spec.displayName,
                    path = File(dir, spec.fileName).absolutePath,
                    fileId = WireProtocol.fnv1a64(rel),
                    rootNote = spec.rootNote,
                    target = spec.target,
                    tags = spec.tags,
                )
            }
        }
    }

    // ---- the pack -----------------------------------------------------------

    private class SoundSpec(
        val fileName: String,
        val displayName: String,
        val rootNote: Int,
        val target: Target,
        val tags: List<String>,
        val render: () -> FloatArray,
    )

    private fun packSpecs(): List<SoundSpec> = listOf(
        SoundSpec("kick_808.wav", "808 Kick", 60, Target.DRUM_PAD,
            listOf("Drums", "Kick", "808")) { renderKick() },
        SoundSpec("snare_tight.wav", "Tight Snare", 60, Target.DRUM_PAD,
            listOf("Drums", "Snare")) { renderSnare() },
        SoundSpec("clap_room.wav", "Room Clap", 60, Target.DRUM_PAD,
            listOf("Drums", "Clap")) { renderClap() },
        SoundSpec("hat_closed.wav", "Closed Hat", 60, Target.DRUM_PAD,
            listOf("Drums", "Hat")) { renderHat(decaySec = 0.07f) },
        SoundSpec("hat_open.wav", "Open Hat", 60, Target.DRUM_PAD,
            listOf("Drums", "Hat", "Open")) { renderHat(decaySec = 0.45f) },
        SoundSpec("pluck_c3.wav", "String Pluck C3", 48, Target.MELODIC,
            listOf("Melodic", "Pluck", "Karplus")) { renderPluck() },
        SoundSpec("bass_c2.wav", "Analog Bass C2", 36, Target.MELODIC,
            listOf("Melodic", "Bass", "Sub")) { renderBass() },
        SoundSpec("stab_am.wav", "Am Synth Stab", 57, Target.MELODIC,
            listOf("Melodic", "Chord", "Stab")) { renderStab() },
    )

    // ---- synthesis ----------------------------------------------------------
    // Deterministic (fixed-seed LCG noise), peak-normalized to -1 dBFS.

    /** Numerical Recipes LCG — repeatable noise across devices and runs. */
    private class Lcg(private var s: Int) {
        fun next(): Float {
            s = s * 1664525 + 1013904223
            return (s ushr 8).toFloat() / 0x00FFFFFF.toFloat() * 2f - 1f
        }
    }

    private fun frames(seconds: Float) = (SAMPLE_RATE * seconds).toInt()

    /** Sine w/ exponential pitch drop 150→48 Hz + drive: the 808 kick recipe. */
    private fun renderKick(): FloatArray {
        val out = FloatArray(frames(0.55f))
        var phase = 0.0
        for (i in out.indices) {
            val t = i.toFloat() / SAMPLE_RATE
            val freq = 48f + 102f * exp(-t / 0.045f)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val amp = exp(-t / 0.18f)
            out[i] = tanh(1.4f * sin(phase).toFloat() * amp)
        }
        return normalize(out)
    }

    /** 185 Hz body + highpassed noise — the classic two-component snare split. */
    private fun renderSnare(): FloatArray {
        val out = FloatArray(frames(0.22f))
        val rng = Lcg(0x5EED5EED)
        var phase = 0.0
        var hpState = 0f
        for (i in out.indices) {
            val t = i.toFloat() / SAMPLE_RATE
            phase += 2.0 * PI * 185.0 / SAMPLE_RATE
            val body = sin(phase).toFloat() * exp(-t / 0.045f) * 0.6f
            val n = rng.next()
            hpState += 0.22f * (n - hpState)          // one-pole LP ~1.8 kHz
            val noise = (n - hpState) * exp(-t / 0.09f) * 0.9f
            out[i] = body + noise
        }
        return normalize(out)
    }

    /** Four pre-delayed bursts + tail — the researched room-clap envelope. */
    private fun renderClap(): FloatArray {
        val out = FloatArray(frames(0.28f))
        val rng = Lcg(0x00C1A900)
        val bursts = intArrayOf(0, frames(0.011f), frames(0.023f), frames(0.036f))
        var lp = 0f
        for (i in out.indices) {
            val t = i.toFloat() / SAMPLE_RATE
            var env = 0f
            for (b in bursts) {
                if (i >= b) {
                    val bt = (i - b).toFloat() / SAMPLE_RATE
                    env += exp(-bt / 0.008f) * 0.7f
                }
            }
            env += if (i >= bursts[3]) {
                exp(-(t - 0.036f) / 0.08f) * 0.8f
            } else 0f
            val n = rng.next()
            lp += 0.35f * (n - lp)                    // tame the top
            out[i] = (n - 0.5f * lp) * env
        }
        return normalize(out)
    }

    /** Six-detuned-square metal cluster, hard highpass — the 808 hat family. */
    private fun renderHat(decaySec: Float): FloatArray {
        val out = FloatArray(frames(decaySec * 1.6f))
        val freqs = floatArrayOf(3211f, 4156f, 5273f, 6549f, 8012f, 9531f)
        val phases = DoubleArray(freqs.size)
        var hp = 0f
        for (i in out.indices) {
            val t = i.toFloat() / SAMPLE_RATE
            var s = 0f
            for (k in freqs.indices) {
                phases[k] += 2.0 * PI * freqs[k] / SAMPLE_RATE
                s += if (sin(phases[k]) >= 0.0) 1f else -1f
            }
            s /= freqs.size
            hp += 0.55f * (s - hp)                    // strong one-pole HP
            out[i] = (s - hp) * exp(-t / decaySec)
        }
        return normalize(out)
    }

    /** Karplus-Strong at C3: noise burst into an averaged delay loop. */
    private fun renderPluck(): FloatArray {
        val out = FloatArray(frames(0.9f))
        val rng = Lcg(0x4B41524C)
        val period = (SAMPLE_RATE / 130.81f).toInt()   // C3
        val loop = FloatArray(period) { rng.next() }
        var idx = 0
        for (i in out.indices) {
            val cur = loop[idx]
            val nxt = loop[(idx + 1) % period]
            out[i] = cur
            loop[idx] = 0.996f * 0.5f * (cur + nxt)    // damped average
            idx = (idx + 1) % period
        }
        return normalize(out)
    }

    /** C2 saw + sub sine through a closing one-pole lowpass. */
    private fun renderBass(): FloatArray {
        val out = FloatArray(frames(0.65f))
        val freq = 65.41f                              // C2
        var saw = 0f
        var lp = 0f
        var phase = 0.0
        val inc = freq / SAMPLE_RATE
        for (i in out.indices) {
            val t = i.toFloat() / SAMPLE_RATE
            saw += 2f * inc
            if (saw > 1f) saw -= 2f
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val cutoff = 150f + 1050f * exp(-t / 0.12f)
            val a = 1f - exp(-2f * PI.toFloat() * cutoff / SAMPLE_RATE)
            lp += a * (saw * 0.7f - lp)
            out[i] = (lp + 0.5f * sin(phase * 0.5).toFloat()) * exp(-t / 0.35f)
        }
        return normalize(out)
    }

    /** Detuned-saw A-minor triad hit through a fast closing lowpass. */
    private fun renderStab(): FloatArray {
        val out = FloatArray(frames(0.45f))
        val roots = floatArrayOf(220.00f, 261.63f, 329.63f)   // A3, C4, E4
        val detune = floatArrayOf(0.9965f, 1.0f, 1.0042f)
        val saws = FloatArray(roots.size * detune.size)
        var lp = 0f
        for (i in out.indices) {
            val t = i.toFloat() / SAMPLE_RATE
            var s = 0f
            var v = 0
            for (f in roots) for (d in detune) {
                saws[v] += 2f * (f * d) / SAMPLE_RATE
                if (saws[v] > 1f) saws[v] -= 2f
                s += saws[v]
                ++v
            }
            s /= saws.size
            val cutoff = 300f + 4700f * exp(-t / 0.07f)
            val a = 1f - exp(-2f * PI.toFloat() * cutoff / SAMPLE_RATE)
            lp += a * (s - lp)
            out[i] = lp * exp(-t / 0.16f)
        }
        return normalize(out)
    }

    private fun normalize(x: FloatArray): FloatArray {
        var peak = 1e-9f
        for (v in x) peak = maxOf(peak, abs(v))
        val g = 0.891f / peak                          // -1 dBFS
        for (i in x.indices) x[i] *= g
        // Bake a 15 ms fade into the tail: the exponential decays are still
        // -14..-26 dB at the truncation point, and SimpleSampler's one-shot
        // end is a hard cut - an assigned open hat would tick without this
        // (review cycle-3). Auditions/pads mask it; files must not rely on
        // the player to.
        val fade = minOf(frames(0.015f), x.size)
        for (i in 0 until fade) {
            x[x.size - fade + i] *= 1f - i.toFloat() / fade
        }
        return x
    }

    // ---- WAV writing --------------------------------------------------------

    /** Canonical 44-byte-header mono 16-bit PCM writer (little-endian). */
    private fun writeWavMono16(file: File, data: FloatArray) {
        val dataBytes = data.size * 2
        val bytes = ByteArray(44 + dataBytes)
        val b = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + dataBytes)
            .put("WAVE".toByteArray(Charsets.US_ASCII))
            .put("fmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
            .putShort(1)                               // PCM
            .putShort(1)                               // mono
            .putInt(SAMPLE_RATE)
            .putInt(SAMPLE_RATE * 2)                   // byte rate
            .putShort(2)                               // block align
            .putShort(16)                              // bits
            .put("data".toByteArray(Charsets.US_ASCII)).putInt(dataBytes)
        for (v in data) {
            val s = (v.coerceIn(-1f, 1f) * 32767f).toInt()
            b.putShort(s.toShort())
        }
        // Write-fsync-rename: without the sync, delayed allocation after a
        // power loss can leave a zero-length file behind the rename that the
        // completeness gate would trust forever (review cycle-3; the gate
        // also demands a plausible length, belt and braces).
        val tmp = File(file.parentFile, file.name + ".tmp")
        RandomAccessFile(tmp, "rw").use { raf ->
            raf.setLength(0)
            raf.write(bytes)
            raf.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
