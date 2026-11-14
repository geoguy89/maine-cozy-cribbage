package com.geoguy89.cribbage.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.geoguy89.cribbage.audio.AudioPlayer
import com.geoguy89.cribbage.audio.Sfx
import com.geoguy89.cribbage.audio.tuneFor
import com.geoguy89.cribbage.board.BoardDesign
import com.geoguy89.cribbage.board.Boards
import com.geoguy89.cribbage.data.Progress
import com.geoguy89.cribbage.data.SavedGame
import com.geoguy89.cribbage.data.Settings
import com.geoguy89.cribbage.data.Stats
import com.geoguy89.cribbage.data.Store
import com.geoguy89.cribbage.game.Difficulty
import com.geoguy89.cribbage.game.Lesson
import com.geoguy89.cribbage.game.LogEntry
import com.geoguy89.cribbage.game.ScoreKind
import com.geoguy89.cribbage.net.HttpTransport
import com.geoguy89.cribbage.net.OnlineService
import com.geoguy89.cribbage.theme.Look
import com.geoguy89.cribbage.theme.Season
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class Screen { TITLE, GAME, LESSONS, LESSON, BOARDS, SETTINGS, FRIENDS, STATS, RULES }
enum class Haptic { TICK, CONFIRM, REJECT, HEAVY }

/** What the app needs from the device. */
interface Platform {
    val versionName: String get() = "dev"
    val versionCode: Int get() = 0
    fun haptic(kind: Haptic) {}
    fun share(text: String) {}
    fun openUrl(url: String) {}
    fun askNotificationPermission() {}
    /** Download and install a newer APK in place (Android); false where the platform can't. */
    fun installUpdate(apkUrl: String, progress: (Float) -> Unit, done: (String?) -> Unit): Boolean = false
}

object NoPlatform : Platform

/** Foldable posture: where the hinge is, if the device is half open. */
data class Posture(val tabletopHingeY: Float? = null, val bookHingeX: Float? = null, val hingeThickness: Float = 0f)

class AppModel(
    val store: Store,
    val audio: AudioPlayer,
    val http: HttpTransport,
    val platform: Platform,
    val scope: CoroutineScope,
    private val today: () -> LocalDate = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date },
) {
    var settings by mutableStateOf(store.settings())
        private set
    var stats by mutableStateOf(store.stats())
        private set
    var progress by mutableStateOf(store.progress())
        private set
    var screen by mutableStateOf(Screen.TITLE)
    var table by mutableStateOf<Table?>(null)
        private set
    var lesson by mutableStateOf<Lesson?>(null)
        private set
    /** Where to go back to from a page. */
    private var returnTo = Screen.TITLE
    var toast by mutableStateOf<String?>(null)
    /** A newer version on GitHub: its version name and download page. */
    var update by mutableStateOf<Pair<String, String>?>(null)
        private set
    /** Download progress (0-1) while an update installs. */
    var updating by mutableStateOf<Float?>(null)
        private set

    /** Installs the update in place where possible, otherwise opens the download. */
    fun updateNow() {
        val (_, url) = update ?: return
        updating = 0f
        val started = platform.installUpdate(url, progress = { p -> scope.launch { updating = p } }) { err ->
            scope.launch {
                updating = null
                if (err != null) { toast = "$err Opening the download instead."; platform.openUrl(url) }
            }
        }
        if (!started) { updating = null; platform.openUrl(url) }
    }

    fun skipUpdate() { update = null }
    private var recordedGame: Any? = null

    val online = OnlineService(http, post = { f -> scope.launch { f() } }, store.account(), store::saveAccount)

    val season: Season? get() = settings.pinnedSeason ?: if (settings.seasonal) Season.forDate(today()) else null
    val look: Look get() = Look(settings.theme, season)
    val board: BoardDesign get() = Boards.byId(settings.board)
    val hasSavedGame: Boolean get() = store.savedGame()?.state?.over == false

    init {
        audio.musicVolume = settings.music
        audio.sfxVolume = settings.sfx
        audio.setTune(tuneFor(season))
        online.onSynced = { notifyIfNeeded() }
        // Keep friends and games fresh: often while those screens are open, now and then otherwise.
        scope.launch {
            while (isActive) {
                if (online.account != null && foreground) online.sync()
                (table as? OnlineTable)?.tick()
                delay(if (screen == Screen.FRIENDS || table is OnlineTable) 6_000 else 30_000)
            }
        }
    }

    var foreground = true
        private set

    fun onForeground() {
        foreground = true
        if (settings.music > 0f) audio.startMusic()
        online.sync()
    }

    fun onBackground() {
        foreground = false
        audio.pauseMusic()
    }

    fun updateSettings(f: (Settings) -> Settings) {
        val old = settings
        val s = f(old)
        settings = s
        store.saveSettings(s)
        audio.musicVolume = s.music
        audio.sfxVolume = s.sfx
        if (old.music == 0f && s.music > 0f) audio.startMusic()
        if (s.music == 0f) audio.pauseMusic()
        audio.setTune(tuneFor(season))
        if (old.playerName != s.playerName && s.playerName.isNotBlank()) online.setName(s.playerName)
    }

    fun sound(sfx: Sfx, volume: Float = 1f, rate: Float = 1f) = audio.play(sfx, volume, rate)
    fun haptic(h: Haptic) { if (settings.haptics) platform.haptic(h) }

    fun go(s: Screen) {
        sound(Sfx.CLICK, 0.6f)
        if (s in listOf(Screen.SETTINGS, Screen.BOARDS, Screen.RULES, Screen.STATS) && screen != s) returnTo = screen
        if (s == Screen.FRIENDS) online.sync()
        screen = s
    }

    fun back() {
        when (screen) {
            Screen.SETTINGS, Screen.BOARDS, Screen.RULES, Screen.STATS -> screen = if (returnTo == screen) Screen.TITLE else returnTo
            Screen.LESSON -> screen = Screen.LESSONS
            Screen.GAME -> leaveGame()
            else -> screen = Screen.TITLE
        }
    }

    // ---- solo games ---------------------------------------------------------------------------------------------

    fun newSoloGame(difficulty: Difficulty = settings.difficulty) {
        table?.close()
        updateSettings { it.copy(difficulty = difficulty) }
        val seed = Clock.System.now().toEpochMilliseconds()
        table = LocalTable(scope, difficulty, settings.playerName, null, 0, seed, ::saveSolo).also(::wire)
        store.saveGame(SavedGame(table!!.state, difficulty, 0, seed))
        sound(Sfx.SHUFFLE)
        screen = Screen.GAME
    }

    fun resumeSolo() {
        val saved = store.savedGame() ?: return newSoloGame()
        table?.close()
        // A fresh seed, so a resumed game doesn't replay the same deals.
        table = LocalTable(scope, saved.difficulty, settings.playerName, saved.state, saved.shownHand, saved.seed + saved.state.seq, ::saveSolo).also(::wire)
        screen = Screen.GAME
    }

    private fun saveSolo(s: com.geoguy89.cribbage.game.GameState, shown: Int, seed: Long) {
        val t = table as? LocalTable ?: return
        store.saveGame(if (s.over) null else SavedGame(s, t.difficulty, shown, seed))
    }

    private fun wire(t: Table) {
        t.onEvent = { e -> onGameEvent(t, e) }
    }

    private fun onGameEvent(t: Table, e: LogEntry) {
        when {
            e.item.kind == ScoreKind.SAYS_GO -> sound(Sfx.GO, 0.7f)
            e.count > 0 || e.item.kind == ScoreKind.HEELS -> {
                sound(if (e.item.points >= 6) Sfx.BIG_SCORE else Sfx.SCORE, 0.8f)
                if (e.player == t.me) haptic(Haptic.CONFIRM)
            }
        }
    }

    /** Opens a prepared table (previews and tests). */
    fun openTable(t: Table) {
        table?.close()
        table = t.also(::wire)
        screen = Screen.GAME
    }

    fun openLessonAt(l: Lesson) { lesson = l; screen = Screen.LESSON }

    fun leaveGame() {
        table?.close()
        table = null
        screen = if (screen == Screen.GAME && returnTo == Screen.FRIENDS) Screen.FRIENDS else Screen.TITLE
        returnTo = Screen.TITLE
    }

    /** Called once when a game ends: stats for solo games, a fanfare either way. */
    fun recordResult(t: Table) {
        val s = t.state
        if (!s.over || recordedGame === s) return
        recordedGame = s
        val won = s.winner == t.me
        sound(if (won) Sfx.WIN else Sfx.LOSE)
        haptic(if (won) Haptic.HEAVY else Haptic.REJECT)
        if (t is LocalTable) {
            val lvl = t.difficulty
            stats = stats.copy(
                played = stats.played + 1, won = stats.won + if (won) 1 else 0,
                skunksGiven = stats.skunksGiven + if (won && s.skunk > 0) 1 else 0,
                skunksTaken = stats.skunksTaken + if (!won && s.skunk > 0) 1 else 0,
                playedByLevel = stats.playedByLevel + (lvl to (stats.playedByLevel[lvl] ?: 0) + 1),
                wonByLevel = if (won) stats.wonByLevel + (lvl to (stats.wonByLevel[lvl] ?: 0) + 1) else stats.wonByLevel,
            ).also(store::saveStats)
            store.saveGame(null)
        }
    }

    /** Hand and crib records, as each show is counted. */
    fun recordShow(t: Table) {
        val show = t.state.lastShow ?: return
        var st = stats
        for (c in show.counts.filter { it.player == t.me }) {
            st = if (c.crib) st.copy(bestCrib = maxOf(st.bestCrib, c.score.total))
            else st.copy(bestHand = maxOf(st.bestHand, c.score.total), handsCounted = st.handsCounted + 1, handPoints = st.handPoints + c.score.total)
        }
        stats = st
        store.saveStats(st)
    }

    fun recordDrill(right: Boolean) {
        stats = stats.copy(drillTried = stats.drillTried + 1, drillRight = stats.drillRight + if (right) 1 else 0).also(store::saveStats)
    }

    // ---- lessons -----------------------------------------------------------------------------------------------

    fun openLesson(l: Lesson) {
        lesson = l
        go(Screen.LESSON)
    }

    fun finishLesson(l: Lesson) {
        progress = Progress(progress.lessonsDone + l.id).also(store::saveProgress)
        sound(Sfx.CHIME)
        screen = Screen.LESSONS
    }

    // ---- online -------------------------------------------------------------------------------------------------

    fun openOnlineGame(id: String) {
        online.loadGame(id, onError = { toast = it }) { v ->
            table?.close()
            val key = "shown-$id"
            val shown = store.notified()[key] ?: 0
            table = OnlineTable(id, online, v, shown, onShown = { h -> store.saveNotified(store.notified() + (key to h)) }).also(::wire)
            returnTo = Screen.FRIENDS
            screen = Screen.GAME
        }
    }

    fun ensureOnline(then: () -> Unit = {}) {
        if (online.account != null) { then(); return }
        platform.askNotificationPermission()
        online.ensureAccount(settings.playerName.ifBlank { "Mainer" }, then = { online.sync(); then() }, onError = { toast = it })
    }

    /** Sounds a chime when a friend's move makes it our turn while we're looking at something else. */
    private var lastTurns: Set<String> = emptySet()
    private fun notifyIfNeeded() {
        val now = online.games.filter { it.myMove && !it.over }.map { it.id }.toSet()
        val fresh = now - lastTurns
        val current = (table as? OnlineTable)?.gameId
        if (fresh.any { it != current } && lastTurns.isNotEmpty()) {
            sound(Sfx.CHIME, 0.6f)
            toast = "Your turn against ${online.games.first { it.id in fresh }.name}"
        }
        lastTurns = now
    }

    // ---- updates ------------------------------------------------------------------------------------------------

    /** Looks for a newer build on GitHub Releases. */
    fun checkForUpdate(http: HttpTransport, quiet: Boolean = true) {
        http.request("GET", "https://api.github.com/repos/geoguy89/maine-cozy-cribbage/releases/latest", null, mapOf("Accept" to "application/vnd.github+json")) { code, body ->
            scope.launch {
                if (code != 200 || body == null) { if (!quiet) toast = "Couldn't check for updates."; return@launch }
                runCatching {
                    val o = Json.parseToJsonElement(body).jsonObject
                    val tag = o["tag_name"]!!.jsonPrimitive.content
                    val build = tag.substringAfter("build.", "0").toIntOrNull() ?: 0
                    val apk = o["assets"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
                    val url = apk?.get("browser_download_url")?.jsonPrimitive?.content ?: o["html_url"]!!.jsonPrimitive.content
                    if (build > platform.versionCode && platform.versionCode > 0) update = tag.removePrefix("v").substringBefore("-build") to url
                    else if (!quiet) toast = "You're up to date."
                }
            }
        }
    }
}
