package com.geoguy89.cribbage.game

/** The tutorial helper's note: a heading, the advice, and the cards it's pointing at. */
data class Tip(val title: String, val text: String, val cards: List<Card> = emptyList())

/**
 * Advice for the player at [me], as the tutorial helper gives it during solo games: what the cards are worth and the
 * play the Old-Timer would make, explained.
 */
object Coach {
    fun tip(s: GameState, me: Int): Tip? {
        if (s.over) return null
        return when (s.phase) {
            Phase.DISCARD -> if (s.hands[me].size == 6) discardTip(s, me) else Tip("Waiting", "Your two cards are in the crib. Waiting on the other player to throw theirs.")
            Phase.PEG -> if (s.turn == me) pegTip(s, me) else Tip("Their turn", countNote(s))
            Phase.OVER -> null
        }
    }

    fun discardTip(s: GameState, me: Int): Tip {
        val ownCrib = s.dealer == me
        val best = Ai.discardOptions(s.hands[me], ownCrib, samples = 40).first()
        val kept = Scoring.score(best.keep, null)
        val whose = if (ownCrib) "your crib" else "their crib (don't feed it)"
        val already = if (kept.total > 0) " Already worth ${kept.total} before the cut." else ""
        return Tip(
            "Building your hand",
            "Keep ${best.keep.joinToString(" ")}, throw ${best.throwAway.joinToString(" ")} to $whose. " +
                "Averages ${Ai.fmt(best.handAverage)} in hand.$already",
            best.throwAway,
        )
    }

    fun pegTip(s: GameState, me: Int): Tip {
        val options = Ai.playOptions(s, me)
        val best = options.first()
        val text = when {
            best.immediate > 0 -> "Play the ${best.card.longName} to make ${best.points.joinToString(" and ") { it.label.lowercase() }}!"
            s.count == 0 && best.card.value < 5 -> "You lead. A card under five is safest: nothing they play can make fifteen. Try the ${best.card.longName}."
            s.count == 0 -> "You lead. The ${best.card.longName} gives them the fewest ways to score."
            best.risk < 0.3 -> "No points to grab right now. The ${best.card.longName} keeps the count safe (it goes to ${s.count + best.card.value})."
            else -> "No points here. The ${best.card.longName} brings the count to ${s.count + best.card.value} and gives them the least to work with."
        }
        return Tip("Pegging · count ${s.count}", text, listOf(best.card))
    }

    private fun countNote(s: GameState): String = when (s.count) {
        0 -> "They're leading. Watch what they lay: a ten-card invites your five for fifteen."
        in 1..14 -> "The count is ${s.count}. If they leave it at 5 or 21, a ten-card scores you two."
        15 -> "Fifteen! Now it's up to 31."
        else -> "The count is ${s.count}. ${31 - s.count} to go for thirty-one."
    }

    fun describe(items: List<ScoreItem>): String =
        items.joinToString(", ") { "${it.label.lowercase()} (${it.cards.joinToString(" ")})" }
}
