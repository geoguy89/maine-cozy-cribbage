package com.geoguy89.cribbage.audio

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random


/** Every sound effect, synthesised: wooden clicks for pegs, papery snaps for cards, and a loon for luck. */
object SoundDesigns {
    const val SFX_RATE = 22050

    fun render(sfx: Sfx): FloatArray {
        val s = Synth(SFX_RATE)
        return when (sfx) {
            Sfx.PEG -> {
                // A hardwood peg seated in its hole: a short knock with a hollow body.
                val b = s.buffer(0.22f)
                s.noise(b, 0f, 0.025f, 0.6f, 3200.0, 900.0, decay = 0.008f)
                s.tone(b, 0f, 620.0, 560.0, 0.18f, 0.35f, decay = 0.035f)
                s.tone(b, 0f, 1240.0, 1180.0, 0.08f, 0.12f, decay = 0.015f)
                s.normalize(b, 0.8f)
            }
            Sfx.CARD -> {
                val b = s.buffer(0.18f)
                s.noise(b, 0f, 0.09f, 0.7f, 6000.0, 1800.0, decay = 0.03f)
                s.tone(b, 0f, 180.0, 120.0, 0.08f, 0.2f, decay = 0.02f)
                s.normalize(b, 0.7f)
            }
            Sfx.DEAL -> {
                val b = s.buffer(0.12f)
                s.noise(b, 0f, 0.1f, 0.6f, 7000.0, 2500.0, decay = 0.025f, highpass = true)
                s.normalize(b, 0.55f)
            }
            Sfx.SHUFFLE -> {
                // A riffle: a run of tiny paper flicks.
                val b = s.buffer(0.9f)
                val r = Random(4)
                var t = 0f
                while (t < 0.8f) {
                    s.noise(b, t, 0.03f, 0.25f + r.nextFloat() * 0.2f, 6500.0, 3000.0, decay = 0.006f, highpass = true)
                    t += 0.012f + r.nextFloat() * 0.018f
                }
                s.fadeOut(s.normalize(b, 0.55f), 0.2f)
            }
            Sfx.SCORE -> {
                val b = s.buffer(0.7f)
                s.pluck(b, 0f, s.midi(74.0), 0.6f, 0.4f, bright = 0.6f)
                s.pluck(b, 0.07f, s.midi(79.0), 0.6f, 0.35f, bright = 0.6f)
                s.normalize(s.reverb(b, 0.3f), 0.6f)
            }
            Sfx.BIG_SCORE -> {
                val b = s.buffer(1.3f)
                for ((i, n) in listOf(67, 71, 74, 79, 83).withIndex()) s.pluck(b, i * 0.06f, s.midi(n.toDouble()), 1.0f, 0.35f, bright = 0.6f)
                s.bell(b, 0.3f, s.midi(91.0), 1.0f, 0.12f)
                s.normalize(s.reverb(b, 0.4f), 0.7f)
            }
            Sfx.GO -> {
                val b = s.buffer(0.3f)
                s.tone(b, 0f, 330.0, 300.0, 0.25f, 0.4f, Synth.Wave.TRIANGLE, decay = 0.06f)
                s.noise(b, 0f, 0.02f, 0.3f, 2000.0, 800.0, decay = 0.006f)
                s.normalize(b, 0.55f)
            }
            Sfx.CLICK -> {
                val b = s.buffer(0.08f)
                s.tone(b, 0f, 900.0, 700.0, 0.06f, 0.4f, decay = 0.012f)
                s.normalize(b, 0.4f)
            }
            Sfx.CHIME -> {
                val b = s.buffer(1.4f)
                s.bell(b, 0f, s.midi(84.0), 1.3f, 0.4f, bright = 0.7f)
                s.bell(b, 0.12f, s.midi(91.0), 1.2f, 0.25f, bright = 0.7f)
                s.normalize(s.reverb(b, 0.35f), 0.6f)
            }
            Sfx.WIN -> {
                // A fiddle-ish reel run up to a bright chord, then bells.
                val b = s.buffer(2.6f)
                val run = listOf(62, 66, 69, 74, 69, 74, 78, 81)
                run.forEachIndexed { i, n -> s.pluck(b, i * 0.1f, s.midi(n.toDouble()), 0.8f, 0.35f, bright = 0.65f) }
                for (n in listOf(62, 66, 69, 74)) s.pluck(b, 0.85f, s.midi(n.toDouble()), 1.6f, 0.3f, bright = 0.5f)
                s.bell(b, 0.9f, s.midi(86.0), 1.6f, 0.15f)
                s.normalize(s.reverb(b, 0.45f), 0.75f)
            }
            Sfx.LOSE -> {
                val b = s.buffer(2.0f)
                listOf(69, 67, 64, 62).forEachIndexed { i, n -> s.pluck(b, i * 0.22f, s.midi(n.toDouble()), 1.2f, 0.3f, bright = 0.35f) }
                s.normalize(s.reverb(b, 0.5f), 0.6f)
            }
            Sfx.LOON -> loon(s)
        }
    }

    /** The loon's tremolo-free wail: a slow rise, a hold, and a falling break, with a hollow, breathy tone. */
    fun loon(s: Synth, amp: Float = 0.5f, at: Float = 0f, buf: FloatArray? = null): FloatArray {
        val b = buf ?: s.buffer(3.2f)
        s.tone(b, at, 520.0, 780.0, 0.9f, amp, attack = 0.35f, release = 0.1f, vibrato = 0.004)
        s.tone(b, at + 0.9f, 780.0, 760.0, 1.1f, amp, attack = 0.05f, release = 0.15f, vibrato = 0.006)
        s.tone(b, at + 2.0f, 900.0, 640.0, 1.0f, amp * 0.8f, attack = 0.05f, release = 0.5f, vibrato = 0.008)
        s.tone(b, at, 1040.0, 1560.0, 0.9f, amp * 0.12f, attack = 0.35f)
        return if (buf == null) s.normalize(s.reverb(b, 0.6f, 0.9f), 0.6f) else b
    }

    fun lowpass(buf: FloatArray, cutoff: Double, sr: Int): FloatArray {
        val a = (1 - kotlin.math.exp(-2 * PI * cutoff / sr)).toFloat()
        var y = 0f
        for (i in buf.indices) { y += a * (buf[i] - y); buf[i] = y }
        return buf
    }
}
