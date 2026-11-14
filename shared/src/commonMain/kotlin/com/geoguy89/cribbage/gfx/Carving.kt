package com.geoguy89.cribbage.gfx

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Wood species: base, light and dark tones, the grain line colour and how strongly it shows. */
enum class Wood(val title: String, val light: Color, val base: Color, val dark: Color, val grain: Color, val grainAlpha: Float, val figure: Float) {
    MAPLE("Hard maple", Color(0xFFF1D7A8), Color(0xFFE3BF86), Color(0xFFB98A52), Color(0xFF9C6B36), 0.20f, 0.6f),
    BIRCH("White birch", Color(0xFFF6E7C8), Color(0xFFEBD3A6), Color(0xFFC6A574), Color(0xFFA9865A), 0.16f, 0.5f),
    CHERRY("Black cherry", Color(0xFFD99063), Color(0xFFB8653B), Color(0xFF7E3B1E), Color(0xFF5C2711), 0.26f, 0.8f),
    WALNUT("Black walnut", Color(0xFF9A6B45), Color(0xFF6E4527), Color(0xFF3E2413), Color(0xFF26150A), 0.32f, 1.0f),
    STAINED("Walnut-stained birch", Color(0xFFB27A42), Color(0xFF8A5528), Color(0xFF4F2C10), Color(0xFF2C1606), 0.30f, 0.9f),
    WHITE_OAK("White oak", Color(0xFFE2C799), Color(0xFFCCA66E), Color(0xFF94703F), Color(0xFF6B4A22), 0.30f, 1.2f),
    PINE("Eastern white pine", Color(0xFFF2D196), Color(0xFFE0B26A), Color(0xFFB07A3A), Color(0xFF8A531C), 0.34f, 1.4f),
    DRIFTWOOD("Weathered driftwood", Color(0xFFD9D2C3), Color(0xFFB8AE9A), Color(0xFF7D7465), Color(0xFF5A5246), 0.30f, 1.1f),
    TEAK("Boat-shop teak", Color(0xFFD1A05E), Color(0xFFAE7A3A), Color(0xFF6E4718), Color(0xFF4A2D0C), 0.28f, 0.9f),
    ASH("Brown ash", Color(0xFFE8D3AA), Color(0xFFD2B585), Color(0xFF9F7F50), Color(0xFF6E5430), 0.30f, 1.3f),
}

/** The engraving colour that suits a wood: a burnt brown on pale woods, a pale scorch on dark ones. */
fun Wood.burn(): Color = if (base.luminance() > 0.35f) Color(0xFF4A2A12) else Color(0xFF1B0E05)
fun Wood.highlight(): Color = lerp(light, Color.White, 0.35f)

fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

object Carving {
    /** A soft drop shadow under [shape], built from a few stacked translucent copies. */
    fun DrawScope.softShadow(shape: Path, offset: Offset, spread: Float, alpha: Float = 0.35f) {
        val steps = 6
        for (i in steps downTo 1) {
            val f = i / steps.toFloat()
            translate(offset.x * f, offset.y * f) {
                drawPath(shape, Color.Black.copy(alpha = alpha / steps), style = Stroke(width = spread * f * 2, join = StrokeJoin.Round))
                drawPath(shape, Color.Black.copy(alpha = alpha / steps))
            }
        }
    }

    /**
     * Fills [shape] with wood: a lit gradient, wavy grain lines along [grainAngle], a knot or two, then a bevelled
     * edge. Everything after this (while clipped) sits on the wood.
     */
    fun DrawScope.woodPlaque(shape: Path, bounds: Rect, wood: Wood, seed: Int, grainAngle: Float = 0f, bevel: Float = 1.6f) {
        drawPath(shape, Brush.linearGradient(listOf(wood.light, wood.base, lerp(wood.base, wood.dark, 0.35f)), bounds.topLeft, bounds.bottomRight))
        clipPath(shape) {
            woodGrain(bounds, wood, seed, grainAngle)
            // Light falls from the top left.
            drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), bounds.topLeft + Offset(bounds.width * 0.25f, bounds.height * 0.2f), bounds.maxDimension * 0.8f))
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f)), bounds.center, bounds.maxDimension * 0.75f))
        }
        bevelEdge(shape, wood, bevel)
    }

    fun DrawScope.woodGrain(bounds: Rect, wood: Wood, seed: Int, angle: Float = 0f) {
        val r = Random(seed)
        val c = bounds.center
        withTransform({ rotate(angle, c) }) {
            val span = bounds.maxDimension * 1.5f
            val lines = (55 * wood.figure).toInt().coerceAtLeast(20)
            for (i in 0 until lines) {
                val y = c.y - span / 2 + span * (i + r.nextFloat() * 0.8f) / lines
                val amp = 0.4f + r.nextFloat() * 1.4f * wood.figure
                val freq = 0.025f + r.nextFloat() * 0.035f
                val ph = r.nextFloat() * 6.28f
                val p = Path()
                var x = c.x - span / 2
                p.moveTo(x, y)
                while (x < c.x + span / 2) {
                    x += 1.6f
                    val yy = y + amp * sin(x * freq + ph) + 0.4f * amp * sin(x * freq * 2.7f + ph * 1.3f)
                    p.lineTo(x, yy)
                }
                val dark = r.nextFloat() < 0.65f
                val w = 0.12f + r.nextFloat() * 0.5f * wood.figure
                drawPath(p, (if (dark) wood.grain else wood.light).copy(alpha = wood.grainAlpha * (0.4f + r.nextFloat() * 0.8f)), style = Stroke(w))
            }
            // Knots: tight ellipses with the grain swirling round them.
            repeat(if (wood.figure > 0.9f) 2 else 1) {
                val k = Offset(c.x + (r.nextFloat() - 0.5f) * bounds.width * 0.8f, c.y + (r.nextFloat() - 0.5f) * bounds.height * 0.8f)
                val kr = 0.8f + r.nextFloat() * 1.6f * wood.figure
                for (ring in 6 downTo 1) {
                    drawOval(wood.grain.copy(alpha = wood.grainAlpha * 0.9f), k - Offset(kr * ring * 1.8f, kr * ring * 0.55f), Size(kr * ring * 3.6f, kr * ring * 1.1f), style = Stroke(0.22f))
                }
                drawOval(wood.dark.copy(alpha = 0.55f), k - Offset(kr * 1.4f, kr * 0.45f), Size(kr * 2.8f, kr * 0.9f))
            }
        }
    }

    /** A rounded-over edge: dark on the outside, a bright line just inside where the light catches it. */
    fun DrawScope.bevelEdge(shape: Path, wood: Wood, width: Float) {
        clipPath(shape) {
            drawPath(shape, wood.dark.copy(alpha = 0.55f), style = Stroke(width * 2.2f))
            drawPath(shape, wood.highlight().copy(alpha = 0.45f), style = Stroke(width * 0.9f))
            drawPath(shape, wood.dark.copy(alpha = 0.35f), style = Stroke(width * 0.35f))
        }
        drawPath(shape, wood.dark, style = Stroke(0.35f))
    }

    /** A burnt or carved line: a dark groove with a sliver of light beneath it. */
    fun DrawScope.groove(path: Path, wood: Wood, width: Float, alpha: Float = 0.75f, cap: StrokeCap = StrokeCap.Round) {
        translate(0.18f, 0.22f) { drawPath(path, wood.highlight().copy(alpha = 0.45f * alpha), style = Stroke(width, cap = cap, join = StrokeJoin.Round)) }
        drawPath(path, wood.burn().copy(alpha = alpha), style = Stroke(width, cap = cap, join = StrokeJoin.Round))
    }

    /** Draws silhouette [s] into [rect], optionally mirrored, as a burnt engraving (or a solid [fill]). */
    fun DrawScope.engrave(s: Sil, rect: Rect, wood: Wood, alpha: Float = 0.8f, flip: Boolean = false, fill: Color? = null, shine: Boolean = true) {
        val color = fill ?: wood.burn()
        withTransform({
            translate(rect.left, rect.top)
            scale(rect.width / 100f, rect.height / 100f, Offset.Zero)
            if (flip) scale(-1f, 1f, Offset(50f, 50f))
        }) {
            if (shine) translate(0.6f, 0.8f) { drawPath(s.path, wood.highlight().copy(alpha = 0.35f * alpha)) }
            drawPath(s.path, color.copy(alpha = alpha))
            s.linePath?.let { lp -> drawPath(lp, color.copy(alpha = alpha), style = Stroke(s.lineWidth, cap = StrokeCap.Round)) }
            s.eye?.let { e -> drawCircle(wood.light.copy(alpha = 0.8f * alpha), 1.4f, e) }
        }
    }

    /**
     * A carved pinecone hanging from a pine sprig: an egg-shaped cone covered in overlapping, diamond-set scales,
     * the needles fanning out above.
     */
    fun DrawScope.pinecone(center: Offset, size: Float, angle: Float, wood: Wood, alpha: Float = 0.8f) {
        val ink = wood.burn().copy(alpha = alpha)
        rotate(angle, center) {
            val h = size * 0.62f
            val w = size * 0.42f
            val top = center.y - h * 0.42f
            // The sprig and its needles, above the cone.
            val stem = Offset(center.x, top - size * 0.06f)
            drawLine(ink, stem, Offset(center.x, top + size * 0.04f), strokeWidth = size * 0.05f, cap = StrokeCap.Round)
            drawLine(ink, stem + Offset(-size * 0.42f, -size * 0.12f), stem + Offset(size * 0.42f, -size * 0.05f), strokeWidth = size * 0.035f, cap = StrokeCap.Round)
            for (i in 0 until 12) {
                val along = -0.4f + 0.8f * i / 11f
                val base = stem + Offset(size * along, -size * 0.12f + size * 0.07f * (along + 0.4f) / 0.8f)
                for (side in listOf(-1f, 1f)) {
                    val a = (-90f + side * (35f + (i % 3) * 8f)) * PI.toFloat() / 180f
                    drawLine(ink, base, base + Offset(cos(a), sin(a)) * (size * 0.2f), strokeWidth = size * 0.016f, cap = StrokeCap.Round)
                }
            }
            // The cone: an egg, widest a third of the way down.
            val cone = Path().apply {
                moveTo(center.x, top)
                cubicTo(center.x + w * 0.75f, top, center.x + w * 0.6f, top + h * 0.7f, center.x, top + h)
                cubicTo(center.x - w * 0.6f, top + h * 0.7f, center.x - w * 0.75f, top, center.x, top)
                close()
            }
            drawPath(cone, lerp(wood.base, wood.dark, 0.25f).copy(alpha = alpha))
            clipPath(cone) {
                // Scales in a diamond lattice, each a rounded point with a dark lip underneath.
                val rows = 8
                for (row in 0 until rows) {
                    val y = top + h * (row + 0.6f) / rows
                    val n = 4
                    for (k in 0..n) {
                        val x = center.x - w * 0.6f + w * 1.2f * (k + if (row % 2 == 0) 0f else 0.5f) / n
                        val sw = w * 0.34f
                        val sh = h * 0.15f
                        val scale = Path().apply {
                            moveTo(x - sw / 2, y - sh * 0.4f)
                            quadraticTo(x - sw * 0.45f, y + sh * 0.55f, x, y + sh * 0.7f)
                            quadraticTo(x + sw * 0.45f, y + sh * 0.55f, x + sw / 2, y - sh * 0.4f)
                            quadraticTo(x, y - sh * 0.1f, x - sw / 2, y - sh * 0.4f)
                        }
                        drawPath(scale, lerp(wood.light, wood.base, 0.4f).copy(alpha = 0.85f * alpha))
                        drawPath(scale, ink, style = Stroke(size * 0.018f))
                    }
                }
            }
            drawPath(cone, ink, style = Stroke(size * 0.025f))
        }
    }

    /** A compass rose with N/E/S/W points. Letters are drawn by the caller. */
    fun DrawScope.compassRose(c: Offset, r: Float, wood: Wood, alpha: Float = 0.8f) {
        val ink = wood.burn().copy(alpha = alpha)
        drawCircle(ink, r * 0.82f, c, style = Stroke(r * 0.04f))
        drawCircle(ink, r * 0.74f, c, style = Stroke(r * 0.02f))
        for (i in 0 until 8) {
            val a = (i * 45 - 90) * PI / 180
            val long = i % 2 == 0
            val len = if (long) r else r * 0.55f
            val tip = c + Offset(cos(a).toFloat(), sin(a).toFloat()) * len
            val side = Offset(-sin(a).toFloat(), cos(a).toFloat()) * (r * if (long) 0.16f else 0.1f)
            val left = Path().apply { moveTo(c.x, c.y); lineTo(tip.x, tip.y); lineTo(c.x + side.x, c.y + side.y); close() }
            val right = Path().apply { moveTo(c.x, c.y); lineTo(tip.x, tip.y); lineTo(c.x - side.x, c.y - side.y); close() }
            drawPath(left, ink)
            drawPath(right, ink, style = Stroke(r * 0.03f))
        }
        drawCircle(ink, r * 0.07f, c)
    }

    /** A recessed panel: the floor is darker, with shadow along the top-left lip and light along the bottom-right. */
    fun DrawScope.recess(shape: Path, wood: Wood, depth: Float) {
        drawPath(shape, wood.dark.copy(alpha = 0.18f))
        clipPath(shape) {
            translate(depth * 0.6f, depth * 0.7f) { drawPath(shape, Color.Black.copy(alpha = 0.28f), style = Stroke(depth * 1.6f)) }
            translate(-depth * 0.5f, -depth * 0.5f) { drawPath(shape, wood.highlight().copy(alpha = 0.35f), style = Stroke(depth)) }
        }
        drawPath(shape, wood.dark.copy(alpha = 0.6f), style = Stroke(0.3f))
    }

    /**
     * Water poured into a carved recess, like an epoxy river table: shallows by the shore deepening towards the
     * middle, a sheen, and the shore burnt dark.
     */
    fun DrawScope.water(shape: Path, bounds: Rect, shallow: Color, deep: Color, wood: Wood) {
        drawPath(shape, deep)
        clipPath(shape) {
            // Depth bands: wide strokes of the shoreline, paling towards the shore.
            val bands = 7
            for (i in bands downTo 1) {
                val f = i / bands.toFloat()
                drawPath(shape, lerp(deep, shallow, 1f - f + 0.1f).copy(alpha = 0.55f), style = Stroke(bounds.minDimension * 0.07f * f, join = StrokeJoin.Round))
            }
            drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent, Color.White.copy(alpha = 0.1f), Color.Transparent), bounds.topLeft, bounds.bottomRight))
            // Faint ripple lines.
            val r = Random(bounds.width.toInt() * 31 + bounds.height.toInt())
            repeat(14) {
                val y = bounds.top + bounds.height * r.nextFloat()
                val x = bounds.left + bounds.width * r.nextFloat()
                val w = bounds.width * (0.04f + r.nextFloat() * 0.08f)
                drawLine(Color.White.copy(alpha = 0.18f), Offset(x, y), Offset(x + w, y), strokeWidth = 0.18f, cap = StrokeCap.Round)
            }
        }
        drawPath(shape, wood.burn().copy(alpha = 0.85f), style = Stroke(0.45f, join = StrokeJoin.Round))
        translate(0.2f, 0.25f) { drawPath(shape, wood.highlight().copy(alpha = 0.3f), style = Stroke(0.25f, join = StrokeJoin.Round)) }
    }

    /** A drilled peg hole: dark shaft, shaded rim, a hint of light on the lower lip. */
    fun DrawScope.hole(c: Offset, r: Float, wood: Wood) {
        drawCircle(wood.highlight().copy(alpha = 0.45f), r * 1.18f, c + Offset(r * 0.12f, r * 0.16f))
        drawCircle(lerp(wood.dark, Color.Black, 0.55f), r, c)
        drawCircle(Brush.radialGradient(listOf(Color.Black, Color.Black.copy(alpha = 0.0f)), c - Offset(r * 0.25f, r * 0.3f), r * 0.9f), r * 0.85f, c)
    }

    /**
     * A peg standing in a hole: shadow, then a turned, shiny head lit from the top left. [lift] (0-1) raises it during
     * a hop, which enlarges and softens the shadow.
     */
    fun DrawScope.peg(c: Offset, r: Float, color: Color, lift: Float = 0f) {
        val up = lift * r * 3f
        drawCircle(Color.Black.copy(alpha = 0.35f - 0.2f * lift), r * (1.1f + lift * 0.6f), c + Offset(r * 0.5f + up * 0.3f, r * 0.7f + up * 0.4f), style = Fill, blendMode = BlendMode.SrcOver)
        val head = c - Offset(0f, up)
        drawCircle(lerp(color, Color.Black, 0.45f), r * 1.05f, head + Offset(0f, r * 0.25f))
        drawCircle(Brush.radialGradient(listOf(lerp(color, Color.White, 0.55f), color, lerp(color, Color.Black, 0.35f)), head - Offset(r * 0.35f, r * 0.4f), r * 1.3f), r, head)
        drawCircle(Color.White.copy(alpha = 0.75f), r * 0.24f, head - Offset(r * 0.35f, r * 0.38f))
    }
}
