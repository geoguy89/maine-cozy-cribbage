package com.geoguy89.cribbage

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.geoguy89.cribbage.audio.SilentAudio
import com.geoguy89.cribbage.data.MemoryKeyValueStore
import com.geoguy89.cribbage.data.Settings
import com.geoguy89.cribbage.data.Store
import com.geoguy89.cribbage.game.Ai
import com.geoguy89.cribbage.game.Difficulty
import com.geoguy89.cribbage.game.GameState
import com.geoguy89.cribbage.game.Lessons
import com.geoguy89.cribbage.game.Phase
import com.geoguy89.cribbage.game.Rules
import com.geoguy89.cribbage.net.NoHttp
import com.geoguy89.cribbage.theme.BaseTheme
import com.geoguy89.cribbage.theme.Season
import com.geoguy89.cribbage.ui.AppModel
import com.geoguy89.cribbage.ui.CribbageApp
import com.geoguy89.cribbage.ui.LocalTable
import com.geoguy89.cribbage.ui.NoPlatform
import com.geoguy89.cribbage.ui.Posture
import com.geoguy89.cribbage.ui.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.LocalDate
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** Renders the app's screens offscreen to build/screenshots, at phone and unfolded-foldable sizes. */
class ScreenRenderTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private fun model(settings: Settings = Settings(playerName = "Travis", nameChosen = true, welcomed = true), date: LocalDate = LocalDate(2026, 7, 14)): AppModel {
        val store = Store(MemoryKeyValueStore())
        store.saveSettings(settings)
        return AppModel(store, SilentAudio, NoHttp, NoPlatform, scope, today = { date })
    }

    private fun render(name: String, wDp: Int, hDp: Int, m: AppModel, posture: Posture = Posture(), frames: Int = 30) {
        val d = 2f
        ImageComposeScene((wDp * d).toInt(), (hDp * d).toInt(), Density(d)) { CribbageApp(m, posture) }.use { scene ->
            var t = 0L
            repeat(frames) { scene.render(t); t += 80_000_000L; Thread.sleep(50) }
            val bytes = scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes
            File("build/screenshots").mkdirs()
            File("build/screenshots/$name.png").writeBytes(bytes)
        }
    }

    /** A game a few cards into pegging, with some points on the board. */
    private fun midGame(seed: Int = 5, cardsPlayed: Int = 3): GameState {
        val r = Random(seed)
        var s = Rules.newGame(r)
        while (s.hand < 3 || s.phase != Phase.PEG || s.played.size < cardsPlayed) {
            s = when (s.phase) {
                Phase.DISCARD -> { val p = (0..1).first { s.hands[it].size == 6 }; Rules.discard(s, p, Ai.chooseDiscard(s.hands[p], s.dealer == p, Difficulty.MEDIUM, r)) }
                Phase.PEG -> Rules.play(s, s.turn, Ai.choosePlay(s, s.turn, Difficulty.MEDIUM, r), r)
                Phase.OVER -> Rules.newGame(r)
            }
        }
        // Make it the player's turn.
        while (s.turn != 0 && !s.over && s.phase == Phase.PEG) s = Rules.play(s, s.turn, Ai.choosePlay(s, s.turn, Difficulty.MEDIUM, r), r)
        return s
    }

    private fun table(m: AppModel, s: GameState, shown: Int = s.lastShow?.hand ?: 0) =
        LocalTable(scope, Difficulty.MEDIUM, "Travis", s, shown, 1L, { _, _, _ -> }, pace = 1000f, tutor = m.settings.help == com.geoguy89.cribbage.data.Help.TUTOR)

    @Test
    fun titleScreens() {
        render("title_phone", 412, 900, model())
        render("title_unfolded", 840, 700, model(Settings(playerName = "Travis", nameChosen = true, welcomed = true, theme = BaseTheme.FLANNEL_CABIN, board = "moosehead"), LocalDate(2026, 12, 18)))
    }

    @Test
    fun themesAndSeasons() {
        val combos = listOf(
            BaseTheme.PINE_TAVERN to LocalDate(2026, 10, 10),
            BaseTheme.FLANNEL_CABIN to LocalDate(2026, 12, 20),
            BaseTheme.BIRCH_MORNING to LocalDate(2027, 4, 2),
            BaseTheme.HARBOR_FOG to LocalDate(2026, 7, 4),
            BaseTheme.LOON_NIGHT to LocalDate(2026, 8, 5),
            BaseTheme.PINE_TAVERN to LocalDate(2026, 10, 30),
        )
        for ((theme, date) in combos) {
            render("theme_${theme.name.lowercase()}_${Season.forDate(date).name.lowercase()}", 412, 900, model(Settings(playerName = "Travis", nameChosen = true, welcomed = true, theme = theme), date))
        }
    }

    @Test
    fun gamePhone() {
        val m = model()
        m.openTable(table(m, midGame()))
        render("game_phone", 412, 900, m)
    }

    @Test
    fun gameTutor() {
        val m = model()
        val s = midGame(12, cardsPlayed = 2)
        val t = table(m, s)
        m.openTable(t)
        t.play(s.playable(0).first())
        render("game_tutor", 412, 900, m)
    }

    @Test
    fun welcomeAndScoringSheet() {
        val w = model(Settings(playerName = "", nameChosen = false, welcomed = false))
        render("title_welcome", 412, 900, w)
        val r = model(); r.go(Screen.RULES); render("page_scoring", 412, 1400, r)
    }

    @Test
    fun gameSmallPhone() {
        val m = model()
        m.openTable(table(m, midGame(21)))
        render("game_phone_small", 360, 760, m)
    }

    /** A hand where some cards would score right now, to see the gold +N badges. */
    @Test
    fun gameScoringBadges() {
        val seed = (1..400).first { sd ->
            val s = midGame(sd, cardsPlayed = 2)
            s.turn == 0 && s.phase == Phase.PEG && s.count in 1..30 && s.playable(0).any { com.geoguy89.cribbage.ui.pegPoints(s, it) > 0 }
        }
        val m = model()
        m.openTable(table(m, midGame(seed, cardsPlayed = 2)))
        render("game_badges", 412, 900, m)
    }

    @Test
    fun gameDiscard() {
        val m = model(Settings(playerName = "Travis", nameChosen = true, welcomed = true, board = "eastport", theme = BaseTheme.FLANNEL_CABIN))
        val s = Rules.newGame(Random(3))
        m.openTable(table(m, s))
        render("game_discard", 412, 900, m)
    }

    @Test
    fun gameUnfoldedAndTabletop() {
        val m = model(Settings(playerName = "Travis", nameChosen = true, welcomed = true, board = "katahdin", theme = BaseTheme.LOON_NIGHT))
        m.openTable(table(m, midGame(8)))
        render("game_unfolded", 840, 760, m)
        val m2 = model(Settings(playerName = "Travis", nameChosen = true, welcomed = true, board = "allagash"))
        m2.openTable(table(m2, midGame(9)))
        render("game_tabletop", 840, 760, m2, Posture(tabletopHingeY = 370f, hingeThickness = 0f))
    }

    @Test
    fun showOverlay() {
        val m = model(Settings(playerName = "Travis", nameChosen = true, welcomed = true, board = "schoodic"))
        val s = midGame(11)
        m.openTable(table(m, s, shown = (s.lastShow?.hand ?: 1) - 1))
        render("game_show", 412, 900, m)
    }

    /** The narrow cover screen of a foldable: everything has to fit, down to the buttons. */
    @Test
    fun gameCoverScreen() {
        val m = model(Settings(playerName = "Travis", nameChosen = true, welcomed = true, board = "eastport"))
        m.openTable(table(m, Rules.newGame(Random(3))))
        render("game_cover_discard", 338, 680, m)
        val m2 = model(Settings(playerName = "Travis", nameChosen = true, welcomed = true, board = "sebec", help = com.geoguy89.cribbage.data.Help.TIPS))
        m2.openTable(table(m2, midGame(21)))
        render("game_cover_play", 338, 680, m2)
        val m3 = model(Settings(playerName = "Travis", nameChosen = true, welcomed = true, board = "oob"))
        val s = midGame(12, cardsPlayed = 2)
        val t = table(m3, s)
        m3.openTable(t)
        t.play(s.playable(0).first())
        render("game_cover_tutor", 338, 680, m3)
    }

    /** Unfolded, nearly square: a long board runs across the top, a round one sits beside the cards. */
    @Test
    fun gameUnfoldedSquare() {
        val m = model(Settings(playerName = "Travis", nameChosen = true, welcomed = true, board = "sebec", theme = BaseTheme.GRANGE_HALL))
        m.openTable(table(m, midGame(8)))
        render("game_square_long_board", 700, 760, m)
        val m2 = model(Settings(playerName = "Travis", nameChosen = true, welcomed = true, board = "dirigo", theme = BaseTheme.ACADIA_SUNRISE, help = com.geoguy89.cribbage.data.Help.TIPS))
        val s = midGame(12, cardsPlayed = 2)
        val t = LocalTable(scope, Difficulty.MEDIUM, "Travis", s, s.lastShow?.hand ?: 0, 1L, { _, _, _ -> }, pace = 1000f, stepByStep = true)
        m2.openTable(t)
        t.play(s.playable(0).first())
        render("game_square_round_board_waiting", 700, 760, m2)
    }

    /** Peg it yourself: after a scoring card, the hole to move to glows. */
    @Test
    fun gameSelfPeg() {
        val seed = (1..400).first { sd ->
            val s = midGame(sd, cardsPlayed = 2)
            s.turn == 0 && s.phase == Phase.PEG && s.count in 1..30 && s.hands[0].size > 1 && s.playable(0).any { com.geoguy89.cribbage.ui.pegPoints(s, it) > 0 }
        }
        val m = model(Settings(playerName = "Travis", nameChosen = true, welcomed = true, board = "machias", selfPeg = true, theme = BaseTheme.LOBSTER_SHACK, help = com.geoguy89.cribbage.data.Help.TIPS))
        val s = midGame(seed, cardsPlayed = 2)
        val t = table(m, s)
        m.openTable(t)
        t.play(s.playable(0).first { com.geoguy89.cribbage.ui.pegPoints(s, it) > 0 })
        render("game_selfpeg", 412, 900, m)
    }

    @Test
    fun newBackdrops() {
        for (theme in listOf(BaseTheme.ACADIA_SUNRISE, BaseTheme.BLUEBERRY_BARRENS, BaseTheme.LOBSTER_SHACK, BaseTheme.GRANGE_HALL)) {
            render("backdrop_${theme.name.lowercase()}", 412, 900, model(Settings(playerName = "Travis", nameChosen = true, welcomed = true, theme = theme, seasonal = false, board = "pemaquid")))
        }
    }

    @Test
    fun pages() {
        val m = model()
        m.go(Screen.BOARDS); render("page_boards_phone", 412, 900, m)
        val wide = model(); wide.go(Screen.BOARDS); render("page_boards_unfolded", 1000, 760, wide)
        val s = model(); s.go(Screen.SETTINGS); render("page_settings", 412, 900, s)
        val l = model(); l.go(Screen.LESSONS); render("page_lessons", 412, 900, l)
        val f = model(); f.go(Screen.FRIENDS); render("page_friends_offline", 412, 900, f)
        for (id in listOf("fifteens", "pegging", "crib")) {
            val lm = model(); lm.openLessonAt(Lessons.byId(id)); render("lesson_$id", 412, 900, lm)
        }
    }

    @Test
    fun holidayCalendar() {
        assertEquals(Season.CHRISTMAS, Season.forDate(LocalDate(2026, 12, 18)))
        assertEquals(Season.THANKSGIVING, Season.forDate(LocalDate(2026, 11, 26)))
        assertEquals(Season.MAPLE_SUNDAY, Season.forDate(LocalDate(2027, 3, 28)))
        assertEquals(Season.EASTER, Season.forDate(LocalDate(2026, 4, 3)))
        assertEquals(Season.INDEPENDENCE, Season.forDate(LocalDate(2026, 7, 4)))
        assertEquals(Season.FALL, Season.forDate(LocalDate(2026, 11, 13)))
        assertEquals(Season.WINTER, Season.forDate(LocalDate(2027, 1, 20)))
        assertEquals(LocalDate(2027, 3, 28), Season.easter(2027))
    }
}
