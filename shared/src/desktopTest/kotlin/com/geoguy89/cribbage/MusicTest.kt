package com.geoguy89.cribbage

import com.geoguy89.cribbage.audio.MusicComposer
import com.geoguy89.cribbage.audio.Sfx
import com.geoguy89.cribbage.audio.SoundDesigns
import com.geoguy89.cribbage.audio.Synth
import com.geoguy89.cribbage.audio.Tune
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders every tune and sound to build/audio as WAV files (to listen to) and checks none clips or comes out silent. */
class MusicTest {
    @Test
    fun tunesAndSounds() {
        val dir = File("build/audio").apply { mkdirs() }
        for (t in Tune.entries) {
            val buf = MusicComposer.compose(t)
            assertTrue(buf.size > MusicComposer.RATE * 20, "$t is at least 20 seconds")
            assertTrue(buf.maxOf { abs(it) } in 0.3f..1f, "$t level")
            File(dir, "music_${t.name.lowercase()}.wav").writeBytes(Synth.toWav(buf, MusicComposer.RATE))
        }
        for (s in Sfx.entries) {
            val buf = SoundDesigns.render(s)
            assertTrue(buf.maxOf { abs(it) } > 0.1f, "$s audible")
            File(dir, "sfx_${s.name.lowercase()}.wav").writeBytes(Synth.toWav(buf, SoundDesigns.SFX_RATE))
        }
    }
}
