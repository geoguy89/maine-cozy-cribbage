package com.geoguy89.cribbage.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.unit.IntSize
import com.geoguy89.cribbage.gfx.Carving.peg
import com.geoguy89.cribbage.gfx.Sil
import com.geoguy89.cribbage.resources.Res
import com.geoguy89.cribbage.resources.cinzel
import com.geoguy89.cribbage.resources.kalam
import com.geoguy89.cribbage.resources.oswald
import kotlinx.coroutines.Dispatchers
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
) {
    BoxWithConstraints(modifier.aspectRatio(design.aspect)) {
        val density = LocalDensity.current
        val px = constraints.maxWidth.coerceAtLeast(1)
        val measurer = rememberTextMeasurer(cacheSize = 64)
        val fonts = rememberBoardFonts()
        val painted by produceState<Painted?>(null, design, px, fonts) {
            value = withContext(Dispatchers.Default) {
                val (img, waters) = renderBoard(design, px, measurer, fonts, density)
                Painted(img, waters)
            }
        }
        val geometry = design.geometry
        // Each player's two pegs, as fractional scores so they can glide; plus how high each is lifted.
        val pegPos = remember(design) { List(2) { p -> listOf(Animatable(prevScores.getOrElse(p) { -1 }.toFloat()), Animatable(scores.getOrElse(p) { 0 }.toFloat())) } }
        val lift = remember(design) { List(2) { List(2) { Animatable(0f) } } }
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
                        val ms = (320 + holes * 22).toInt().coerceAtMost(1100)
                        val up = lift[p][mover]
                        up.animateTo(1f, tween(140))
                        pegPos[p][mover].animateTo(score.toFloat(), tween(ms, easing = FastOutSlowInEasing))
                        up.animateTo(0f, tween(160))
                        onPegLanded()
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
        Canvas(Modifier.fillMaxSize()) {
            val ready = painted ?: return@Canvas
            val img = ready.image
            val waters = ready.waters
            drawImage(img, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
            val k = size.width / 100f
            withTransform({ scale(k, k, Offset.Zero) }) {
                if (ambient) for ((i, w) in waters.withIndex()) wildlife(w, time, i)
                val r = design.trackSpec.holeR * 1.3f
                for (p in 0..1) for (j in 0..1) {
                    val pos = geometry.position(p, pegPos[p][j].value)
                    peg(pos, r, pegs.colors[p], lift[p][j].value)
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
