package com.geoguy89.cribbage.audio

/**
 * The soundtrack: kitchen-party music from down east, written out note by note. A fiddle tune over strummed guitar,
 * upright bass and brushes for the lakeside; felt piano over a breathing accordion by the woodstove; a music box,
 * fiddle and sleigh bells for the holidays. Played in a small, warm room and looped seamlessly.
 */
object MusicComposer {
    const val RATE = 22050

    private val chords = mapOf(
        "D" to intArrayOf(50, 54, 57), "G" to intArrayOf(43, 47, 50), "A" to intArrayOf(45, 49, 52), "Bm" to intArrayOf(47, 50, 54),
        "Em" to intArrayOf(40, 43, 47), "C" to intArrayOf(48, 52, 55), "Am" to intArrayOf(45, 48, 52),
    )

    /** A waltz: tempo, a chord per bar, and the melody bar by bar as (midi note or 0 for a rest, beats). */
    private class Waltz(val bpm: Double, val bars: List<String>, val melody: List<List<Pair<Int, Int>>>)

    private val lakeside = Waltz(
        96.0,
        listOf("D", "D", "G", "D", "Bm", "G", "A", "A", "D", "D", "G", "Em", "G", "A", "D", "D"),
        listOf(
            listOf(69 to 1, 66 to 1, 69 to 1), listOf(74 to 2, 69 to 1), listOf(71 to 1, 69 to 1, 67 to 1), listOf(66 to 3),
            listOf(66 to 1, 67 to 1, 69 to 1), listOf(71 to 2, 74 to 1), listOf(73 to 1, 71 to 1, 69 to 1), listOf(64 to 3),
            listOf(69 to 1, 66 to 1, 69 to 1), listOf(74 to 2, 76 to 1), listOf(78 to 1, 76 to 1, 74 to 1), listOf(71 to 2, 67 to 1),
            listOf(69 to 1, 71 to 1, 74 to 1), listOf(73 to 1, 76 to 1, 73 to 1), listOf(74 to 3), listOf(0 to 3),
        ),
    )

    private val woodstove = Waltz(
        80.0,
        listOf("Em", "Em", "C", "G", "D", "D", "Em", "Em", "C", "G", "D", "Bm", "C", "D", "Em", "Em"),
        listOf(
            listOf(71 to 2, 67 to 1), listOf(76 to 2, 74 to 1), listOf(72 to 2, 71 to 1), listOf(71 to 1, 69 to 1, 67 to 1),
            listOf(69 to 2, 66 to 1), listOf(74 to 3), listOf(71 to 1, 69 to 1, 67 to 1), listOf(64 to 3),
            listOf(76 to 2, 74 to 1), listOf(74 to 1, 71 to 1, 67 to 1), listOf(69 to 1, 66 to 1, 69 to 1), listOf(71 to 3),
            listOf(67 to 1, 69 to 1, 71 to 1), listOf(69 to 2, 66 to 1), listOf(64 to 3), listOf(0 to 3),
        ),
    )

    private val holiday = Waltz(
        92.0,
        listOf("G", "G", "C", "G", "Em", "Am", "D", "D", "G", "G", "C", "Am", "G", "D", "G", "G"),
        listOf(
            listOf(74 to 1, 71 to 1, 74 to 1), listOf(79 to 2, 74 to 1), listOf(76 to 1, 74 to 1, 72 to 1), listOf(71 to 3),
            listOf(71 to 1, 72 to 1, 74 to 1), listOf(76 to 2, 72 to 1), listOf(74 to 1, 72 to 1, 69 to 1), listOf(69 to 3),
            listOf(74 to 1, 71 to 1, 74 to 1), listOf(79 to 2, 78 to 1), listOf(76 to 1, 79 to 1, 76 to 1), listOf(72 to 3),
            listOf(71 to 1, 74 to 1, 79 to 1), listOf(78 to 1, 76 to 1, 74 to 1), listOf(79 to 3), listOf(0 to 3),
        ),
    )

    fun compose(tune: Tune): FloatArray = when (tune) {
        Tune.LAKESIDE -> render(lakeside, Style.FIDDLE)
        Tune.WOODSTOVE -> render(woodstove, Style.PIANO)
        Tune.HOLIDAY -> render(holiday, Style.MUSIC_BOX)
    }

    private enum class Style { FIDDLE, PIANO, MUSIC_BOX }

    private fun render(t: Waltz, style: Style): FloatArray {
        val s = Synth(RATE)
        val beat = (60.0 / t.bpm).toFloat()
        val bar = beat * 3
        val loop = bar * t.bars.size
        val n = loop + 3f
        val mix = s.buffer(n)
        val lead = s.buffer(n) // the melody, mellowed on its own
        val pad = s.buffer(n) // the accordion, mellowed on its own
        t.bars.forEachIndexed { i, name ->
            val t0 = i * bar
            val ch = chords.getValue(name)
            // Upright bass: the root on one, the fifth on three.
            for ((b, note) in listOf(0 to ch[0] - 12, 2 to ch[2] - 12)) {
                s.tone(mix, t0 + b * beat, s.midi(note.toDouble()), s.midi(note.toDouble()), beat * 1.1f, 0.22f, attack = 0.01f, release = 0.12f, decay = 0.35f)
                s.tone(mix, t0 + b * beat, s.midi(note + 12.0), s.midi(note + 12.0), beat * 0.5f, 0.05f, Synth.Wave.TRIANGLE, decay = 0.1f)
            }
            if (style == Style.PIANO) {
                // Accordion: two reeds a hair apart, breathing in with the bellows.
                for (note in ch) for (det in doubleArrayOf(-0.08, 0.08)) {
                    s.tone(pad, t0, s.midi(note + 12 + det), s.midi(note + 12 + det), bar + 0.05f, 0.035f, Synth.Wave.SQUARE, attack = 0.35f, release = 0.4f, vibrato = 0.003)
                }
                // Light fingerpicking on two and three.
                for (b in 1..2) s.pluck(mix, t0 + b * beat, s.midi(ch[b] + 12.0), beat * 1.5f, 0.07f, damping = 0.992f, bright = 0.5f)
            } else {
                // Guitar: a full strum on one, quick chucks on two and three.
                val voicing = intArrayOf(ch[0], ch[2], ch[0] + 12, ch[1] + 12, ch[2] + 12)
                voicing.forEachIndexed { k, note -> s.pluck(mix, t0 + k * 0.014f, s.midi(note.toDouble()), beat * 1.6f, 0.1f, damping = 0.994f, bright = 0.55f) }
                for (b in 1..2) for (k in 2 until voicing.size) {
                    s.pluck(mix, t0 + b * beat + (k - 2) * 0.01f, s.midi(voicing[k].toDouble()), beat * 0.35f, 0.055f, damping = 0.97f, bright = 0.7f)
                }
                // Brushes on the snare.
                for (b in 0..2) s.noise(mix, t0 + b * beat, beat * 0.6f, if (b == 0) 0.025f else 0.04f, 6000.0, 2500.0, attack = 0.03f, decay = 0.12f, highpass = true)
            }
            if (style == Style.MUSIC_BOX) for (b in 0..2) {
                // Sleigh bells: a shimmer of tiny jingles on each beat.
                for (j in 0 until 5) s.noise(mix, t0 + b * beat + j * 0.012f, 0.05f, 0.02f, 11000.0, 8000.0, decay = 0.02f, highpass = true)
            }
            // The melody.
            var at = t0
            for ((note, beats) in t.melody[i]) {
                val dur = beats * beat
                val f = s.midi(note.toDouble())
                if (note > 0) when (style) {
                    Style.FIDDLE -> {
                        s.tone(lead, at, f, f, dur * 0.97f, 0.11f, Synth.Wave.SAW, attack = 0.06f, release = 0.12f, vibrato = 0.006)
                        s.tone(lead, at, f * 1.003, f * 1.003, dur * 0.97f, 0.05f, Synth.Wave.SAW, attack = 0.08f, release = 0.12f, vibrato = 0.005)
                    }
                    Style.PIANO -> {
                        // Felt piano: a soft hammer, a quick fade, a faint octave above.
                        s.tone(lead, at, f, f, dur + 1.2f, 0.18f, attack = 0.006f, release = 0.3f, decay = 0.9f)
                        s.tone(lead, at, f * 2, f * 2, dur + 0.6f, 0.04f, Synth.Wave.TRIANGLE, attack = 0.004f, decay = 0.4f)
                        s.noise(lead, at, 0.02f, 0.02f, 1200.0, 600.0, decay = 0.008f)
                    }
                    Style.MUSIC_BOX -> {
                        // A music-box comb: bright and short with a glassy partial; a fiddle doubles it an octave down.
                        s.tone(lead, at, f * 2, f * 2, 1.4f, 0.12f, attack = 0.002f, decay = 0.35f)
                        s.tone(lead, at, f * 6, f * 6, 0.4f, 0.025f, attack = 0.002f, decay = 0.08f)
                        s.tone(lead, at, f, f, dur * 0.95f, 0.05f, Synth.Wave.SAW, attack = 0.08f, release = 0.12f, vibrato = 0.006)
                    }
                }
                at += dur
            }
        }
        // Mellow the lead and the reeds, then put everything in a small wooden room.
        SoundDesigns.lowpass(lead, if (style == Style.MUSIC_BOX) 5200.0 else 2600.0, RATE)
        SoundDesigns.lowpass(pad, 1700.0, RATE)
        for (i in mix.indices) mix[i] += lead[i] + pad[i]
        val wet = s.reverb(mix, 0.22f, 0.62f, 0.55f)
        return s.normalize(foldLoop(wet, loop), 0.6f)
    }

    /** Fold the tail back onto the start so the loop is seamless. */
    private fun foldLoop(wet: FloatArray, loopSeconds: Float): FloatArray {
        val loopLen = (loopSeconds * RATE).toInt()
        val out = FloatArray(loopLen) { wet[it] }
        for (i in 0 until wet.size - loopLen) out[i % loopLen] += wet[loopLen + i]
        return out
    }
}
