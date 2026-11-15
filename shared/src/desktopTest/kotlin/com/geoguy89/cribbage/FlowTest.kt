package com.geoguy89.cribbage

import com.geoguy89.cribbage.game.Ai
import com.geoguy89.cribbage.game.Difficulty
import com.geoguy89.cribbage.game.GameState
import com.geoguy89.cribbage.game.Phase
import com.geoguy89.cribbage.game.Rules
import com.geoguy89.cribbage.ui.LocalTable
import com.geoguy89.cribbage.ui.pegPoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The pacing of a solo game: waiting for Continue, and waiting for the player to move their own peg. */
class FlowTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Both players have thrown to the crib and the play is about to start. */
    private fun dealt(seed: Int): GameState {
        val r = Random(seed)
        var s = Rules.newGame(r)
        while (s.phase == Phase.DISCARD) {
            val p = (0..1).first { s.hands[it].size == 6 }
            s = Rules.discard(s, p, Ai.chooseDiscard(s.hands[p], s.dealer == p, Difficulty.MEDIUM, r))
        }
        return s
    }

    private fun table(s: GameState, stepByStep: Boolean) =
        LocalTable(scope, Difficulty.MEDIUM, "Tester", s, s.lastShow?.hand ?: 0, 1L, { _, _, _ -> }, pace = 0.01f, stepByStep = stepByStep)

    private fun waitFor(what: () -> Boolean): Boolean {
        repeat(100) { if (what()) return true; Thread.sleep(30) }
        return what()
    }

    @Test
    fun computerWaitsForContinue() {
        val s = (1..50).map(::dealt).first { it.turn == 0 }
        val t = table(s, stepByStep = true)
        t.play(s.playable(0).first())
        assertEquals(1, t.state.turn)
        assertTrue(t.waiting, "the computer should hold its card until Continue")
        Thread.sleep(400)
        assertEquals(1, t.state.played.size, "nothing moves while the player is reading")
        t.proceed()
        assertTrue(waitFor { t.state.played.size >= 2 }, "after Continue the computer plays")
    }

    @Test
    fun withoutStepByStepTheComputerJustPlays() {
        val s = (1..50).map(::dealt).first { it.turn == 0 }
        val t = table(s, stepByStep = false)
        t.play(s.playable(0).first())
        assertTrue(waitFor { t.state.played.size >= 2 })
    }

    @Test
    fun scoringInThePlayWaitsForThePeg() {
        // The computer leads, and the player has a card that scores off it.
        val r = Random(7)
        val (s, card) = (1..400).firstNotNullOf { seed ->
            val d = dealt(seed)
            if (d.turn != 1) null else {
                val led = Rules.play(d, 1, Ai.choosePlay(d, 1, Difficulty.MEDIUM, r), r)
                led.playable(0).firstOrNull { pegPoints(led, it) > 0 }?.let { led to it }
            }
        }
        val t = table(s, stepByStep = false)
        t.selfPeg = true
        val before = t.state.scores[0]
        t.play(card)
        assertTrue(t.state.scores[0] > before)
        assertEquals(before, t.pegAt, "the peg stays put until the player moves it")
        assertNotNull(t.pegOwed)
        Thread.sleep(300)
        assertEquals(2, t.state.played.size, "the computer waits for the peg")
        t.pegMoved()
        assertNull(t.pegOwed)
        assertEquals(t.state.scores[0], t.pegAt)
        assertTrue(waitFor { t.state.played.size >= 3 || t.state.turn == 0 })
    }
}
