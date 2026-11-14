package com.geoguy89.cribbage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoguy89.cribbage.data.Help
import com.geoguy89.cribbage.game.Card
import com.geoguy89.cribbage.game.GameState
import com.geoguy89.cribbage.game.Phase
import com.geoguy89.cribbage.game.Scoring

/** What the player should be doing right now: the step of the hand they're on and a plain-words instruction. */
data class GuideInfo(val step: Int, val instruction: String, val yourTurn: Boolean)

/** Points [card] would score if laid now in the play. */
fun pegPoints(s: GameState, card: Card): Int =
    if (card.value + s.count > 31) 0 else Scoring.pegging(s.pile.map { it.card } + card, s.count + card.value).sumOf { it.points }

/** The three steps of a hand, as the guide strip shows them. */
val GUIDE_STEPS = listOf("1 · Crib", "2 · Play", "3 · Count")

fun guideFor(table: Table, help: Help): GuideInfo? {
    val s = table.state
    if (s.over) return null
    val opp = table.names[table.opponent]
    if (table.pendingShow != null) {
        return GuideInfo(2, "The play is over. Now the hands are counted: the non-dealer's first, then the dealer's, then the crib.", false)
    }
    return when (s.phase) {
        Phase.DISCARD ->
            if (s.hands[table.me].size == 6) {
                val crib = if (s.dealer == table.me) "your crib" else "$opp's crib"
                GuideInfo(0, when (table.selected.size) {
                    0 -> "Tap 2 cards to put in $crib, then press Throw to crib. The other 4 are your hand."
                    1 -> "One more card for $crib."
                    else -> "Press Throw to crib. (Tap a card again to change your mind.)"
                }, true)
            } else GuideInfo(0, "Waiting for $opp to choose their crib cards…", false)
        Phase.PEG ->
            if (s.turn == table.me) {
                val playable = s.playable(table.me)
                val scoring = help != Help.OFF && playable.any { pegPoints(s, it) > 0 }
                val base = if (s.count == 0) "Your lead: play any card. The count starts at its value."
                else "Add a card to the count (now ${s.count}, ${31 - s.count} to go). It can't go over 31."
                val extra = when {
                    scoring -> " Cards marked +N score right now!"
                    playable.size < s.hands[table.me].size -> " Greyed cards would go over 31."
                    else -> ""
                }
                GuideInfo(1, base + extra, true)
            } else GuideInfo(1, "$opp is choosing a card…", false)
        Phase.OVER -> null
    }
}

/** The strip above the table: which step of the hand you're on, whose turn it is, and what to do. */
@Composable
fun GuideBar(table: Table, help: Help, compact: Boolean, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    val g = guideFor(table, help) ?: return
    Column(modifier.fillMaxWidth().plank(ui.wood, 41, 8.dp).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            GUIDE_STEPS.forEachIndexed { i, label ->
                val on = i == g.step
                Column(Modifier.drawBehind {
                    drawRoundRect(if (on) ui.accent else Color(0x22000000), cornerRadius = CornerRadius(10.dp.toPx()))
                }.padding(horizontal = 9.dp, vertical = 2.dp)) {
                    Txt(label, size = 12.sp, color = if (on) Color.White else ui.ink.copy(alpha = 0.6f), weight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                }
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.drawBehind {
                drawRoundRect(if (g.yourTurn) Color(0xFF3E8E3E) else Color(0x33000000), cornerRadius = CornerRadius(10.dp.toPx()))
            }.padding(horizontal = 9.dp, vertical = 2.dp)) {
                Txt(if (g.yourTurn) "YOUR TURN" else "WAIT", size = 11.sp, color = if (g.yourTurn) Color.White else ui.ink.copy(alpha = 0.7f), weight = FontWeight.Bold, maxLines = 1)
            }
        }
        Spacer(Modifier.height(3.dp))
        Hand(g.instruction, size = if (compact) 14.sp else 16.sp)
    }
}

// ---- the scoring cheat sheet ----------------------------------------------------------------------------------------

@Composable
private fun ScoreEntry(cards: String, name: String, pts: String, how: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        if (cards.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (c in Card.list(cards)) CardFace(c, Modifier.height(54.dp))
                Spacer(Modifier.weight(1f))
                Carved(pts, 24.sp, align = androidx.compose.ui.text.style.TextAlign.End)
            }
            Txt(name, Modifier.padding(top = 2.dp), size = 16.sp, weight = FontWeight.SemiBold)
        } else {
            // No cards to show: the name and points share a line.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(name, Modifier.weight(1f), size = 16.sp, weight = FontWeight.SemiBold)
                Carved(pts, 24.sp, align = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
        Hand(how, size = 14.sp)
    }
}

@Composable
private fun ScoreHeading(text: String) {
    Txt(text.uppercase(), Modifier.padding(top = 14.dp, bottom = 2.dp), size = 13.sp, weight = FontWeight.Bold, color = LocalUi.current.ink.copy(alpha = 0.65f))
}

/** Every way to score, with real cards as examples. Used in the game's Scoring sheet and the How to Play page. */
@Composable
fun ScoringGuide() {
    Hand("Card values: Ace = 1 · 2 to 10 = their number · Jack, Queen, King = 10.", size = 15.sp, weight = FontWeight.Bold, color = Color(0xFF8A3A20))
    ScoreHeading("While playing the cards (pegging)")
    Hand("You and your opponent take turns laying cards onto one running count. It can't go over 31.", size = 14.sp)
    ScoreEntry("5H KS", "Fifteen", "2", "Your card makes the count exactly 15 (here 5 + 10).")
    ScoreEntry("KH QS JD AC", "Thirty-one", "2", "Your card makes the count exactly 31 (10 + 10 + 10 + 1). The count then starts again at 0.")
    ScoreEntry("7H 7S", "Pair", "2", "Your card has the same rank as the card just before it.")
    ScoreEntry("7H 7S 7D", "Pair royal", "6", "A third card of the same rank. Three alike make three pairs.")
    ScoreEntry("7H 7S 7D 7C", "Double pair royal", "12", "All four of a kind.")
    ScoreEntry("4H 6S 5D", "Run", "1 each", "The last three or more cards laid make a sequence, in any order (4, 6, 5 = 4-5-6). One point per card.")
    ScoreEntry("", "Go", "1", "When nobody can lay a card without passing 31, the player who laid the last card scores 1.")
    ScoreEntry("", "Last card", "1", "Whoever lays the very last card of the hand scores 1.")
    ScoreHeading("When the hands are counted (the show)")
    Hand("Each hand is counted with the starter card as a fifth card. The non-dealer counts first, then the dealer's hand, then the dealer's crib.", size = 14.sp)
    ScoreEntry("5H JS", "Fifteen", "2 each", "Every different combination of cards adding to 15 scores 2. Three or more cards can make 15 too (4 + 5 + 6).")
    ScoreEntry("7H 7S", "Pair", "2", "Two cards of the same rank. Three alike score 6, four alike 12.")
    ScoreEntry("4H 5S 6D", "Run", "1 each", "Three or more cards in sequence. 6-7-7-8 is two runs of three (6) plus a pair (2) = 8.")
    ScoreEntry("2H 5H 9H KH", "Flush", "4 or 5", "All four hand cards the same suit: 4. Add the starter if it matches too: 5. (A crib only counts all five.)")
    ScoreEntry("JH 4H", "His nobs", "1", "A Jack in your hand that matches the suit of the starter card (here the starter is the 4 of hearts).")
    ScoreEntry("", "His heels", "2", "If the starter card is a Jack, the dealer scores 2 right away.")
    ScoreHeading("Winning")
    Hand("First to 121 wins, even in the middle of a hand. If the loser is still under 91, it's a skunk (two games); under 61, a double skunk.", size = 14.sp)
}
