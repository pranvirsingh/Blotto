package com.pranvir.blotto

import java.io.ByteArrayOutputStream
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/** Sound effect ids. */
object Sfx {
    const val TAP = 0
    const val LOCK = 1
    const val NOPE = 2
    const val INK = 3
    const val BULB = 4
    const val STAR = 5
    const val WIN = 6
    const val BUTTON = 7
    const val IRIS_CLOSE = 8
    const val IRIS_OPEN = 9
    const val BEEP = 10
    const val TICK = 11
    const val TIMEUP = 12
    const val HINT = 13
    const val SOLVED = 14
    const val COUNT = 15
    const val PROJECTOR = 100
}

/** Procedural old-timey audio. Everything is synthesised, no audio files are shipped. */
object Synth {
    const val RATE = 22050
    private const val TAU = (2 * PI).toFloat()

    fun midiHz(m: Float): Float = (440.0 * 2.0.pow((m - 69.0) / 12.0)).toFloat()

    private fun buf(seconds: Float) = FloatArray((seconds * RATE).toInt().coerceAtLeast(1))

    private fun toPcm(f: FloatArray, gain: Float): ShortArray {
        var peak = 0.0001f
        for (v in f) { val a = if (v < 0) -v else v; if (a > peak) peak = a }
        val g = gain / peak
        return ShortArray(f.size) { (tanh((f[it] * g).toDouble()) * 32000).toInt().toShort() }
    }

    private fun env(t: Float, attack: Float, decay: Float): Float =
        (if (t < attack) t / attack else 1f) * exp(-(t - attack).coerceAtLeast(0f) * decay)

    fun render(id: Int): ShortArray {
        val rnd = Random(1234L + id)
        return when (id) {
            Sfx.TAP -> {
                val b = buf(0.09f)
                for (i in b.indices) {
                    val t = i / RATE.toFloat()
                    val e = env(t, 0.001f, 70f)
                    b[i] = e * (sin(TAU * 820f * t) + 0.5f * sin(TAU * 1480f * t) + 0.25f * sin(TAU * 2350f * t)) +
                        (if (t < 0.004f) (rnd.nextFloat() - 0.5f) * 1.2f * (1f - t / 0.004f) else 0f)
                }
                toPcm(b, 0.8f)
            }
            Sfx.LOCK -> {
                val b = buf(0.3f)
                val parts = floatArrayOf(1180f, 1930f, 3070f, 4210f)
                for (i in b.indices) {
                    val t = i / RATE.toFloat()
                    var s = 0f
                    for ((k, f) in parts.withIndex()) s += sin(TAU * f * t) * exp(-t * (14f + k * 6f)) / (k + 1)
                    s += if (t < 0.006f) (rnd.nextFloat() - 0.5f) * 1.5f else 0f
                    // second hit (the bolt seating)
                    val t2 = t - 0.07f
                    if (t2 > 0) s += 0.6f * sin(TAU * 640f * t2) * exp(-t2 * 40f)
                    b[i] = s
                }
                toPcm(b, 0.75f)
            }
            Sfx.NOPE -> {
                val b = buf(0.22f)
                var ph = 0f
                for (i in b.indices) {
                    val t = i / RATE.toFloat()
                    val f = 210f - 90f * (t / 0.22f)
                    ph += TAU * f / RATE
                    val sq = if (sin(ph) > 0) 1f else -1f
                    b[i] = env(t, 0.003f, 16f) * (0.6f * sin(ph) + 0.25f * sq)
                }
                toPcm(b, 0.7f)
            }
            Sfx.INK -> {
                val b = buf(0.14f)
                var ph = 0f
                for (i in b.indices) {
                    val t = i / RATE.toFloat()
                    val f = 260f + 900f * (t / 0.14f).pow(0.6f)
                    ph += TAU * f / RATE
                    b[i] = sin(ph) * env(t, 0.004f, 22f)
                }
                toPcm(b, 0.55f)
            }
            Sfx.BULB -> bell(0.9f, 880f, 3.2f, rnd)
            Sfx.STAR -> {
                val b = buf(0.6f)
                for (i in b.indices) {
                    val t = i / RATE.toFloat()
                    b[i] = sin(TAU * 1318f * t) * env(t, 0.002f, 6f) + 0.5f * sin(TAU * 2637f * t) * env(t, 0.002f, 10f) +
                        0.4f * sin(TAU * (1760f + 1500f * t) * t) * env(t, 0.01f, 12f)
                }
                toPcm(b, 0.7f)
            }
            Sfx.WIN -> {
                val b = buf(1.8f)
                val notes = floatArrayOf(72f, 76f, 79f, 84f, 79f, 84f)
                val starts = floatArrayOf(0f, 0.1f, 0.2f, 0.3f, 0.55f, 0.68f)
                for ((k, m) in notes.withIndex()) xylo(b, starts[k], midiHz(m), if (k >= 4) 1f else 0.8f)
                // final chord
                for (m in floatArrayOf(72f, 76f, 79f)) xylo(b, 0.68f, midiHz(m), 0.45f)
                toPcm(b, 0.8f)
            }
            Sfx.BUTTON -> {
                val b = buf(0.08f)
                var ph = 0f
                for (i in b.indices) {
                    val t = i / RATE.toFloat()
                    val f = 700f - 2500f * t
                    ph += TAU * f / RATE
                    b[i] = sin(ph) * env(t, 0.002f, 45f)
                }
                toPcm(b, 0.6f)
            }
            Sfx.IRIS_CLOSE -> slideWhistle(1350f, 480f, 0.42f, rnd)
            Sfx.IRIS_OPEN -> slideWhistle(520f, 1250f, 0.42f, rnd)
            Sfx.BEEP -> {
                val b = buf(0.16f)
                for (i in b.indices) {
                    val t = i / RATE.toFloat()
                    val e = if (t < 0.005f) t / 0.005f else if (t > 0.14f) (0.16f - t) / 0.02f else 1f
                    b[i] = sin(TAU * 1000f * t) * e
                }
                toPcm(b, 0.5f)
            }
            Sfx.TICK -> {
                val b = buf(0.04f)
                for (i in b.indices) {
                    val t = i / RATE.toFloat()
                    b[i] = (sin(TAU * 2600f * t) + (rnd.nextFloat() - 0.5f)) * env(t, 0.0005f, 180f)
                }
                toPcm(b, 0.5f)
            }
            Sfx.TIMEUP -> {
                // "wah wah wah waaah" on a muted trombone
                val b = buf(2.4f)
                val notes = floatArrayOf(64f, 63f, 62f, 61f)
                val st = floatArrayOf(0f, 0.42f, 0.84f, 1.26f)
                val du = floatArrayOf(0.38f, 0.38f, 0.38f, 1.1f)
                for (k in notes.indices) brass(b, st[k], du[k], midiHz(notes[k] - 12f), k == 3)
                toPcm(b, 0.75f)
            }
            Sfx.HINT -> {
                val b = buf(0.8f)
                val ms = floatArrayOf(84f, 88f, 91f, 96f)
                for ((k, m) in ms.withIndex()) {
                    val s0 = k * 0.07f
                    val f = midiHz(m)
                    val o = (s0 * RATE).toInt()
                    for (i in o until b.size) {
                        val t = (i - o) / RATE.toFloat()
                        b[i] += 0.5f * sin(TAU * f * t) * env(t, 0.002f, 7f)
                    }
                }
                toPcm(b, 0.6f)
            }
            Sfx.SOLVED -> {
                val b = buf(0.5f)
                xylo(b, 0f, midiHz(79f), 1f)
                xylo(b, 0.09f, midiHz(84f), 1f)
                toPcm(b, 0.7f)
            }
            Sfx.PROJECTOR -> projector(rnd)
            else -> ShortArray(1)
        }
    }

    private fun bell(seconds: Float, f: Float, decay: Float, rnd: Random): ShortArray {
        val b = buf(seconds)
        val ratios = floatArrayOf(1f, 2f, 2.76f, 5.4f)
        val amps = floatArrayOf(1f, 0.45f, 0.3f, 0.12f)
        for (i in b.indices) {
            val t = i / RATE.toFloat()
            var s = 0f
            for (k in ratios.indices) s += amps[k] * sin(TAU * f * ratios[k] * t) * exp(-t * decay * (1f + k * 0.8f))
            b[i] = s * (if (t < 0.002f) t / 0.002f else 1f)
        }
        return toPcm(b, 0.75f)
    }

    private fun xylo(b: FloatArray, start: Float, f: Float, amp: Float) {
        val o = (start * RATE).toInt()
        for (i in o until b.size) {
            val t = (i - o) / RATE.toFloat()
            if (t > 1.2f) break
            b[i] += amp * (sin(TAU * f * t) * exp(-t * 7f) + 0.35f * sin(TAU * f * 4f * t) * exp(-t * 30f)) *
                (if (t < 0.002f) t / 0.002f else 1f)
        }
    }

    private fun brass(b: FloatArray, start: Float, dur: Float, f: Float, wobble: Boolean) {
        val o = (start * RATE).toInt()
        var ph = 0f
        val n = ((dur + 0.08f) * RATE).toInt()
        for (j in 0 until n) {
            val i = o + j
            if (i >= b.size) break
            val t = j / RATE.toFloat()
            val vib = if (wobble && t > 0.25f) 1f + 0.025f * sin(TAU * 5.5f * t) else 1f
            val bend = if (t < 0.05f) 0.97f + 0.03f * (t / 0.05f) else 1f
            ph += TAU * f * vib * bend / RATE
            // mute "wah": brightness opens and closes
            val bright = 0.35f + 0.65f * sin((PI * (t / dur).coerceIn(0f, 1f)).toFloat())
            var s = 0f
            for (k in 1..7) s += sin(ph * k) / k * (if (k == 1) 1f else bright * 0.9f.pow(k))
            val e = when {
                t < 0.03f -> t / 0.03f
                t > dur -> ((dur + 0.08f - t) / 0.08f).coerceAtLeast(0f)
                else -> 1f
            }
            b[i] += s * e * 0.6f
        }
    }

    private fun slideWhistle(f0: Float, f1: Float, seconds: Float, rnd: Random): ShortArray {
        val b = buf(seconds)
        var ph = 0f
        for (i in b.indices) {
            val t = i / RATE.toFloat()
            val p = t / seconds
            val f = f0 + (f1 - f0) * (p * p * (3f - 2f * p)) * (1f + 0.012f * sin(TAU * 7f * t))
            ph += TAU * f / RATE
            val e = (if (p < 0.08f) p / 0.08f else 1f) * (if (p > 0.85f) (1f - p) / 0.15f else 1f)
            b[i] = e * (sin(ph) + 0.12f * sin(ph * 2f) + (rnd.nextFloat() - 0.5f) * 0.06f)
        }
        return toPcm(b, 0.5f)
    }

    /** One seamless second of projector clatter: 24 shutter clicks and a faint motor hum. */
    private fun projector(rnd: Random): ShortArray {
        val n = RATE
        val b = FloatArray(n)
        val per = n / 24f
        var lp = 0f
        for (i in 0 until n) {
            val t = i / RATE.toFloat()
            val local = (i % per.toInt()) / RATE.toFloat()
            val click = exp(-local * 420f) * (rnd.nextFloat() - 0.5f) * (if ((i / per.toInt()) % 2 == 0) 1f else 0.7f)
            lp += (click - lp) * 0.35f
            val hum = 0.05f * sin(TAU * 50f * t) + 0.02f * sin(TAU * 100f * t)
            val hiss = (rnd.nextFloat() - 0.5f) * 0.02f
            b[i] = lp * 1.4f + hum + hiss
        }
        return toPcm(b, 0.35f)
    }

    // ------------------------------------------------------------------ ragtime loop

    /** An original 8-bar honky-tonk rag, 100 bpm, loops seamlessly. */
    fun renderMusic(): ShortArray {
        val sixteenth = 0.15f
        val bars = 8
        val total = bars * 16 * sixteenth
        val n = (total * RATE).toInt()
        val b = FloatArray(n)
        val rnd = Random(77)

        // chords: bass root, bass alt, chord tones
        val bassRoot = intArrayOf(48, 48, 45, 45, 50, 43, 48, 43)
        val bassAlt = intArrayOf(43, 43, 40, 40, 45, 50, 43, 50)
        val chords = arrayOf(
            intArrayOf(52, 55, 60), intArrayOf(52, 55, 60), intArrayOf(55, 57, 61), intArrayOf(55, 57, 61),
            intArrayOf(54, 57, 60), intArrayOf(53, 55, 59), intArrayOf(52, 55, 60), intArrayOf(53, 55, 59)
        )
        for (bar in 0 until bars) {
            val t0 = bar * 16 * sixteenth
            piano(b, t0, 0.5f, bassRoot[bar], 0.9f, 3.5f)
            piano(b, t0 + 0.6f * 2, 0.5f, bassAlt[bar], 0.8f, 3.5f)
            for (beat in intArrayOf(1, 3)) for (m in chords[bar]) piano(b, t0 + 0.6f * beat, 0.25f, m, 0.33f, 6f)
        }
        // melody: triples of (bar, start16, dur16, midi)
        val mel = intArrayOf(
            0, 0, 3, 76, 0, 3, 3, 79, 0, 6, 2, 76, 0, 8, 2, 72, 0, 10, 2, 74, 0, 12, 4, 76,
            1, 0, 3, 79, 1, 3, 3, 76, 1, 6, 2, 79, 1, 8, 6, 84, 1, 14, 1, 83, 1, 15, 1, 81,
            2, 0, 3, 81, 2, 3, 3, 85, 2, 6, 2, 81, 2, 8, 2, 79, 2, 10, 2, 76, 2, 12, 2, 73, 2, 14, 2, 76,
            3, 0, 3, 79, 3, 3, 3, 76, 3, 6, 2, 73, 3, 8, 4, 69, 3, 12, 1, 69, 3, 13, 1, 71, 3, 14, 2, 73,
            4, 0, 3, 74, 4, 3, 3, 78, 4, 6, 2, 81, 4, 8, 3, 84, 4, 11, 3, 81, 4, 14, 2, 78,
            5, 0, 3, 79, 5, 3, 3, 83, 5, 6, 2, 86, 5, 8, 3, 77, 5, 11, 3, 74, 5, 14, 2, 71,
            6, 0, 2, 72, 6, 2, 2, 76, 6, 4, 2, 79, 6, 6, 2, 84, 6, 8, 3, 88, 6, 11, 1, 86, 6, 12, 4, 84,
            7, 0, 3, 83, 7, 3, 3, 79, 7, 6, 2, 77, 7, 8, 2, 74, 7, 10, 2, 71, 7, 12, 2, 67, 7, 14, 1, 71, 7, 15, 1, 74
        )
        var k = 0
        while (k + 3 < mel.size) {
            val t = (mel[k] * 16 + mel[k + 1]) * sixteenth
            val d = mel[k + 2] * sixteenth
            piano(b, t, d, mel[k + 3], 0.75f, 2.4f)
            // octave doubling for sparkle
            piano(b, t, d, mel[k + 3] + 12, 0.18f, 3.5f)
            k += 4
        }
        // Wrap the decaying tails of the last notes into the start so the loop is seamless
        // (piano() already wraps using modulo indexing).
        // vinyl crackle
        for (i in 0 until n) if (rnd.nextFloat() < 0.0012f) b[i] += (rnd.nextFloat() - 0.5f) * 0.5f
        return toPcm(b, 0.85f)
    }

    private fun piano(b: FloatArray, start: Float, dur: Float, midi: Int, amp: Float, decay: Float) {
        val f = midiHz(midi.toFloat())
        val f2 = f * 1.0035f // honky-tonk detune
        val n = b.size
        val o = (start * RATE).toInt()
        val len = ((dur + 0.25f) * RATE).toInt()
        val amps = floatArrayOf(1f, 0.5f, 0.28f, 0.16f, 0.08f)
        for (j in 0 until len) {
            val t = j / RATE.toFloat()
            val rel = if (t > dur) exp(-(t - dur) * 14f) else 1f
            val att = if (t < 0.004f) t / 0.004f else 1f
            var s = 0f
            for (h in amps.indices) {
                val hh = h + 1
                val e = exp(-t * decay * (1f + h * 0.5f))
                s += amps[h] * e * (sin(TAU * f * hh * t) + 0.7f * sin(TAU * f2 * hh * t))
            }
            b[(o + j) % n] += s * amp * att * rel * 0.5f
        }
    }

    // ------------------------------------------------------------------ wav

    fun wav(pcm: ShortArray): ByteArray {
        val out = ByteArrayOutputStream(44 + pcm.size * 2)
        fun i32(v: Int) { out.write(v and 255); out.write((v shr 8) and 255); out.write((v shr 16) and 255); out.write((v shr 24) and 255) }
        fun i16(v: Int) { out.write(v and 255); out.write((v shr 8) and 255) }
        out.write("RIFF".toByteArray()); i32(36 + pcm.size * 2); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); i32(16); i16(1); i16(1); i32(RATE); i32(RATE * 2); i16(2); i16(16)
        out.write("data".toByteArray()); i32(pcm.size * 2)
        for (s in pcm) i16(s.toInt())
        return out.toByteArray()
    }
}
