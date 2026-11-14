package com.geoguy89.cribbage.game

import kotlinx.serialization.Serializable

enum class ScoreKind {
    FIFTEEN, PAIR, RUN, FLUSH, NOBS,
    /** A jack turned up as the starter: two for the dealer. */
    HEELS,
    /** The last player to lay a card before neither can go. */
    GO,
    LAST_CARD,
    THIRTY_ONE,
    /** Saying "Go" scores nothing; it's logged so the table can show it. */
    SAYS_GO,
}

/** One scoring combination: what it is, how many points, and the cards that make it. */
@Serializable
data class ScoreItem(val kind: ScoreKind, val points: Int, val cards: List<Card> = emptyList()) {
    val label: String get() = when (kind) {
        ScoreKind.FIFTEEN -> "Fifteen for 2"
        ScoreKind.PAIR -> when (points) { 6 -> "Pair royal for 6"; 12 -> "Double pair royal for 12"; else -> "Pair for 2" }
        ScoreKind.RUN -> "Run of ${cards.size} for ${points}"
        ScoreKind.FLUSH -> "Flush for $points"
        ScoreKind.NOBS -> "His nobs for 1"
        ScoreKind.HEELS -> "His heels for 2"
        ScoreKind.GO -> "Go for 1"
        ScoreKind.LAST_CARD -> "Last card for 1"
        ScoreKind.THIRTY_ONE -> "Thirty-one for 2"
        ScoreKind.SAYS_GO -> "Go"
    }
}

@Serializable
data class HandScore(val items: List<ScoreItem>) {
    val total: Int get() = items.sumOf { it.points }

    /** The traditional spoken count: "Fifteen two, fifteen four, and a pair is six." */
    val chant: String get() {
        if (items.isEmpty()) return "Nineteen! (That's cribbage for nothing at all.)"
        var running = 0
        val parts = items.map { item ->
            running += item.points
            when (item.kind) {
                ScoreKind.FIFTEEN -> "fifteen $running"
                ScoreKind.PAIR -> if (item.points == 2) "a pair is $running" else "${item.label.substringBefore(" for").lowercase()} is $running"
                ScoreKind.RUN -> "a run of ${item.cards.size} is $running"
                ScoreKind.FLUSH -> "a flush is $running"
                ScoreKind.NOBS -> "and his nobs is $running"
                else -> "${item.label.lowercase()} is $running"
            }
        }
        return parts.joinToString(", ").replaceFirstChar { it.uppercase() } + "."
    }
}

object Scoring {
    /** Every combination of cards adding to exactly 15. */
    fun fifteens(cards: List<Card>): List<List<Card>> {
        val out = mutableListOf<List<Card>>()
        val n = cards.size
        for (mask in 1 until (1 shl n)) {
            var sum = 0
            for (i in 0 until n) if (mask and (1 shl i) != 0) sum += cards[i].value
            if (sum == 15) out += (0 until n).filter { mask and (1 shl it) != 0 }.map { cards[it] }
        }
        return out.sortedWith(compareBy({ it.size }, { it.joinToString { c -> c.id.toString() } }))
    }

    /** Pairs of matching rank, grouped: two of a kind is one pair, three is a pair royal (6), four a double pair royal (12). */
    fun pairs(cards: List<Card>): List<List<Card>> =
        cards.groupBy { it.rank }.values.filter { it.size >= 2 }.sortedBy { it.first().rank }

    fun pairPoints(n: Int): Int = n * (n - 1)

    /**
     * Runs of three or more, counted the cribbage way: every distinct combination of the longest runs present. A hand of
     * 6-7-7-8 holds two runs of three (a "double run").
     */
    fun runs(cards: List<Card>): List<List<Card>> {
        val n = cards.size
        for (size in n downTo 3) {
            val found = mutableListOf<List<Card>>()
            for (mask in 0 until (1 shl n)) {
                if (mask.countOneBits() != size) continue
                val pick = (0 until n).filter { mask and (1 shl it) != 0 }.map { cards[it] }.sortedBy { it.rank }
                if (isRun(pick)) found += pick
            }
            if (found.isNotEmpty()) return found.sortedBy { r -> r.joinToString { it.id.toString() } }
        }
        return emptyList()
    }

    fun isRun(cards: List<Card>): Boolean {
        if (cards.size < 3) return false
        val ranks = cards.map { it.rank }.sorted()
        for (i in 1 until ranks.size) if (ranks[i] != ranks[i - 1] + 1) return false
        return true
    }

    /**
     * A flush needs all four hand cards the same suit (four points, five with the starter). The crib only counts a
     * five-card flush.
     */
    fun flush(hand: List<Card>, starter: Card?, crib: Boolean): List<Card>? {
        if (hand.size < 4) return null
        val suit = hand.first().suit
        if (hand.any { it.suit != suit }) return null
        val withStarter = starter != null && starter.suit == suit
        return when {
            withStarter -> hand + starter!!
            crib -> null
            else -> hand
        }
    }

    /** The jack in hand matching the starter's suit. */
    fun nobs(hand: List<Card>, starter: Card?): Card? =
        starter?.let { s -> hand.firstOrNull { it.rank == 11 && it.suit == s.suit } }

    /** Scores a hand (or crib) of four with the starter. */
    fun score(hand: List<Card>, starter: Card?, crib: Boolean = false): HandScore {
        val all = if (starter != null) hand + starter else hand
        val items = mutableListOf<ScoreItem>()
        fifteens(all).forEach { items += ScoreItem(ScoreKind.FIFTEEN, 2, it) }
        pairs(all).forEach { items += ScoreItem(ScoreKind.PAIR, pairPoints(it.size), it) }
        runs(all).forEach { items += ScoreItem(ScoreKind.RUN, it.size, it) }
        flush(hand, starter, crib)?.let { items += ScoreItem(ScoreKind.FLUSH, it.size, it) }
        nobs(hand, starter)?.let { items += ScoreItem(ScoreKind.NOBS, 1, listOf(it)) }
        return HandScore(items)
    }

    fun total(hand: List<Card>, starter: Card?, crib: Boolean = false): Int = score(hand, starter, crib).total

    /**
     * Points for the card just laid in pegging. [pile] is every card since the count last reset, the newest last;
     * [count] is the running total including it.
     */
    fun pegging(pile: List<Card>, count: Int): List<ScoreItem> {
        val items = mutableListOf<ScoreItem>()
        if (pile.isEmpty()) return items
        if (count == 15) items += ScoreItem(ScoreKind.FIFTEEN, 2, pile)
        if (count == 31) items += ScoreItem(ScoreKind.THIRTY_ONE, 2, pile)
        // Pairs: matching ranks laid one after another.
        val last = pile.last()
        var same = 1
        for (i in pile.size - 2 downTo 0) if (pile[i].rank == last.rank) same++ else break
        if (same >= 2) items += ScoreItem(ScoreKind.PAIR, pairPoints(same), pile.takeLast(same))
        // Runs: the longest tail of the pile that forms a run, in any order.
        for (len in pile.size downTo 3) {
            val tail = pile.takeLast(len)
            if (tail.map { it.rank }.toSet().size == len && isRun(tail)) {
                items += ScoreItem(ScoreKind.RUN, len, tail)
                break
            }
        }
        return items
    }
}
