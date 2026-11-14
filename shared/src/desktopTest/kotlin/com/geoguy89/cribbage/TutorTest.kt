package com.geoguy89.cribbage

import com.geoguy89.cribbage.game.Ai
import com.geoguy89.cribbage.game.Difficulty
import com.geoguy89.cribbage.game.Narrator
import com.geoguy89.cribbage.game.Phase
import com.geoguy89.cribbage.game.Rules
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/** The full tutor narrates every step of real games. Writes what it says to build/tutor.txt for reading. */
class TutorTest {
    @Test
    fun narratesWholeHands() {
        val out = StringBuilder()
        val names = listOf("You", "Camp Regular")
        val r = Random(77)
        var s = Rules.newGame(r)
        fun log(n: com.geoguy89.cribbage.game.Note) { out.appendLine("== ${n.title}"); n.lines.forEach { out.appendLine("   $it") } }
        log(Narrator.firstDeal(s, 0, names))
        var notes = 1
        while (!s.over && s.hand <= 3) {
            val before = s
            s = when (s.phase) {
                Phase.DISCARD -> {
                    val p = (0..1).first { s.hands[it].size == 6 }
                    val throwAway = Ai.chooseDiscard(s.hands[p], s.dealer == p, Difficulty.MEDIUM, r)
                    if (p == 0) log(Narrator.discard(s.hands[0], throwAway, s.dealer == 0))
                    Rules.discard(s, p, throwAway)
                }
                Phase.PEG -> {
                    val p = s.turn
                    val card = Ai.choosePlay(s, p, Difficulty.MEDIUM, r)
                    val next = Rules.play(s, p, card, r)
                    Narrator.play(s, next, p, card, 0, names).forEach { log(it); notes++ }
                    next
                }
                Phase.OVER -> s
            }
            if (before.phase == Phase.DISCARD && s.phase == Phase.PEG) log(Narrator.cut(before, s, 0, names))
            if (s.hand != before.hand && s.lastShow != null) {
                val show = s.lastShow!!
                for (c in show.counts) {
                    out.appendLine("-- ${Narrator.showIntro(c, show.starter, 0, names)}")
                    c.score.items.forEach { out.appendLine("   ${Narrator.explain(it, show.starter, c.crib)}") }
                }
                if (!s.over) log(Narrator.deal(s, 0, names))
            }
        }
        File("build").mkdirs()
        File("build/tutor.txt").writeText(out.toString())
        assertTrue(notes > 20)
    }
}
