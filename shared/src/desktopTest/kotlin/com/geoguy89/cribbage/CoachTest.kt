package com.geoguy89.cribbage

import com.geoguy89.cribbage.game.Ai
import com.geoguy89.cribbage.game.Coach
import com.geoguy89.cribbage.game.Difficulty
import com.geoguy89.cribbage.game.Phase
import com.geoguy89.cribbage.game.Rules
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/** The helper always explains itself. Writes sample advice to build/coach.txt for reading. */
class CoachTest {
    @Test
    fun everyTipHasReasons() {
        val out = StringBuilder()
        repeat(6) { g ->
            val r = Random(40 + g)
            var s = Rules.newGame(r)
            var tips = 0
            while (!s.over && tips < 8) {
                if ((s.phase == Phase.DISCARD && s.hands[0].size == 6) || (s.phase == Phase.PEG && s.turn == 0)) {
                    val tip = Coach.tip(s, 0)!!
                    assertTrue(tip.why.size >= 3, "a reasoned tip: $tip")
                    out.appendLine("== ${tip.title}: ${tip.text}")
                    tip.why.forEach { out.appendLine("   $it") }
                    tips++
                }
                s = when (s.phase) {
                    Phase.DISCARD -> { val p = (0..1).first { s.hands[it].size == 6 }; Rules.discard(s, p, Ai.chooseDiscard(s.hands[p], s.dealer == p, Difficulty.HARD, r)) }
                    Phase.PEG -> {
                        val p = s.turn
                        val card = Ai.choosePlay(s, p, if (p == 0) Difficulty.EASY else Difficulty.HARD, r)
                        val next = Rules.play(s, p, card, r)
                        if (next.hand == s.hand && tips < 8) {
                            val rev = Coach.reviewPlay(s, next, p, card, 0, listOf("You", "Old-Timer"))
                            out.appendLine("-- ${rev.title}")
                            rev.why.forEach { out.appendLine("   $it") }
                        }
                        next
                    }
                    Phase.OVER -> s
                }
            }
        }
        File("build").mkdirs()
        File("build/coach.txt").writeText(out.toString())
    }
}
