package com.geoguy89.cribbage.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.geoguy89.cribbage.gfx.Carving.engrave
import com.geoguy89.cribbage.gfx.Carving.groove
import com.geoguy89.cribbage.gfx.Carving.hole
import com.geoguy89.cribbage.gfx.Carving.recess
import com.geoguy89.cribbage.gfx.Carving.softShadow
import com.geoguy89.cribbage.gfx.Carving.water
import com.geoguy89.cribbage.gfx.Carving.woodPlaque
import com.geoguy89.cribbage.gfx.Geo
import com.geoguy89.cribbage.gfx.Outline
import com.geoguy89.cribbage.gfx.Sil
import com.geoguy89.cribbage.gfx.Wood
import com.geoguy89.cribbage.gfx.burn
import com.geoguy89.cribbage.gfx.highlight

class BoardFonts(val title: FontFamily, val hand: FontFamily, val label: FontFamily)

enum class Face { TITLE, HAND, LABEL }

/** A place on the map, by real latitude and longitude. */
class Town(val name: String, val lat: Double, val lon: Double, val dx: Float = 1.2f, val dy: Float = 0f, val peak: Boolean = false)

/** Water on a board, where fish can jump and loons can swim. */
class WaterArea(val path: Path, val bounds: Rect)

/**
 * Draws a board design in board units (100 wide). Decorations use the helpers here; the static layer is painted once
 * into an image, and the pegs and wildlife are drawn over it every frame.
 */
class BoardPainter(
    scope: DrawScope,
    private val measurer: TextMeasurer,
    private val fonts: BoardFonts,
    val design: BoardDesign,
    /** Pixels per board unit. */
    val k: Float,
) : DrawScope by scope {
    val wood: Wood get() = design.wood
    val W = 100f
    val H get() = design.height
    val waters = mutableListOf<WaterArea>()
    val shallow: Color get() = design.shallow
    val deep: Color get() = design.deep

    fun family(face: Face) = when (face) { Face.TITLE -> fonts.title; Face.HAND -> fonts.hand; Face.LABEL -> fonts.label }

    private fun style(size: Float, face: Face, color: Color, weight: FontWeight?, spacing: Float) = TextStyle(
        color = color, fontFamily = family(face), fontSize = (size * k / (density * fontScale)).sp,
        fontWeight = weight ?: if (face == Face.TITLE) FontWeight.Bold else FontWeight.Normal,
        letterSpacing = (spacing * k / (density * fontScale)).sp,
    )

    /** Carved text centred on [at] (board units, [size] = cap height-ish), optionally rotated. */
    fun text(
        text: String, at: Offset, size: Float, face: Face = Face.TITLE, color: Color? = null, rotation: Float = 0f,
        weight: FontWeight? = null, spacing: Float = 0f, carved: Boolean = true,
    ) {
        val ink = color ?: wood.burn()
        val kk = k
        val layout = measurer.measure(text, style(size, face, ink, weight, spacing))
        val shine = if (carved) measurer.measure(text, style(size, face, wood.highlight().copy(alpha = 0.55f), weight, spacing)) else null
        withTransform({ scale(1f / kk, 1f / kk, Offset.Zero) }) {
            val c = at * kk
            withTransform({ rotate(rotation, c) }) {
                val tl = c - Offset(layout.size.width / 2f, layout.size.height / 2f)
                if (shine != null) drawText(shine, topLeft = tl + Offset(0.6f, 0.9f) * (kk / 4f))
                drawText(layout, topLeft = tl)
            }
        }
    }

    /** Text bent round a circle centred on [c], starting at [startDeg] (0 = east, clockwise), reading clockwise or not. */
    fun arcText(text: String, c: Offset, radius: Float, centreDeg: Float, size: Float, face: Face = Face.TITLE, color: Color? = null, clockwise: Boolean = true, spacing: Float = 0.3f) {
        val ink = color ?: wood.burn()
        val widths = text.map { ch -> measurer.measure(ch.toString(), style(size, face, ink, null, 0f)).size.width / k + spacing }
        val totalDeg = widths.sum() / radius * (180f / kotlin.math.PI.toFloat())
        var deg = if (clockwise) centreDeg - totalDeg / 2 else centreDeg + totalDeg / 2
        text.forEachIndexed { i, ch ->
            val step = widths[i] / radius * (180f / kotlin.math.PI.toFloat())
            val mid = if (clockwise) deg + step / 2 else deg - step / 2
            val p = c + Geo.rotate(Offset(radius, 0f), mid)
            text(ch.toString(), p, size, face, ink, rotation = if (clockwise) mid + 90f else mid - 90f, spacing = 0f)
            deg = if (clockwise) deg + step else deg - step
        }
    }

    /** Places an outline's point (unit box) on the board: centred at [center], longest side [size], turned [rotation]. */
    private fun place(o: Outline, center: Offset, size: Float, rotation: Float): (Float, Float) -> Offset = { x, y ->
        center + Geo.rotate(Offset((x - o.width / 2) * size, (y - o.height / 2) * size), rotation)
    }

    fun outlinePath(o: Outline, pts: FloatArray, center: Offset, size: Float, rotation: Float): Path {
        val f = place(o, center, size, rotation)
        return Geo.path((0 until pts.size / 2).map { f(pts[it * 2], pts[it * 2 + 1]) }, close = true)
    }

    /** A real lake poured in epoxy, with its islands left as wood and towns marked round the shore. */
    fun lake(
        o: Outline, center: Offset, size: Float, rotation: Float = 0f, towns: List<Town> = emptyList(),
        shallow: Color = design.shallow, deep: Color = design.deep, townSize: Float = 1.7f,
    ) {
        val path = outlinePath(o, o.outer, center, size, rotation)
        val b = path.getBounds()
        water(path, b, shallow, deep, wood)
        for (isle in o.islands) {
            val ip = outlinePath(o, isle, center, size, rotation)
            drawPath(ip, wood.base)
            drawPath(ip, wood.burn().copy(alpha = 0.7f), style = Stroke(0.3f))
        }
        waters += WaterArea(path, b)
        val f = place(o, center, size, rotation)
        for (t in towns) {
            val p = f(o.projection.x(t.lon), o.projection.y(t.lat))
            if (t.peak) {
                val tri = Path().apply { moveTo(p.x, p.y - 1.1f); lineTo(p.x + 1f, p.y + 0.6f); lineTo(p.x - 1f, p.y + 0.6f); close() }
                drawPath(tri, wood.burn().copy(alpha = 0.85f))
            } else {
                drawCircle(wood.burn().copy(alpha = 0.85f), 0.45f, p)
            }
            val dir = if (t.dx >= 0) 1 else -1
            val w = measurer.measure(t.name, style(townSize, Face.HAND, Color.Black, null, 0f)).size.width / k
            text(t.name, p + Offset(t.dx + dir * w / 2, t.dy), townSize, Face.HAND, carved = false)
        }
    }

    /** Engraved roads and streams across an area, for the map look. */
    fun mapLines(area: Rect, seed: Int, count: Int = 9, alpha: Float = 0.32f) {
        val r = kotlin.random.Random(seed)
        val ink = wood.burn().copy(alpha = alpha)
        clipPath(Path().apply { addRect(area) }) {
            repeat(count) { i ->
                val horizontal = i % 2 == 0
                val a = if (horizontal) Offset(area.left - 2, area.top + area.height * r.nextFloat()) else Offset(area.left + area.width * r.nextFloat(), area.top - 2)
                val b = if (horizontal) Offset(area.right + 2, area.top + area.height * r.nextFloat()) else Offset(area.left + area.width * r.nextFloat(), area.bottom + 2)
                drawPath(Geo.path(Geo.wobble(a, b, seed * 31 + i, area.minDimension * 0.12f)), ink, style = Stroke(0.22f, cap = StrokeCap.Round))
            }
        }
    }

    fun sil(s: Sil, l: Float, t: Float, w: Float, h: Float = w, flip: Boolean = false, alpha: Float = 0.8f, fill: Color? = null) =
        engrave(s, Rect(l, t, l + w, t + h), wood, alpha, flip, fill)

    fun inset(shape: Path, depth: Float = 0.8f) = recess(shape, wood, depth)

    fun roundRect(l: Float, t: Float, r: Float, b: Float, radius: Float): Path = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(l, t, r, b, androidx.compose.ui.geometry.CornerRadius(radius)))
    }

    // ---- the whole board ------------------------------------------------------------------------------------------

    fun paintStatic() {
        val shape = design.shape(W, H)
        softShadow(shape, Offset(1.2f, 1.8f), 1.2f, 0.4f)
        woodPlaque(shape, Rect(0f, 0f, W, H), wood, design.seed, design.grainAngle, design.bevel)
        val self = this
        clipPath(shape) {
            self.design.decor(self)
            self.paintTrack()
            self.design.over(self)
        }
    }

    private fun paintTrack() {
        val spec = design.trackSpec
        val g = design.geometry
        val laneW = spec.laneGap
        for (seg in spec.segments.map { it.pts } + spec.connectors) {
            val p = Geo.path(seg)
            spec.paint?.let { paint ->
                drawPath(p, paint, style = Stroke(laneW * 0.32f, cap = StrokeCap.Round))
                drawPath(p, Color.Black.copy(alpha = 0.15f), style = Stroke(0.12f))
            } ?: run {
                if (spec.groove) groove(p, wood, 0.22f, 0.6f)
            }
        }
        if (spec.paint == null && spec.groove) {
            for (line in g.laneLines) groove(Geo.path(line), wood, 0.12f, 0.35f)
        }
        // Group ticks across the track between every five holes.
        for (i in 5 until 120 step 5) {
            val a = g.centre[i]; val b = g.centre[i + 1]
            val m = (a + b) / 2f
            val n = (g.normal[i] + g.normal[i + 1]) / 2f
            val tick = Geo.path(listOf(m + n * (laneW * 0.85f), m - n * (laneW * 0.85f)))
            if (spec.paint != null) drawPath(tick, spec.paint, style = Stroke(0.35f)) else if (i % 10 == 0 || spec.numbers != Numbers.NONE) groove(tick, wood, 0.12f, 0.35f)
        }
        // Numbers.
        when (spec.numbers) {
            Numbers.PILLS -> for (i in 5..120 step 5) {
                val p = (g.centre[i] + g.centre[(i + 1).coerceAtMost(120)]) / 2f
                val t = g.normal[i].let { Offset(it.y, -it.x) }
 val ang = Geo.angleDeg(t).let { a -> if (a > 90 || a < -90) a + 180 else a }
                val pill = spec.paint ?: wood.burn()
                withTransform({ rotate(ang, p) }) {
                    val w = if (i >= 100) 3.6f else 2.8f
                    drawRoundRect(pill, Offset(p.x - w / 2, p.y - 1.05f), Size(w, 2.1f), androidx.compose.ui.geometry.CornerRadius(1f))
                }
                text("$i", p, 1.45f, Face.LABEL, Color(0xFF173A40), rotation = ang, carved = false, weight = FontWeight.SemiBold)
            }
            Numbers.TENS, Numbers.FIVES -> {
                val every = if (spec.numbers == Numbers.TENS) 10 else 5
                for (i in every..120 step every) {
                    val p = g.centre[i] + g.normal[i] * (laneW * 1.35f * spec.numberSide)
                    text("$i", p, 1.5f, Face.LABEL, weight = FontWeight.Medium)
                }
            }
            Numbers.NONE -> Unit
        }
        if (spec.skunkLine) {
            val m = (g.centre[90] + g.centre[91]) / 2f
            val n = g.normal[90]
            groove(Geo.path(listOf(m + n * (laneW * 1.1f), m - n * (laneW * 1.1f))), wood, 0.25f, 0.8f)
            text("S", m - n * (laneW * 1.55f * spec.numberSide), 1.6f, Face.TITLE)
        }
        val r = spec.holeR
        for (lane in 0..1) {
            for (i in 1..120) hole(g.holes[lane][i], r, wood)
            for (p in g.start[lane]) hole(p, r, wood)
        }
        hole(g.finish, r * 1.25f, wood)
    }
}

/** Renders the static layer of [design] at [px] wide into an image. */
fun renderBoard(design: BoardDesign, px: Int, measurer: TextMeasurer, fonts: BoardFonts, density: androidx.compose.ui.unit.Density): Pair<ImageBitmap, List<WaterArea>> {
    val h = (px * design.height / 100f).toInt().coerceAtLeast(1)
    val image = ImageBitmap(px, h)
    val canvas = androidx.compose.ui.graphics.Canvas(image)
    val k = px / 100f
    var waters: List<WaterArea> = emptyList()
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(density, androidx.compose.ui.unit.LayoutDirection.Ltr, canvas, Size(px.toFloat(), h.toFloat())) {
        withTransform({ scale(k, k, Offset.Zero) }) {
            val p = BoardPainter(this, measurer, fonts, design, k)
            p.paintStatic()
            waters = p.waters
        }
    }
    return image to waters
}
