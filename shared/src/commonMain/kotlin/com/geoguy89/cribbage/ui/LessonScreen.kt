package com.geoguy89.cribbage.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoguy89.cribbage.audio.Sfx
import com.geoguy89.cribbage.game.Ai
import com.geoguy89.cribbage.game.Card
import com.geoguy89.cribbage.game.Coach
import com.geoguy89.cribbage.game.Lesson
import com.geoguy89.cribbage.game.Lessons
import com.geoguy89.cribbage.game.ScoreKind
import com.geoguy89.cribbage.game.Step
import kotlinx.coroutines.delay
import kotlin.random.Random

/** A lesson, one step at a time: read, find the combinations, count, choose a discard, lay a card. */
@Composable
fun LessonScreen(model: AppModel, lesson: Lesson) {
    var index by remember(lesson.id) { mutableIntStateOf(0) }
    val random = remember(lesson.id) { Random(lesson.id.hashCode() + kotlinx.datetime.Clock.System.now().toEpochMilliseconds()) }
    val step = remember(lesson.id, index) { if (lesson.endless) Lessons.drillStep(lesson, index, random) else lesson.steps[index] }
    var done by remember(lesson.id, index) { mutableStateOf(step is Step.Read) }
    val total = if (lesson.endless) null else lesson.steps.size
    Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WoodButton("‹ Lessons", model::back, size = 14.sp)
            Spacer(Modifier.weight(1f))
            Plaque(if (total != null) "${index + 1} of $total" else "Practice", size = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        WoodSign(lesson.title, size = 20.sp)
        Spacer(Modifier.height(10.dp))
        Parchment(Modifier.fillMaxWidth().widthIn(max = 640.dp).weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScrollCompat()) {
                Hand(step.title, size = 22.sp, weight = FontWeight.Bold)
                Hand(step.text, size = 17.sp)
                Spacer(Modifier.height(12.dp))
                when (step) {
                    is Step.Read -> ReadStep(step)
                    is Step.Find -> FindStep(model, step) { done = true }
                    is Step.Count -> CountStep(model, step, index, lesson.endless) { done = true }
                    is Step.Discard -> DiscardStep(model, step) { done = true }
                    is Step.Peg -> PegStep(model, step) { done = true }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().widthIn(max = 640.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (index > 0 && !lesson.endless) WoodButton("Back", { index-- }, Modifier.weight(1f))
            val last = total != null && index == total - 1
            WoodButton(if (last) "Finish" else "Next", {
                if (last) model.finishLesson(lesson) else { index++; model.sound(Sfx.CLICK) }
            }, Modifier.weight(2f), primary = done, enabled = done || lesson.endless)
        }
    }
}

/** A row of cards, with the starter set apart. */
@Composable
private fun CardRow(cards: List<Card>, starter: Card?, lit: Set<Card> = emptySet(), picked: Set<Card> = emptySet(), onTap: ((Card) -> Unit)? = null) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        val slots = cards.size + if (starter != null) 1 else 0
        val pad = (4 - slots).coerceAtLeast(0)
        if (pad > 0) Spacer(Modifier.weight(pad / 2f))
        for (c in cards) Box(Modifier.weight(1f).graphicsLayer { translationY = if (c in picked) -14.dp.toPx() else 0f }.let { if (onTap != null) it.clickable { onTap(c) } else it }) {
            CardFace(c, Modifier.fillMaxWidth(), glow = if (c in lit || c in picked) ui.accent else null)
        }
        if (starter != null) {
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).let { if (onTap != null) it.clickable { onTap(starter) } else it }, horizontalAlignment = Alignment.CenterHorizontally) {
                Txt("starter", size = 11.sp)
                CardFace(starter, Modifier.fillMaxWidth().graphicsLayer { translationY = if (starter in picked) -14.dp.toPx() else 0f }, glow = if (starter in lit || starter in picked) ui.accent else null)
            }
        }
        if (pad > 0) Spacer(Modifier.weight(pad / 2f))
    }
}

@Composable
private fun ReadStep(step: Step.Read) {
    if (step.cards.isEmpty()) return
    var g by remember(step) { mutableIntStateOf(0) }
    // Light up each combination in turn.
    if (step.groups.isNotEmpty()) LaunchedEffect(step) { while (true) { delay(1400); g = (g + 1) % step.groups.size } }
    CardRow(step.cards, step.starter, lit = step.groups.getOrNull(g)?.toSet().orEmpty())
}

@Composable
private fun FindStep(model: AppModel, step: Step.Find, onDone: () -> Unit) {
    var picked by remember(step) { mutableStateOf(setOf<Card>()) }
    var found by remember(step) { mutableStateOf(listOf<List<Card>>()) }
    var note by remember(step) { mutableStateOf<String?>(null) }
    val targets = step.targets
    CardRow(step.hand, step.starter, picked = picked) { c ->
        picked = if (c in picked) picked - c else picked + c
        val match = targets.firstOrNull { it.toSet() == picked }
        when {
            match != null && match in found -> { note = "You found that one already."; picked = emptySet() }
            match != null -> {
                found = found + listOf(match)
                picked = emptySet()
                model.sound(Sfx.SCORE)
                note = when (step.kind) { ScoreKind.FIFTEEN -> "Fifteen ${found.size * 2}!"; ScoreKind.PAIR -> "A pair!"; else -> "A run of ${match.size}!" }
                if (found.size == targets.size) { note = "That's all of them: ${found.size} found."; onDone() }
            }
            picked.size >= 5 || (step.kind == ScoreKind.PAIR && picked.size >= 2) -> { note = "Not quite. Try again."; picked = emptySet(); model.sound(Sfx.GO) }
        }
    }
    Spacer(Modifier.height(10.dp))
    Hand("Found ${found.size} of ${targets.size}" + (note?.let { " · $it" } ?: ""), size = 17.sp, weight = FontWeight.Bold)
    for (f in found) Hand("✓ ${f.joinToString(" ")}", size = 15.sp, color = Color(0xFF2F6B2F))
}

@Composable
private fun CountStep(model: AppModel, step: Step.Count, seed: Int, drill: Boolean, onDone: () -> Unit) {
    var answer by remember(step) { mutableStateOf<Int?>(null) }
    var lit by remember(step) { mutableIntStateOf(-1) }
    CardRow(step.hand, step.starter, lit = step.score.items.getOrNull(lit)?.cards?.toSet().orEmpty())
    Spacer(Modifier.height(12.dp))
    if (answer == null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in step.choices(seed)) WoodButton("$c", {
                answer = c
                val right = c == step.score.total
                model.sound(if (right) Sfx.BIG_SCORE else Sfx.GO)
                if (drill) model.recordDrill(right)
                onDone()
            }, Modifier.weight(1f), size = 20.sp)
        }
    } else {
        val right = answer == step.score.total
        LaunchedEffect(step) { for (i in step.score.items.indices) { lit = i; delay(1100) }; lit = -1 }
        Hand(if (right) "Right! ${step.score.total} points." else "It's ${step.score.total}. Here's how:", size = 19.sp, weight = FontWeight.Bold, color = if (right) Color(0xFF2F6B2F) else Color(0xFF8A3A20))
        Hand(step.score.chant, size = 16.sp)
        for ((i, it) in step.score.items.withIndex()) Hand("• ${it.label}: ${it.cards.joinToString(" ")}", size = 15.sp, weight = if (i == lit) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun DiscardStep(model: AppModel, step: Step.Discard, onDone: () -> Unit) {
    var picked by remember(step) { mutableStateOf(setOf<Card>()) }
    var verdict by remember(step) { mutableStateOf<String?>(null) }
    CardRow(step.six, null, picked = picked) { c ->
        if (verdict != null) return@CardRow
        picked = if (c in picked) picked - c else (picked + c).let { if (it.size > 2) it.drop(1).toSet() else it }
    }
    Spacer(Modifier.height(10.dp))
    if (verdict == null) WoodButton("Throw these two", {
        val options = Ai.discardOptions(step.six, step.ownCrib, samples = 60)
        val mine = options.first { it.throwAway.toSet() == picked }
        val best = options.first()
        val rank = options.indexOf(mine) + 1
        verdict = if (rank == 1) "Spot on: that's the best throw. Your hand averages ${Ai.fmt(mine.handAverage)}${if (step.ownCrib) " plus about ${Ai.fmt(mine.cribAverage)} in the crib" else ", giving their crib about ${Ai.fmt(mine.cribAverage)}"}."
        else "Not bad (#$rank of 15). Best was to keep ${best.keep.joinToString(" ")}: ${Ai.fmt(best.value)} on average against your ${Ai.fmt(mine.value)}."
        model.sound(if (rank <= 2) Sfx.SCORE else Sfx.GO)
        onDone()
    }, Modifier.fillMaxWidth(), primary = picked.size == 2, enabled = picked.size == 2)
    verdict?.let { Hand(it, size = 16.sp) }
}

@Composable
private fun PegStep(model: AppModel, step: Step.Peg, onDone: () -> Unit) {
    var played by remember(step) { mutableStateOf<Card?>(null) }
    Hand("On the table · count ${step.count}", size = 15.sp, weight = FontWeight.Bold)
    CardRow(step.pile + listOfNotNull(played), null)
    Spacer(Modifier.height(10.dp))
    Hand("Your cards:", size = 15.sp, weight = FontWeight.Bold)
    CardRow(step.hand.filter { it != played }, null) { c ->
        if (played != null || c.value + step.count > 31) return@CardRow
        played = c
        val pts = step.pointsFor(c).sumOf { it.points }
        model.sound(if (pts > 0) Sfx.SCORE else Sfx.CARD)
        onDone()
    }
    played?.let { c ->
        val items = step.pointsFor(c)
        Spacer(Modifier.height(8.dp))
        Hand(if (items.isNotEmpty()) "${items.joinToString(" and ") { it.label }}! (count ${step.count + c.value})" else "No points: the count is ${step.count + c.value}. ${if (c != step.best) "The ${step.best.longName} would have scored." else ""}",
            size = 18.sp, weight = FontWeight.Bold, color = if (items.isNotEmpty()) Color(0xFF2F6B2F) else Color(0xFF8A3A20))
        if (items.isEmpty()) WoodButton("Try again", { played = null }, size = 13.sp)
    }
}

