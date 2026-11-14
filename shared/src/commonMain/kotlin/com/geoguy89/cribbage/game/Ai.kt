package com.geoguy89.cribbage.game

import com.geoguy89.cribbage.game.Difficulty.EASY
import com.geoguy89.cribbage.game.Difficulty.HARD
import com.geoguy89.cribbage.game.Difficulty.MEDIUM
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.random.Random

/** Solo opponents, named the way Mainers sort folks out. */
@Serializable
enum class Difficulty(val title: String, val blurb: String) {
    EASY("Summer Visitor", "Knows the rules, plays by feel. Leaves points on the table."),
    MEDIUM("Camp Regular", "Keeps the best hand and grabs pegging points when they're there."),
    HARD("Old-Timer", "Weighs every starter, minds the crib and won't hand you a fifteen."),
}

/** A way to split six cards: what to keep, what to throw, and what it's worth on average. */
data class DiscardOption(
    val keep: List<Card>,
    val throwAway: List<Card>,
    /** Average hand score over every possible starter. */
    val handAverage: Double,
    /** Average the two thrown cards add to the crib (positive when it's your crib). */
    val cribAverage: Double,
    val ownCrib: Boolean,
) {
    val value: Double get() = handAverage + if (ownCrib) cribAverage else -cribAverage
}

/** One possible card to lay in pegging, with what it scores now and how much it might give away. */
data class PlayOption(val card: Card, val points: List<ScoreItem>, val risk: Double, val value: Double) {
    val immediate: Int get() = points.sumOf { it.points }
}

object Ai {
    /** All fifteen ways to throw two of six, best first. [samples] sets how carefully the crib is estimated. */
    fun discardOptions(hand: List<Card>, ownCrib: Boolean, samples: Int = 48, seed: Int = 7): List<DiscardOption> {
        require(hand.size == 6)
        val unseen = Card.deck - hand.toSet()
        val out = mutableListOf<DiscardOption>()
        for (i in 0 until 6) for (j in i + 1 until 6) {
            val throwAway = listOf(hand[i], hand[j])
            val keep = hand - throwAway.toSet()
            var handSum = 0
            for (starter in unseen) handSum += Scoring.total(keep, starter)
            out += DiscardOption(keep, throwAway, handSum.toDouble() / unseen.size, cribAverage(throwAway, unseen, samples, seed + i * 6 + j), ownCrib)
        }
        return out.sortedByDescending { it.value }
    }

    /** What two thrown cards are worth in a crib: sampled opponent throws and starters. */
    fun cribAverage(throwAway: List<Card>, unseen: List<Card>, samples: Int, seed: Int): Double {
        if (samples <= 0) return 0.0
        val rnd = Random(seed)
        var sum = 0
        repeat(samples) {
            val pick = unseen.shuffled(rnd).take(3)
            sum += Scoring.total(throwAway + pick[0] + pick[1], pick[2], crib = true)
        }
        return sum.toDouble() / samples
    }

    fun chooseDiscard(hand: List<Card>, ownCrib: Boolean, difficulty: Difficulty, random: Random): List<Card> {
        val options = when (difficulty) {
            EASY -> discardOptions(hand, ownCrib, samples = 0)
            MEDIUM -> discardOptions(hand, ownCrib, samples = 12)
            HARD -> discardOptions(hand, ownCrib, samples = 60)
        }
        return when (difficulty) {
            // A summer visitor keeps something decent, but not always the best.
            EASY -> options[random.nextInt(minOf(5, options.size))].throwAway
            MEDIUM -> (if (random.nextInt(6) == 0) options[1] else options[0]).throwAway
            HARD -> options[0].throwAway
        }
    }

    /**
     * Cards [player] could lay, best first. Risk is what the opponent could score in reply, averaged over the cards
     * they might hold (any card [player] hasn't seen).
     */
    fun playOptions(s: GameState, player: Int, careful: Boolean = true): List<PlayOption> {
        val playable = s.playable(player)
        if (playable.isEmpty()) return emptyList()
        val seen = s.hands[player] + s.played.map { it.card } + listOfNotNull(s.starter)
        val unseen = Card.deck - seen.toSet()
        val oppHolds = s.hands[1 - player].size
        return playable.map { card ->
            val pile = s.pile.map { it.card } + card
            val count = s.count + card.value
            val items = Scoring.pegging(pile, count)
            var risk = 0.0
            if (careful && oppHolds > 0 && count < 31) {
                // Expected points of the opponent's best reply, for each rank they might hold.
                val byRank = unseen.groupBy { it.rank }
                var total = 0.0
                for ((_, cards) in byRank) {
                    val reply = cards.first()
                    if (count + reply.value > 31) continue
                    val pts = Scoring.pegging(pile + reply, count + reply.value).sumOf { it.points }
                    // The chance they hold at least one of this rank.
                    val p = 1.0 - choose(unseen.size - cards.size, oppHolds) / choose(unseen.size, oppHolds)
                    total += pts * p
                }
                risk = total
            }
            var value = items.sumOf { it.points } - risk * 0.75
            if (careful) {
                // Keep low cards for later counts, don't lead a card that pairs or fifteens easily.
                if (s.count == 0 && card.value == 5) value -= 1.0
                if (s.count == 0 && card.value < 5) value += 0.4
                if (count == 5 || count == 21) value -= 0.6
                // Holding a pair back can make a pair royal later.
                if (s.hands[player].count { it.rank == card.rank } >= 2 && s.count == 0) value += 0.3
            }
            PlayOption(card, items, risk, value)
        }.sortedWith(compareByDescending<PlayOption> { it.value }.thenByDescending { it.card.value })
    }

    fun choosePlay(s: GameState, player: Int, difficulty: Difficulty, random: Random): Card {
        val playable = s.playable(player)
        return when (difficulty) {
            EASY -> {
                // Takes an obvious score, otherwise plays anything.
                val scoring = playable.filter { c -> Scoring.pegging(s.pile.map { it.card } + c, s.count + c.value).isNotEmpty() }
                if (scoring.isNotEmpty() && random.nextInt(3) > 0) scoring.random(random) else playable.random(random)
            }
            MEDIUM -> playOptions(s, player, careful = false).first().card
            HARD -> playOptions(s, player, careful = true).first().card
        }
    }

    private fun choose(n: Int, k: Int): Double {
        if (k < 0 || k > n) return 0.0
        var r = 1.0
        for (i in 0 until k) r = r * (n - i) / (i + 1)
        return r
    }

    /** "8.4" style. */
    fun fmt(v: Double): String {
        val r = kotlin.math.abs((v * 10).roundToInt())
        return (if (v < -0.05) "-" else "") + "${r / 10}.${r % 10}"
    }
}
