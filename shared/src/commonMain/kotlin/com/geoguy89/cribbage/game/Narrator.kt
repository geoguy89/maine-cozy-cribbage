package com.geoguy89.cribbage.game

/** One step the tutor explains before the game moves on. */
data class Note(val title: String, val lines: List<String>, val cards: List<Card> = emptyList())

/**
 * The full tutor: explains every step of a hand in plain words with the arithmetic written out: the deal, the
 * discard, the cut, each card laid by either player, Go, the go point, the last card, and every score in the show.
 */
object Narrator {
    private fun who(p: Int, me: Int, names: List<String>) = if (p == me) "You" else names[p]
    private fun whoLower(p: Int, me: Int, names: List<String>) = if (p == me) "you" else names[p]
    private fun valueOf(c: Card) = when (c.rank) {
        1 -> "an Ace counts 1"
        11, 12, 13 -> "a ${c.rankName} counts 10 (all face cards do)"
        else -> "a ${c.rankName.lowercase()} counts ${c.value}"
    }

    // ---- the deal and the cut ---------------------------------------------------------------------------------------

    fun firstDeal(s: GameState, me: Int, names: List<String>): Note {
        val lines = mutableListOf<String>()
        if (s.cutForDeal.size == 2) {
            val mine = s.cutForDeal[me]; val theirs = s.cutForDeal[1 - me]
            lines += "To decide who deals first, each player cut a card: you got the ${mine.longName}, ${names[1 - me]} got the ${theirs.longName}. Low card deals (Aces are low)."
        }
        lines += "The goal of cribbage: be first to move your peg 121 holes around the board. You score points in three ways: during the play (\"pegging\"), when your hand is counted (\"the show\"), and from the crib if you're the dealer."
        lines += "Card values: an Ace counts 1, 2 to 10 count their number, and Jack, Queen and King all count 10. Values matter for adding up to 15 and 31; the ranks themselves matter for pairs and runs."
        return Note("Welcome to the table", lines + deal(s, me, names).lines)
    }

    fun deal(s: GameState, me: Int, names: List<String>): Note {
        val dealer = s.dealer
        val lines = mutableListOf<String>()
        lines += "Hand ${s.hand}. ${who(dealer, me, names)} ${if (dealer == me) "deal" else "deals"}, so the crib belongs to ${if (dealer == me) "you" else names[dealer]}."
        if (s.hand <= 2) lines += "The crib is a bonus hand: each player throws 2 of their 6 cards into it, and it's counted for the dealer at the end of the hand."
        lines += if (dealer == me) "Since it's your crib, the two you throw still score for you. Throwing a pair or a 5 into your own crib is fine."
        else "Since it's their crib, anything you throw scores for them. Avoid giving them 5s, pairs, or two cards that add to 15."
        lines += if (s.hand <= 2) "Your job now: tap two cards to throw away, then tap \"Throw to crib\". The other four are your hand. Tap Hint if you'd like the best two picked for you."
            else "Pick two cards for the crib."
        return Note("The deal", lines)
    }

    fun cut(before: GameState, after: GameState, me: Int, names: List<String>): Note {
        val st = after.starter!!
        val lines = mutableListOf<String>()
        lines += "Both players have thrown. The deck is cut and the top card turned up: the ${st.longName}. This is the starter."
        lines += "The starter belongs to everyone: when hands are counted at the end, it counts as a fifth card in your hand, their hand, and the crib."
        if (st.rank == 11) lines += "It's a Jack! The dealer (${whoLower(after.dealer, me, names)}) immediately pegs 2 points for \"his heels\". Score: ${after.scores[after.dealer]}."
        lines += "Now the play (\"pegging\") begins. ${who(after.pone, me, names)} ${if (after.pone == me) "lay" else "lays"} first, because the non-dealer always leads. " +
            "Each card laid adds its value to a running count, which can't go past 31. You score for hitting exactly 15 or 31, for pairs, and for runs."
        return Note("The starter card", lines, listOf(st))
    }

    // ---- pegging ----------------------------------------------------------------------------------------------------

    /** Everything that happened when [player] laid [card]: the arithmetic, each score, and any Go or last card. */
    fun play(before: GameState, after: GameState, player: Int, card: Card, me: Int, names: List<String>): List<Note> {
        val notes = mutableListOf<Note>()
        val lines = mutableListOf<String>()
        val pile = before.pile.map { it.card } + card
        val count = before.count + card.value
        val name = who(player, me, names)
        lines += "$name laid the ${card.longName}: ${valueOf(card)}."
        lines += if (before.count == 0) "This starts a new count at ${card.value}." else "The count was ${before.count}. ${before.count} + ${card.value} = $count."
        val items = Scoring.pegging(pile, count)
        val start = before.scores[player]
        if (items.isEmpty()) {
            lines += "No points: $count isn't 15 or 31, it doesn't match the card before it" +
                (if (before.pile.isNotEmpty()) " (${before.pile.last().card.short})" else "") + ", and the last cards don't make a run."
        } else {
            var total = 0
            for (item in items) {
                total += item.points
                lines += when (item.kind) {
                    ScoreKind.FIFTEEN -> "The count is exactly 15: \"fifteen for 2\". 2 points."
                    ScoreKind.THIRTY_ONE -> "The count is exactly 31: 2 points. Nothing can be added to 31, so the count goes back to 0."
                    ScoreKind.PAIR -> when (item.points) {
                        2 -> "It's the same rank as the card before it (two ${plural(card)}): a pair, 2 points."
                        6 -> "That's three ${plural(card)} in a row: a \"pair royal\". Three cards make 3 different pairs, at 2 each = 6 points."
                        else -> "Four ${plural(card)} in a row: a \"double pair royal\". Four cards make 6 different pairs = 12 points."
                    }
                    ScoreKind.RUN -> {
                        val sorted = item.cards.sortedBy { it.rank }
                        "The last ${item.cards.size} cards (${item.cards.joinToString(", ") { it.rankLabel }}) can be put in order: ${sorted.joinToString("-") { it.rankLabel }}. That's a run of ${item.cards.size}, 1 point per card = ${item.points}."
                    }
                    else -> item.label
                }
            }
            val end = (start + total).coerceAtMost(GAME_POINTS)
            lines += "$name ${if (player == me) "score" else "scores"} $total in all. ${if (player == me) "Your" else "Their"} front peg moves from $start to $end; the back peg marks where it was."
        }
        if (player == me) {
            // How it compared with the helper's choice.
            val options = Ai.playOptions(before, me)
            val best = options.firstOrNull()
            if (best != null && best.card != card && options.size > 1) {
                val mine = options.firstOrNull { it.card == card }
                lines += if (best.immediate > (mine?.immediate ?: 0)) "Tip: the ${best.card.short} would have scored ${best.immediate} here (${best.points.joinToString(" and ") { it.label.lowercase() }})."
                else "Tip: the ${best.card.short} would have been a little safer, leaving them fewer ways to score."
            }
        }
        notes += Note(if (player == me) "Your card" else "${names[player]}'s card", lines, listOf(card))

        // What followed on its own: Go, the go point, a reset after 31, the last card.
        val follow = after.log.filter { it.seq > before.seq && it.item.kind in setOf(ScoreKind.SAYS_GO, ScoreKind.GO, ScoreKind.LAST_CARD) }
        for (e in follow) notes += when (e.item.kind) {
            ScoreKind.SAYS_GO -> {
                val p = e.player
                val why = if (p == me) {
                    val cards = (if (after.hand == before.hand) after.hands[me] else emptyList())
                    if (cards.isNotEmpty()) "Your cards ${cards.joinToString(", ") { "${it.short} (${it.value})" }} would each take the count of ${e.count} past 31." else "None of your cards fit under 31."
                } else "None of their remaining cards would fit: each would take the count of ${e.count} past 31."
                Note("\"Go\"", listOf(
                    "${who(p, me, names)} can't lay a card. $why",
                    "So ${whoLower(p, me, names)} ${if (p == me) "say" else "says"} \"Go\". The other player keeps laying cards for as long as they can stay at or under 31.",
                ))
            }
            ScoreKind.GO -> Note("1 for the go", listOf(
                "Now neither player can lay a card without passing 31 (the count is ${e.count}).",
                "${who(e.player, me, names)} laid the last card, so ${whoLower(e.player, me, names)} ${if (e.player == me) "peg" else "pegs"} 1 point \"for the go\".",
                "The count goes back to 0, and the other player starts the next count.",
            ))
            else -> Note("Last card", listOf(
                "That was the final card of the hand. Whoever lays the very last card scores 1 (unless it made exactly 31, which already scored 2).",
                "${who(e.player, me, names)} ${if (e.player == me) "peg" else "pegs"} 1. Next comes the show: each hand is counted.",
            ))
        }
        if (count == 31 && after.hand == before.hand) notes += Note("Back to 0", listOf("After 31 the count starts again at 0. ${who(after.turn, me, names)} ${if (after.turn == me) "lead" else "leads"} the next round."))
        if (after.hand != before.hand || after.over) {
            if (!after.over) notes += Note("The play is over", listOf(
                "All eight cards have been laid. The cards on the table stay put so you can see how it ended.",
                "Next, the show: the non-dealer's hand is counted first, then the dealer's hand, then the dealer's crib. Each uses the starter as a fifth card.",
            ))
        }
        return notes
    }

    private fun plural(c: Card) = when (c.rank) { 1 -> "Aces"; 11 -> "Jacks"; 12 -> "Queens"; 13 -> "Kings"; 6 -> "sixes"; else -> c.rankName.lowercase() + "s" }

    fun discard(six: List<Card>, thrown: List<Card>, ownCrib: Boolean): Note {
        val r = Coach.reviewDiscard(six, thrown, ownCrib)
        return Note("Your discard", r.why, thrown)
    }

    // ---- the show ---------------------------------------------------------------------------------------------------

    /** Introduces one count in the show. */
    fun showIntro(count: ShowCount, starter: Card, me: Int, names: List<String>): String {
        val whose = if (count.player == me) "your" else "${names[count.player]}'s"
        return "Counting $whose ${if (count.crib) "crib" else "hand"}: the four cards plus the starter (${starter.short}). " +
            "Look for cards adding to 15, pairs, runs, a flush and his nobs." +
            if (count.score.items.isEmpty()) " This one has nothing: 0 points (cribbage players jokingly call it \"nineteen\", since 19 is impossible)." else ""
    }

    /** One score in the show, with its arithmetic. */
    fun explain(item: ScoreItem, starter: Card, crib: Boolean): String = when (item.kind) {
        ScoreKind.FIFTEEN -> item.cards.joinToString(" + ") { it.short } + " = " + item.cards.joinToString(" + ") { it.value.toString() } + " = 15. Each different combination adding to 15 is worth 2."
        ScoreKind.PAIR -> when (item.cards.size) {
            2 -> "Two ${plural(item.cards[0])} (${item.cards.joinToString(" ")}): a pair, 2 points."
            3 -> "Three ${plural(item.cards[0])}: they make 3 different pairs, 2 each = 6 points (a \"pair royal\")."
            else -> "Four ${plural(item.cards[0])}: 6 different pairs, 2 each = 12 points."
        }
        ScoreKind.RUN -> {
            val sorted = item.cards.sortedBy { it.rank }
            "${sorted.joinToString("-") { it.rankLabel }} are in sequence: a run of ${item.cards.size}, 1 point per card = ${item.points}. (If a pair sits inside a run, the run counts once for each card of the pair.)"
        }
        ScoreKind.FLUSH -> if (item.cards.size == 5) "All five cards, starter included, are ${item.cards[0].suit.label.lowercase()}: a flush, 5 points."
            else "All four cards in the hand are ${item.cards[0].suit.label.lowercase()}: a flush, 4 points." + if (crib) "" else " (A crib only scores a flush if the starter matches too.)"
        ScoreKind.NOBS -> "The ${item.cards[0].longName} matches the starter's suit (${starter.suit.symbol}): \"one for his nobs\", 1 point."
        else -> item.label
    }
}
