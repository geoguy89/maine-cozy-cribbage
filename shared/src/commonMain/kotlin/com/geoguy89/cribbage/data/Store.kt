package com.geoguy89.cribbage.data

import com.geoguy89.cribbage.board.PegSet
import com.geoguy89.cribbage.game.Difficulty
import com.geoguy89.cribbage.game.GameState
import com.geoguy89.cribbage.net.Account
import com.geoguy89.cribbage.theme.BaseTheme
import com.geoguy89.cribbage.theme.Season
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Simple string storage; SharedPreferences on Android, memory in tests. */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

class MemoryKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
}

@Serializable
data class Settings(
    val playerName: String = "",
    val nameChosen: Boolean = false,
    val board: String = "sebec",
    val theme: BaseTheme = BaseTheme.PINE_TAVERN,
    /** Follow the calendar: seasons and holidays change the decorations on their own. */
    val seasonal: Boolean = true,
    /** Show this season or holiday whatever the date (null = follow the calendar). */
    val pinnedSeason: Season? = null,
    val music: Float = 0.45f,
    val sfx: Float = 0.8f,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    /** How much the helper explains in solo games. */
    val help: Help = Help.TUTOR,
    /** How quickly the computer plays. */
    val pace: Pace = Pace.RELAXED,
    val pegs: PegSet = PegSet.CLASSIC,
    val haptics: Boolean = true,
    /** Wildlife, fish and drifting particles. */
    val ambient: Boolean = true,
    /** Count the show for me (otherwise the player taps through each score). */
    val autoShow: Boolean = false,
    val notifications: Boolean = true,
    /** The opening walkthrough has been offered. */
    val welcomed: Boolean = false,
    /** With the helper on, the computer waits for Continue before each card it lays. */
    val stepByStep: Boolean = true,
    /** Move your own peg (tap the hole) for points scored in the play. */
    val selfPeg: Boolean = false,
)

/** Off: no helper. Tips: advice and a Why? panel. Tutor: every step explained, and the game waits for you. */
@Serializable
enum class Help(val title: String, val blurb: String) {
    OFF("Off", "Just play."),
    TIPS("Tips", "Suggested plays with a Why? panel."),
    TUTOR("Full tutor", "Explains every step, the arithmetic and the scoring, and waits for you to tap Continue."),
}

@Serializable
enum class Pace(val title: String, val factor: Float) { RELAXED("Relaxed", 1.8f), NORMAL("Normal", 1f), QUICK("Quick", 0.5f) }

@Serializable
data class Stats(
    val played: Int = 0,
    val won: Int = 0,
    val skunksGiven: Int = 0,
    val skunksTaken: Int = 0,
    val bestHand: Int = 0,
    val bestCrib: Int = 0,
    val bestPegging: Int = 0,
    val handsCounted: Int = 0,
    val handPoints: Int = 0,
    val wonByLevel: Map<Difficulty, Int> = emptyMap(),
    val playedByLevel: Map<Difficulty, Int> = emptyMap(),
    /** Count-the-hand drill: right answers and attempts. */
    val drillRight: Int = 0,
    val drillTried: Int = 0,
) {
    val averageHand: String get() = if (handsCounted == 0) "–" else com.geoguy89.cribbage.game.Ai.fmt(handPoints.toDouble() / handsCounted)
}

/** A solo game in progress, so it can be picked up again. */
@Serializable
data class SavedGame(val state: GameState, val difficulty: Difficulty, val shownHand: Int, val seed: Long)

@Serializable
data class Progress(val lessonsDone: Set<String> = emptySet())

private val NOTIFIED = kotlinx.serialization.builtins.MapSerializer(String.serializer(), Int.serializer())

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

class Store(private val kv: KeyValueStore) {
    private fun <T> read(key: String, ser: KSerializer<T>): T? =
        kv.get(key)?.let { runCatching { json.decodeFromString(ser, it) }.getOrNull() }
    private fun <T> write(key: String, ser: KSerializer<T>, value: T?) =
        kv.put(key, value?.let { json.encodeToString(ser, it) })

    fun settings(): Settings = read("settings", Settings.serializer()) ?: Settings()
    fun saveSettings(s: Settings) = write("settings", Settings.serializer(), s)
    fun stats(): Stats = read("stats", Stats.serializer()) ?: Stats()
    fun saveStats(s: Stats) = write("stats", Stats.serializer(), s)
    fun savedGame(): SavedGame? = read("game", SavedGame.serializer())
    fun saveGame(g: SavedGame?) = write("game", SavedGame.serializer(), g)
    fun progress(): Progress = read("progress", Progress.serializer()) ?: Progress()
    fun saveProgress(p: Progress) = write("progress", Progress.serializer(), p)
    fun account(): Account? = read("account", Account.serializer())
    fun saveAccount(a: Account?) = write("account", Account.serializer(), a)
    /** Game ids and versions the player has already been told about (for "your turn" notifications). */
    fun notified(): Map<String, Int> = read("notified", NOTIFIED) ?: emptyMap()
    fun saveNotified(m: Map<String, Int>) = write("notified", NOTIFIED, m)
}
