package com.geoguy89.cribbage.game

/**
 * The tutorial helper's note: a heading, the short advice, the cards it points at, and the reasoning behind it,
 * step by step, for the "Why?" panel.
 */
data class Tip(val title: String, val text: String, val cards: List<Card> = emptyList(), val why: List<String> = emptyList())

/**
 * Advice for the player at [me] during solo games, always with the reasons: what each card is worth, what it can
 * make with the others, and what it leaves the opponent.
 */
object Coach {
    fun tip(s: GameState, me: Int): Tip? {
        if (s.over) return null
        return when (s.phase) {
            Phase.DISCARD -> if (s.hands[me].size == 6) discardTip(s, me) else Tip(
                "Waiting", "Your two cards are in the crib. Waiting on the other player to throw theirs.",
                why = listOf("Once both players have thrown, the pone cuts and the starter is turned up. It counts as a fifth card for both hands and the crib."),
            )
            Phase.PEG -> if (s.turn == me) pegTip(s, me) else Tip("Their turn", countNote(s), why = watchWhy())
            Phase.OVER -> null
        }
    }

    // ---- discarding ------------------------------------------------------------------------------------------------

    fun discardTip(s: GameState, me: Int): Tip {
        val hand = s.hands[me]
        val ownCrib = s.dealer == me
        val options = Ai.discardOptions(hand, ownCrib, samples = 40)
        val best = options.first()
        val whose = if (ownCrib) "your crib" else "their crib"
        val text = "Keep ${best.keep.joinToString(" ")} and throw ${best.throwAway.joinToString(" ")} to $whose. Tap Why? for the reasoning."
        return Tip("Building your hand", text, best.throwAway, discardWhy(hand, ownCrib, options))
    }

    private fun discardWhy(hand: List<Card>, ownCrib: Boolean, options: List<DiscardOption>): List<String> {
        val best = options.first()
        val why = mutableListOf<String>()
        if (hand.any { it.value == 5 }) why += "You hold a 5: fives are gold, since any ten-card (10, J, Q, K: 16 of the 52 cards) makes fifteen with it."

        // What the keepers already make.
        val kept = Scoring.score(best.keep, null)
        why += if (kept.total > 0) {
            "Your four keepers already score ${kept.total} before the cut: ${kept.items.joinToString("; ") { "${it.label.substringBefore(" for").lowercase()} with ${it.cards.joinToString(" ")} (${it.points})" }}."
        } else {
            "Your four keepers don't score on their own yet, but they're the set most likely to score once the starter is cut."
        }

        // Why each thrown card goes.
        for (c in best.throwAway) why += "Throw ${c.short} (counts ${c.value}): ${reasonToThrow(c, best.keep)}"

        // Which starters help.
        val unseen = Card.deck - hand.toSet()
        val gains = unseen.groupBy { it.rank }.map { (rank, cards) ->
            val avg = cards.sumOf { Scoring.total(best.keep, it) } / cards.size.toDouble()
            rank to avg - kept.total
        }.sortedByDescending { it.second }
        val top = gains.take(2).filter { it.second >= 1.0 }
        if (top.isNotEmpty()) why += "Best cuts for you: " + top.joinToString(" or ") { (rank, gain) -> "${article(Card(rank, Suit.SPADES).rankName.lowercase())} (+${Ai.fmt(gain)})" } +
            ". On average the hand comes to ${Ai.fmt(best.handAverage)} points with the starter."

        // The crib.
        why += if (ownCrib) {
            "It's your crib, so the two you throw still score for you there (about ${Ai.fmt(best.cribAverage)} on average). That's why you can afford to throw cards that might help a crib, like a five or two touching cards."
        } else {
            "It's their crib, so anything you throw scores for them. ${best.throwAway.joinToString(" and ")} add only about ${Ai.fmt(best.cribAverage)} there. Never hand them a five or a pair if you can help it, and avoid two cards that touch (like 7-8) or add to 15."
        }

        // Against the next best.
        options.getOrNull(1)?.let { alt ->
            why += "Next best: keep ${alt.keep.joinToString(" ")} (worth ${Ai.fmt(alt.value)} a hand counting the crib, against ${Ai.fmt(best.value)}). " +
                if (best.value - alt.value < 0.3) "Nearly as good; either is fine." else "The best choice is worth about ${Ai.fmt(best.value - alt.value)} more each time."
        }
        return why
    }

    /** Why a card does nothing for the four being kept. */
    private fun reasonToThrow(c: Card, keep: List<Card>): String {
        val pairs = keep.count { it.rank == c.rank }
        val fifteens = Scoring.fifteens(keep + c).count { c in it }
        val runs = Scoring.runs(keep + c).count { c in it }
        if (pairs == 0 && runs == 0 && fifteens == 0) {
            val need = 15 - c.value
            val fifteen = if (need in 1..10) "no fifteen with your keepers (it would need cards adding to $need)" else "no fifteen"
            return "nothing to pair with, it doesn't join a run, and $fifteen. It's dead weight in your hand."
        }
        val makes = listOfNotNull(
            if (fifteens > 0) "$fifteens fifteen${if (fifteens > 1) "s" else ""}" else null,
            if (pairs > 0) "a pair" else null,
            if (runs > 0) "a run" else null,
        ).joinToString(" and ")
        return "it does make $makes with your keepers, but the four you're keeping score more together than any set that includes it."
    }

    // ---- pegging ---------------------------------------------------------------------------------------------------

    fun pegTip(s: GameState, me: Int): Tip {
        val options = Ai.playOptions(s, me)
        val best = options.first()
        val after = s.count + best.card.value
        val text = when {
            best.immediate > 0 -> "Play the ${best.card.longName}: ${best.points.joinToString(" and ") { it.label.lowercase() }} (count $after)."
            s.count == 0 -> "You lead. Play the ${best.card.longName}."
            else -> "No points this time. Play the ${best.card.longName}: it takes the count to $after and leaves them the least."
        } + " Tap Why? for the reasoning."
        return Tip("Pegging · count ${s.count}", text, listOf(best.card), pegWhy(s, me, options))
    }

    private fun pegWhy(s: GameState, me: Int, options: List<PlayOption>): List<String> {
        val why = mutableListOf<String>()
        val best = options.first()
        if (s.count == 0) {
            why += "Leading: a card under 5 is safest, because nothing they lay next can make 15 (it would need a card worth more than 10). A 5 is the worst lead: any ten-card (16 of 52) makes 15 off it. Ten-cards are risky too, as their 5 makes 15."
        }
        val unseen = Card.deck - (s.hands[me] + s.played.map { it.card } + listOfNotNull(s.starter)).toSet()
        why += "The count is ${s.count}. What each of your cards would do:"
        for (o in options) {
            val count = s.count + o.card.value
            val mine = if (o.immediate > 0) "you score ${o.immediate} (${o.points.joinToString(", ") { it.label.substringBefore(" for").lowercase() }})" else "you score nothing"
            val threats = threats(s.pile.map { it.card } + o.card, count, unseen)
            val theirs = when {
                count == 31 -> "the count starts again"
                threats.isEmpty() -> "nothing they lay can score off it"
                else -> "they could score with ${threats.joinToString("; ")}"
            }
            val odds = if (s.hands[1 - me].isNotEmpty() && count != 31 && threats.isNotEmpty()) " (weighing how likely they are to hold those, about ${Ai.fmt(o.risk)} points back on average)" else ""
            why += "• ${o.card.short} (counts ${o.card.value}) → count $count: $mine; $theirs$odds."
        }
        val oppHolds = s.hands[1 - me].size
        why += when {
            oppHolds == 0 -> "They're out of cards, so nothing can come back at you: play on, and the last card scores 1."
            best.immediate > 0 -> "The ${best.card.short} wins: points now are certain, while anything they score back depends on what they hold."
            options.size == 1 -> "It's your only card that fits under 31."
            else -> "The ${best.card.short} leaves the least for them: about ${Ai.fmt(best.risk)} points back on average, counting how many of each card are still unseen and that they hold $oppHolds. " +
                "Cards you've seen (your hand, the starter, everything played) can't be in their hand, which is why the odds differ."
        }
        val after = s.count + best.card.value
        if (after == 5 || after == 21) why += "Watch out: leaving 5 or 21 lets any ten-card make 15 or 31."
        if (after in 22..30) why += "Near 31 it gets tight: if neither of you can go, whoever laid the last card scores 1 for the go. Holding low cards helps you be the one still able to play."
        return why
    }

    /** What the next card could score for the opponent: "a six (fifteen, 2)", "any ten-card (thirty-one, 2)". */
    private fun threats(pile: List<Card>, count: Int, unseen: List<Card>): List<String> {
        val out = mutableListOf<String>()
        var tensNamed = false
        for (rank in 1..13) {
            val sample = unseen.firstOrNull { it.rank == rank } ?: continue
            if (count + sample.value > 31) continue
            val items = Scoring.pegging(pile + sample, count + sample.value)
            val pts = items.sumOf { it.points }
            if (pts == 0) continue
            // Ten-cards that only make 15 or 31 are named together.
            val name = if (sample.value == 10 && items.none { it.kind == ScoreKind.PAIR || it.kind == ScoreKind.RUN }) {
                if (tensNamed) continue
                tensNamed = true
                "any ten-card"
            } else article(sample.rankName.lowercase())
            out += "$name (${items.joinToString(", ") { it.label.substringBefore(" for").lowercase() }}, $pts)"
        }
        return out
    }

    private fun countNote(s: GameState): String = when (s.count) {
        0 -> "They're leading. Watch what they lay: if it's a ten-card, your five makes 15."
        in 1..14 -> "The count is ${s.count}. If they leave it at 5 or 21, a ten-card scores you two."
        15 -> "Fifteen! Now it's a race to 31."
        else -> "The count is ${s.count}. ${31 - s.count} to go for thirty-one."
    }

    private fun watchWhy(): List<String> = listOf(
        "While they think, keep track of what's been played. Each rank has four cards: if three nines are gone, nobody is likely to pair the last one.",
        "When it's your turn again, the helper will compare every card you could lay and what each leaves them.",
    )

    private fun article(word: String) = (if (word.first() in "aeiou") "an " else "a ") + word

    /** What each card counts, for the top of every explanation. */
    const val VALUES = "Card values: A = 1 · 2 to 10 = their number · J, Q, K = 10. Only the count matters for fifteens and 31; ranks matter for pairs and runs."

    private fun scored(items: List<ScoreItem>) = items.filter { it.points > 0 }.joinToString(" and ") { it.label.lowercase() }

    /**
     * Looking back at a card just laid: what it counted and scored, and (for the player's own card) how it compared
     * with the helper's pick, or (for the opponent's) what the player can now score in reply.
     */
    fun reviewPlay(before: GameState, after: GameState, player: Int, card: Card, me: Int, names: List<String>): Tip {
        val who = if (player == me) "You" else names[player]
        val count = before.count + card.value
        val items = Scoring.pegging(before.pile.map { it.card } + card, count)
        val pts = items.sumOf { it.points }
        val lines = mutableListOf<String>()
        lines += "$who laid the ${card.longName}, which counts ${card.value}: the count went from ${before.count} to $count." +
            if (pts > 0) " That scored ${scored(items)}." else " No points for that."
        // Anything that happened straight after: Go, the go point, last card.
        val extra = after.log.filter { it.seq > before.seq && it.count > 0 && it.item.kind in setOf(ScoreKind.SAYS_GO, ScoreKind.GO, ScoreKind.LAST_CARD) }
        for (e in extra) lines += when (e.item.kind) {
            ScoreKind.SAYS_GO -> "${if (e.player == me) "You" else names[e.player]} couldn't lay a card without passing 31, so ${if (e.player == me) "you" else "they"} said Go."
            ScoreKind.GO -> "Neither player could go, so ${if (e.player == me) "you" else names[e.player]} took 1 for the go (last to lay a card) and the count started again at 0."
            else -> "That was the last card of the hand: 1 point for ${if (e.player == me) "you" else names[e.player]}."
        }
        if (player == me) {
            val options = Ai.playOptions(before, me)
            val best = options.first()
            if (best.card == card || options.size == 1) {
                lines += "That was the helper's pick too" + if (options.size == 1) " (your only card that fit under 31)." else "."
            } else {
                val mine = options.firstOrNull { it.card == card }
                lines += "The helper would have laid the ${best.card.short} (counts ${best.card.value}): " + when {
                    best.immediate > (mine?.immediate ?: 0) -> "it scored ${scored(best.points)} on the spot."
                    else -> "it left them about ${Ai.fmt(best.risk)} points to score back on average, against ${Ai.fmt(mine?.risk ?: 0.0)} for yours."
                }
            }
            val unseen = Card.deck - (before.hands[me] + before.played.map { it.card } + listOfNotNull(before.starter)).toSet()
            if (count < 31 && before.hands[1 - me].isNotEmpty()) {
                val t = threats(before.pile.map { it.card } + card, count, unseen)
                lines += if (t.isEmpty()) "Nothing they lay next can score off it." else "From here they could score with ${t.joinToString("; ")}."
            }
        } else if (after.phase == Phase.PEG && after.hand == before.hand && after.turn == me) {
            // Their card: what it opens up for the player.
            val replies = after.hands[me].filter { it.value + after.count <= 31 }.mapNotNull { c ->
                val r = Scoring.pegging(after.pile.map { it.card } + c, after.count + c.value)
                if (r.isEmpty()) null else "your ${c.short} (counts ${c.value}) makes ${scored(r)}"
            }
            lines += if (replies.isNotEmpty()) "Your chance: ${replies.joinToString("; ")}." else "None of your cards can score off it this time."
            when (count) {
                5, 21 -> lines += "Leaving $count is a gift: any ten-card makes ${if (count == 5) "15" else "31"}."
                in 11..14 -> lines += "With the count at $count, a card worth ${15 - count} would make 15."
            }
        }
        return Tip("Why the ${card.short}?", lines.first(), listOf(card), lines)
    }

    /** Looking back at the player's discard: where it ranked among the fifteen ways to throw, and why. */
    fun reviewDiscard(six: List<Card>, thrown: List<Card>, ownCrib: Boolean): Tip {
        val options = Ai.discardOptions(six, ownCrib, samples = 40)
        val rank = options.indexOfFirst { it.throwAway.toSet() == thrown.toSet() } + 1
        val mine = options[rank - 1]
        val best = options.first()
        val lines = mutableListOf<String>()
        lines += "You threw ${thrown.joinToString(" and ") { "${it.short} (counts ${it.value})" }} to ${if (ownCrib) "your" else "their"} crib and kept ${mine.keep.joinToString(" ")}."
        lines += when (rank) {
            1 -> "That's the best of the 15 possible throws: your hand averages ${Ai.fmt(mine.handAverage)} with the starter."
            in 2..3 -> "That's #$rank of 15, nearly the best (${Ai.fmt(mine.value)} against ${Ai.fmt(best.value)} a hand)."
            else -> "That's #$rank of 15. Keeping ${best.keep.joinToString(" ")} and throwing ${best.throwAway.joinToString(" ")} averages ${Ai.fmt(best.value)} against ${Ai.fmt(mine.value)}."
        }
        val kept = Scoring.score(mine.keep, null)
        if (kept.total > 0) lines += "Your hand already holds ${kept.total}: ${kept.items.joinToString("; ") { "${it.label.substringBefore(" for").lowercase()} (${it.cards.joinToString(" ")})" }}."
        lines += if (ownCrib) "Your crib gets about ${Ai.fmt(mine.cribAverage)} from those two." else "Their crib gets about ${Ai.fmt(mine.cribAverage)} from those two."
        return Tip("Your discard", lines[1], thrown, lines)
    }

    fun describe(items: List<ScoreItem>): String =
        items.joinToString(", ") { "${it.label.lowercase()} (${it.cards.joinToString(" ")})" }
}
