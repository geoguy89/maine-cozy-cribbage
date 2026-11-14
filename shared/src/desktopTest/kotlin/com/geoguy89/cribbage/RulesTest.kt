package com.geoguy89.cribbage

import com.geoguy89.cribbage.game.Ai
import com.geoguy89.cribbage.game.Card
import com.geoguy89.cribbage.game.Difficulty
import com.geoguy89.cribbage.game.GAME_POINTS
import com.geoguy89.cribbage.game.Phase
import com.geoguy89.cribbage.game.Rules
import com.geoguy89.cribbage.game.ScoreKind
import com.geoguy89.cribbage.game.Scoring
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RulesTest {
    private fun total(hand: String, starter: String, crib: Boolean = false) =
        Scoring.total(Card.list(hand), Card.parse(starter), crib)

    @Test
    fun famousHands() {
        assertEquals(29, total("5H 5D 5S JC", "5C"), "the perfect hand")
        assertEquals(28, total("5H 5D 5S 5C", "JC"))
        assertEquals(24, total("7H 7D 8C 8S", "9H"), "double double run")
        assertEquals(0, total("2C 4D 6S 8H", "KC"), "nineteen")
        assertEquals(14, total("4H 5D 6C 6S", "KD"), "double run with fifteens")
        assertEquals(12, total("3C 3D 3H 9S", "AC"), "three threes and a nine")
    }

    @Test
    fun flushRules() {
        assertEquals(8, total("2H 4H 6H QH", "9S"))
        assertEquals(4, total("2H 4H 6H QH", "9S", crib = true), "a four-card crib flush doesn't count")
        assertEquals(9, total("2H 4H 6H QH", "9H", crib = true), "five-card crib flush does")
        assertEquals(0, Scoring.score(Card.list("2H 4H 6H QD"), Card.parse("9H")).items.count { it.kind == ScoreKind.FLUSH })
    }

    @Test
    fun nobs() {
        val s = Scoring.score(Card.list("JH 2C 3D 7S"), Card.parse("4H"))
        assertEquals(1, s.items.count { it.kind == ScoreKind.NOBS })
        val none = Scoring.score(Card.list("JH 2C 3D 7S"), Card.parse("4S"))
        assertEquals(0, none.items.count { it.kind == ScoreKind.NOBS })
    }

    @Test
    fun peggingScores() {
        fun peg(cards: String): Int { val c = Card.list(cards); return Scoring.pegging(c, c.sumOf { it.value }).sumOf { it.points } }
        assertEquals(2, peg("5H KS"), "fifteen")
        assertEquals(2, peg("7H 7S"), "pair")
        assertEquals(6, peg("7H 7S 7D"), "pair royal")
        assertEquals(12, peg("2H 2S 2D 2C"), "double pair royal")
        assertEquals(3, peg("3H 5S 4D"), "run of three out of order")
        assertEquals(4, peg("3H 5S 4D 6C"), "run of four")
        assertEquals(0, peg("3H 5S 5D 4C"), "a pair breaks the run")
        assertEquals(2, peg("KH QS JD AC"), "thirty-one")
        assertEquals(5, peg("4H 5S 6D"), "run of three making fifteen")
    }

    @Test
    fun aiFindsTheObviousKeep() {
        val best = Ai.discardOptions(Card.list("5H 5D 5S JC 2C 9D"), ownCrib = true).first()
        assertEquals(setOf("2C", "9D"), best.throwAway.map { it.toString().replace("♣", "C").replace("♦", "D") }.toSet())
    }

    /** Whole games between two computer players: cards are conserved and the game always ends at 121. */
    @Test
    fun fullGames() {
        repeat(60) { g ->
            val r = Random(g)
            var s = Rules.newGame(r)
            var steps = 0
            while (!s.over) {
                steps++
                check(steps < 5000) { "game $g stuck" }
                when (s.phase) {
                    Phase.DISCARD -> {
                        val p = (0..1).first { s.hands[it].size == 6 }
                        s = Rules.discard(s, p, Ai.chooseDiscard(s.hands[p], s.dealer == p, Difficulty.entries[g % 3], r))
                    }
                    Phase.PEG -> {
                        val cardsOut = s.hands.sumOf { it.size } + s.crib.size + s.deck.size + 1 + s.played.size
                        assertEquals(52, cardsOut, "cards conserved")
                        assertTrue(s.count in 0..31)
                        assertTrue(s.canPlay(s.turn), "the player on turn can always lay a card")
                        s = Rules.play(s, s.turn, Ai.choosePlay(s, s.turn, Difficulty.entries[(g + s.turn) % 3], r), r)
                    }
                    Phase.OVER -> Unit
                }
            }
            assertEquals(GAME_POINTS, s.scores[s.winner!!])
            assertTrue(s.scores[1 - s.winner!!] < GAME_POINTS)
        }
    }

    @Test
    fun hardBeatsEasy() {
        var hardWins = 0
        repeat(40) { g ->
            val r = Random(1000 + g)
            var s = Rules.newGame(r)
            val level = listOf(Difficulty.HARD, Difficulty.EASY)
            while (!s.over) {
                s = when (s.phase) {
                    Phase.DISCARD -> { val p = (0..1).first { s.hands[it].size == 6 }; Rules.discard(s, p, Ai.chooseDiscard(s.hands[p], s.dealer == p, level[p], r)) }
                    Phase.PEG -> Rules.play(s, s.turn, Ai.choosePlay(s, s.turn, level[s.turn], r), r)
                    Phase.OVER -> s
                }
            }
            if (s.winner == 0) hardWins++
        }
        assertTrue(hardWins >= 24, "Old-Timer won only $hardWins of 40 against the Summer Visitor")
    }

    @Serializable data class HandVector(val hand: List<Int>, val starter: Int, val crib: Boolean, val total: Int)
    @Serializable data class PegVector(val pile: List<Int>, val points: Int)
    @Serializable data class Vectors(val hands: List<HandVector>, val pegging: List<PegVector>)

    /**
     * spec/scoring-vectors.json holds scored examples that the server's tests check its own scoring against. They're
     * regenerated when missing; otherwise this test checks they still agree with the engine.
     */
    @Test
    fun sharedScoringVectors() {
        val file = File("../spec/scoring-vectors.json")
        val json = Json { prettyPrint = false }
        if (!file.exists()) {
            val r = Random(121)
            val hands = (0 until 600).map {
                val d = Card.shuffled(r)
                val crib = it % 3 == 0
                // Bias some hands towards flushes so the crib rule is exercised.
                val hand = if (it % 7 == 0) d.filter { c -> c.suit == d[0].suit }.take(4) else d.take(4)
                val starter = (d - hand.toSet()).let { rest -> if (it % 14 == 0) rest.first { c -> c.suit == hand[0].suit } else rest.first() }
                HandVector(hand.map { c -> c.id }, starter.id, crib, Scoring.total(hand, starter, crib))
            }
            val pegging = (0 until 600).map {
                val d = Card.shuffled(r).sortedBy { c -> if (it % 2 == 0) c.rank else 0 }.let { s -> if (it % 2 == 0) s.drop(r.nextInt(30)) else s }
                val pile = mutableListOf<Card>()
                var count = 0
                for (c in d) { if (count + c.value > 31 || pile.size >= 8) break; pile += c; count += c.value }
                // Shuffle the tail a little so runs out of order turn up.
                val p = if (it % 3 == 0) pile.dropLast(3) + pile.takeLast(3).shuffled(r) else pile
                PegVector(p.map { c -> c.id }, Scoring.pegging(p, p.sumOf { c -> c.value }).sumOf { s -> s.points })
            }
            file.parentFile.mkdirs()
            file.writeText(json.encodeToString(Vectors.serializer(), Vectors(hands, pegging)))
        }
        val v = json.decodeFromString(Vectors.serializer(), file.readText())
        for (h in v.hands) assertEquals(h.total, Scoring.total(h.hand.map(Card::of), Card.of(h.starter), h.crib), "hand $h")
        for (p in v.pegging) {
            val pile = p.pile.map(Card::of)
            assertEquals(p.points, Scoring.pegging(pile, pile.sumOf { it.value }).sumOf { it.points }, "pile $p")
        }
        assertTrue(v.hands.any { it.total >= 20 } && v.pegging.any { it.points >= 3 })
    }
}
