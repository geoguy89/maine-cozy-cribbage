package com.geoguy89.cribbage.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Sound effects through SoundPool, and the looping soundtrack through a static AudioTrack with loop points
 * (gapless). Everything is synthesised once in the background and cached on disk, so later launches are instant.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioEngine(private val context: Context) : AudioPlayer {
    /** One worker, so tune changes apply in order. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val pool = SoundPool.Builder().setMaxStreams(10).setAudioAttributes(attrs).build()
    private val ids = ConcurrentHashMap<Sfx, Int>()
    private val loaded = ConcurrentHashMap.newKeySet<Int>()

    @Volatile private var music: AudioTrack? = null
    @Volatile private var musicWanted = false
    @Volatile private var tune: Tune? = null

    override var sfxVolume = 0.8f
    override var musicVolume = 0.45f
        set(value) {
            field = value
            music?.setVolume(value * MUSIC_GAIN)
        }

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded += id }
        scope.launch { prepareSfx() }
    }

    override fun setTune(tune: Tune) {
        if (this.tune == tune) return
        this.tune = tune
        scope.launch { if (this@AudioEngine.tune == tune) prepareMusic(tune) }
    }

    private fun prepareSfx() {
        val dir = File(context.cacheDir, "sfx-v$SOUND_VERSION").apply { mkdirs() }
        for (sfx in Sfx.entries) {
            try {
                val f = File(dir, "${sfx.name.lowercase()}.wav")
                if (!f.exists()) {
                    val tmp = File(dir, f.name + ".tmp")
                    tmp.writeBytes(Synth.toWav(SoundDesigns.render(sfx), SoundDesigns.SFX_RATE))
                    tmp.renameTo(f)
                }
                ids[sfx] = pool.load(f.path, 1)
            } catch (e: Exception) {
                Log.w("AudioEngine", "Could not prepare $sfx", e)
            }
        }
    }

    private fun prepareMusic(tune: Tune) {
        try {
            music?.let { old -> music = null; old.stop(); old.release() }
            val f = File(context.cacheDir, "music-v$SOUND_VERSION-${tune.name.lowercase()}.pcm")
            val pcm: ShortArray = if (f.exists()) {
                val bytes = f.readBytes()
                ShortArray(bytes.size / 2) { ((bytes[it * 2 + 1].toInt() shl 8) or (bytes[it * 2].toInt() and 0xFF)).toShort() }
            } else {
                val shorts = Synth.toPcm16(MusicComposer.compose(tune))
                val bytes = ByteArray(shorts.size * 2)
                shorts.forEachIndexed { i, s -> bytes[i * 2] = (s.toInt() and 0xFF).toByte(); bytes[i * 2 + 1] = (s.toInt() shr 8).toByte() }
                val tmp = File(f.path + ".tmp")
                tmp.writeBytes(bytes)
                tmp.renameTo(f)
                shorts
            }
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(MusicComposer.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            track.write(pcm, 0, pcm.size)
            track.setLoopPoints(0, pcm.size, -1)
            track.setVolume(musicVolume * MUSIC_GAIN)
            music = track
            if (musicWanted && musicVolume > 0f) track.play()
        } catch (e: Exception) {
            Log.w("AudioEngine", "Could not prepare music", e)
        }
    }

    override fun play(sfx: Sfx, volume: Float, rate: Float) {
        if (sfxVolume <= 0f) return
        val id = ids[sfx] ?: return
        if (id !in loaded) return
        val v = (sfxVolume * volume).coerceIn(0f, 1f)
        pool.play(id, v, v, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    override fun startMusic() {
        musicWanted = true
        music?.let { if (it.playState != AudioTrack.PLAYSTATE_PLAYING && musicVolume > 0f) it.play() }
    }

    override fun pauseMusic() {
        musicWanted = false
        music?.let { if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.pause() }
    }

    override fun release() {
        pool.release()
        music?.release()
    }

    private companion object {
        const val SOUND_VERSION = 2
        const val MUSIC_GAIN = 0.6f
    }
}
