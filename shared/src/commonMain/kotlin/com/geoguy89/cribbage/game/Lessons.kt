package com.geoguy89.cribbage.game

import kotlin.random.Random

/** One step of a lesson. */
sealed class Step {
    abstract val title: String
    abstract val text: String

    /** Read and look: cards shown, with any combinations highlighted in turn. */
    data class Read(override val title: String, override val text: String, val cards: List<Card> = emptyList(), val starter: Card? = null, val groups: List<List<Card>> = emptyList()) : Step()

    /** Tap the cards of each combination of [kind] in turn until all are found. */
    data class Find(override val title: String, override val text: String, val hand: List<Card>, val starter: Card?, val kind: ScoreKind) : Step() {
        val targets: List<List<Card>> by lazy {
            val all = if (starter != null) hand + starter else hand
            when (kind) {
                ScoreKind.FIFTEEN -> Scoring.fifteens(all)
                ScoreKind.PAIR -> Scoring.pairs(all).flatMap { g -> g.indices.flatMap { i -> (i + 1 until g.size).map { j -> listOf(g[i], g[j]) } } }
                ScoreKind.RUN -> Scoring.runs(all)
                else -> emptyList()
            }
        }
    }

    /** How many points? Pick from four answers; the breakdown is shown afterwards. */
    data class Count(override val title: String, override val text: String, val hand: List<Card>, val starter: Card, val crib: Boolean = false) : Step() {
        val score: HandScore by lazy { Scoring.score(hand, starter, crib) }
        fun choices(seed: Int): List<Int> {
            val right = score.total
            val r = Random(seed)
            val wrong = mutableSetOf<Int>()
            for (d in listOf(-2, 2, -1, 1, -4, 4, 3, -3, 6)) { if (right + d >= 0 && right + d != right) wrong += right + d; if (wrong.size >= 6) break }
            return (wrong.shuffled(r).take(3) + right).sorted()
        }
    }

    /** Choose two cards for the crib; compared with the best choice. */
    data class Discard(override val title: String, override val text: String, val six: List<Card>, val ownCrib: Boolean) : Step()

    /** Choose a card to lay on the pile. */
    data class Peg(override val title: String, override val text: String, val pile: List<Card>, val hand: List<Card>) : Step() {
        val count: Int get() = pile.sumOf { it.value }
        fun pointsFor(card: Card): List<ScoreItem> = Scoring.pegging(pile + card, count + card.value)
        val best: Card get() = hand.filter { it.value + count <= 31 }.maxBy { c -> pointsFor(c).sumOf { it.points } * 10 - c.value }
    }
}

class Lesson(val id: String, val title: String, val blurb: String, val steps: List<Step>, val endless: Boolean = false)

private fun c(t: String) = Card.list(t)
private fun s(t: String) = Card.parse(t)

object Lessons {
    val all: List<Lesson> = listOf(
        Lesson("basics", "The Cards & the Count", "What each card is worth, and the goal: 121 holes.", listOf(
            Step.Read("Welcome to the table", "Cribbage is a race round the board to 121. You score by making combinations with your cards, and you keep track by moving pegs. Two pegs each: the back one always leapfrogs the front."),
            Step.Read("What cards are worth", "Aces count one. Number cards count their number. Jacks, Queens and Kings all count ten, the same as a ten.", c("AH 5S 9D 10C JH QS KD")),
            Step.Read("Suits hardly matter", "Suits only count for flushes and for \"his nobs\" (a Jack). Mostly you're looking at the numbers.", c("7H 7S 7D")),
            Step.Peg("Try it", "Lay a card so the two cards on the table add up to exactly fifteen. That's worth two points.", c("KD"), c("5H 8C 3S")),
        )),
        Lesson("fifteens", "Fifteens", "Any cards adding to 15 score two. Find them all.", listOf(
            Step.Read("Fifteen two", "Every different combination of cards that adds to exactly 15 scores 2. A 5 and a Jack make fifteen.", c("5H JS"), groups = listOf(c("5H JS"))),
            Step.Read("More than two cards", "Three or more cards can make fifteen too: 4 + 5 + 6.", c("4C 5D 6S"), groups = listOf(c("4C 5D 6S"))),
            Step.Find("Find the fifteens", "Tap the cards that make each fifteen (starter included). There are four.", c("5H 10S JD QC"), s("KC"), ScoreKind.FIFTEEN),
            Step.Find("Trickier", "Find every fifteen in this hand and starter. Look for the three-card ones.", c("7H 8S 3D 5C"), s("2H"), ScoreKind.FIFTEEN),
            Step.Count("Count it", "How many points do the fifteens make here? (Two for each.)", c("6H 9S 6D 9C"), s("KH")),
        )),
        Lesson("pairs", "Pairs", "Two of a kind is two points. Three of a kind is six.", listOf(
            Step.Read("A pair", "Two cards of the same rank are a pair: 2 points. A King and a Queen are not a pair, even though both count ten.", c("8H 8S"), groups = listOf(c("8H 8S"))),
            Step.Read("Pair royal", "Three of a kind hold three different pairs: 6 points. Four of a kind (a double pair royal) is six pairs: 12.", c("4H 4S 4D"), groups = listOf(c("4H 4S"), c("4H 4D"), c("4S 4D"))),
            Step.Find("Find the pairs", "Tap each pair. Three of a kind counts as three pairs.", c("9H 9S 9D 2C"), s("2S"), ScoreKind.PAIR),
            Step.Count("Count it", "Pairs and fifteens together: how many?", c("5H 5S QD KC"), s("3C")),
        )),
        Lesson("runs", "Runs", "Three or more in a row score one per card.", listOf(
            Step.Read("A run", "Three or more cards in sequence (suits don't matter) score one point per card: 4-5-6 is a run of three for 3.", c("4H 5S 6D"), groups = listOf(c("4H 5S 6D"))),
            Step.Read("A double run", "With a pair inside a run, the run counts twice: 6-7-7-8 is two runs of three (6) plus the pair (2): 8 points.", c("6H 7S 7D 8C"), groups = listOf(c("6H 7S 8C"), c("6H 7D 8C"), c("7S 7D"))),
            Step.Find("Find the runs", "Tap the cards of each run. There are two.", c("3H 4S 5D 5C"), s("9H"), ScoreKind.RUN),
            Step.Count("Count it", "Runs, pairs and fifteens. Take your time.", c("4H 5S 6D 6C"), s("KD")),
        )),
        Lesson("flush", "Flushes & His Nobs", "Same-suit hands, and the lucky Jack.", listOf(
            Step.Read("A flush", "All four cards in your hand of one suit: 4 points, 5 if the starter matches too.", c("2H 7H 9H KH"), s("4H")),
            Step.Read("The crib is fussier", "In the crib, a flush only counts if all five cards (with the starter) match.", c("2S 7S 9S KS"), s("4H")),
            Step.Read("His nobs", "Holding the Jack of the same suit as the starter card scores 1: \"one for his nobs\". If the starter itself is a Jack, the dealer pegs 2 straight away for \"his heels\".", c("JD 3C 8S 4H"), s("6D"), groups = listOf(listOf(Card.parse("JD"), Card.parse("6D")))),
            Step.Count("Count it", "Flush, nobs and fifteens: how many?", c("JH 2H 3H 9H"), s("5H")),
            Step.Count("A crib", "This is the crib, so the flush rule changes. How many?", c("JC 2C 3C 9C"), s("5H"), crib = true),
        )),
        Lesson("crib", "The Deal & the Crib", "Six cards each, two go to the crib.", listOf(
            Step.Read("The deal", "Each player gets six cards. Each throws two face down into the crib, an extra hand that belongs to the dealer. The deal swaps every hand."),
            Step.Read("The starter", "Then the pone (the non-dealer) cuts and the top card is turned up: the starter. It counts as a fifth card for both hands and the crib."),
            Step.Discard("Your own crib", "You're dealing, so the crib is yours. Pick the two cards to throw.", c("5H 5D JS 6C 2H 9S"), ownCrib = true),
            Step.Discard("Their crib", "Now it's their crib. Don't hand them fives or pairs if you can help it.", c("4H 5S 6D 9C KS 2H"), ownCrib = false),
        )),
        Lesson("pegging", "Pegging", "Lay cards in turn, count to 31, score as you go.", listOf(
            Step.Read("The play", "Starting with the pone, players lay cards one at a time, calling the running total. It can't go past 31."),
            Step.Read("Scoring in play", "Make the count exactly 15: 2 points. Exactly 31: 2. Pair the last card: 2 (three in a row is 6). Complete a run (in any order): one per card."),
            Step.Peg("Fifteen", "The count is 10. Score something.", c("10S"), c("5H 9C 2D KS")),
            Step.Peg("A pair", "They just played a 7. Make a pair.", c("4S 7D"), c("7H 3C QD")),
            Step.Peg("A run", "4 then 6 are down. Which card completes a run?", c("4C 6S"), c("5D 9H KC")),
            Step.Read("Go", "If you can't lay a card without passing 31, you say \"Go\". The other player keeps playing if they can; whoever lays the last card gets one for the go. Then the count starts again from zero. The very last card of the hand is worth one too."),
        )),
        Lesson("show", "The Show & Winning", "Counting hands, and the race to 121.", listOf(
            Step.Read("The show", "After the play, hands are counted in order: the pone's hand, then the dealer's, then the dealer's crib. The pone counts first, which matters near the end."),
            Step.Read("Winning", "First to 121 wins, even in the middle of a hand. Beat your opponent before they reach 91 and that's a skunk: it counts as two games. Under 61 is a double skunk!"),
            Step.Count("One more", "Count this one like a regular at camp.", c("3H 4S 5D 5C"), s("6H")),
            Step.Count("The best hand there is", "Three fives, the Jack, and a five cut. How many?", c("5H 5D 5S JC"), s("5C")),
        )),
        Lesson("drill-count", "Practice: Count Hands", "Endless hands to count. Gets quick with practice.", emptyList(), endless = true),
        Lesson("drill-discard", "Practice: Discards", "Endless six-card hands: what do you throw?", emptyList(), endless = true),
    )

    fun byId(id: String) = all.first { it.id == id }

    /** A fresh drill step, different every time. */
    fun drillStep(lesson: Lesson, n: Int, random: Random): Step {
        val d = Card.shuffled(random)
        return if (lesson.id == "drill-count") {
            val crib = random.nextInt(4) == 0
            Step.Count(if (crib) "Count the crib #${n + 1}" else "Count the hand #${n + 1}", if (crib) "This is a crib: flushes need all five cards." else "Fifteens, pairs, runs, flushes and nobs.", d.take(4), d[4], crib)
        } else {
            val own = random.nextBoolean()
            Step.Discard("Discard #${n + 1}", if (own) "It's your crib. Which two do you throw?" else "It's their crib. Which two do you throw?", d.take(6).sorted(), own)
        }
    }
}
