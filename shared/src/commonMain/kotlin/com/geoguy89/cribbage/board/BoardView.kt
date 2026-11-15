package com.geoguy89.cribbage.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import com.geoguy89.cribbage.gfx.Carving.peg
import com.geoguy89.cribbage.gfx.Sil
import com.geoguy89.cribbage.resources.Res
import com.geoguy89.cribbage.resources.cinzel
import com.geoguy89.cribbage.resources.kalam
import com.geoguy89.cribbage.resources.oswald
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.Font
import kotlin.math.PI
import kotlin.math.sin

/** Peg colours to choose from. */
enum class PegSet(val title: String, val colors: List<Color>) {
    CLASSIC("Red & blue", listOf(Color(0xFFC62828), Color(0xFF1E5AA8))),
    BRASS("Brass & pewter", listOf(Color(0xFFC9A13B), Color(0xFF9AA3AB))),
    WOODS("Moss & maple", listOf(Color(0xFF3F7D3A), Color(0xFFD9822B))),
    LOBSTER("Lobster & buoy", listOf(Color(0xFFD9442B), Color(0xFFF2C230))),
}

@Composable
fun rememberBoardFonts(): BoardFonts {
    val title = FontFamily(Font(Res.font.cinzel, FontWeight.Normal), Font(Res.font.cinzel, FontWeight.Bold))
    val hand = FontFamily(Font(Res.font.kalam))
    val label = FontFamily(Font(Res.font.oswald, FontWeight.Normal), Font(Res.font.oswald, FontWeight.Medium), Font(Res.font.oswald, FontWeight.SemiBold), Font(Res.font.oswald, FontWeight.Bold))
    return remember(title, hand, label) { BoardFonts(title, hand, label) }
}

private class Painted(val image: ImageBitmap, val waters: List<WaterArea>)

/** Boards already painted this session, so the board list and the game don't paint them again. Main thread only. */
private object BoardCache {
    private val map = LinkedHashMap<String, Painted>()
    operator fun get(key: String): Painted? = map[key]
    operator fun set(key: String, value: Painted) {
        map.remove(key)
        map[key] = value
        while (map.size > 24) map.remove(map.keys.first())
    }
}

/**
 * A board with both players' pegs. When a score goes up, the back peg hops past the front one to the new hole, the
 * way it's done on a real board. [ambient] adds fish jumping in the water.
 */
@Composable
fun BoardView(
    design: BoardDesign,
    scores: List<Int>,
    prevScores: List<Int>,
    modifier: Modifier = Modifier,
    pegs: PegSet = PegSet.CLASSIC,
    ambient: Boolean = true,
    animatePegs: Boolean = true,
    onPegLanded: () -> Unit = {},
    /** Flags with each player's score beside their front peg. */
    showScores: Boolean = true,
    /** Called for a tap on the board (when no peg is waiting to be moved). */
    onTap: (() -> Unit)? = null,
    /** Peg it yourself: (player, score) of a peg waiting to be moved by hand. The hole glows until it's tapped. */
    pegTarget: Pair<Int, Int>? = null,
    /** A tap while a peg is waiting: true if it was on (or near) the right hole. */
    onPegTap: (Boolean) -> Unit = {},
) {
    BoxWithConstraints(modifier.aspectRatio(design.aspect)) {
        val density = LocalDensity.current
        val px = constraints.maxWidth.coerceAtLeast(1)
        val measurer = rememberTextMeasurer(cacheSize = 64)
        // The score flags get their own measurer: the board's is busy on another thread while it paints.
        val flagMeasurer = rememberTextMeasurer(cacheSize = 8)
        val fonts = rememberBoardFonts()
        val cacheKey = "${design.id}@$px@${density.density}@${density.fontScale}"
        val painted by produceState(BoardCache[cacheKey], design, px, fonts) {
            value = BoardCache[cacheKey]
            if (value == null) {
                val base = withContext(Dispatchers.Default) { renderBoardBase(design, px, measurer, fonts, density) }
                // Back on the main thread for the lettering.
                value = Painted(base.letter(), base.waters).also { BoardCache[cacheKey] = it }
            }
        }
        val geometry = design.geometry
        val target by rememberUpdatedState(pegTarget)
        val tapped by rememberUpdatedState(onTap)
        val pegTapped by rememberUpdatedState(onPegTap)
        val beckon by rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(850), RepeatMode.Reverse))
        // Each player's two pegs, as fractional scores so they can glide; plus how high each is lifted.
        val pegPos = remember(design) { List(2) { p -> listOf(Animatable(prevScores.getOrElse(p) { -1 }.toFloat()), Animatable(scores.getOrElse(p) { 0 }.toFloat())) } }
        val lift = remember(design) { List(2) { List(2) { Animatable(0f) } } }
        // The holes a player has just pegged glow for a moment, and a ring spreads from where the peg lands.
        val gain = remember(design) { List(2) { mutableStateOf(0 to 0) } }
        val gainGlow = remember(design) { List(2) { Animatable(0f) } }
        val ring = remember(design) { List(2) { Animatable(1f) } }
        for (p in 0..1) {
            val score = scores.getOrElse(p) { 0 }
            val prev = prevScores.getOrElse(p) { -1 }
            LaunchedEffect(design, score, prev) {
                val a = pegPos[p][0]; val b = pegPos[p][1]
                val front = if (a.value >= b.value) 0 else 1
                val frontVal = pegPos[p][front].value
                when {
                    score.toFloat() == frontVal -> Unit
                    score > frontVal && animatePegs -> {
                        // The back peg leapfrogs to the new score.
                        val mover = 1 - front
                        val holes = (score - pegPos[p][mover].value).coerceAtLeast(1f)
                        val ms = (260 + holes * 70).toInt().coerceAtMost(1700)
                        val up = lift[p][mover]
                        gain[p].value = frontVal.toInt() to score
                        gainGlow[p].snapTo(1f)
                        up.animateTo(1f, tween(140))
                        pegPos[p][mover].animateTo(score.toFloat(), tween(ms, easing = FastOutSlowInEasing))
                        up.animateTo(0f, tween(160))
                        onPegLanded()
                        ring[p].snapTo(0f)
                        launch { ring[p].animateTo(1f, tween(700)) }
                        gainGlow[p].animateTo(0f, tween(1800, delayMillis = 900))
                    }
                    else -> {
                        // A new game or a jump backwards: set both pegs straight away.
                        pegPos[p][0].snapTo(prev.toFloat()); pegPos[p][1].snapTo(score.toFloat())
                    }
                }
            }
        }
        var time by remember { mutableLongStateOf(0L) }
        if (ambient) LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { time = (it - start) / 1_000_000 }
        }
        val taps = if (onTap == null && pegTarget == null) Modifier else Modifier.pointerInput(design) {
            detectTapGestures { pos ->
                val t = target
                if (t == null) tapped?.invoke()
                else {
                    val at = geometry.position(t.first, t.second) * (size.width / 100f)
                    // Generous: a fingertip on a phone covers a good few holes.
                    pegTapped((pos - at).getDistance() <= maxOf(size.width * 0.09f, 22.dp.toPx()))
                }
            }
        }
        Canvas(Modifier.fillMaxSize().then(taps)) {
            val ready = painted ?: return@Canvas
            val img = ready.image
            val waters = ready.waters
            drawImage(img, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
            val k = size.width / 100f
            withTransform({ scale(k, k, Offset.Zero) }) {
                if (ambient) for ((i, w) in waters.withIndex()) wildlife(w, time, i)
                val r = design.trackSpec.holeR * 1.6f
                for (p in 0..1) {
                    val glow = gainGlow[p].value
                    if (glow > 0.01f) {
                        val (from, to) = gain[p].value
                        for (hole in (from + 1).coerceAtLeast(1)..to.coerceAtMost(120)) {
                            val at = geometry.holes[p][hole]
                            drawCircle(Color(0xFFFFE08A).copy(alpha = 0.35f * glow), r * 1.5f, at)
                            drawCircle(pegs.colors[p].copy(alpha = 0.85f * glow), r * 0.62f, at)
                        }
                    }
                    val rv = ring[p].value
                    if (rv < 0.99f) {
                        val at = geometry.position(p, maxOf(pegPos[p][0].value, pegPos[p][1].value))
                        drawCircle(Color.White.copy(alpha = 0.8f * (1f - rv)), r * (1.2f + 3.2f * rv), at, style = Stroke(0.35f * (1f - rv) + 0.08f))
                        drawCircle(pegs.colors[p].copy(alpha = 0.5f * (1f - rv)), r * (1.0f + 2.2f * rv), at, style = Stroke(0.3f))
                    }
                }
                // Peg it yourself: the holes to count light up, and the one to move to beckons.
                target?.let { (p, to) ->
                    val from = maxOf(pegPos[p][0].value, pegPos[p][1].value).toInt()
                    for (hole in (from + 1)..to) {
                        val at = geometry.position(p, hole)
                        drawCircle(Color(0xFFFFE08A).copy(alpha = 0.55f), r * 1.25f, at)
                        drawCircle(pegs.colors[p].copy(alpha = 0.9f), r * 0.55f, at)
                    }
                    val at = geometry.position(p, to)
                    drawCircle(Color(0xFFFFE08A).copy(alpha = 0.30f + 0.25f * beckon), r * (2.6f + 1.6f * beckon), at)
                    drawCircle(Color.White.copy(alpha = 0.95f), r * (2.0f + 1.4f * beckon), at, style = Stroke(0.45f))
                    drawCircle(pegs.colors[p], r * (1.5f + 0.9f * beckon), at, style = Stroke(0.35f))
                }
                // A peg on the move hops from hole to hole.
                fun hop(v: Float, up: Float): Float = up * (0.45f + 0.55f * kotlin.math.abs(sin(v * PI)).toFloat())
                for (p in 0..1) {
                    val front = if (pegPos[p][0].value >= pegPos[p][1].value) 0 else 1
                    // The back peg (the previous score) is smaller and faded, so the front one clearly leads.
                    val back = 1 - front
                    peg(geometry.position(p, pegPos[p][back].value), r * 0.8f, pegs.colors[p].copy(alpha = 0.6f), hop(pegPos[p][back].value, lift[p][back].value))
                    peg(geometry.position(p, pegPos[p][front].value), r, pegs.colors[p], hop(pegPos[p][front].value, lift[p][front].value))
                }
                if (showScores) for (p in 0..1) {
                    // A small flag in the player's colour beside the front peg, with the score on it.
                    val score = scores.getOrElse(p) { 0 }.coerceIn(0, 121)
                    val fv = maxOf(pegPos[p][0].value, pegPos[p][1].value)
                    val at = geometry.position(p, fv)
                    val i = fv.toInt().coerceIn(1, 120)
                    val side = if (p == 0) 1f else -1f
                    val out = geometry.normal[i] * (design.trackSpec.laneGap * 1.15f * side)
                    val c = at + out
                    val label = flagMeasurer.measure(score.toString(), androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = (2.3f * k / (this.density * this.fontScale)).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontFamily = fonts.label))
                    val w = label.size.width / k + 1.6f
                    val h = label.size.height / k + 0.2f
                    drawRoundRect(Color.Black.copy(alpha = 0.35f), c - Offset(w / 2 - 0.25f, h / 2 - 0.35f), androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
                    drawRoundRect(pegs.colors[p], c - Offset(w / 2, h / 2), androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
                    drawRoundRect(Color.White.copy(alpha = 0.8f), c - Offset(w / 2, h / 2), androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2), style = Stroke(0.25f))
                    withTransform({ scale(1f / k, 1f / k, Offset.Zero) }) {
                        drawText(label, topLeft = Offset(c.x * k - label.size.width / 2f, c.y * k - label.size.height / 2f))
                    }
                }
            }
        }
    }
}

/** Fish jumping and ripples spreading in a patch of water, on a loop that never quite repeats. */
private fun DrawScope.wildlife(w: WaterArea, timeMs: Long, index: Int) {
    val b = w.bounds
    clipPath(w.path) {
        // Ripples: three rings drifting about.
        for (k in 0 until 3) {
            val period = 4200L + k * 1300L + index * 700L
            val t = ((timeMs + k * 1700L) % period) / period.toFloat()
            val cycle = (timeMs + k * 1700L) / period
            val rx = pseudo(cycle * 7 + k + index * 13)
            val ry = pseudo(cycle * 11 + k * 3 + index * 5)
            val c = Offset(b.left + b.width * (0.2f + 0.6f * rx), b.top + b.height * (0.2f + 0.6f * ry))
            for (ring in 0..1) {
                val rt = (t - ring * 0.18f).coerceIn(0f, 1f)
                if (rt <= 0f) continue
                drawOval(Color.White.copy(alpha = 0.35f * (1f - rt)), c - Offset(rt * 3.2f, rt * 1.4f), androidx.compose.ui.geometry.Size(rt * 6.4f, rt * 2.8f), style = Stroke(0.18f))
            }
        }
    }
    // A fish jumps every nine seconds or so (it may leave the water, so this isn't clipped).
    val period = 9000L + index * 2300L
    val cycle = timeMs / period
    val t = (timeMs % period) / 1100f
    if (t in 0f..1f) {
        val x0 = b.left + b.width * (0.25f + 0.5f * pseudo(cycle.toInt() * 17 + index))
        val y0 = b.top + b.height * (0.35f + 0.4f * pseudo(cycle.toInt() * 29 + index))
        val dir = if (pseudo(cycle.toInt() * 5 + index) > 0.5f) 1f else -1f
        val span = 5f
        val x = x0 + dir * span * t
        val y = y0 - sin(t * PI).toFloat() * 3.4f
        val angle = (-60f + 120f * t) * dir
        val s = 3.2f
        withTransform({
            rotate(angle, Offset(x, y))
        }) {
            com.geoguy89.cribbage.gfx.Carving.run {
                engrave(Sil.TROUT, Rect(x - s / 2, y - s / 2, x + s / 2, y + s / 2), com.geoguy89.cribbage.gfx.Wood.WALNUT, 0.95f, flip = dir < 0, fill = Color(0xFF3C4B3A), shine = false)
            }
        }
        // Splashes where it left and where it lands.
        for (sp in listOf(Offset(x0, y0) to t, Offset(x0 + dir * span, y0) to (t - 0.8f) * 5f)) {
            val st = sp.second.coerceIn(0f, 1f)
            if (st in 0.01f..0.99f) drawOval(Color.White.copy(alpha = 0.6f * (1f - st)), sp.first - Offset(st * 2.4f, st * 0.9f), androidx.compose.ui.geometry.Size(st * 4.8f, st * 1.8f), style = Stroke(0.22f))
        }
    }
}

/** A repeatable 0-1 number for [n]. */
internal fun pseudo(n: Long): Float = pseudo(n.toInt())
internal fun pseudo(n: Int): Float {
    var x = n * 374761393 + 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFFFF) / 16777216f
}
