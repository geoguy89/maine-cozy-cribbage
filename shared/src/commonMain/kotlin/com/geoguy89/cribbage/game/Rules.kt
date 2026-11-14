package com.geoguy89.cribbage.game

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Two-player six-card cribbage to 121. The game server runs the same rules (server/src/cribbage.ts); both are checked
 * against spec/scoring-vectors.json.
 *
 * A hand flows DISCARD -> PEG, then the show is counted at once and the next hand dealt. The counted show is kept in
 * [GameState.lastShow] so the table can walk through it card by card while the new hand waits underneath.
 */
enum class Phase { DISCARD, PEG, OVER }

const val GAME_POINTS = 121
/** Losing with fewer than 91 is a skunk (counts as two games); fewer than 61 a double skunk. */
const val SKUNK_LINE = 91
const val DOUBLE_SKUNK_LINE = 61

@Serializable
data class Played(val player: Int, val card: Card)

/** One line of the game log: who scored what (or said Go), in which hand. */
@Serializable
data class LogEntry(val seq: Int, val hand: Int, val player: Int, val item: ScoreItem, val count: Int = 0)

@Serializable
data class ShowCount(val player: Int, val crib: Boolean, val cards: List<Card>, val score: HandScore)

/** A hand's show: the starter, each hand and the crib with their scores, in counting order. */
@Serializable
data class Show(
    val hand: Int,
    val dealer: Int,
    val starter: Card,
    /** Scores before the show was counted, so the board can be pegged along with the count. */
    val startScores: List<Int>,
    val counts: List<ShowCount>,
)

@Serializable
data class GameState(
    val scores: List<Int> = listOf(0, 0),
    /** Where each player's back peg sits (-1 = still in the start holes). */
    val prevScores: List<Int> = listOf(-1, -1),
    val dealer: Int = 0,
    /** Hand number, from 1. */
    val hand: Int = 1,
    val phase: Phase = Phase.DISCARD,
    /** Cards each player still holds. Six before the discard, four after, fewer as they're pegged. */
    val hands: List<List<Card>> = listOf(emptyList(), emptyList()),
    /** The four cards each player kept, for the show. */
    val kept: List<List<Card>> = listOf(emptyList(), emptyList()),
    val crib: List<Card> = emptyList(),
    val starter: Card? = null,
    /** The undealt cards (the starter is cut from these). */
    val deck: List<Card> = emptyList(),
    val turn: Int = 1,
    /** The running pegging count. */
    val count: Int = 0,
    /** Cards laid since the count last reset. */
    val pile: List<Played> = emptyList(),
    /** Every card laid this hand, in order. */
    val played: List<Played> = emptyList(),
    val lastPlayer: Int = -1,
    /** Who has said "Go" since the count last reset. */
    val go: List<Boolean> = listOf(false, false),
    val log: List<LogEntry> = emptyList(),
    val seq: Int = 0,
    val lastShow: Show? = null,
    val winner: Int? = null,
    /** The cut for first deal, low card deals: [player 0's card, player 1's card]. */
    val cutForDeal: List<Card> = emptyList(),
) {
    val pone: Int get() = 1 - dealer
    fun discarded(player: Int): Boolean = phase != Phase.DISCARD || hands[player].size <= 4
    fun canPlay(player: Int): Boolean = hands[player].any { it.value + count <= 31 }
    fun playable(player: Int): List<Card> = hands[player].filter { it.value + count <= 31 }
    val over: Boolean get() = phase == Phase.OVER

    /** How the loser fared: 0 none, 1 skunk, 2 double skunk. */
    val skunk: Int get() {
        val w = winner ?: return 0
        val loser = scores[1 - w]
        return when { loser < DOUBLE_SKUNK_LINE -> 2; loser < SKUNK_LINE -> 1; else -> 0 }
    }
}

object Rules {
    private fun other(p: Int) = 1 - p

    /** Cut for deal (low card deals; ties cut again), then deal the first hand. */
    fun newGame(random: Random): GameState {
        var cut: List<Card>
        do {
            val deck = Card.shuffled(random)
            cut = listOf(deck[0], deck[1 + random.nextInt(50)])
        } while (cut[0].rank == cut[1].rank)
        val dealer = if (cut[0].rank < cut[1].rank) 0 else 1
        return deal(GameState(dealer = dealer, cutForDeal = cut), random)
    }

    /** Shuffle and deal six each, the pone first. */
    fun deal(s: GameState, random: Random): GameState {
        val deck = Card.shuffled(random)
        val hands = listOf(mutableListOf<Card>(), mutableListOf())
        for (i in 0 until 12) hands[if (i % 2 == 0) s.pone else s.dealer] += deck[i]
        return s.copy(
            phase = Phase.DISCARD,
            hands = hands.map { it.sorted() },
            kept = listOf(emptyList(), emptyList()),
            crib = emptyList(),
            starter = null,
            deck = deck.drop(12),
            turn = s.pone,
            count = 0,
            pile = emptyList(),
            played = emptyList(),
            lastPlayer = -1,
            go = listOf(false, false),
        )
    }

    /** Lay two cards away to the dealer's crib. Once both players have, the starter is cut and pegging begins. */
    fun discard(s: GameState, player: Int, cards: List<Card>): GameState {
        require(s.phase == Phase.DISCARD) { "not discarding" }
        require(cards.size == 2 && cards.distinct().size == 2) { "discard two cards" }
        require(s.hands[player].size == 6) { "already discarded" }
        require(s.hands[player].containsAll(cards)) { "not your cards" }
        val hand = s.hands[player] - cards.toSet()
        var n = s.copy(
            hands = s.hands.replace(player, hand),
            kept = s.kept.replace(player, hand),
            crib = s.crib + cards,
        )
        if (n.hands.all { it.size == 4 }) n = cut(n)
        return n
    }

    private fun cut(s: GameState): GameState {
        val starter = s.deck.first()
        var n = s.copy(starter = starter, deck = s.deck.drop(1), phase = Phase.PEG, turn = s.pone)
        if (starter.rank == 11) n = award(n, n.dealer, ScoreItem(ScoreKind.HEELS, 2, listOf(starter)))
        return n
    }

    /** Lay a card in pegging. "Go", the go point and last card are all settled automatically afterwards. */
    fun play(s: GameState, player: Int, card: Card, random: Random): GameState {
        require(s.phase == Phase.PEG) { "not pegging" }
        require(s.turn == player) { "not your turn" }
        require(card in s.hands[player]) { "not your card" }
        require(card.value + s.count <= 31) { "that would pass 31" }
        val count = s.count + card.value
        val laid = Played(player, card)
        val pile = s.pile + laid
        var n = s.copy(
            hands = s.hands.replace(player, s.hands[player] - card),
            count = count,
            pile = pile,
            played = s.played + laid,
            lastPlayer = player,
            turn = other(player),
        )
        for (item in Scoring.pegging(pile.map { it.card }, count)) {
            n = award(n, player, item, count)
            if (n.over) return n
        }
        return settle(n, random)
    }

    /** After a card: decide whose turn it is, scoring Go, resetting the count and moving on to the show as needed. */
    private fun settle(start: GameState, random: Random): GameState {
        var s = start
        while (true) {
            if (s.over) return s
            if (s.hands.all { it.isEmpty() }) {
                if (s.count in 1..30) s = award(s, s.lastPlayer, ScoreItem(ScoreKind.LAST_CARD, 1), s.count)
                return if (s.over) s else finishHand(s, random)
            }
            if (s.count == 31) {
                s = s.copy(count = 0, pile = emptyList(), go = listOf(false, false), turn = other(s.lastPlayer))
                continue
            }
            val t = s.turn
            if (s.canPlay(t)) return s
            // They can't go. If they still hold cards they say so; then the other player plays on, if they can.
            if (s.hands[t].isNotEmpty() && !s.go[t]) {
                s = log(s, t, ScoreItem(ScoreKind.SAYS_GO, 0), s.count).copy(go = s.go.replace(t, true))
            }
            val o = other(t)
            if (s.canPlay(o)) {
                s = s.copy(turn = o)
                return s
            }
            // Neither can lay a card: one for the go to whoever laid last, and the count starts again.
            s = award(s, s.lastPlayer, ScoreItem(ScoreKind.GO, 1), s.count)
            if (s.over) return s
            s = s.copy(count = 0, pile = emptyList(), go = listOf(false, false), turn = other(s.lastPlayer))
        }
    }

    /** Count the pone's hand, the dealer's hand and the crib (stopping if someone reaches 121), then deal again. */
    fun finishHand(s: GameState, random: Random): GameState {
        val starter = s.starter!!
        val counts = listOf(
            ShowCount(s.pone, false, s.kept[s.pone], Scoring.score(s.kept[s.pone], starter)),
            ShowCount(s.dealer, false, s.kept[s.dealer], Scoring.score(s.kept[s.dealer], starter)),
            ShowCount(s.dealer, true, s.crib, Scoring.score(s.crib, starter, crib = true)),
        )
        val startScores = s.scores
        var n = s
        val counted = mutableListOf<ShowCount>()
        for (c in counts) {
            counted += c
            for (item in c.score.items) n = award(n, c.player, item)
            if (n.over) break
        }
        n = n.copy(lastShow = Show(s.hand, s.dealer, starter, startScores, counted))
        if (n.over) return n
        return deal(n.copy(dealer = s.pone, hand = s.hand + 1), random)
    }

    /** Peg [item]'s points for [player]: the back peg leapfrogs to the new score. Reaching 121 ends the game at once. */
    fun award(s: GameState, player: Int, item: ScoreItem, count: Int = 0): GameState {
        if (s.over || item.points <= 0) return if (item.points <= 0) log(s, player, item, count) else s
        val old = s.scores[player]
        val new = (old + item.points).coerceAtMost(GAME_POINTS)
        var n = log(s, player, item, count).copy(
            scores = s.scores.replace(player, new),
            prevScores = s.prevScores.replace(player, old),
        )
        if (new >= GAME_POINTS) n = n.copy(phase = Phase.OVER, winner = player)
        return n
    }

    private fun log(s: GameState, player: Int, item: ScoreItem, count: Int): GameState {
        val entry = LogEntry(s.seq + 1, s.hand, player, item, count)
        return s.copy(log = (s.log + entry).takeLast(80), seq = s.seq + 1)
    }

    /** Ends the game in the other player's favour (online resignations). */
    fun resign(s: GameState, player: Int): GameState =
        if (s.over) s else s.copy(phase = Phase.OVER, winner = other(player))
}

internal fun <T> List<T>.replace(index: Int, value: T): List<T> = mapIndexed { i, v -> if (i == index) value else v }
