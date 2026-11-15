package com.geoguy89.cribbage.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.geoguy89.cribbage.board.pseudo
import com.geoguy89.cribbage.gfx.Carving.engrave
import com.geoguy89.cribbage.gfx.Carving.pinecone
import com.geoguy89.cribbage.gfx.Carving.woodGrain
import com.geoguy89.cribbage.gfx.Geo
import com.geoguy89.cribbage.gfx.Sil
import com.geoguy89.cribbage.gfx.Wood
import com.geoguy89.cribbage.theme.Backdrop
import com.geoguy89.cribbage.theme.Look
import com.geoguy89.cribbage.theme.Particles
import com.geoguy89.cribbage.theme.Props
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The table: a painted backdrop for the theme, the season's decorations, and particles drifting over it all. */
@Composable
fun Scenery(look: Look, modifier: Modifier = Modifier, animate: Boolean = true, content: @Composable () -> Unit = {}) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.coerceAtLeast(1)
        val h = constraints.maxHeight.coerceAtLeast(1)
        val density = LocalDensity.current
        val image by produceState<ImageBitmap?>(null, look, w, h) {
            value = withContext(Dispatchers.Default) { renderBackdrop(look, w, h, density.density) }
        }
        var time by remember { mutableLongStateOf(0L) }
        if (animate) LaunchedEffect(look) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { time = (it - start) / 1_000_000 }
        }
        Canvas(Modifier.fillMaxSize()) {
            val img = image
            if (img == null) drawRect(look.base.bg1) else drawImage(img, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
        }
        Canvas(Modifier.fillMaxSize()) { alive(look.base.backdrop, time, density.density) }
        Box(Modifier.fillMaxSize()) { content() }
        if (look.season != null) Canvas(Modifier.fillMaxSize()) {
            if (look.season.props == Props.LIGHTS) twinkleLights(time, density.density)
            particles(look.season.particles, time, density.density)
        }
    }
}

/** Paints the theme's table and the season's edge decorations into an image. */
fun renderBackdrop(look: Look, w: Int, h: Int, dp: Float): ImageBitmap {
    val img = ImageBitmap(w, h)
    CanvasDrawScope().draw(androidx.compose.ui.unit.Density(dp), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(img), Size(w.toFloat(), h.toFloat())) {
        val t = look.base
        when (t.backdrop) {
            Backdrop.PLANKS -> planks(t.tableWood, dp)
            Backdrop.PLAID -> plaid(t.bg1, t.bg2, dp)
            Backdrop.BIRCH -> birch(t.bg1, t.bg2, dp)
            Backdrop.HARBOR -> harbor(t.bg1, t.bg2, dp)
            Backdrop.NIGHT -> night(t.bg1, t.bg2, dp)
            Backdrop.SUNRISE -> sunrise(t.bg1, t.bg2, dp)
            Backdrop.BARRENS -> barrens(t.bg1, t.bg2, dp)
            Backdrop.CLAPBOARD -> clapboard(t.bg1, t.bg2, dp)
            Backdrop.FELT -> felt(t.bg1, t.bg2, t.tableWood, dp)
        }
        // A soft vignette pulls the eye to the middle.
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.38f)), center, size.maxDimension * 0.75f))
        look.season?.let { props(it.props, dp) }
    }
    return img
}

private fun DrawScope.planks(wood: Wood, dp: Float) {
    val plankW = (110 * dp).coerceAtMost(size.width / 3f)
    val n = (size.width / plankW).toInt() + 1
    for (i in 0 until n) {
        val x = i * plankW
        val shade = 0.85f + 0.3f * pseudo(i * 7 + 3)
        val rect = Rect(x, 0f, x + plankW, size.height)
        val base = lerp(wood.dark, wood.base, shade.coerceAtMost(1f))
        drawRect(Brush.horizontalGradient(listOf(lerp(base, wood.light, 0.15f), base, lerp(base, wood.dark, 0.25f)), rect.left, rect.right), rect.topLeft, rect.size)
        withTransform({ clipRect(rect.left, rect.top, rect.right, rect.bottom); scale(dp * 2f, dp * 2f, rect.topLeft) }) {
            woodGrain(Rect(rect.left, rect.top, rect.left + plankW / (dp * 2f), rect.top + size.height / (dp * 2f)), wood, i * 13 + 5, 90f)
        }
        drawLine(Color.Black.copy(alpha = 0.55f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.5f * dp)
        drawLine(lerp(wood.light, Color.White, 0.3f).copy(alpha = 0.25f), Offset(x + 2.5f * dp, 0f), Offset(x + 2.5f * dp, size.height), strokeWidth = 1f * dp)
        // Nails where the planks cross the joists.
        for (y in listOf(0.18f, 0.82f)) {
            val c = Offset(x + plankW / 2, size.height * y)
            drawCircle(Color.Black.copy(alpha = 0.45f), 3.2f * dp, c + Offset(dp, dp))
            drawCircle(Color(0xFF6E6E6E), 2.8f * dp, c)
            drawCircle(Color.White.copy(alpha = 0.35f), 1.1f * dp, c - Offset(dp, dp))
        }
    }
}

private fun DrawScope.plaid(red: Color, dark: Color, dp: Float) {
    drawRect(red)
    val s = 46 * dp
    var x = 0f
    var i = 0
    while (x < size.width) {
        if (i % 2 == 0) drawRect(dark.copy(alpha = 0.55f), Offset(x, 0f), Size(s, size.height))
        x += s; i++
    }
    var y = 0f
    i = 0
    while (y < size.height) {
        if (i % 2 == 0) drawRect(dark.copy(alpha = 0.55f), Offset(0f, y), Size(size.width, s))
        y += s; i++
    }
    // Twill: fine diagonal threads.
    var d = -size.height
    while (d < size.width) {
        drawLine(Color.Black.copy(alpha = 0.07f), Offset(d, 0f), Offset(d + size.height, size.height), strokeWidth = 1.2f * dp)
        d += 4 * dp
    }
    // Thin accent stripes, like a camp blanket.
    x = s * 0.5f
    while (x < size.width) { drawLine(Color(0xFFE8C46A).copy(alpha = 0.25f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.4f * dp); x += s * 4 }
    y = s * 0.5f
    while (y < size.height) { drawLine(Color(0xFFE8C46A).copy(alpha = 0.25f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.4f * dp); y += s * 4 }
}

private fun DrawScope.birch(light: Color, shade: Color, dp: Float) {
    drawRect(Brush.verticalGradient(listOf(light, shade)))
    val r = Random(7)
    repeat((size.width * size.height / (dp * dp * 900)).toInt().coerceAtMost(900)) {
        val x = r.nextFloat() * size.width
        val y = r.nextFloat() * size.height
        val len = (6 + r.nextFloat() * 34) * dp
        val thick = (1 + r.nextFloat() * 3) * dp
        drawRoundRect(Color(0xFF1E1A16).copy(alpha = 0.35f + r.nextFloat() * 0.5f), Offset(x, y), Size(len, thick), androidx.compose.ui.geometry.CornerRadius(thick))
    }
    // Peeling curls and the odd dark eye where a branch fell.
    repeat(10) {
        val c = Offset(r.nextFloat() * size.width, r.nextFloat() * size.height)
        val s = (14 + r.nextFloat() * 22) * dp
        val eye = Path().apply { moveTo(c.x - s, c.y); quadraticTo(c.x, c.y - s * 0.45f, c.x + s, c.y); quadraticTo(c.x, c.y + s * 0.45f, c.x - s, c.y) }
        drawPath(eye, Color(0xFF2A221A).copy(alpha = 0.55f))
        drawLine(Color(0xFFB98A5E).copy(alpha = 0.35f), c + Offset(-s * 1.4f, s * 0.9f), c + Offset(s * 1.2f, s * 0.9f), strokeWidth = 2f * dp, cap = StrokeCap.Round)
    }
}

private fun DrawScope.harbor(top: Color, bottom: Color, dp: Float) {
    drawRect(Brush.verticalGradient(listOf(lerp(top, Color.White, 0.25f), top, bottom)))
    val r = Random(3)
    // Distant islands with spruce tops, and a lighthouse on the point.
    val horizon = size.height * 0.62f
    val isle = Path().apply {
        moveTo(0f, horizon)
        var x = 0f
        while (x <= size.width) {
            val hh = (10 + 18 * pseudo((x / dp).toInt() / 14)) * dp
            lineTo(x, horizon - hh)
            lineTo(x + 7 * dp, horizon - hh - 9 * dp * pseudo((x / dp).toInt() / 7 + 1))
            x += 14 * dp
        }
        lineTo(size.width, horizon + 4 * dp); lineTo(0f, horizon + 4 * dp); close()
    }
    drawPath(isle, lerp(bottom, top, 0.45f).copy(alpha = 0.8f))
    engrave(Sil.LIGHTHOUSE, Rect(size.width * 0.78f, horizon - 48 * dp, size.width * 0.78f + 30 * dp, horizon + 2 * dp), Wood.DRIFTWOOD, 0.75f, fill = lerp(bottom, top, 0.2f), shine = false)
    // Water below: long glints.
    repeat(80) {
        val y = horizon + r.nextFloat() * (size.height - horizon)
        val x = r.nextFloat() * size.width
        drawLine(Color.White.copy(alpha = 0.05f + r.nextFloat() * 0.08f), Offset(x, y), Offset(x + (20 + r.nextFloat() * 60) * dp, y), strokeWidth = dp)
    }
    // Fog banks.
    repeat(6) { i ->
        val y = size.height * (0.15f + i * 0.14f)
        drawOval(Color.White.copy(alpha = 0.07f), Offset(-size.width * 0.2f + r.nextFloat() * size.width * 0.3f, y), Size(size.width * 1.1f, 60 * dp))
    }
}

private fun DrawScope.night(top: Color, bottom: Color, dp: Float) {
    drawRect(Brush.verticalGradient(listOf(bottom, top, lerp(top, Color(0xFF1B3A4B), 0.6f))))
    val r = Random(11)
    repeat(260) {
        val c = Offset(r.nextFloat() * size.width, r.nextFloat() * size.height * 0.7f)
        drawCircle(Color.White.copy(alpha = 0.25f + r.nextFloat() * 0.7f), (0.5f + r.nextFloat() * 1.3f) * dp, c)
    }
    // Northern lights: soft curtains of green and violet.
    for (band in 0..2) {
        val p = Path()
        val base = size.height * (0.18f + band * 0.07f)
        p.moveTo(0f, base)
        var x = 0f
        while (x <= size.width) { p.lineTo(x, base + sin(x / size.width * 6f + band) * 30 * dp); x += 8 * dp }
        drawPath(p, Brush.verticalGradient(listOf(Color.Transparent, (if (band == 1) Color(0xFF9C7BD6) else Color(0xFF5FE3A1)).copy(alpha = 0.22f), Color.Transparent), base - 70 * dp, base + 50 * dp), style = Stroke(70 * dp, cap = StrokeCap.Round))
    }
    // The far shore: a ragged line of spruce, mirrored in the lake.
    val shore = size.height * 0.78f
    var x = -10 * dp
    while (x < size.width + 20 * dp) {
        val hh = (18 + 30 * pseudo((x / dp).toInt() / 9 + 4)) * dp
        engrave(Sil.PINE, Rect(x, shore - hh, x + hh * 0.55f, shore), Wood.WALNUT, 1f, fill = Color(0xFF040A12), shine = false)
        engrave(Sil.PINE, Rect(x, shore, x + hh * 0.55f, shore + hh * 0.6f), Wood.WALNUT, 0.35f, fill = Color(0xFF07121F), shine = false)
        x += hh * 0.38f
    }
    drawRect(Color(0xFF0B1A2C).copy(alpha = 0.45f), Offset(0f, shore), Size(size.width, size.height - shore))
}

/** Where the sun sits in the sunrise backdrop. */
private fun DrawScope.sunriseSun() = Offset(size.width * 0.5f, size.height * 0.5f)

private fun DrawScope.sunrise(top: Color, glow: Color, dp: Float) {
    val horizon = size.height * 0.5f
    val sun = sunriseSun()
    drawRect(Brush.verticalGradient(listOf(top, lerp(top, Color(0xFFB04A6A), 0.75f), glow, Color(0xFFFFD08A)), 0f, horizon), Offset.Zero, Size(size.width, horizon))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF1C0).copy(alpha = 0.85f), Color(0xFFFFC46A).copy(alpha = 0.35f), Color.Transparent), sun, size.width * 0.55f), size.width * 0.55f, sun)
    // Long streaks of cloud catching the light from below.
    val r = Random(21)
    repeat(9) {
        val y = horizon * (0.12f + r.nextFloat() * 0.7f)
        val w = size.width * (0.25f + r.nextFloat() * 0.5f)
        val x = r.nextFloat() * size.width - w / 2
        drawOval(lerp(Color(0xFFFF9E7A), Color(0xFF5A3A6E), 1f - y / horizon).copy(alpha = 0.45f), Offset(x, y), Size(w, (6 + r.nextFloat() * 10) * dp))
    }
    drawCircle(Color(0xFFFFF4D2), 30 * dp, sun)
    // The bay, with the sun laid down across it.
    drawRect(Brush.verticalGradient(listOf(Color(0xFFE08A5A), Color(0xFF6A4A6E), Color(0xFF1C2A4A), Color(0xFF0E1830)), horizon, size.height), Offset(0f, horizon), Size(size.width, size.height - horizon))
    repeat(70) {
        val f = r.nextFloat()
        val y = horizon + f * (size.height - horizon) * 0.8f
        val spread = (20 + f * 120) * dp
        val x = sun.x + (r.nextFloat() - 0.5f) * spread
        drawLine(Color(0xFFFFE2A8).copy(alpha = 0.5f * (1f - f) + 0.08f), Offset(x, y), Offset(x + (10 + r.nextFloat() * 26) * dp, y), strokeWidth = 1.4f * dp, cap = StrokeCap.Round)
    }
    // The Porcupine Islands: spruce-backed humps on the water.
    for ((cx, w, h) in listOf(Triple(0.16f, 0.26f, 26f), Triple(0.42f, 0.16f, 16f), Triple(0.7f, 0.22f, 22f), Triple(0.93f, 0.2f, 30f))) {
        val l = size.width * (cx - w / 2); val rr = size.width * (cx + w / 2)
        val isle = Path().apply {
            moveTo(l, horizon + 3 * dp)
            var x = l
            while (x <= rr) {
                val t = (x - l) / (rr - l)
                val hump = sin(t * kotlin.math.PI).toFloat()
                lineTo(x, horizon - (h * hump + 5 * hump * pseudo((x / dp).toInt() / 5)) * dp)
                x += 5 * dp
            }
            lineTo(rr, horizon + 3 * dp); close()
        }
        drawPath(isle, Color(0xFF1A1830))
    }
    // The pink granite of the summit in the foreground, and a pair of pitch pines.
    val ledge = Path().apply {
        moveTo(0f, size.height); lineTo(0f, size.height * 0.9f)
        quadraticTo(size.width * 0.25f, size.height * 0.84f, size.width * 0.5f, size.height * 0.91f)
        quadraticTo(size.width * 0.8f, size.height * 0.97f, size.width, size.height * 0.88f)
        lineTo(size.width, size.height); close()
    }
    drawPath(ledge, Brush.verticalGradient(listOf(Color(0xFF8A5A5E), Color(0xFF3A2632)), size.height * 0.84f, size.height))
    engrave(Sil.PINE, Rect(size.width - 74 * dp, size.height * 0.88f - 96 * dp, size.width - 14 * dp, size.height * 0.88f + 8 * dp), Wood.WALNUT, 1f, fill = Color(0xFF14101E), shine = false)
    engrave(Sil.PINE, Rect(size.width - 112 * dp, size.height * 0.9f - 60 * dp, size.width - 72 * dp, size.height * 0.9f + 6 * dp), Wood.WALNUT, 1f, fill = Color(0xFF14101E), shine = false)
}

private fun DrawScope.barrens(crimson: Color, dark: Color, dp: Float) {
    val horizon = size.height * 0.3f
    drawRect(Brush.verticalGradient(listOf(Color(0xFF6E8CA8), Color(0xFFC9C3C0), Color(0xFFF0CDA8)), 0f, horizon), Offset.Zero, Size(size.width, horizon + 2f))
    // Far hills, blue with distance.
    for ((k, tone) in listOf(Color(0xFF6A7A98), Color(0xFF4E5578)).withIndex()) {
        val hill = Path().apply {
            moveTo(0f, horizon + 4 * dp)
            var x = 0f
            while (x <= size.width + 12 * dp) { lineTo(x, horizon - (14 - k * 6 + 16 * sin(x / size.width * (5f + k * 3f) + k * 2f)) * dp); x += 12 * dp }
            lineTo(size.width, horizon + 4 * dp); close()
        }
        drawPath(hill, tone)
    }
    // The barrens: bands of crimson, maroon and rust rolling away, each a little wavier than the last.
    drawRect(Brush.verticalGradient(listOf(lerp(crimson, Color(0xFFD9703A), 0.35f), crimson, dark), horizon, size.height), Offset(0f, horizon), Size(size.width, size.height - horizon))
    val tones = listOf(Color(0xFFB8402E), Color(0xFF8E1F3A), Color(0xFFC2622A), Color(0xFF6E1C3C), Color(0xFFA83232), Color(0xFF58183A))
    for (i in 0 until 11) {
        val f = i / 11f
        val y = horizon + (size.height - horizon) * f * f
        val band = Path().apply {
            moveTo(0f, y)
            var x = 0f
            while (x <= size.width + 16 * dp) { lineTo(x, y + sin(x / size.width * (3f + i) + i * 1.7f) * (4 + i * 2.2f) * dp); x += 16 * dp }
            lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        drawPath(band, tones[i % tones.size].copy(alpha = 0.5f))
    }
    val r = Random(31)
    // The low bushes themselves, thicker towards your feet, and berries still on a few.
    repeat(1400) {
        val f = r.nextFloat()
        val y = horizon + (size.height - horizon) * f * f
        val c = Offset(r.nextFloat() * size.width, y)
        val s = (0.8f + 3.2f * f) * dp
        drawCircle(tones[r.nextInt(tones.size)].copy(alpha = 0.55f), s, c)
        if (it % 9 == 0) drawCircle(Color(0xFF3A4590).copy(alpha = 0.85f), s * 0.6f, c + Offset(s, -s * 0.4f))
    }
    // Granite the glacier left behind, and a lone pine.
    repeat(7) {
        val f = 0.25f + r.nextFloat() * 0.7f
        val y = horizon + (size.height - horizon) * f * f
        val w = (10 + 46 * f) * dp
        val x = r.nextFloat() * size.width
        drawOval(Color.Black.copy(alpha = 0.3f), Offset(x - w * 0.05f, y + w * 0.3f), Size(w * 1.1f, w * 0.25f))
        drawOval(Brush.verticalGradient(listOf(Color(0xFFC9C5BE), Color(0xFF7A766F)), y - w * 0.15f, y + w * 0.4f), Offset(x, y - w * 0.15f), Size(w, w * 0.55f))
    }
    engrave(Sil.PINE, Rect(size.width * 0.12f, horizon - 44 * dp, size.width * 0.12f + 30 * dp, horizon + 8 * dp), Wood.WALNUT, 1f, fill = Color(0xFF1E2A2A), shine = false)
}

/** Where the buoys hang on the clapboard wall: x as a share of the width, and the buoy's colours. */
private val SHACK_BUOYS = listOf(
    Triple(0.10f, Color(0xFFD9442B), Color(0xFFF5F0E6)), Triple(0.26f, Color(0xFFF2C230), Color(0xFF1F5E8C)), Triple(0.42f, Color(0xFF2E7D5B), Color(0xFFF5F0E6)),
    Triple(0.58f, Color(0xFF1F5E8C), Color(0xFFE8862E)), Triple(0.74f, Color(0xFFF5F0E6), Color(0xFFD9442B)), Triple(0.90f, Color(0xFFE8862E), Color(0xFF26313A)),
)

private fun DrawScope.buoy(top: Offset, dp: Float, body: Color, stripe: Color, swing: Float = 0f) {
    withTransform({ rotate(swing, top) }) {
        drawLine(Color(0xFF8A7A5E), top, top + Offset(0f, 30 * dp), strokeWidth = 2f * dp)
        val c = top + Offset(0f, 30 * dp)
        drawRect(Color(0xFF6E5A3E), c + Offset(-2.5f * dp, 0f), Size(5 * dp, 10 * dp))
        val shape = Path().apply {
            moveTo(c.x - 11 * dp, c.y + 10 * dp); lineTo(c.x + 11 * dp, c.y + 10 * dp)
            quadraticTo(c.x + 12 * dp, c.y + 40 * dp, c.x, c.y + 58 * dp)
            quadraticTo(c.x - 12 * dp, c.y + 40 * dp, c.x - 11 * dp, c.y + 10 * dp); close()
        }
        drawPath(shape, Color.Black.copy(alpha = 0.25f), style = Stroke(5 * dp))
        drawPath(shape, body)
        clipPath(shape) {
            drawRect(stripe, Offset(c.x - 14 * dp, c.y + 22 * dp), Size(28 * dp, 9 * dp))
            drawRect(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.Transparent, Color.Black.copy(alpha = 0.3f)), c.x - 12 * dp, c.x + 12 * dp), Offset(c.x - 14 * dp, c.y), Size(28 * dp, 60 * dp))
        }
    }
}

private fun DrawScope.clapboard(light: Color, shade: Color, dp: Float) {
    val board = 46 * dp
    val r = Random(17)
    var y = 0f
    var i = 0
    while (y < size.height) {
        val tint = lerp(light, shade, 0.25f * pseudo(i * 5 + 1))
        drawRect(Brush.verticalGradient(listOf(lerp(tint, Color.White, 0.25f), tint, lerp(tint, shade, 0.5f)), y, y + board), Offset(0f, y), Size(size.width, board))
        // The shadow each board throws on the one below, and the worn paint along its edge.
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.38f), Color.Transparent), y, y + 7 * dp), Offset(0f, y), Size(size.width, 7 * dp))
        repeat(10) {
            val x = r.nextFloat() * size.width
            drawLine(Color(0xFF8A8478).copy(alpha = 0.18f), Offset(x, y + board * (0.2f + 0.6f * r.nextFloat())), Offset(x + (20 + r.nextFloat() * 90) * dp, y + board * (0.2f + 0.6f * r.nextFloat())), strokeWidth = dp)
        }
        // A butt joint here and there, with its two nails.
        val jx = size.width * pseudo(i * 11 + 2)
        drawLine(Color.Black.copy(alpha = 0.3f), Offset(jx, y + 4 * dp), Offset(jx, y + board), strokeWidth = 1.2f * dp)
        for (dx in listOf(-7f, 7f)) drawCircle(Color(0xFF7A7468).copy(alpha = 0.7f), 1.6f * dp, Offset(jx + dx * dp, y + board * 0.55f))
        y += board; i++
    }
    // A red trim board down the bottom.
    drawRect(Brush.verticalGradient(listOf(Color(0xFFB5322A), Color(0xFF7A1E1A)), size.height - 26 * dp, size.height), Offset(0f, size.height - 26 * dp), Size(size.width, 26 * dp))
    drawRect(Color.Black.copy(alpha = 0.3f), Offset(0f, size.height - 28 * dp), Size(size.width, 2 * dp))
}

private fun DrawScope.felt(green: Color, dark: Color, rail: Wood, dp: Float) {
    drawRect(Brush.radialGradient(listOf(lerp(green, Color.White, 0.12f), green, dark), center, size.maxDimension * 0.7f))
    // The nap of the cloth: thousands of short fibres.
    val r = Random(41)
    repeat((size.width * size.height / (dp * dp * 70)).toInt().coerceAtMost(9000)) {
        val p = Offset(r.nextFloat() * size.width, r.nextFloat() * size.height)
        val a = r.nextFloat() * 6.28f
        drawLine((if (it % 2 == 0) Color.White else Color.Black).copy(alpha = 0.05f), p, p + Offset(kotlin.math.cos(a), sin(a)) * (3 * dp), strokeWidth = dp * 0.8f)
    }
    // The table's wooden rail round the edge.
    val w = 16 * dp
    for (rect in listOf(Rect(0f, 0f, size.width, w), Rect(0f, size.height - w, size.width, size.height), Rect(0f, 0f, w, size.height), Rect(size.width - w, 0f, size.width, size.height))) {
        drawRect(Brush.linearGradient(listOf(rail.light, rail.base, rail.dark), rect.topLeft, rect.bottomRight), rect.topLeft, rect.size)
    }
    drawRect(Color.Black.copy(alpha = 0.45f), Offset(w, w), Size(size.width - 2 * w, size.height - 2 * w), style = Stroke(3 * dp))
    drawRect(rail.light.copy(alpha = 0.5f), Offset(1.5f * dp, 1.5f * dp), Size(size.width - 3 * dp, size.height - 3 * dp), style = Stroke(1.5f * dp))
}

/** The moving parts of a backdrop: stars that twinkle, gulls, a lighthouse lamp, buoys stirring in the breeze. */
private fun DrawScope.alive(b: Backdrop, time: Long, dp: Float) {
    val t = time / 1000f
    when (b) {
        Backdrop.NIGHT -> {
            val r = Random(5)
            repeat(46) {
                val c = Offset(r.nextFloat() * size.width, r.nextFloat() * size.height * 0.62f)
                val tw = 0.5f + 0.5f * sin(t * (0.8f + r.nextFloat() * 1.6f) + it * 2.1f)
                drawCircle(Color.White.copy(alpha = 0.75f * tw), (1f + r.nextFloat() * 1.2f) * dp, c)
            }
            // The northern lights breathe.
            val glow = 0.5f + 0.5f * sin(t * 0.35f)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF5FE3A1).copy(alpha = 0.10f * glow), Color(0xFF9C7BD6).copy(alpha = 0.07f * (1f - glow)), Color.Transparent), size.height * 0.05f, size.height * 0.45f), Offset(0f, size.height * 0.05f), Size(size.width, size.height * 0.4f))
            // A shooting star now and then.
            val cycle = (time / 13000L).toInt()
            val st = (time % 13000L) / 700f
            if (time > 0 && st in 0f..1f) {
                val a = Offset(size.width * (0.15f + 0.6f * pseudo(cycle * 7 + 1)), size.height * (0.05f + 0.25f * pseudo(cycle * 13 + 2)))
                val d = Offset(140 * dp, 60 * dp)
                val head = a + d * st
                drawLine(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.9f * (1f - st))), head - d * 0.35f, head), head - d * 0.35f, head, strokeWidth = 1.6f * dp, cap = StrokeCap.Round)
            }
        }
        Backdrop.SUNRISE -> {
            val sun = sunriseSun()
            val pulse = 0.5f + 0.5f * sin(t * 0.5f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF1C0).copy(alpha = 0.16f + 0.12f * pulse), Color.Transparent), sun, (120 + 30 * pulse) * dp), (120 + 30 * pulse) * dp, sun)
            // Glints coming and going on the water.
            for (i in 0 until 14) {
                val f = pseudo(i * 17 + 3)
                val a = (sin(t * (1.2f + f * 2f) + i) * 0.5f + 0.5f)
                val y = size.height * (0.52f + 0.3f * pseudo(i * 5 + 1))
                val x = sun.x + (pseudo(i * 29 + 7) - 0.5f) * (40 + 220 * (y / size.height - 0.5f) * 2f) * dp
                drawLine(Color(0xFFFFF1C0).copy(alpha = 0.55f * a), Offset(x, y), Offset(x + 16 * dp, y), strokeWidth = 1.5f * dp, cap = StrokeCap.Round)
            }
            // Gulls working along the shore.
            for (i in 0 until 3) {
                val period = 26f + i * 9f
                val p = ((t + i * 11f) % period) / period
                val x = if (i % 2 == 0) -30 * dp + (size.width + 60 * dp) * p else size.width + 30 * dp - (size.width + 60 * dp) * p
                val y = size.height * (0.16f + 0.1f * i) + sin(t * 0.7f + i * 2f) * 8 * dp
                val flap = sin(t * (5f + i)) * 3.5f * dp
                val s = (9 - i * 1.5f) * dp
                val wing = Path().apply {
                    moveTo(x - s, y - flap); quadraticTo(x - s * 0.45f, y - s * 0.55f - flap, x, y)
                    quadraticTo(x + s * 0.45f, y - s * 0.55f - flap, x + s, y - flap)
                }
                drawPath(wing, Color(0xFF1A1830).copy(alpha = 0.8f), style = Stroke(1.6f * dp, cap = StrokeCap.Round))
            }
        }
        Backdrop.HARBOR -> {
            // The light on the point, turning.
            val horizon = size.height * 0.62f
            val lamp = Offset(size.width * 0.78f + 15 * dp, horizon - 41 * dp)
            val beat = (sin(t * 1.3f) * 0.5f + 0.5f).let { it * it * it }
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF3C4).copy(alpha = 0.9f * beat), Color.Transparent), lamp, (10 + 60 * beat) * dp), (10 + 60 * beat) * dp, lamp)
            // Sea-smoke drifting through.
            for (i in 0 until 3) {
                val period = 70f + i * 23f
                val p = ((t + i * 31f) % period) / period
                drawOval(Color.White.copy(alpha = 0.06f), Offset(-size.width * 0.8f + size.width * 1.8f * p, size.height * (0.2f + 0.22f * i)), Size(size.width * 0.8f, 70 * dp))
            }
        }
        Backdrop.BARRENS -> {
            // Cloud shadows crossing the barrens.
            for (i in 0 until 2) {
                val period = 55f + i * 21f
                val p = ((t + i * 19f) % period) / period
                drawOval(Color.Black.copy(alpha = 0.10f), Offset(-size.width * 0.6f + size.width * 1.6f * p, size.height * (0.42f + 0.25f * i)), Size(size.width * 0.6f, (60 + 50 * i) * dp))
            }
        }
        Backdrop.CLAPBOARD -> for ((i, b3) in SHACK_BUOYS.withIndex()) {
            buoy(Offset(size.width * b3.first, 0f), dp, b3.second, b3.third, swing = sin(t * 0.9f + i * 1.3f) * 2.2f)
        }
        else -> Unit
    }
}

// ---- seasonal decorations ------------------------------------------------------------------------------------------

private fun DrawScope.props(p: Props, dp: Float) {
    val s = minOf(size.width, size.height)
    val u = (s / 11f).coerceAtMost(70 * dp)
    fun corner(left: Boolean, top: Boolean, inset: Float = 0.2f) = Offset(if (left) u * (0.5f + inset) else size.width - u * (1.5f + inset), if (top) u * (0.4f + inset) else size.height - u * (1.4f + inset))
    fun sil(s: Sil, at: Offset, scale: Float, color: Color, angle: Float = 0f) {
        rotate(angle, at + Offset(u * scale / 2, u * scale / 2)) {
            engrave(s, Rect(at.x, at.y, at.x + u * scale, at.y + u * scale), Wood.MAPLE, 1f, fill = color, shine = false)
        }
    }
    when (p) {
        Props.NONE, Props.LIGHTS -> Unit
        Props.WREATH -> Unit
        Props.PUMPKINS -> {
            sil(Sil.PUMPKIN, corner(true, false), 1.3f, Color(0xFFE07B24))
            sil(Sil.PUMPKIN, corner(false, false) + Offset(u * 0.1f, 0f), 1.0f, Color(0xFFD06A1A))
            sil(Sil.PUMPKIN, corner(false, false) + Offset(-u * 0.75f, u * 0.25f), 0.8f, Color(0xFFE38A33))
        }
        Props.GOURDS -> {
            sil(Sil.PUMPKIN, corner(true, false), 1.1f, Color(0xFFCB8A3A))
            sil(Sil.OAK_LEAF, corner(true, false) + Offset(u * 0.8f, u * 0.2f), 0.8f, Color(0xFFA2501E), 30f)
            sil(Sil.PUMPKIN, corner(false, false), 1.0f, Color(0xFFDFC07A))
            sil(Sil.OAK_LEAF, corner(false, false) + Offset(-u * 0.6f, u * 0.2f), 0.8f, Color(0xFFB8641E), -20f)
        }
        Props.BUNTING -> {
            // Red, white and blue swags along the top.
            val swags = 4
            for (i in 0 until swags) {
                val x0 = size.width * i / swags; val x1 = size.width * (i + 1) / swags
                val fan = Path().apply { moveTo(x0, 0f); quadraticTo((x0 + x1) / 2, u * 1.4f, x1, 0f); close() }
                drawPath(fan, Color(0xFFB22234))
                val mid = Path().apply { moveTo(x0, 0f); quadraticTo((x0 + x1) / 2, u * 0.95f, x1, 0f); close() }
                drawPath(mid, Color(0xFFF5F2EA))
                val inner = Path().apply { moveTo(x0, 0f); quadraticTo((x0 + x1) / 2, u * 0.5f, x1, 0f); close() }
                drawPath(inner, Color(0xFF2B4B8C))
                drawCircle(Color(0xFFE5C04B), u * 0.12f, Offset(x0, u * 0.05f))
            }
        }
        Props.HEARTS -> {
            sil(Sil.HEART, corner(true, false), 1.0f, Color(0xFFD94A6A), -15f)
            sil(Sil.HEART, corner(true, false) + Offset(u * 0.8f, u * 0.3f), 0.6f, Color(0xFFF08AA0), 10f)
            sil(Sil.HEART, corner(false, false), 1.0f, Color(0xFFD94A6A), 15f)
        }
        Props.SHAMROCKS -> {
            sil(Sil.SHAMROCK, corner(true, false), 1.1f, Color(0xFF3E9A55), -10f)
            sil(Sil.SHAMROCK, corner(false, false), 1.0f, Color(0xFF2F7F44), 12f)
            sil(Sil.PINE, corner(false, true), 0.9f, Color(0xFF1F4E3D))
        }
        Props.EGGS -> {
            val colors = listOf(Color(0xFFF2B5C4), Color(0xFFB5D9F2), Color(0xFFF7E3A1), Color(0xFFC8E6B5))
            for (i in 0..3) sil(Sil.EGG, corner(i % 2 == 0, false) + Offset((if (i % 2 == 0) 1 else -1) * u * 0.6f * (i / 2), u * 0.2f * (i / 2)), 0.7f, colors[i], -10f + i * 9)
        }
        Props.BUOYS -> {
            sil(Sil.BUOY, corner(true, false), 1.1f, Color(0xFFD9442B), -12f)
            sil(Sil.BUOY, corner(true, false) + Offset(u * 0.6f, u * 0.15f), 0.9f, Color(0xFFF2C230), 8f)
            sil(Sil.BUOY, corner(false, false), 1.1f, Color(0xFF2E7D8C), 10f)
        }
        Props.BLUEBERRIES -> {
            sil(Sil.BLUEBERRY, corner(true, false), 1.0f, Color(0xFF3D4A8C))
            sil(Sil.BLUEBERRY, corner(false, false), 0.9f, Color(0xFF4B5BA8))
            sil(Sil.BLUEBERRY, corner(false, true), 0.7f, Color(0xFF3D4A8C))
        }
        Props.SUGAR_BUCKETS -> {
            for (left in listOf(true, false)) {
                val c = corner(left, false)
                val bucket = Path().apply { moveTo(c.x, c.y + u * 0.2f); lineTo(c.x + u, c.y + u * 0.2f); lineTo(c.x + u * 0.85f, c.y + u * 1.1f); lineTo(c.x + u * 0.15f, c.y + u * 1.1f); close() }
                drawPath(bucket, Color(0xFF9AA3AB))
                drawLine(Color(0xFF6E767E), Offset(c.x, c.y + u * 0.2f), Offset(c.x + u, c.y + u * 0.2f), strokeWidth = 3 * dp)
                drawRect(Color(0xFF6E767E), Offset(c.x - u * 0.05f, c.y), Size(u * 1.1f, u * 0.12f))
                sil(Sil.MAPLE_LEAF, c + Offset(u * 0.25f, u * 0.4f), 0.5f, Color(0xFFB8641E))
            }
        }
        Props.PINECONES -> {
            pinecone(corner(true, false) + Offset(u * 0.5f, u * 0.6f), u * 1.3f, -20f, Wood.WALNUT, 0.9f)
            pinecone(corner(false, false) + Offset(u * 0.5f, u * 0.6f), u * 1.1f, 25f, Wood.WALNUT, 0.9f)
        }
        Props.STARS -> {
            sil(Sil.STAR, corner(true, true), 0.7f, Color(0xFFE5C04B), -12f)
            sil(Sil.STAR, corner(false, true), 0.6f, Color(0xFFE5C04B), 18f)
            sil(Sil.STAR, corner(false, false), 0.8f, Color(0xFFC9A13B), 5f)
        }
    }
}

/** Christmas lights strung in swags along the top, each bulb glowing on its own slow pulse. */
private fun DrawScope.twinkleLights(time: Long, dp: Float) {
    val swags = (size.width / (160 * dp)).toInt().coerceIn(2, 6)
    val colors = listOf(Color(0xFFE53935), Color(0xFF43A047), Color(0xFFFFC107), Color(0xFF1E88E5), Color(0xFFFF7043))
    var bulb = 0
    for (i in 0 until swags) {
        val x0 = size.width * i / swags; val x1 = size.width * (i + 1) / swags
        val sag = 34 * dp
        val wire = Path().apply { moveTo(x0, 4 * dp); quadraticTo((x0 + x1) / 2, sag * 2, x1, 4 * dp) }
        drawPath(wire, Color(0xFF1B3B1B), style = Stroke(2.2f * dp))
        for (k in 1..5) {
            val t = k / 6f
            val x = x0 + (x1 - x0) * t
            val y = 4 * dp + (sag * 2 - 4 * dp) * 2 * t * (1 - t)
            val c = colors[bulb % colors.size]
            val glow = 0.55f + 0.45f * sin(time / 600f + bulb * 1.7f).toFloat()
            drawCircle(c.copy(alpha = 0.25f * glow), 11 * dp, Offset(x, y + 9 * dp))
            drawOval(c.copy(alpha = 0.6f + 0.4f * glow), Offset(x - 4 * dp, y + 3 * dp), Size(8 * dp, 12 * dp))
            drawRect(Color(0xFF4A4A4A), Offset(x - 2.5f * dp, y), Size(5 * dp, 4 * dp))
            bulb++
        }
    }
}

// ---- particles ------------------------------------------------------------------------------------------------------

/** Stateless particles: every flake, leaf or spark is a function of time, so nothing needs storing. */
fun DrawScope.particles(kind: Particles, time: Long, dp: Float, intensity: Float = 1f) {
    val t = time / 1000f
    val w = size.width; val h = size.height
    fun fall(i: Int, speed: Float, drift: Float): Offset {
        val sp = speed * (0.6f + 0.8f * pseudo(i * 3 + 1))
        val y = ((pseudo(i * 7 + 2) * (h + 80 * dp) + t * sp * dp) % (h + 80 * dp)) - 40 * dp
        val x = (pseudo(i * 11 + 5) * w + sin(t * 0.6f + i) * drift * dp)
        return Offset(x, y)
    }
    fun rise(i: Int, speed: Float, drift: Float): Offset {
        val p = fall(i, speed, drift)
        return Offset(p.x, h - p.y)
    }
    val n = ((w * h) / (dp * dp * 9000f) * intensity).toInt().coerceIn(12, 90)
    when (kind) {
        Particles.NONE -> Unit
        Particles.SNOW -> for (i in 0 until n) {
            val r = (1.2f + 2.6f * pseudo(i * 13)) * dp
            drawCircle(Color.White.copy(alpha = 0.55f + 0.4f * pseudo(i)), r, fall(i, 38f, 22f))
        }
        Particles.RAIN -> for (i in 0 until n) {
            val p = fall(i, 420f, 0f)
            drawLine(Color(0xFFBFD6E8).copy(alpha = 0.35f), p, p + Offset(-2 * dp, 12 * dp), strokeWidth = 1.2f * dp)
        }
        Particles.LEAVES, Particles.OAK_LEAVES, Particles.MAPLE -> {
            val palette = listOf(Color(0xFFD9442B), Color(0xFFE38A33), Color(0xFFF2C230), Color(0xFFA2501E))
            for (i in 0 until n / 3) {
                val p = fall(i, 45f, 40f)
                val s = (14 + 10 * pseudo(i * 5)) * dp
                rotate(t * 60f * (if (i % 2 == 0) 1 else -1) + i * 40, p) {
                    engrave(if (kind == Particles.OAK_LEAVES) Sil.OAK_LEAF else Sil.MAPLE_LEAF, Rect(p.x - s / 2, p.y - s / 2, p.x + s / 2, p.y + s / 2), Wood.MAPLE, 0.85f, fill = palette[i % palette.size], shine = false)
                }
            }
        }
        Particles.HEARTS -> for (i in 0 until n / 3) {
            val p = rise(i, 40f, 25f)
            val s = (12 + 10 * pseudo(i * 5)) * dp
            engrave(Sil.HEART, Rect(p.x - s / 2, p.y - s / 2, p.x + s / 2, p.y + s / 2), Wood.MAPLE, 0.6f, fill = if (i % 3 == 0) Color(0xFFF08AA0) else Color(0xFFD94A6A), shine = false)
        }
        Particles.SHAMROCKS -> for (i in 0 until n / 3) {
            val p = fall(i, 35f, 30f)
            val s = (14 + 8 * pseudo(i * 5)) * dp
            rotate(t * 30f + i * 50, p) { engrave(Sil.SHAMROCK, Rect(p.x - s / 2, p.y - s / 2, p.x + s / 2, p.y + s / 2), Wood.MAPLE, 0.75f, fill = Color(0xFF3E9A55), shine = false) }
        }
        Particles.PETALS -> for (i in 0 until n / 2) {
            val p = fall(i, 30f, 45f)
            val c = listOf(Color(0xFFF7C6D9), Color(0xFFFFFFFF), Color(0xFFE8D1F2))[i % 3]
            rotate(t * 80f + i * 33, p) { drawOval(c.copy(alpha = 0.85f), Offset(p.x - 5 * dp, p.y - 2.5f * dp), Size(10 * dp, 5 * dp)) }
        }
        Particles.BLUEBERRIES -> for (i in 0 until n / 3) {
            val p = fall(i, 60f, 10f)
            val r = (4 + 3 * pseudo(i)) * dp
            drawCircle(Color(0xFF3D4A8C), r, p)
            drawCircle(Color.White.copy(alpha = 0.25f), r * 0.35f, p - Offset(r * 0.3f, r * 0.3f))
            drawCircle(Color(0xFF1E2550), r * 0.25f, p + Offset(0f, -r * 0.75f))
        }
        Particles.FIREFLIES -> for (i in 0 until n / 2) {
            val base = Offset(pseudo(i * 17) * w, h * (0.35f + 0.6f * pseudo(i * 19)))
            val p = base + Offset(sin(t * 0.4f + i) * 30 * dp, cos(t * 0.3f + i * 2) * 18 * dp)
            val glow = (sin(t * 1.6f + i * 2.1f) * 0.5f + 0.5f).let { it * it }
            drawCircle(Color(0xFFE9F57A).copy(alpha = 0.25f * glow), 9 * dp, p)
            drawCircle(Color(0xFFF7FFB0).copy(alpha = 0.9f * glow), 2.2f * dp, p)
        }
        Particles.CONFETTI -> for (i in 0 until n) {
            val p = fall(i, 70f, 30f)
            val c = listOf(Color(0xFFE5C04B), Color(0xFFD94A6A), Color(0xFF3FA7B5), Color(0xFF6FD3A8), Color.White)[i % 5]
            rotate(t * 200f + i * 40, p) { drawRect(c, Offset(p.x - 3 * dp, p.y - 1.5f * dp), Size(6 * dp, 3 * dp)) }
        }
        Particles.FIREWORKS -> fireworks(t, dp, 1f)
        Particles.BATS -> for (i in 0 until 5) {
            val period = 9f + i * 2.3f
            val ph = ((t + i * 3.1f) % period) / period
            val x = -40 * dp + ph * (w + 80 * dp)
            val y = h * (0.12f + 0.35f * pseudo(i * 9)) + sin(ph * 18f) * 14 * dp
            val flap = sin(t * 14f + i) * 0.6f
            val s = (10 + 6 * pseudo(i)) * dp
            val bat = Path().apply {
                moveTo(x, y)
                quadraticTo(x - s * 0.6f, y - s * (0.6f + flap), x - s * 1.4f, y - s * 0.1f)
                quadraticTo(x - s * 0.9f, y + s * 0.1f, x - s * 0.7f, y + s * 0.35f)
                quadraticTo(x - s * 0.3f, y + s * 0.1f, x, y + s * 0.3f)
                quadraticTo(x + s * 0.3f, y + s * 0.1f, x + s * 0.7f, y + s * 0.35f)
                quadraticTo(x + s * 0.9f, y + s * 0.1f, x + s * 1.4f, y - s * 0.1f)
                quadraticTo(x + s * 0.6f, y - s * (0.6f + flap), x, y)
            }
            drawPath(bat, Color(0xFF15121A).copy(alpha = 0.85f))
        }
    }
}

/** Bursts of sparks on staggered timers. Also used for winning a game. */
fun DrawScope.fireworks(t: Float, dp: Float, intensity: Float) {
    val colors = listOf(Color(0xFFE53935), Color(0xFFFFFFFF), Color(0xFF1E88E5), Color(0xFFFFC107), Color(0xFF6FD3A8))
    val shells = (4 * intensity).toInt().coerceAtLeast(1)
    for (s in 0 until shells) {
        val period = 2.6f + s * 0.7f
        val cycle = ((t + s * 1.3f) / period).toInt()
        val ph = ((t + s * 1.3f) % period) / period
        val c = Offset(size.width * (0.15f + 0.7f * pseudo(cycle * 31 + s)), size.height * (0.12f + 0.35f * pseudo(cycle * 17 + s * 3)))
        val color = colors[(cycle + s) % colors.size]
        if (ph < 0.18f) {
            // The rising shell.
            val k = ph / 0.18f
            val from = Offset(c.x, size.height)
            drawCircle(color.copy(alpha = 0.8f), 2.2f * dp, from + (c - from) * k)
        } else {
            val k = (ph - 0.18f) / 0.82f
            val r = 70 * dp * (1 - (1 - k) * (1 - k))
            for (i in 0 until 28) {
                val a = i / 28f * 2 * PI.toFloat()
                val p = c + Offset(cos(a), sin(a)) * r + Offset(0f, k * k * 30 * dp)
                drawCircle(color.copy(alpha = (1 - k) * 0.95f), 2.4f * dp * (1 - k * 0.5f), p)
                drawLine(color.copy(alpha = (1 - k) * 0.35f), p, p - Offset(cos(a), sin(a)) * 10f * dp, strokeWidth = 1.2f * dp)
            }
        }
    }
}

