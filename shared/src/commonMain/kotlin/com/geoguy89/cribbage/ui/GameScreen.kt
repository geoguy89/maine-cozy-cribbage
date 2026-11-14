package com.geoguy89.cribbage.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoguy89.cribbage.audio.Sfx
import com.geoguy89.cribbage.board.BoardView
import com.geoguy89.cribbage.game.Card
import com.geoguy89.cribbage.game.Coach
import com.geoguy89.cribbage.game.Phase
import com.geoguy89.cribbage.game.Show
import com.geoguy89.cribbage.net.EMOTES
import kotlinx.coroutines.delay

private enum class Sheet { LOG, RULES, MENU, TALK }

/** Where the walk through a show has got to: which count, and how many of its scores are revealed. */
private data class ShowStep(val count: Int = 0, val items: Int = 0)

private fun scoresAt(show: Show, step: ShowStep): List<Int> {
    val s = show.startScores.toMutableList()
    show.counts.forEachIndexed { i, c ->
        val n = when { i < step.count -> c.score.items.size; i == step.count -> step.items; else -> 0 }
        s[c.player] = (s[c.player] + c.score.items.take(n).sumOf { it.points }).coerceAtMost(121)
    }
    return s
}

@Composable
fun GameScreen(model: AppModel, table: Table, posture: Posture) {
    val state = table.state
    val show = table.pendingShow
    var step by remember(show?.hand) { mutableStateOf(ShowStep()) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var zoom by remember { mutableStateOf(false) }
    val display = show?.let { scoresAt(it, step) } ?: state.scores
    val pegColors = model.settings.pegs.colors
    // Seat 0's pegs are always the first colour, so both players see the same board.
    val boardScores = display
    val boardPrev = if (show != null) show.startScores else state.prevScores

    LaunchedEffect(state.played.size) { if (state.played.isNotEmpty()) model.sound(Sfx.CARD, 0.8f) }
    LaunchedEffect(state.hand) { model.sound(Sfx.SHUFFLE, 0.6f) }
    LaunchedEffect(show?.hand) { if (show != null) model.recordShow(table) }
    LaunchedEffect(table.message) { if (table.message != null) { delay(3500); table.message = null } }
    LaunchedEffect(table.emote) { if (table.emote != null) { delay(3500); table.emote = null } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val mw = maxWidth
        val mh = maxHeight
        val wide = posture.bookHingeX != null || (mw >= 600.dp && mw > mh * 0.8f)
        val tabletop = posture.tabletopHingeY != null
        val board: @Composable (Modifier) -> Unit = { m ->
            BoardView(model.board, boardScores, boardPrev, m.clickable { zoom = true }, pegs = model.settings.pegs, ambient = model.settings.ambient, onPegLanded = { model.sound(Sfx.PEG, 0.9f) })
        }
        when {
            tabletop -> Column(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth().height(posture.tabletopHingeY!!.dp).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ScoreBar(model, table, display) { sheet = Sheet.MENU }
                    Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1.15f).fillMaxHeight(), contentAlignment = Alignment.Center) { board(Modifier) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            OpponentRow(table)
                            PlayArea(model, table, Modifier.fillMaxWidth().weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(posture.hingeThickness.dp))
                Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp)) {
                    PlayColumn(model, table, compact = true, onSheet = { sheet = it }, showOpponent = false, showTable = false)
                }
            }
            wide -> Row(Modifier.fillMaxSize()) {
                val split = posture.bookHingeX?.dp ?: (mw * 0.56f)
                Column(Modifier.width(split).fillMaxHeight().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ScoreBar(model, table, display) { sheet = Sheet.MENU }
                    Spacer(Modifier.height(8.dp))
                    OpponentRow(table)
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) { board(Modifier) }
                }
                Spacer(Modifier.width(posture.hingeThickness.dp))
                Column(Modifier.weight(1f).fillMaxHeight().padding(10.dp)) {
                    PlayColumn(model, table, compact = false, onSheet = { sheet = it }, showOpponent = false)
                }
            }
            else -> Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ScoreBar(model, table, display) { sheet = Sheet.MENU }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().heightIn(max = mh * (if (table.notes.isNotEmpty()) 0.27f else 0.42f)), contentAlignment = Alignment.Center) { board(Modifier) }
                PlayColumn(model, table, compact = true, onSheet = { sheet = it })
            }
        }

        if (zoom) BoardZoom(model, boardScores, boardPrev) { zoom = false }
        // The show, then the end of the game, over everything else.
        // The show waits until the tutor has finished explaining the end of the play.
        if (show != null && table.notes.isEmpty()) ShowOverlay(model, table, show, step, onStep = { step = it }, onDone = { table.ackShow(); model.sound(Sfx.DEAL) })
        else if (state.over) GameOverOverlay(model, table)
        when (sheet) {
            Sheet.LOG -> LogSheet(table) { sheet = null }
            Sheet.RULES -> RulesSheet { sheet = null }
            Sheet.MENU -> MenuSheet(model, table) { sheet = null }
            Sheet.TALK -> TalkSheet(table) { sheet = null }
            null -> Unit
        }
        table.message?.let { Toast(it, Modifier.align(Alignment.TopCenter).padding(top = 70.dp)) }
    }
}

@Composable
private fun ScoreBar(model: AppModel, table: Table, display: List<Int>, onMenu: () -> Unit) {
    val colors = model.settings.pegs.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WoodButton("☰", onMenu, size = 16.sp, modifier = Modifier.width(48.dp))
        Plaque("${model.board.name}, ME  ⤢", Modifier.weight(1f), size = 13.sp)
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Plaque("${table.names[table.me].take(18)}: ${display[table.me]}", size = 13.sp, pegColor = colors[table.me])
            Plaque("${table.names[table.opponent].take(18)}: ${display[table.opponent]}", size = 13.sp, pegColor = colors[table.opponent])
        }
    }
}

/** The other player's cards, whose crib it is, and anything they've said. */
@Composable
private fun OpponentRow(table: Table) {
    val ui = LocalUi.current
    val s = table.state
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (table.online) Box(Modifier.size(9.dp).drawBehind { drawCircle(if (table.opponentHere) Color(0xFF6FD36F) else Color(0xFF9A9A9A)) })
                if (table.online) Spacer(Modifier.width(6.dp))
                Txt(table.names[table.opponent], size = 14.sp, color = ui.onTable, weight = FontWeight.Medium, shadow = true, maxLines = 1)
                if (s.dealer == table.opponent) { Spacer(Modifier.width(6.dp)); DealerChip() }
            }
            table.emote?.let { (_, text) -> SpeechBubble(text) }
        }
        val n = table.handCounts[table.opponent]
        Box(Modifier.height(44.dp).width((26 + n * 12).dp)) {
            for (i in 0 until n) CardBack(Modifier.height(42.dp).offset(x = (i * 12).dp).graphicsLayer { rotationZ = (i - n / 2f) * 4f }, ui.look.base.cardBack)
        }
    }
}

@Composable
private fun DealerChip() {
    Box(Modifier.size(20.dp).drawBehind {
        drawCircle(Color.Black.copy(alpha = 0.3f), size.minDimension / 2, center + Offset(1f, 2f))
        drawCircle(Brush.radialGradient(listOf(Color(0xFFF7E2A6), Color(0xFFB08A3A))), size.minDimension / 2)
    }, contentAlignment = Alignment.Center) { Txt("D", size = 11.sp, color = Color(0xFF3A2414), weight = FontWeight.Bold) }
}

@Composable
private fun SpeechBubble(text: String) {
    Box(Modifier.padding(top = 4.dp).drawBehind {
        drawRoundRect(Color(0xFFFFF8E8), cornerRadius = CornerRadius(10.dp.toPx()))
        drawRoundRect(Color(0xFF3A2414), cornerRadius = CornerRadius(10.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
    }.padding(horizontal = 10.dp, vertical = 4.dp)) { Hand(text, size = 15.sp) }
}

/** Everything below the board: the opponent, the table, the coach's note, your hand and the buttons. */
@Composable
private fun ColumnScope.PlayColumn(model: AppModel, table: Table, compact: Boolean, onSheet: (Sheet) -> Unit, showOpponent: Boolean = true, showTable: Boolean = true) {
    val s = table.state
    val help = model.settings.help
    val coachOn = help != com.geoguy89.cribbage.data.Help.OFF && !table.online
    val tip = remember(s.seq, s.hand, s.phase, s.hands[table.me].size, coachOn) { if (coachOn) Coach.tip(s, table.me) else null }
    val note = table.notes.firstOrNull()
    if (showOpponent) OpponentRow(table)
    if (showTable) PlayArea(model, table, Modifier.fillMaxWidth().weight(1f).heightIn(min = 118.dp)) else Spacer(Modifier.weight(1f))
    if (note != null) {
        TutorCard(note, table.notes.size - 1, compact) { table.dismissNote(); model.sound(Sfx.CLICK, 0.5f) }
    } else if (tip != null) {
        var why by remember { mutableStateOf(false) }
        Parchment(Modifier.fillMaxWidth().padding(vertical = 4.dp), pins = !compact) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Hand("tutorial helper", Modifier.weight(1f), size = 15.sp, weight = FontWeight.Bold, color = Color(0xFF6B3A1E))
                if (tip.why.isNotEmpty()) Box(Modifier.clickable { why = !why }.padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Hand(if (why) "Hide ▴" else "Why? ▾", size = 15.sp, weight = FontWeight.Bold, color = Color(0xFF8A3A20))
                }
            }
            val review = table.review?.takeIf { help == com.geoguy89.cribbage.data.Help.TIPS }
            if (review != null) Hand("Last: " + review.text, size = if (compact) 13.sp else 15.sp, color = Color(0xFF5A3A1E))
            Hand(tip.text.removeSuffix(" Tap Why? for the reasoning."), size = if (compact) 14.sp else 16.sp, weight = FontWeight.Medium)
            if (why) Column(Modifier.heightIn(max = 260.dp).verticalScrollCompat().padding(top = 6.dp)) {
                if (review != null) {
                    Hand(review.title, Modifier.padding(top = 6.dp), size = 15.sp, weight = FontWeight.Bold)
                    for (line in review.why) Hand(line, Modifier.padding(vertical = 2.dp), size = 14.sp, color = Color(0xFF2E2014))
                    Hand("What to do now", Modifier.padding(top = 8.dp), size = 15.sp, weight = FontWeight.Bold)
                }
                for (line in tip.why) Hand(line, Modifier.padding(vertical = 2.dp), size = 14.sp, color = Color(0xFF2E2014))
            }
        }
    }
    HandRow(model, table, Modifier.fillMaxWidth().padding(top = 4.dp))
    ActionRow(model, table, tip?.cards.orEmpty(), onSheet)
    Spacer(Modifier.height(6.dp))
    LeatherTabs(buildList {
        add("Boards" to { model.go(Screen.BOARDS) })
        add("Settings" to { model.go(Screen.SETTINGS) })
        if (table.online) add("Talk" to { onSheet(Sheet.TALK) })
        add("Log" to { onSheet(Sheet.LOG) })
    })
}

/** The middle of the table: the deck and starter, the crib, the cards laid in pegging and the count. */
@Composable
private fun PlayArea(model: AppModel, table: Table, modifier: Modifier) {
    val ui = LocalUi.current
    val s = table.state
    BoxWithConstraints(modifier) {
        val areaW = maxWidth
        val cardH = minOf(maxHeight * 0.92f, 120.dp)
        val cardW = cardH * CARD_ASPECT
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            // Deck with the starter turned up on it.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(cardW * 0.8f).height(cardH * 0.8f)) {
                    CardBack(Modifier.height(cardH * 0.8f).offset(2.dp, 2.dp), ui.look.base.cardBack)
                    CardBack(Modifier.height(cardH * 0.8f), ui.look.base.cardBack)
                    val starter = table.pendingShow?.starter ?: s.starter
                    starter?.let { st -> key(st.id, table.pendingShow?.hand ?: s.hand) { FlipIn { CardFace(st, Modifier.height(cardH * 0.8f)) } } }
                }
                Txt(if (s.starter != null || table.pendingShow != null) "Starter" else "Deck", size = 11.sp, color = ui.onTable, shadow = true)
            }
            Spacer(Modifier.width(10.dp))
            // The pile in play.
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                val ended = table.endPile?.takeIf { table.pendingShow != null }
                val pile = ended ?: s.pile
                val step = if (pile.size <= 1) 0.dp else minOf(cardW * 0.55f, (areaW - cardW * 2.6f) / (pile.size - 1).coerceAtLeast(1))
                pile.forEachIndexed { i, p ->
                    key(p.card.id, s.hand) {
                        SlideIn(fromAbove = p.player != table.me) {
                            CardFace(p.card, Modifier.height(cardH).offset(x = step * i, y = if (p.player == table.me) 6.dp else (-6).dp))
                        }
                    }
                }
                if (ended != null) CountBadge(table.endCount, Modifier.align(Alignment.TopEnd))
                else if (s.phase == Phase.PEG) CountBadge(s.count, Modifier.align(Alignment.TopEnd))
                if (s.phase == Phase.DISCARD && ended == null) {
                    Hand(if (s.hands[table.me].size == 6) "Pick two cards for ${if (s.dealer == table.me) "your" else "their"} crib." else "Waiting for ${table.names[table.opponent]} to throw...", Modifier.padding(8.dp), size = 16.sp, color = ui.onTable)
                }
                Popups(table, Modifier.fillMaxSize())
            }
            // The crib.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(cardW * 0.7f).height(cardH * 0.7f)) {
                    for (i in 0 until table.cribCount) CardBack(Modifier.height(cardH * 0.7f).offset((i * 2).dp, (i * -2).dp), ui.look.base.cardBack)
                }
                Txt(if (s.dealer == table.me) "Your crib" else "Their crib", size = 11.sp, color = ui.onTable, shadow = true)
            }
        }
    }
}

@Composable
private fun CountBadge(count: Int, modifier: Modifier) {
    val ui = LocalUi.current
    val scale by animateFloatAsState(if (count == 15 || count == 31) 1.25f else 1f, spring(Spring.DampingRatioMediumBouncy))
    Box(modifier.size(58.dp).graphicsLayer { scaleX = scale; scaleY = scale }.drawBehind {
        drawCircle(Color.Black.copy(alpha = 0.35f), size.minDimension / 2, center + Offset(2f, 3f))
        drawCircle(Brush.radialGradient(listOf(ui.wood.light, ui.wood.base, ui.wood.dark)), size.minDimension / 2)
        drawCircle(ui.wood.dark, size.minDimension / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
    }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Txt("COUNT", size = 9.sp, color = ui.ink.copy(alpha = 0.7f))
            Carved("$count", 22.sp)
        }
    }
}

/** A card dropping onto the pile, from the hand (below) or the opponent (above). */
@Composable
private fun SlideIn(fromAbove: Boolean, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
    Box(Modifier.graphicsLayer {
        translationY = (1 - a.value) * (if (fromAbove) -160f else 220f)
        rotationZ = (1 - a.value) * (if (fromAbove) -12f else 10f)
        alpha = a.value.coerceIn(0.2f, 1f)
    }) { content() }
}

/** The starter turning over. */
@Composable
private fun FlipIn(content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(150); a.animateTo(1f, tween(420)) }
    Box(Modifier.graphicsLayer { rotationY = (1 - a.value) * 90f; cameraDistance = 12 * density; alpha = if (a.value < 0.02f) 0f else 1f }) { content() }
}

/** Scores floating up from the table. */
@Composable
private fun Popups(table: Table, modifier: Modifier) {
    val ui = LocalUi.current
    Box(modifier, contentAlignment = Alignment.Center) {
        for (p in table.popups) key(p.id) {
            val a = remember { Animatable(0f) }
            LaunchedEffect(Unit) { a.animateTo(1f, tween(1700)); table.dropPopup(p.id) }
            val mine = p.player == table.me
            Box(Modifier.graphicsLayer {
                translationY = -a.value * 90f + (if (mine) 30f else -30f)
                alpha = (1.4f - a.value * 1.4f).coerceIn(0f, 1f)
                val pop = if (a.value < 0.15f) 0.6f + a.value / 0.15f * 0.5f else 1.1f - (a.value - 0.15f) * 0.12f
                scaleX = pop; scaleY = pop
            }.plank(ui.wood, p.id, 10.dp, glow = if (p.points > 0) ui.accent else null).padding(horizontal = 14.dp, vertical = 6.dp)) {
                Carved((if (mine) "" else "${table.names[p.player].take(10)}: ") + p.text, 18.sp)
            }
        }
    }
}

/** Your hand, fanned. Tap to choose; tap a chosen card again to play it. */
@Composable
private fun HandRow(model: AppModel, table: Table, modifier: Modifier) {
    val s = table.state
    val hand = if (table.pendingShow != null) emptyList() else s.hands[table.me]
    val ui = LocalUi.current
    BoxWithConstraints(modifier.height(140.dp), contentAlignment = Alignment.BottomCenter) {
        val handW = maxWidth
        val cardH = minOf(maxHeight * 0.86f, 136.dp, handW * 0.3f)
        val cardW = cardH * CARD_ASPECT
        val n = hand.size.coerceAtLeast(1)
        val step = if (n <= 1) 0.dp else minOf(cardW * 0.92f, (handW - cardW - 36.dp) / (n - 1))
        val total = step * (n - 1) + cardW
        hand.forEachIndexed { i, card ->
            key(card.id, s.hand) {
                val sel = card in table.selected
                val playable = s.phase != Phase.PEG || card.value + s.count <= 31
                val lift by animateFloatAsState(if (sel) 1f else 0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                val enter = remember { Animatable(0f) }
                LaunchedEffect(Unit) { delay(i * 90L); model.sound(Sfx.DEAL, 0.5f); enter.animateTo(1f, tween(380, easing = FastOutSlowInEasing)) }
                val angle = (i - (n - 1) / 2f) * 4f
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = (handW - total) / 2 + step * i)
                        .graphicsLayer {
                            translationY = -lift * 22.dp.toPx() + kotlin.math.abs(i - (n - 1) / 2f) * 3.dp.toPx() + (1 - enter.value) * -260.dp.toPx()
                            translationX = (1 - enter.value) * 120.dp.toPx()
                            rotationZ = angle * enter.value
                            alpha = enter.value
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                        }
                        .clickable(enabled = table.myTurn && !table.busy) {
                            if (sel && s.phase == Phase.PEG && playable) { table.play(card); model.haptic(Haptic.CONFIRM) }
                            else { table.toggle(card); model.haptic(Haptic.TICK); model.sound(Sfx.CLICK, 0.4f) }
                        },
                ) {
                    CardFace(card, Modifier.height(cardH), dim = s.phase == Phase.PEG && table.myTurn && !playable, glow = if (sel) ui.accent else null)
                    if (model.settings.help != com.geoguy89.cribbage.data.Help.OFF && !table.online) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).drawBehind {
                        drawRoundRect(Color(0xFF3B2414).copy(alpha = 0.85f), cornerRadius = CornerRadius(8.dp.toPx()))
                    }.padding(horizontal = 7.dp, vertical = 1.dp)) { Txt("${card.value}", size = 12.sp, color = Color.White, weight = FontWeight.Bold) }
                }
            }
        }
        if (table.pendingShow != null) Hand("Next: counting the hands.", Modifier.padding(bottom = 30.dp), size = 16.sp, color = ui.onTable)
        else if (hand.isEmpty() && s.phase == Phase.PEG) Hand("All your cards are on the table.", Modifier.padding(bottom = 30.dp), size = 16.sp, color = ui.onTable)
    }
}

@Composable
private fun ActionRow(model: AppModel, table: Table, hint: List<Card>, onSheet: (Sheet) -> Unit) {
    val s = table.state
    val sel = table.selected.toList()
    val (label, enabled, action) = when {
        s.over -> Triple("Game over", false) {}
        table.blocked -> Triple("Continue above", false) {}
        !table.myTurn -> Triple(if (table.busy) "Sending..." else "${table.names[table.opponent]}'s turn", false) {}
        s.phase == Phase.DISCARD -> if (sel.size == 2) Triple("Throw to crib", true) { table.discard(sel) } else Triple(if (sel.isEmpty()) "Pick 2 cards" else "Pick 1 more", false) {}
        else -> sel.firstOrNull()?.let { c -> Triple("Play ${c.short}", c.value + s.count <= 31) { table.play(c) } } ?: Triple("Pick a card", false) {}
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WoodButton(label, { action(); model.haptic(Haptic.CONFIRM) }, Modifier.weight(1.6f), primary = enabled, enabled = enabled, size = 16.sp)
        WoodButton("Hint", {
            val cards = if (hint.isNotEmpty()) hint else (if (s.phase == Phase.DISCARD) Coach.discardTip(s, table.me).cards else if (table.myTurn) Coach.pegTip(s, table.me).cards else emptyList())
            if (table.myTurn && cards.isNotEmpty()) table.selected = cards.toSet()
            model.sound(Sfx.CHIME, 0.4f)
        }, Modifier.weight(1f), enabled = table.myTurn, size = 15.sp, icon = { Lantern() })
        WoodButton("Rules", { onSheet(Sheet.RULES) }, Modifier.weight(1f), size = 15.sp)
    }
}

/** A small hurricane lantern for the Hint button. */
@Composable
private fun Lantern() {
    Canvas(Modifier.size(14.dp, 20.dp)) {
        val w = size.width; val h = size.height
        drawLine(Color(0xFF3A2414), Offset(w * 0.3f, h * 0.08f), Offset(w * 0.7f, h * 0.08f), strokeWidth = 2f)
        drawRoundRect(Color(0xFFFFD866), Offset(w * 0.18f, h * 0.22f), androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.55f), CornerRadius(w * 0.3f))
        drawRoundRect(Color(0xFF3A2414), Offset(w * 0.18f, h * 0.22f), androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.55f), CornerRadius(w * 0.3f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        drawRect(Color(0xFF3A2414), Offset(w * 0.1f, h * 0.8f), androidx.compose.ui.geometry.Size(w * 0.8f, h * 0.14f))
    }
}

// ---- overlays -------------------------------------------------------------------------------------------------------

/**
 * The show, counted out loud: each hand and the crib laid out with the starter, and every fifteen, pair, run, flush
 * and nobs lit up in turn while the pegs move.
 */
@Composable
private fun ShowOverlay(model: AppModel, table: Table, show: Show, step: ShowStep, onStep: (ShowStep) -> Unit, onDone: () -> Unit) {
    val ui = LocalUi.current
    val count = show.counts.getOrNull(step.count)
    val tutor = model.settings.help == com.geoguy89.cribbage.data.Help.TUTOR && !table.online
    val auto = model.settings.autoShow && !tutor
    fun next() {
        val c = show.counts.getOrNull(step.count) ?: return onDone()
        when {
            step.items < c.score.items.size -> onStep(step.copy(items = step.items + 1))
            step.count + 1 < show.counts.size -> onStep(ShowStep(step.count + 1, 0))
            else -> onDone()
        }
    }
    LaunchedEffect(step) {
        val c = show.counts.getOrNull(step.count) ?: return@LaunchedEffect
        if (step.items > 0) {
            val item = c.score.items[step.items - 1]
            model.sound(if (item.points >= 6) Sfx.BIG_SCORE else Sfx.SCORE, 0.7f)
        }
        if (auto) { delay(if (step.items == 0) 1300 else 900); next() }
    }
    Box(Modifier.fillMaxSize().drawBehind {
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), startY = size.height * 0.3f))
    }.clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) {}, contentAlignment = Alignment.BottomCenter) {
        Parchment(Modifier.padding(horizontal = 12.dp, vertical = 16.dp).widthIn(max = 560.dp)) {
            if (count == null) return@Parchment
            val whose = if (count.player == table.me) "Your" else "${table.names[count.player]}'s"
            val shown = count.score.items.take(step.items)
            Hand("${whose} ${if (count.crib) "crib" else "hand"} · hand ${show.hand}", size = 22.sp, weight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val lit = shown.lastOrNull()?.cards.orEmpty()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                for (c in count.cards) CardFace(c, Modifier.weight(1f), glow = if (c in lit) ui.accent else null)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Txt("starter", size = 11.sp)
                    CardFace(show.starter, Modifier.fillMaxWidth(), glow = if (show.starter in lit) ui.accent else null)
                }
            }
            Spacer(Modifier.height(10.dp))
            val running = shown.sumOf { it.points }
            val chant = if (shown.isEmpty()) (if (count.score.items.isEmpty()) "Nineteen! Nothing to count." else "Let's count it...")
                else com.geoguy89.cribbage.game.HandScore(shown).chant
            Hand(chant, size = 18.sp)
            if (tutor) {
                val last = shown.lastOrNull()
                Hand(if (last == null) com.geoguy89.cribbage.game.Narrator.showIntro(count, show.starter, table.me, table.names)
                    else com.geoguy89.cribbage.game.Narrator.explain(last, show.starter, count.crib),
                    Modifier.padding(top = 6.dp), size = 16.sp, color = Color(0xFF2F4F2F), weight = FontWeight.Medium)
                if (step.items >= count.score.items.size && count.score.items.isNotEmpty()) {
                    Hand("Total: ${count.score.items.joinToString(" + ") { it.points.toString() }} = ${count.score.total} points for ${if (count.player == table.me) "you" else table.names[count.player]}.", Modifier.padding(top = 4.dp), size = 16.sp, weight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Carved("$running", 30.sp)
                Txt(if (step.items >= count.score.items.size) "  points" else "  so far", size = 14.sp)
                Spacer(Modifier.weight(1f))
                if (!auto && !tutor) WoodButton("Count it all", {
                    onStep(ShowStep(show.counts.size - 1, show.counts.last().score.items.size))
                }, size = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            val last = step.count == show.counts.size - 1 && step.items >= count.score.items.size
            WoodButton(if (last) (if (table.state.over) "See the result" else "Next deal") else if (step.items >= count.score.items.size) "Next" else "Count", ::next, Modifier.fillMaxWidth(), primary = true)
        }
    }
}

@Composable
private fun GameOverOverlay(model: AppModel, table: Table) {
    val ui = LocalUi.current
    val s = table.state
    val won = s.winner == table.me
    LaunchedEffect(s.seq) { delay(700); model.recordResult(table) }
    var time by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { val start = withFrameNanos { it }; while (true) withFrameNanos { time = (it - start) / 1_000_000 } }
    Box(Modifier.fillMaxSize()) {
        Scrim {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                WoodSign(if (won) "You win!" else "${table.names[s.winner ?: 0]} wins", size = 30.sp)
                Spacer(Modifier.height(10.dp))
                Parchment(Modifier.widthIn(max = 420.dp)) {
                    val skunk = when (s.skunk) { 2 -> "A double skunk!"; 1 -> "A skunk!"; else -> null }
                    skunk?.let { Hand(if (won) "$it Counts as ${s.skunk + 1} games." else "$it Ouch.", size = 22.sp, weight = FontWeight.Bold) }
                    Hand("${table.names[table.me]}: ${s.scores[table.me]}    ${table.names[table.opponent]}: ${s.scores[table.opponent]}", size = 20.sp)
                    Hand(if (won) "Wicked good game. That'll do it!" else "Some close. Go again?", size = 17.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (table is LocalTable) WoodButton("Play again", { model.newSoloGame(table.difficulty) }, Modifier.weight(1f), primary = true)
                        else if (table is OnlineTable) WoodButton("Rematch", {
                            model.online.invite(table.view.players[table.opponent].playerId, { model.toast = it }) { model.toast = "Rematch invitation sent." }
                        }, Modifier.weight(1f), primary = true)
                        WoodButton(if (table.online) "Friends" else "Home", { model.leaveGame() }, Modifier.weight(1f))
                    }
                }
            }
        }
        if (won && model.settings.ambient) Canvas(Modifier.fillMaxSize()) {
            fireworks(time / 1000f, density, 2f)
            particles(com.geoguy89.cribbage.theme.Particles.CONFETTI, time, density, 0.6f)
        }
    }
}

@Composable
private fun Sheet(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scrim(onClose) {
        Parchment(Modifier.padding(16.dp).widthIn(max = 560.dp).heightIn(max = 640.dp).clickable(enabled = false) {}) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Hand(title, Modifier.weight(1f), size = 22.sp, weight = FontWeight.Bold)
                WoodButton("Close", onClose, size = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.verticalScrollCompat()) { content() }
        }
    }
}

@Composable
private fun LogSheet(table: Table, onClose: () -> Unit) {
    Sheet("Game log", onClose) {
        val entries = table.state.log.reversed()
        if (entries.isEmpty()) Hand("Nothing scored yet.", size = 16.sp)
        var lastHand = -1
        for (e in entries) {
            if (e.hand != lastHand) { lastHand = e.hand; Txt("Hand ${e.hand}", Modifier.padding(top = 8.dp), size = 13.sp, weight = FontWeight.Bold) }
            val who = if (e.player == table.me) "You" else table.names[e.player]
            val where = if (e.count > 0) " (count ${e.count})" else ""
            Hand("$who: ${e.item.label}$where" + if (e.item.cards.isNotEmpty() && e.count == 0) " · ${e.item.cards.joinToString(" ")}" else "", size = 15.sp)
        }
    }
}

@Composable
fun RulesSheet(onClose: () -> Unit) {
    Sheet("How to play cribbage", onClose) { RulesText() }
}

@Composable
fun RulesText() {
    for ((h, b) in RULES) {
        Txt(h, Modifier.padding(top = 10.dp), size = 16.sp, weight = FontWeight.SemiBold)
        Hand(b, size = 15.sp)
    }
}

val RULES = listOf(
    "The goal" to "Be first to peg 121 holes. Two players, a standard deck. Each player has two pegs; the back one leapfrogs the front one each time you score, so you can always see your last score.",
    "The deal" to "Low card deals first. The dealer gives six cards each. Both players throw two cards face down into the crib, which belongs to the dealer. The deal changes hands every hand.",
    "The starter" to "The non-dealer (the pone) cuts and the dealer turns up the top card: the starter. If it's a Jack, the dealer pegs 2 for his heels.",
    "The play (pegging)" to "Starting with the pone, take turns laying a card face up, calling out the running total. It can't go past 31. Score 2 for making exactly 15 or 31; 2 for a pair with the last card (6 for three of a kind, 12 for four); and for a run of three or more among the last cards, one per card, in any order.",
    "Go" to "If you can't play without passing 31, say Go. Your opponent keeps playing while they can. The last player to lay a card scores 1 (2 if they hit 31 exactly), then the count starts again at zero. The last card of the hand scores 1.",
    "The show" to "Hands are counted in order: pone's hand, dealer's hand, dealer's crib. Each uses the starter as a fifth card. Score 2 for every combination adding to 15, 2 for each pair, 1 per card for runs, 4 for a four-card flush in hand (5 if the starter matches; the crib needs all five), and 1 for his nobs: the Jack of the starter's suit.",
    "Winning" to "The game ends the moment someone reaches 121, even mid-hand. Win before your opponent reaches 91 and it's a skunk (two games); under 61, a double skunk.",
    "Maine table talk" to "\"Fifteen two, fifteen four, and a pair is six.\" \"Nineteen\" means a hand worth nothing (you can't actually score 19). And if you see a hand of 29, call the newspaper.",
)

@Composable
private fun MenuSheet(model: AppModel, table: Table, onClose: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Sheet("Menu", onClose) {
        WoodButton("Back to the game", onClose, Modifier.fillMaxWidth(), primary = true)
        Spacer(Modifier.height(8.dp))
        if (table is LocalTable) {
            WoodButton("New game", { onClose(); model.newSoloGame(table.difficulty) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        if (!table.state.over) {
            WoodButton(if (confirm) "Tap again to resign" else "Resign", { if (confirm) { table.resign(); onClose() } else confirm = true }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        WoodButton(if (table.online) "Back to Friends (game keeps)" else "Save & quit", { onClose(); model.leaveGame() }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun TalkSheet(table: Table, onClose: () -> Unit) {
    Sheet("Table talk", onClose) {
        EMOTES.chunked(2).forEachIndexed { r, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                pair.forEachIndexed { k, text -> WoodButton(text, { table.sendEmote(r * 2 + k); onClose() }, Modifier.weight(1f), size = 13.sp) }
            }
        }
    }
}

@Composable
fun Toast(text: String, modifier: Modifier = Modifier) {
    AnimatedVisibility(true, modifier, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.padding(horizontal = 24.dp).plank(LocalUi.current.wood, 77, 10.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Txt(text, size = 15.sp, align = TextAlign.Center, weight = FontWeight.Medium)
        }
    }
}

/**
 * The board at full size. On a phone held upright it's turned sideways, so it can use the screen's height and the
 * holes and pegs are big enough to follow. Tap anywhere to close.
 */
@Composable
private fun BoardZoom(model: AppModel, scores: List<Int>, prev: List<Int>, onClose: () -> Unit) {
    val ui = LocalUi.current
    BoxWithConstraints(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = 0.75f)) }
        .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null, onClick = onClose), contentAlignment = Alignment.Center) {
        val turn = maxHeight > maxWidth && model.board.aspect > 1.05f
        val w = if (turn) maxHeight else maxWidth
        val h = if (turn) maxWidth else maxHeight
        Box(Modifier.requiredSize(w, h).graphicsLayer { rotationZ = if (turn) 90f else 0f }.padding(12.dp), contentAlignment = Alignment.Center) {
            BoardView(model.board, scores, prev, Modifier, pegs = model.settings.pegs, ambient = model.settings.ambient)
        }
        Txt("Tap to close", Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), size = 14.sp, color = ui.onTable, shadow = true)
    }
}

/** The full tutor's note: what just happened, explained, with Continue to move on. */
@Composable
private fun TutorCard(note: com.geoguy89.cribbage.game.Note, more: Int, compact: Boolean, onContinue: () -> Unit) {
    Parchment(Modifier.fillMaxWidth().padding(vertical = 4.dp), pins = !compact) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Hand("tutor · " + note.title, Modifier.weight(1f), size = 17.sp, weight = FontWeight.Bold, color = Color(0xFF6B3A1E))
            if (more > 0) Hand("$more more", size = 13.sp, color = Color(0xFF8A6A4A))
        }
        Column(Modifier.heightIn(max = if (compact) 150.dp else 320.dp).verticalScrollCompat().padding(vertical = 4.dp)) {
            for (line in note.lines) Hand(line, Modifier.padding(vertical = 2.dp), size = if (compact) 15.sp else 16.sp, color = Color(0xFF2E2014))
        }
        WoodButton("Continue ▸", onContinue, Modifier.fillMaxWidth(), primary = true, size = 15.sp)
    }
}
