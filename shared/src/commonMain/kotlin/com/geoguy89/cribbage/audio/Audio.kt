package com.geoguy89.cribbage.audio

import com.geoguy89.cribbage.theme.Season

/** The game's sound effects, all synthesised at first launch (see [SoundDesigns]). */
enum class Sfx { DEAL, CARD, PEG, SCORE, BIG_SCORE, GO, SHUFFLE, CLICK, WIN, LOSE, LOON, CHIME }

/** Which soundtrack to play: the everyday lakeside tune, or a seasonal variation. */
enum class Tune { LAKESIDE, WOODSTOVE, HOLIDAY }

fun tuneFor(season: Season?): Tune = when (season) {
    Season.CHRISTMAS, Season.NEW_YEAR -> Tune.HOLIDAY
    Season.WINTER, Season.FALL, Season.THANKSGIVING, Season.HALLOWEEN -> Tune.WOODSTOVE
    else -> Tune.LAKESIDE
}

/** Plays the sounds; each platform supplies its own backend. */
interface AudioPlayer {
    var sfxVolume: Float
    var musicVolume: Float
    fun play(sfx: Sfx, volume: Float = 1f, rate: Float = 1f)
    fun setTune(tune: Tune)
    fun startMusic()
    fun pauseMusic()
    fun release()
}

object SilentAudio : AudioPlayer {
    override var sfxVolume = 0f
    override var musicVolume = 0f
    override fun play(sfx: Sfx, volume: Float, rate: Float) = Unit
    override fun setTune(tune: Tune) = Unit
    override fun startMusic() = Unit
    override fun pauseMusic() = Unit
    override fun release() = Unit
}
