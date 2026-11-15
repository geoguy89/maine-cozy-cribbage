package com.geoguy89.cribbage.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import com.geoguy89.cribbage.gfx.Carving.compassRose
import com.geoguy89.cribbage.gfx.Carving.groove
import com.geoguy89.cribbage.gfx.Carving.pinecone
import com.geoguy89.cribbage.gfx.Carving.water
import com.geoguy89.cribbage.gfx.Carving.woodGrain
import com.geoguy89.cribbage.gfx.Geo
import com.geoguy89.cribbage.gfx.Outlines
import com.geoguy89.cribbage.gfx.Sil
import com.geoguy89.cribbage.gfx.Wood
import com.geoguy89.cribbage.gfx.burn

/** A cribbage board: its outline and wood, its peg track, and everything carved or poured into it. */
class BoardDesign(
    val id: String,
    val name: String,
    /** Where it is: "Piscataquis County". */
    val place: String,
    val blurb: String,
    /** Width over height. */
    val aspect: Float,
    val wood: Wood,
    val shallow: Color = Color(0xFF4FB3BF),
    val deep: Color = Color(0xFF0D4F63),
    val seed: Int,
    val grainAngle: Float = 0f,
    val bevel: Float = 1.6f,
    val shape: (Float, Float) -> Path,
    track: (Float, Float) -> TrackSpec,
    val decor: BoardPainter.() -> Unit,
    val over: BoardPainter.() -> Unit = {},
) {
    val height: Float get() = 100f / aspect
    val trackSpec: TrackSpec by lazy { track(100f, height) }
    val geometry: TrackGeometry by lazy { TrackGeometry(trackSpec) }
}

private fun rounded(l: Float, t: Float, r: Float, b: Float, radius: Float) = Path().apply {
    addRoundRect(androidx.compose.ui.geometry.RoundRect(l, t, r, b, androidx.compose.ui.geometry.CornerRadius(radius)))
}

/** An open loop round a rounded rectangle: holes on [cover] of it, the rest left for the start and game holes. */
private fun loop(l: Float, t: Float, r: Float, b: Float, radius: Float, startFrac: Float, cover: Float = 0.9f): List<Offset> {
    val ring = Geo.roundedRect(l, t, r, b, radius, startFrac, 600)
    return ring.take((ring.size * cover).toInt())
}

private fun Path.addPoints(pts: List<Offset>) {
    moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
    close()
}

private val TEAL = Color(0xFF3FBCCB)
private val TAFFY = Color(0xFFF4A6B8)
private val BERRY = Color(0xFF93A3E6)

/** The State of Maine, roughly: (longitude, latitude) round the border from Kittery, clockwise. */
private val MAINE_OUTLINE = listOf(
    -70.70f to 43.07f, -70.82f to 43.24f, -70.98f to 43.38f, -70.96f to 43.57f, -71.01f to 44.28f, -71.03f to 45.0f, -71.08f to 45.30f,
    -70.86f to 45.24f, -70.80f to 45.42f, -70.64f to 45.40f, -70.55f to 45.66f, -70.38f to 45.74f, -70.28f to 45.96f, -70.31f to 46.20f,
    -70.05f to 46.42f, -70.0f to 46.70f, -69.23f to 47.46f, -69.05f to 47.30f, -69.04f to 47.42f, -68.90f to 47.18f, -68.58f to 47.28f,
    -68.33f to 47.36f, -67.95f to 47.19f, -67.79f to 47.07f, -67.78f to 45.94f, -67.75f to 45.82f, -67.80f to 45.68f, -67.43f to 45.60f,
    -67.42f to 45.38f, -67.48f to 45.28f, -67.34f to 45.13f, -67.16f to 45.16f, -66.98f to 44.82f, -67.20f to 44.64f, -67.40f to 44.60f,
    -67.57f to 44.52f, -67.85f to 44.50f, -68.04f to 44.34f, -68.20f to 44.45f, -68.32f to 44.24f, -68.50f to 44.38f, -68.56f to 44.24f,
    -68.72f to 44.34f, -68.82f to 44.48f, -68.98f to 44.42f, -69.06f to 44.10f, -69.22f to 43.94f, -69.40f to 43.98f, -69.52f to 43.84f,
    -69.78f to 43.76f, -69.90f to 43.84f, -70.18f to 43.66f, -70.22f to 43.56f, -70.36f to 43.44f, -70.52f to 43.30f, -70.58f to 43.14f,
)

object Boards {
    // ---- Sebec Lake: the classic, a racetrack round a carved map --------------------------------------------------
    val SEBEC = BoardDesign(
        id = "sebec", name = "Sebec Lake", place = "Piscataquis County",
        blurb = "Hard maple with the lake poured in teal, a racetrack round the map and pinecones in the corners.",
        aspect = 1.42f, wood = Wood.MAPLE, seed = 11,
        shape = { w, h -> rounded(1.5f, 1.5f, w - 1.5f, h - 1.5f, 5f) },
        track = { w, h -> TrackSpec(listOf(Segment(loop(14f, 13.5f, w - 14f, h - 13.5f, 10f, 0.06f), 120)), laneGap = 2.7f, holeR = 0.55f, numbers = Numbers.TENS) },
        decor = {
            val inset = roundRect(20f, 19.5f, 80f, H - 19.5f, 4f)
            inset(inset)
            mapLines(Rect(20f, 19.5f, 80f, H - 19.5f), 3)
            lake(Outlines.SEBEC, Offset(48f, 38f), 54f, towns = listOf(
                Town("Bowerbank", 45.322, -69.25, dy = -1.4f), Town("Sebec", 45.268, -69.117, dx = -1.2f, dy = 1.8f),
            ))
            text("Sebec Lake", Offset(63f, 25f), 3.6f)
            text("Maine", Offset(63f, 29f), 2.1f, Face.TITLE, weight = FontWeight.Normal)
            text("45°17′N  69°15′W", Offset(33f, H - 23f), 1.5f, Face.HAND, carved = false)
            pinecone(Offset(8.5f, 9f), 9f, -25f, wood)
            pinecone(Offset(W - 8.5f, 9f), 9f, 25f, wood)
            sil(Sil.MOOSE, 2.5f, H - 13f, 11f)
            compassRose(Offset(W - 7.5f, H - 7.5f), 5.2f, wood)
        },
        over = {
            text("START", geometry().start[0][0] + Offset(-5f, 1.4f), 1.4f, Face.LABEL)
            text("121", design.geometry.finish + Offset(-3f, 0f), 1.6f, Face.LABEL, weight = FontWeight.Bold)
        },
    )

    // ---- Moosehead: an oval track, the lake and a bull moose ------------------------------------------------------
    val MOOSEHEAD = BoardDesign(
        id = "moosehead", name = "Moosehead Lake", place = "Greenville",
        blurb = "Black cherry, an oval track round Maine's biggest lake and the bull moose it's named for.",
        aspect = 1.42f, wood = Wood.CHERRY, seed = 23, grainAngle = 4f,
        shallow = Color(0xFF5FC2C4), deep = Color(0xFF0B4058),
        shape = { w, h -> rounded(1.5f, 1.5f, w - 1.5f, h - 1.5f, 9f) },
        track = { w, h -> TrackSpec(listOf(Segment(Geo.ellipse(Offset(w / 2, h / 2 - 1f), 41f, 25f, -84f, 330f, 500), 120)), laneGap = 2.7f, holeR = 0.55f, numbers = Numbers.TENS, numberSide = -1) },
        decor = {
            val c = Offset(W / 2, H / 2 - 1f)
            val oval = Path().apply { addOval(Rect(c.x - 34.5f, c.y - 18.5f, c.x + 34.5f, c.y + 18.5f)) }
            inset(oval)
            mapLines(Rect(c.x - 31f, c.y - 15f, c.x + 31f, c.y + 15f), 8, 8)
            lake(Outlines.MOOSEHEAD, Offset(35f, c.y), 33f, towns = listOf(
                Town("Greenville", 45.459, -69.590, dy = 0.5f), Town("Rockwood", 45.676, -69.741, dx = -1.2f), Town("Mt. Kineo", 45.700, -69.733, dx = 2.6f, dy = -1.6f, peak = true),
            ), townSize = 1.6f)
            sil(Sil.MOOSE, 51f, c.y - 15f, 25f)
            text("Moosehead Lake", Offset(63.5f, c.y + 13f), 2.8f)
            text("Maine", Offset(63.5f, c.y + 16.2f), 1.7f, weight = FontWeight.Normal)
            sil(Sil.PINE, 2.5f, 3f, 9f)
            sil(Sil.PINE, W - 11.5f, 3f, 9f)
            sil(Sil.CANOE, 1.5f, H - 13f, 15f, 9f)
            compassRose(Offset(W - 7f, H - 7f), 4.6f, wood)
        },
        over = {
            text("START", geometry().start[0][0] + Offset(-4.5f, 0f), 1.4f, Face.LABEL)
        },
    )

    // ---- Schoodic Lake: the lake inside a carved trout ------------------------------------------------------------
    val SCHOODIC = BoardDesign(
        id = "schoodic", name = "Schoodic Lake", place = "Brownville",
        blurb = "White birch with a walnut brook trout set in the middle, the lake swimming inside it.",
        aspect = 1.42f, wood = Wood.BIRCH, seed = 37,
        shallow = Color(0xFF59B8C9), deep = Color(0xFF115A73),
        shape = { w, h -> rounded(1.5f, 1.5f, w - 1.5f, h - 1.5f, 4f) },
        track = { w, h -> TrackSpec(listOf(Segment(loop(10f, 11f, w - 10f, h - 11f, (h - 22f) / 2f, 0.07f), 120)), laneGap = 2.7f, holeR = 0.6f, numbers = Numbers.FIVES, numberSide = -1) },
        decor = {
            // The trout: a raised walnut panel with its own grain.
            val fish = Rect(16f, 17f, 85f, H - 15f)
            val fishPath = Path().apply {
                addPath(Sil.TROUT.path)
            }
            withTransform({ translate(fish.left, fish.top); scale(fish.width / 100f, fish.height / 100f, Offset.Zero) }) {
                drawPath(fishPath, Color.Black.copy(alpha = 0.3f), style = Stroke(3f))
                drawPath(fishPath, Wood.WALNUT.base)
                clipPath(fishPath) { woodGrain(Rect(0f, 0f, 100f, 100f), Wood.WALNUT, 5) }
                drawPath(fishPath, Wood.WALNUT.light.copy(alpha = 0.5f), style = Stroke(0.8f))
                // Gill line and spots.
                drawPath(Geo.path(listOf(Offset(80f, 39f), Offset(77f, 50f), Offset(80f, 61f))), Wood.WALNUT.light.copy(alpha = 0.6f), style = Stroke(0.7f))
                drawCircle(Wood.WALNUT.light, 2.2f, Offset(86f, 47f))
                drawCircle(Color.Black, 1.2f, Offset(86f, 47f))
            }
            lake(Outlines.SCHOODIC, Offset(47f, H / 2 + 0.5f), 37f, rotation = -90f, townSize = 1.5f
            )
            text("Schoodic Lake", Offset(W / 2, 17.6f), 2.7f)
            sil(Sil.LOON, 3f, 2f, 9f)
            pinecone(Offset(W - 7.5f, 7.8f), 7.5f, 25f, wood)
            sil(Sil.PINE, 2f, H - 10.5f, 8f)
            compassRose(Offset(W - 6f, H - 6f), 4f, wood)
            text("Brownville · Piscataquis County · Maine", Offset(W / 2, H - 16.5f), 1.6f, Face.HAND, carved = false)
        },
    )

    // ---- Eastport: a copy of a real hand-made board ---------------------------------------------------------------
    val EASTPORT = BoardDesign(
        id = "eastport", name = "Eastport", place = "Moose Island",
        blurb = "Round, walnut-stained with teal inlay and two rings of track: the easternmost city in the U.S.",
        aspect = 1f, wood = Wood.STAINED, seed = 1893, bevel = 2.2f,
        shallow = TEAL, deep = Color(0xFF2A9DAD),
        shape = { w, h -> Path().apply { addOval(Rect(1.5f, 1.5f, w - 1.5f, h - 1.5f)) } },
        track = { w, h ->
            val c = Offset(w / 2, h / 2)
            val outer = Geo.circle(c, 41.5f, 9f, 338f, 600)
            val inner = Geo.circle(c, 32f, 18f, 318f, 500)
            val hop = Geo.chaikin(listOf(outer.last(), Geo.rotate(Offset(37f, 0f), 357f) + c, Geo.rotate(Offset(33f, 0f), 8f) + c, inner.first()), 3)
            val startC = c + Offset(45.3f, -1.5f)
            TrackSpec(
                listOf(Segment(outer, 60), Segment(inner, 60)), laneGap = 3.6f, holeR = 0.66f, connectors = listOf(hop),
                numbers = Numbers.PILLS, paint = TEAL, groove = false,
                startHoles = listOf(listOf(startC + Offset(-1.4f, -2.4f), startC + Offset(1.4f, -2.4f)), listOf(startC + Offset(-1.4f, 0.6f), startC + Offset(1.4f, 0.6f))),
                finish = c + Geo.rotate(Offset(29f, 0f), -24f),
            )
        },
        decor = {
            val c = Offset(50f, 50f)
            // Darker scorched rim, as the stain pools at the edge.
            drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color.Transparent, Color(0xAA1A0C03)), c, 50f), 49f, c)
            // The medallion.
            drawCircle(Color.Black.copy(alpha = 0.3f), 25.6f, c + Offset(0.4f, 0.6f))
            drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFF6ED3DE), TEAL, Color(0xFF2B98A7)), c - Offset(6f, 8f), 34f), 25f, c)
            clipPath(Path().apply { addOval(Rect(c.x - 25f, c.y - 25f, c.x + 25f, c.y + 25f)) }) { woodGrain(Rect(25f, 25f, 75f, 75f), Wood.BIRCH.let { it }, 77) }
            drawCircle(Color(0x332B98A7), 25f, c)
            drawCircle(Color(0xFF1C5E66), 25f, c, style = Stroke(0.5f))
            drawCircle(Color(0xFF1C5E66), 22.5f, c, style = Stroke(0.25f))
            engraveInk(Sil.MOOSE, Rect(32f, 30f, 66f, 66f), Color(0xFF173C42))
            val ink = Color(0xFF173C42)
            arcText("EASTPORT, MAINE", c, 19.6f, 18f, 3.4f, Face.TITLE, ink, clockwise = false)
            arcText("44°54′49″N  67°0′14″W", c, 20.3f, 222f, 1.8f, Face.LABEL, ink)
            arcText("MARCH 18, 1893", c, 20f, 128f, 1.9f, Face.TITLE, ink, clockwise = false)
            // The start tab.
            val s = Offset(95.3f, 49f)
            drawRoundRect(TEAL, Offset(s.x - 3.6f, s.y - 4.2f), androidx.compose.ui.geometry.Size(7.2f, 7.4f), androidx.compose.ui.geometry.CornerRadius(1.2f))
        },
        over = {
            val c = Offset(50f, 50f)
            text("START", Offset(91f, 56f), 1.5f, Face.LABEL, Color(0xFF173C42), rotation = 90f, carved = false)
            text("FINISH", c + Geo.rotate(Offset(29f, 0f), -33f), 1.3f, Face.LABEL, Color(0xFFBDEFF4), rotation = 57f, carved = false)
            text("SKUNK", geometry().centre[90] + geometry().normal[90] * 4.2f, 1.2f, Face.LABEL, Color(0xFFBDEFF4), carved = false)
        },
    )

    // ---- Rangeley: a long three-street board under the mountains --------------------------------------------------
    val RANGELEY = BoardDesign(
        id = "rangeley", name = "Rangeley Lake", place = "Rangeley & Oquossoc",
        blurb = "Brown ash, three long streets in the old style, and loons on the lake below Saddleback.",
        aspect = 1.5f, wood = Wood.ASH, seed = 51, grainAngle = -2f,
        shallow = Color(0xFF6BB7D6), deep = Color(0xFF123F6B),
        shape = { w, h -> rounded(1.5f, 1.5f, w - 1.5f, h - 1.5f, (h - 3f) * 0.12f) },
        track = { w, h ->
            val ys = listOf(h - 27f, h - 18.5f, h - 10f)
            val l = 11f; val r = w - 11f
            val s1 = listOf(Offset(l, ys[0]), Offset(r, ys[0]))
            val s2 = listOf(Offset(r, ys[1]), Offset(l, ys[1]))
            val s3 = listOf(Offset(l, ys[2]), Offset(r, ys[2]))
            val turn1 = Geo.ellipse(Offset(r, (ys[0] + ys[1]) / 2), 4.2f, (ys[1] - ys[0]) / 2, -90f, 180f, 40)
            val turn2 = Geo.ellipse(Offset(l, (ys[1] + ys[2]) / 2), 4.2f, (ys[2] - ys[1]) / 2, 270f, -180f, 40)
            TrackSpec(listOf(Segment(Geo.resample(s1, 100), 40), Segment(Geo.resample(s2, 100), 40), Segment(Geo.resample(s3, 100), 40)),
                laneGap = 2.5f, holeR = 0.55f, connectors = listOf(turn1, turn2), numbers = Numbers.TENS, numberSide = -1,
                finish = Offset(r + 4f, ys[2]))
        },
        decor = {
            // Saddleback behind the lake, then the lake itself.
            sil(Sil.MOUNTAINS, 18f, 3.5f, 64f, 18f, alpha = 0.28f)
            val lakeBand = roundRect(8f, 9f, W - 8f, H - 33f, 3f)
            inset(lakeBand, 0.6f)
            mapLines(Rect(8f, 9f, W - 8f, H - 33f), 51, 7, 0.25f)
            lake(Outlines.RANGELEY, Offset(W / 2 + 12f, (9f + H - 33f) / 2 + 0.5f), 44f, towns = listOf(
                Town("Rangeley", 44.966, -70.643, dx = 1.4f), Town("Oquossoc", 44.965, -70.775, dx = -1.2f, dy = 1.8f),
            ), shallow = shallow, deep = deep)
            sil(Sil.LOON, 58f, 15.5f, 7f, alpha = 0.95f, fill = Color(0xFF14202A))
            sil(Sil.LOON, 66f, 18f, 5f, flip = true, alpha = 0.95f, fill = Color(0xFF14202A))
            text("Rangeley Lake", Offset(19.5f, 15.5f), 2.9f)
            text("Franklin County, Maine", Offset(19.5f, 19f), 1.5f, Face.HAND, carved = false)
            text("Height of Land", Offset(W - 18f, 5.8f), 1.5f, Face.HAND, carved = false)
        },
        over = {
            text("START", geometry().start[0][0] + Offset(1.5f, -3.2f), 1.4f, Face.LABEL)
        },
    )

    // ---- Sebago: the track follows the shoreline itself -----------------------------------------------------------
    val SEBAGO = BoardDesign(
        id = "sebago", name = "Sebago Lake", place = "Standish, Raymond & Naples",
        blurb = "White oak, with the peg track following Sebago's own shoreline round deep navy water.",
        aspect = 1.25f, wood = Wood.WHITE_OAK, seed = 64, grainAngle = 90f,
        shallow = Color(0xFF3F86B5), deep = Color(0xFF0B2547),
        shape = { w, h -> Path().apply { addOval(Rect(1.5f, 1.5f, w - 1.5f, h - 1.5f)) } },
        track = { w, h ->
            val o = Outlines.SEBAGO
            val c = Offset(w / 2, h / 2)
            val size = 50f
            val pts = (0 until o.outer.size / 2).map { c + Offset((o.outer[it * 2] - o.width / 2) * size, (o.outer[it * 2 + 1] - o.height / 2) * size) }
            // Push the shoreline outward and smooth it until it's a path pegs can follow.
            var ring = Geo.resample(convexHull(pts).let { it + it.first() }, 90).dropLast(1)
            repeat(3) { ring = ring.indices.map { i -> (ring[(i - 2 + ring.size) % ring.size] + ring[(i - 1 + ring.size) % ring.size] + ring[i] + ring[(i + 1) % ring.size] + ring[(i + 2) % ring.size]) / 5f } }
            val cen = ring.fold(Offset.Zero) { a, b -> a + b } / ring.size.toFloat()
            ring = ring.map { p -> val v = p - cen; val d = v.getDistance().coerceAtLeast(0.01f); cen + v + v / d * 6.5f + Offset(v.x * 0.12f, 0f) }
            val closed = Geo.resample(Geo.chaikin(ring, 3, closed = true), 700)
            // Begin at the bottom of the lake.
            val startIdx = closed.indices.maxBy { closed[it].y - kotlin.math.abs(closed[it].x - cen.x) * 0.4f }
            val rot = closed.drop(startIdx) + closed.take(startIdx)
            TrackSpec(listOf(Segment(rot.take((rot.size * 0.91f).toInt()), 120)), laneGap = 2.4f, holeR = 0.5f, numbers = Numbers.NONE)
        },
        decor = {
            val c = Offset(W / 2, H / 2)
            lake(Outlines.SEBAGO, c, 50f, towns = listOf(
                Town("Naples", 43.971, -70.609, dx = -1.2f), Town("Raymond", 43.901, -70.470, dx = -1.4f, dy = -1.6f), Town("Standish", 43.778, -70.555, dx = 1.2f, dy = 1.4f),
            ), townSize = 1.5f)
            sil(Sil.SAILBOAT, c.x + 6f, c.y - 9f, 6f, alpha = 0.9f, fill = Color(0xFFE9E2D2))
            sil(Sil.SAILBOAT, c.x - 3f, c.y + 4f, 4.5f, flip = true, alpha = 0.9f, fill = Color(0xFFE9E2D2))
            text("SEBAGO", Offset(14f, H / 2 - 2f), 3.2f, rotation = -90f)
            text("LAKE", Offset(W - 14f, H / 2 - 2f), 3.2f, rotation = 90f)
            text("Cumberland County · Maine", Offset(W / 2, H - 6f), 1.6f, Face.HAND, carved = false)
            sil(Sil.PINE, 15f, 9f, 7f)
            sil(Sil.PINE, W - 22f, 9f, 7f)
        },
        over = {
            text("START", geometry().start[0][0] + Offset(-5f, 0.6f), 1.3f, Face.LABEL)
            text("121", design.geometry.finish + Offset(4f, 0f), 1.4f, Face.LABEL, weight = FontWeight.Bold)
        },
    )

    // ---- Bar Harbor: the island raised from an epoxy sea, two laps round ------------------------------------------
    val BAR_HARBOR = BoardDesign(
        id = "barharbor", name = "Bar Harbor", place = "Mount Desert Island",
        blurb = "Weathered driftwood, Mount Desert Island standing out of a poured sea, and two laps of track.",
        aspect = 1.42f, wood = Wood.DRIFTWOOD, seed = 4609,
        shallow = Color(0xFF5FA9B8), deep = Color(0xFF1C4A5E),
        shape = { w, h -> rounded(1.5f, 1.5f, w - 1.5f, h - 1.5f, 3f) },
        track = { w, h ->
            val outer = loop(7.5f, 7f, w - 7.5f, h - 7f, 6f, 0.06f, 0.95f)
            val inner = loop(14.5f, 13.5f, w - 14.5f, h - 13.5f, 5f, 0.035f, 0.95f)
            TrackSpec(listOf(Segment(outer, 60), Segment(inner, 60)), laneGap = 2.4f, holeR = 0.55f,
                connectors = listOf(Geo.chaikin(listOf(outer.last(), outer.last() + Offset(4f, 0f), inner.first() + Offset(-4f, 0f), inner.first()), 3)),
                numbers = Numbers.NONE)
        },
        decor = {
            val sea = roundRect(20f, 18.5f, W - 20f, H - 18.5f, 2.5f)
            inset(sea, 0.6f)
            water(sea, Rect(20f, 18.5f, W - 20f, H - 18.5f), shallow, deep, wood)
            waters += WaterArea(sea, Rect(20f, 18.5f, W - 20f, H - 18.5f))
            // The island is wood, standing proud of the water.
            val o = Outlines.MOUNT_DESERT
            val c = Offset(W / 2 - 3f, H / 2 + 0.5f)
            val island = outlinePath(o, o.outer, c, 30f, 0f)
            drawPath(island, Color.Black.copy(alpha = 0.35f), style = Stroke(1.2f))
            drawPath(island, Wood.PINE.base)
            clipPath(island) { woodGrain(island.getBounds(), Wood.PINE, 3) }
            drawPath(island, Wood.PINE.dark, style = Stroke(0.35f))
            val proj = o.projection
            fun at(lat: Double, lon: Double) = c + Offset((proj.x(lon) - o.width / 2) * 30f, (proj.y(lat) - o.height / 2) * 30f)
            listOf(Triple("Bar Harbor", at(44.387, -68.204), 1f), Triple("Southwest Harbor", at(44.279, -68.326), -1f), Triple("Northeast Harbor", at(44.293, -68.289), 1f)).forEach { (n, p, side) ->
                drawCircle(Color(0xFF2B1A0C), 0.45f, p)
                text(n, p + Offset(side * (n.length * 0.42f + 1f), if (n.startsWith("North")) 1.6f else 0f), 1.45f, Face.HAND, Color(0xFFEFE8D8), carved = false)
            }
            val cad = at(44.352, -68.225)
            drawPath(Path().apply { moveTo(cad.x, cad.y - 1.3f); lineTo(cad.x + 1.2f, cad.y + 0.7f); lineTo(cad.x - 1.2f, cad.y + 0.7f); close() }, Color(0xFF2B1A0C))
            text("Cadillac Mtn.", cad + Offset(0f, -2.4f), 1.3f, Face.HAND, Color(0xFF2B1A0C), carved = false)
            sil(Sil.LIGHTHOUSE, 22f, H - 31f, 9f, 11f, alpha = 0.95f, fill = Color(0xFFF1ECE2))
            sil(Sil.SAILBOAT, W - 31f, 21f, 8f, alpha = 0.9f, fill = Color(0xFFF1ECE2))
            sil(Sil.BUOY, W - 28f, H - 29f, 4f, 6f, alpha = 1f, fill = Color(0xFFD9442B))
            sil(Sil.BUOY, W - 24f, H - 27.5f, 3.4f, 5f, alpha = 1f, fill = Color(0xFFF2C230))
            text("BAR HARBOR", Offset(W / 2, 22.2f), 2.4f, Face.TITLE, Color(0xFFEFE8D8), carved = false)
            text("Acadia · Maine", Offset(W / 2, H - 21.2f), 1.5f, Face.HAND, Color(0xFFEFE8D8), carved = false)
        },
    )

    // ---- Katahdin: switchbacks up the mountain and down -----------------------------------------------------------
    val KATAHDIN = BoardDesign(
        id = "katahdin", name = "Katahdin", place = "Millinocket · Baxter State Park",
        blurb = "A live-edge walnut slab with the track switchbacking up Maine's highest peak and down the other side.",
        aspect = 1.42f, wood = Wood.WALNUT, seed = 5267, grainAngle = -6f,
        shape = { w, h ->
            Path().apply {
                val r = kotlin.random.Random(5267)
                val top = (0..40).map { i -> val x = 2f + (w - 4f) * i / 40; Offset(x, 4f + r.nextFloat() * 1.8f + 2.2f * kotlin.math.sin(i * 0.45f)) }
                addPoints(listOf(Offset(2f, h - 5f)) + top + listOf(Offset(w - 2f, h - 5f), Offset(w - 6f, h - 1.5f), Offset(6f, h - 1.5f)))
            }
        },
        track = { w, h ->
            val pts = Geo.line(Offset(11f, h - 10f), Offset(38f, h - 12f)) +
                uTurn(Offset(38f, h - 12f), Offset(38f, h - 23f), 1f) +
                Geo.line(Offset(38f, h - 23f), Offset(18f, h - 26f)) +
                uTurn(Offset(18f, h - 26f), Offset(18f, h - 37f), -1f) +
                Geo.line(Offset(18f, h - 37f), Offset(50f, h - 52f)) +
                Geo.line(Offset(50f, h - 52f), Offset(82f, h - 37f)) +
                uTurn(Offset(82f, h - 37f), Offset(82f, h - 26f), 1f) +
                Geo.line(Offset(82f, h - 26f), Offset(62f, h - 23f)) +
                uTurn(Offset(62f, h - 23f), Offset(62f, h - 12f), -1f) +
                Geo.line(Offset(62f, h - 12f), Offset(88f, h - 10f))
            TrackSpec(listOf(Segment(Geo.resample(Geo.chaikin(pts, 2), 900), 120)), laneGap = 2.4f, holeR = 0.52f, numbers = Numbers.NONE)
        },
        decor = {
            sil(Sil.MOUNTAINS, 4f, 6f, 92f, H - 14f, alpha = 0.22f)
            text("KATAHDIN", Offset(W / 2, 9.9f), 3.4f, Face.TITLE)
            text("5,267 ft · Baxter State Park", Offset(W / 2, 13.9f), 1.6f, Face.HAND, carved = false)
            sil(Sil.BEAR, 42f, H - 16.5f, 16f, 10f)
            sil(Sil.PINE, 4f, 12f, 7f); sil(Sil.PINE, 9f, 15f, 5f)
            sil(Sil.PINE, W - 11f, 12f, 7f); sil(Sil.PINE, W - 14f, 15f, 5f)
            text("Millinocket, Maine", Offset(W / 2, H - 4.4f), 1.5f, Face.HAND, carved = false)
        },
        over = {
            text("START", geometry().start[0][0] + Offset(0.6f, -3f), 1.3f, Face.LABEL)
            text("121", design.geometry.finish + Offset(0f, -2.8f), 1.4f, Face.LABEL, weight = FontWeight.Bold)
            text("SUMMIT", geometry().centre[60] + Offset(0f, -3.3f), 1.2f, Face.LABEL)
        },
    )

    // ---- Camden: waves across the harbour, windjammers under sail --------------------------------------------------
    val CAMDEN = BoardDesign(
        id = "camden", name = "Camden Harbor", place = "Where the mountains meet the sea",
        blurb = "Boat-shop teak with an arched top for the Camden Hills, three rolling waves of track and the windjammer fleet.",
        aspect = 1.42f, wood = Wood.TEAK, seed = 1769,
        shallow = Color(0xFF4AA39A), deep = Color(0xFF0F4A48),
        shape = { w, h ->
            Path().apply {
                moveTo(1.5f, 16f)
                cubicTo(20f, 1f, w - 20f, 1f, w - 1.5f, 16f)
                lineTo(w - 1.5f, h - 5f); quadraticTo(w - 1.5f, h - 1.5f, w - 5f, h - 1.5f)
                lineTo(5f, h - 1.5f); quadraticTo(1.5f, h - 1.5f, 1.5f, h - 5f); close()
            }
        },
        track = { w, h ->
            fun wave(y: Float, l2r: Boolean): List<Offset> = (0..200).map { i ->
                val t = i / 200f
                val x = if (l2r) 12f + t * (w - 24f) else w - 12f - t * (w - 24f)
                Offset(x, y + 3.2f * kotlin.math.sin((x - 12f) / (w - 24f) * 2 * kotlin.math.PI.toFloat() * 2.5f))
            }
            val rows = listOf(wave(24f, true), wave(37f, false), wave(50f, true))
            val turn1 = Geo.chaikin(listOf(rows[0].last(), rows[0].last() + Offset(4.5f, 2f), rows[1].first() + Offset(4.5f, -2f), rows[1].first()), 3)
            val turn2 = Geo.chaikin(listOf(rows[1].last(), rows[1].last() + Offset(-4.5f, 2f), rows[2].first() + Offset(-4.5f, -2f), rows[2].first()), 3)
            TrackSpec(rows.map { Segment(it, 40) }, laneGap = 2.5f, holeR = 0.55f, connectors = listOf(turn1, turn2), numbers = Numbers.NONE)
        },
        decor = {
            sil(Sil.MOUNTAINS, 22f, 3f, 56f, 13f, alpha = 0.3f)
            text("CAMDEN HARBOR", Offset(W / 2, 15.2f), 3f)
            sil(Sil.LIGHTHOUSE, W - 15f, 9.5f, 8f, 10f)
            val sea = Path().apply { addRect(Rect(0f, H - 15.5f, W, H)) }
            water(sea, Rect(0f, H - 15.5f, W, H), shallow, deep, wood)
            waters += WaterArea(sea, Rect(4f, H - 15f, W - 4f, H - 3f))
            for ((i, x) in listOf(14f, 40f, 66f).withIndex()) {
                sil(Sil.SAILBOAT, x, H - 15f - i % 2, 13f - i, 11f, flip = i == 1, alpha = 0.95f, fill = Color(0xFFF3EBDD))
            }
        },
        over = {
            text("START", geometry().start[0][0] + Offset(-0.5f, 3.6f), 1.3f, Face.LABEL)
            text("121", design.geometry.finish + Offset(0f, 3f), 1.4f, Face.LABEL, weight = FontWeight.Bold)
        },
    )

    // ---- Allagash: two banks of a wild river ----------------------------------------------------------------------
    val ALLAGASH = BoardDesign(
        id = "allagash", name = "Allagash Wilderness", place = "The North Maine Woods",
        blurb = "A pine plank with a river poured down its length; one player pegs each bank, all the way to Allagash Falls.",
        aspect = 1.42f, wood = Wood.PINE, seed = 92, grainAngle = 2f,
        shallow = Color(0xFF6DA9B8), deep = Color(0xFF1E5263),
        shape = { w, h -> rounded(1.5f, 1.5f, w - 1.5f, h - 1.5f, 2f) },
        track = { w, h -> TrackSpec(listOf(Segment(allagashRiver(w, h), 120)), laneGap = 5f, holeR = 0.55f, numbers = Numbers.NONE, groove = false, independentLanes = true) },
        decor = {
            // The river: a ribbon of water down the middle of the track.
            val river = allagashRiver(W, H)
            val ribbon = Geo.path(river)
            drawPath(ribbon, deep, style = Stroke(3.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            drawPath(ribbon, shallow.copy(alpha = 0.55f), style = Stroke(2.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            drawPath(ribbon, Color.White.copy(alpha = 0.25f), style = Stroke(0.3f))
            for (side in listOf(1f, -1f)) {
                val bank = river.indices.map { i -> val (p, t) = Geo.at(river, Geo.length(river.subList(0, i + 1))); p + Offset(-t.y, t.x) * (1.95f * side) }
                groove(Geo.path(bank), wood, 0.3f, 0.7f)
            }
            text("ALLAGASH", Offset(W - 22f, 9.5f), 3.2f)
            text("Wilderness Waterway", Offset(W - 22f, 13.2f), 1.7f, Face.HAND, carved = false)
            val (cp, ct) = Geo.at(river, Geo.length(river) * 0.6f)
            val self = this
            withTransform({ rotate(Geo.angleDeg(ct), cp) }) { self.sil(Sil.CANOE, cp.x - 4.5f, cp.y - 4.2f, 9f, 7f, alpha = 0.95f, fill = Color(0xFFB5442C)) }
            sil(Sil.MOOSE, 6f, H - 22f, 12f, alpha = 0.7f)
            sil(Sil.PINE, W - 36f, H - 19f, 8f); sil(Sil.PINE, W - 29f, H - 16f, 6f)
            sil(Sil.PINE, 52f, 20f, 7f); sil(Sil.PINE, 58f, 22f, 5f)
            text("Allagash Falls", Offset(W - 12f, H - 4.5f), 1.6f, Face.HAND, carved = false)
            text("Telos", Offset(9f, 4.2f), 1.6f, Face.HAND, carved = false)
        },
        over = {
            text("START", geometry().start[0][0] + Offset(1f, -4.6f), 1.3f, Face.LABEL)
        },
    )

    // ---- Pemaquid Point: a spiral in to the light -----------------------------------------------------------------
    val PEMAQUID = BoardDesign(
        id = "pemaquid", name = "Pemaquid Point", place = "Bristol · Lincoln County",
        blurb = "An eight-sided birch board with the track spiralling in to the lighthouse, like the stairs up its tower.",
        aspect = 1f, wood = Wood.BIRCH, seed = 1827, bevel = 2f,
        shallow = Color(0xFF3F7FA6), deep = Color(0xFF10304F),
        shape = { w, h -> Path().apply { addPoints(Geo.circle(Offset(w / 2, h / 2), 52.4f, 22.5f, 315f, 7)) } },
        track = { w, h ->
            val c = Offset(w / 2, h / 2)
            val spiral = (0..900).map { i ->
                val t = i / 900f
                c + Geo.rotate(Offset(43f - 17.2f * t, 0f), 100f + 860f * t)
            }
            TrackSpec(listOf(Segment(spiral, 120)), laneGap = 2.6f, holeR = 0.63f, numbers = Numbers.NONE,
                finish = c + Geo.rotate(Offset(24.9f, 0f), 100f + 860f + 19f))
        },
        decor = {
            val c = Offset(50f, 50f)
            val r = 20.5f
            val deepC = deep; val shallowC = shallow
            val disc = Path().apply { addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r)) }
            drawCircle(Color.Black.copy(alpha = 0.3f), r + 0.6f, c + Offset(0.4f, 0.6f))
            // Night sky over the sea.
            drawCircle(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF0B1B33), Color(0xFF1D4468), Color(0xFF3B6E8F)), c.y - r, c.y + r), r, c)
            clipPath(disc) {
                val rnd = kotlin.random.Random(1827)
                repeat(26) { drawCircle(Color.White.copy(alpha = 0.35f + rnd.nextFloat() * 0.5f), 0.12f + rnd.nextFloat() * 0.16f, Offset(c.x - r + rnd.nextFloat() * 2 * r, c.y - r + rnd.nextFloat() * r * 1.1f)) }
                // The beam, sweeping out both ways from the lantern.
                val lamp = Offset(50f, 43.6f)
                val beam = Color(0xFFFFE9A8)
                drawPath(Path().apply { moveTo(lamp.x, lamp.y); lineTo(c.x - r, lamp.y - 6.5f); lineTo(c.x - r, lamp.y + 2.5f); close() },
                    androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Transparent, beam.copy(alpha = 0.55f)), c.x - r, lamp.x))
                drawPath(Path().apply { moveTo(lamp.x, lamp.y); lineTo(c.x + r, lamp.y - 2.5f); lineTo(c.x + r, lamp.y + 6.5f); close() },
                    androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(beam.copy(alpha = 0.55f), Color.Transparent), lamp.x, c.x + r))
                // The sea and the striped ledges the point is known for.
                val sea = Path().apply { addRect(Rect(c.x - r, 61.5f, c.x + r, c.y + r)) }
                drawPath(sea, deepC)
                drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(shallowC.copy(alpha = 0.7f), Color.Transparent), 61.5f, 70f), Offset(c.x - r, 61.5f), androidx.compose.ui.geometry.Size(2 * r, 9f))
                for (i in 0..4) drawPath(Geo.path(Geo.wobble(Offset(c.x - r, 63f + i * 1.5f), Offset(c.x + r, 63.4f + i * 1.5f), 40 + i, 0.5f)), Color.White.copy(alpha = 0.22f), style = Stroke(0.16f))
                val ledge = Path().apply { moveTo(34f, 62.2f); quadraticTo(43f, 57.4f, 50f, 58.2f); quadraticTo(60f, 57.6f, 67f, 62.2f); close() }
                drawPath(ledge, Color(0xFF6E6A66))
                clipPath(ledge) { for (i in 0..9) drawLine(Color(0xFFCFC9BE).copy(alpha = 0.6f), Offset(34f + i * 3.6f, 63f), Offset(39f + i * 3.6f, 56f), strokeWidth = 0.3f) }
            }
            waters += WaterArea(Path().apply { addOval(Rect(c.x - 17f, 62f, c.x + 17f, c.y + r - 1f)) }, Rect(c.x - 15f, 62.5f, c.x + 15f, c.y + r - 1.5f))
            engraveInk(Sil.LIGHTHOUSE, Rect(39.5f, 38.6f, 61.5f, 60.6f), Color(0xFFF6F1E4))
            drawCircle(Color(0xFFFFE9A8), 0.9f, Offset(50f, 43.6f))
            drawCircle(Color(0xFF0B1B33), r, c, style = Stroke(0.6f))
            drawCircle(Color(0xFFF6F1E4).copy(alpha = 0.6f), r - 1.1f, c, style = Stroke(0.18f))
            arcText("PEMAQUID POINT", c, 16.6f, 270f, 2.5f, Face.TITLE, Color(0xFFF6F1E4))
            arcText("BRISTOL · MAINE · 1827", c, 17.6f, 90f, 1.45f, Face.LABEL, Color(0xFFE6EEF2), clockwise = false)
        },
        over = {
            text("START", geometry().start[0][0] + Offset(3.4f, 2.6f), 1.3f, Face.LABEL)
            text("121", design.geometry.finish + Offset(0.4f, -2.6f), 1.3f, Face.LABEL, weight = FontWeight.Bold)
        },
    )

    // ---- Stonington: four streets below the Thorofare -------------------------------------------------------------
    val STONINGTON = BoardDesign(
        id = "stonington", name = "Stonington", place = "Deer Isle",
        blurb = "Four short streets, thirty holes each, under the spruce-topped granite islands and painted lobster buoys.",
        aspect = 1.42f, wood = Wood.TEAK, seed = 207, grainAngle = 1f,
        shallow = Color(0xFF58A9B0), deep = Color(0xFF14475A),
        shape = { w, h -> rounded(1.5f, 1.5f, w - 1.5f, h - 1.5f, 3.5f) },
        track = { w, h ->
            val ys = listOf(h - 31.5f, h - 24f, h - 16.5f, h - 9f)
            val l = 13f; val r = w - 12f
            val streets = ys.mapIndexed { i, y -> Geo.resample(if (i % 2 == 0) listOf(Offset(l, y), Offset(r, y)) else listOf(Offset(r, y), Offset(l, y)), 100) }
            fun turn(i: Int) = if (i % 2 == 0) Geo.ellipse(Offset(r, (ys[i] + ys[i + 1]) / 2), 3.9f, (ys[i + 1] - ys[i]) / 2, -90f, 180f, 40)
                else Geo.ellipse(Offset(l, (ys[i] + ys[i + 1]) / 2), 3.9f, (ys[i + 1] - ys[i]) / 2, 270f, -180f, 40)
            TrackSpec(streets.map { Segment(it, 30) }, laneGap = 2.5f, holeR = 0.63f, connectors = listOf(turn(0), turn(1), turn(2)),
                numbers = Numbers.NONE, skunkLine = false, finish = Offset(l - 4.6f, ys[3]))
        },
        decor = {
            text("STONINGTON", Offset(W / 2, 6.3f), 3f)
            val box = Rect(8f, 10.5f, W - 8f, H - 36.5f)
            val sea = roundRect(box.left, box.top, box.right, box.bottom, 2.5f)
            inset(sea, 0.6f)
            water(sea, box, shallow, deep, wood)
            waters += WaterArea(sea, Rect(box.left + 4f, box.top + 12f, box.right - 4f, box.bottom - 1f))
            // Granite islands with their spruce.
            fun island(l: Float, t: Float, w: Float, h: Float, trees: List<Triple<Float, Float, Float>>) {
                val rock = Path().apply { moveTo(l, t + h); quadraticTo(l + w * 0.1f, t, l + w * 0.45f, t + h * 0.12f); quadraticTo(l + w * 0.9f, t - h * 0.1f, l + w, t + h); close() }
                for ((x, y, s) in trees) sil(Sil.PINE, x, y, s, alpha = 1f, fill = Color(0xFF1C3A2B))
                drawPath(rock, Color(0xFFB9B2A6))
                drawPath(rock, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.Black.copy(alpha = 0.3f)), t, t + h))
                drawPath(rock, Color(0xFF4B463F), style = Stroke(0.25f))
                drawLine(Color.White.copy(alpha = 0.5f), Offset(l - 1f, t + h + 0.3f), Offset(l + w + 1f, t + h + 0.3f), strokeWidth = 0.25f)
            }
            island(14f, 21.5f, 22f, 4.5f, listOf(Triple(16.5f, 15.2f, 7f), Triple(22f, 13.4f, 8.6f), Triple(28.5f, 16.2f, 6f)))
            island(60f, 19.5f, 26f, 5f, listOf(Triple(62.5f, 13.6f, 6.6f), Triple(68f, 11.6f, 8.6f), Triple(74.5f, 12.8f, 7.6f), Triple(80f, 15.4f, 5f)))
            sil(Sil.SAILBOAT, 42f, 14f, 9f, alpha = 0.95f, fill = Color(0xFFF3EBDD))
            // Lobster buoys bobbing in the Thorofare.
            val buoys = listOf(Triple(13f, Color(0xFFD9442B), Color(0xFFF4EFE4)), Triple(33f, Color(0xFFF2C230), Color(0xFF1F5E8C)), Triple(67f, Color(0xFF2E8B57), Color(0xFFF4EFE4)), Triple(88f, Color(0xFFF08A24), Color(0xFF1C1C1C)))
            for ((i, b) in buoys.withIndex()) {
                val (x, body, band) = b
                val y = box.bottom - 6.6f + (i % 2) * 1.1f
                val rect = Rect(x - 1.5f, y, x + 1.5f, y + 4.6f)
                engraveInk(Sil.BUOY, rect, body)
                drawRect(band, Offset(x - 0.48f, y + 1.9f), androidx.compose.ui.geometry.Size(0.96f, 0.7f))
                drawOval(Color.White.copy(alpha = 0.45f), Offset(x - 2f, y + 4f), androidx.compose.ui.geometry.Size(4f, 0.9f), style = Stroke(0.15f))
            }
            text("Deer Isle Thorofare", Offset(W / 2, box.bottom - 1.9f), 1.5f, Face.HAND, Color(0xFFEFE8D8), carved = false)
            text("Lobster capital of Maine", Offset(W / 2, H - 3.4f), 1.45f, Face.HAND, carved = false)
        },
        over = {
            val g = geometry()
            text("START", g.start[0][0] + Offset(1.2f, -5.3f), 1.3f, Face.LABEL)
            text("121", design.geometry.finish + Offset(0f, 2.8f), 1.3f, Face.LABEL, weight = FontWeight.Bold)
            // Each street's numbers sit in the gutter above it, so no two streets share one.
            for (i in 10..120 step 10) text("$i", g.centre[i] + Offset(0f, -3.5f), 1.5f, Face.LABEL, weight = FontWeight.Medium)
            text("SKUNK", (g.centre[90] + g.centre[91]) / 2f + Offset(6.6f, 0f), 1.2f, Face.LABEL, rotation = 90f)
        },
    )

    // ---- Belgrade Lakes: a six-sided board for loon country -------------------------------------------------------
    val BELGRADE = BoardDesign(
        id = "belgrade", name = "Belgrade Lakes", place = "Great Pond · Kennebec County",
        blurb = "A six-sided cherry board, the track running round a pond where the loons call at dusk.",
        aspect = 1.1547f, wood = Wood.CHERRY, seed = 1774, grainAngle = -3f, bevel = 2f,
        shallow = Color(0xFF6CB6C9), deep = Color(0xFF123C5A),
        shape = { w, h -> Path().apply { addPoints(Geo.circle(Offset(w / 2, h / 2), 48.5f, 0f, 300f, 5)) } },
        track = { w, h ->
            val c = Offset(w / 2, h / 2)
            fun lap(radius: Float, near: Offset, cover: Float): List<Offset> {
                val ring = Geo.resample(Geo.chaikin(Geo.circle(c, radius, 0f, 300f, 5), 3, closed = true), 700).dropLast(1)
                val startIdx = ring.indices.minBy { (ring[it] - near).getDistance() }
                val rot = ring.drop(startIdx) + ring.take(startIdx)
                return rot.take((rot.size * cover).toInt())
            }
            val outer = lap(42.6f, Offset(c.x + 12f, h), 0.93f)
            val inner = lap(35.4f, outer.last() + (c - outer.last()) * 0.17f, 0.925f)
            TrackSpec(listOf(Segment(outer, 60), Segment(inner, 60)), laneGap = 2.6f, holeR = 0.63f, numbers = Numbers.TENS, numberSide = -1,
                connectors = listOf(listOf(outer.last(), inner.first())))
        },
        decor = {
            val c = Offset(W / 2, H / 2)
            val pondPts = Geo.chaikin(Geo.circle(c, 28f, 0f, 300f, 5), 2, closed = true)
            val pond = Geo.path(pondPts, close = true)
            inset(pond, 0.7f)
            water(pond, pond.getBounds(), shallow, deep, wood)
            waters += WaterArea(pond, Rect(c.x - 18f, c.y - 4f, c.x + 18f, c.y + 18f))
            // The far shore at dusk.
            clipPath(pond) {
                drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFF2B872).copy(alpha = 0.75f), Color(0xFFE98A6B).copy(alpha = 0.35f), Color.Transparent), c.y - 27f, c.y - 6f), Offset(c.x - 31f, c.y - 27f), androidx.compose.ui.geometry.Size(62f, 21f))
                drawCircle(Color(0xFFFFE2A0).copy(alpha = 0.9f), 3.2f, Offset(c.x + 9f, c.y - 13.2f))
                val shore = Path().apply {
                    moveTo(c.x - 32f, c.y - 9.5f)
                    for (i in 0..32) lineTo(c.x - 32f + i * 2f, c.y - 10.8f - 1.6f * pseudo(i * 13 + 5) - (if (i % 5 == 2) 1.4f else 0f))
                    lineTo(c.x + 32f, c.y - 9.5f); close()
                }
                drawPath(shore, Color(0xFF16302A))
                drawRect(Color(0xFF16302A), Offset(c.x - 32f, c.y - 9.6f), androidx.compose.ui.geometry.Size(64f, 0.5f))
                drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFFFE2A0).copy(alpha = 0.4f), Color.Transparent), c.y - 9f, c.y + 6f), Offset(c.x + 6.8f, c.y - 9f), androidx.compose.ui.geometry.Size(4.4f, 15f))
            }
            sil(Sil.LOON, c.x - 16f, c.y - 3.5f, 11f, alpha = 1f, fill = Color(0xFF0E1A22))
            sil(Sil.LOON, c.x + 4f, c.y + 2f, 7.5f, flip = true, alpha = 1f, fill = Color(0xFF0E1A22))
            sil(Sil.CANOE, c.x + 10f, c.y - 9.5f, 9f, 7f, alpha = 1f, fill = Color(0xFF9E3A26))
            text("BELGRADE LAKES", Offset(c.x, c.y + 12.5f), 2.6f, Face.TITLE, Color(0xFFF3EBDD), carved = false)
            text("Great Pond · Long Pond · Messalonskee", Offset(c.x, c.y + 16.3f), 1.35f, Face.HAND, Color(0xFFDCE9EC), carved = false)
        },
        over = {
            text("START", geometry().start[0][0] + Offset(4.6f, 1.4f), 1.3f, Face.LABEL)
        },
    )

    // ---- West Quoddy Head: a horseshoe round the candy-striped light ----------------------------------------------
    val QUODDY = BoardDesign(
        id = "quoddy", name = "West Quoddy Head", place = "Lubec · Washington County",
        blurb = "An arched maple board: up one side, over the top and back down, twice round the candy-striped light at first sunrise.",
        aspect = 1.42f, wood = Wood.MAPLE, seed = 1858, grainAngle = 2f,
        shape = { w, h ->
            Path().apply {
                moveTo(1.5f, 26f)
                cubicTo(1.5f, -6f, w - 1.5f, -6f, w - 1.5f, 26f)
                lineTo(w - 1.5f, h - 5f); quadraticTo(w - 1.5f, h - 1.5f, w - 5f, h - 1.5f)
                lineTo(5f, h - 1.5f); quadraticTo(1.5f, h - 1.5f, 1.5f, h - 5f); close()
            }
        },
        track = { w, h ->
            val foot = h - 12f
            val c = Offset(w / 2, 30f)
            val outer = Geo.line(Offset(9f, foot), Offset(9f, 30f)) + Geo.ellipse(c, 41f, 21.5f, 180f, 180f, 200) + Geo.line(Offset(w - 9f, 30f), Offset(w - 9f, foot))
            val inner = Geo.line(Offset(w - 16.2f, foot), Offset(w - 16.2f, 30f)) + Geo.ellipse(c, 33.8f, 14.3f, 0f, -180f, 200) + Geo.line(Offset(16.2f, 30f), Offset(16.2f, foot))
            val hop = Geo.chaikin(listOf(outer.last(), outer.last() + Offset(0f, 4.6f), inner.first() + Offset(0f, 4.6f), inner.first()), 3)
            TrackSpec(listOf(Segment(Geo.resample(outer, 500), 60), Segment(Geo.resample(inner, 500), 60)), laneGap = 2.5f, holeR = 0.63f,
                connectors = listOf(hop), numbers = Numbers.NONE, numberSide = -1)
        },
        decor = {
            val ground = H - 12.5f
            val ink = wood.burn()
            // The first sunrise in the country, coming up behind the point.
            val sun = Offset(31f, ground)
            clipPath(Path().apply { addRect(Rect(19f, 0f, 81f, ground)) }) {
                drawCircle(Color(0xFFE9A23B).copy(alpha = 0.55f), 5.2f, sun)
                drawCircle(ink.copy(alpha = 0.7f), 5.2f, sun, style = Stroke(0.3f))
                for (i in 0..8) {
                    val a = 180f + i * 22.5f
                    drawLine(ink.copy(alpha = 0.55f), sun + Geo.rotate(Offset(6.6f, 0f), a), sun + Geo.rotate(Offset(if (i % 2 == 0) 11f else 9f, 0f), a), strokeWidth = 0.3f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
            // The tower: red and white bands.
            val top = 30f
            val tower = Path().apply { moveTo(44f, ground); lineTo(46.3f, top); lineTo(53.7f, top); lineTo(56f, ground); close() }
            drawPath(tower, Color.Black.copy(alpha = 0.3f), style = Stroke(0.9f))
            clipPath(tower) {
                val bands = 8
                val bh = (ground - top) / bands
                for (i in 0 until bands) drawRect(if (i % 2 == 0) Color(0xFFC23A2B) else Color(0xFFF6F1E4), Offset(43f, top + i * bh), androidx.compose.ui.geometry.Size(14f, bh + 0.05f))
                drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent, Color.Black.copy(alpha = 0.32f)), 44f, 56f), Offset(43f, top), androidx.compose.ui.geometry.Size(14f, ground - top))
            }
            drawPath(tower, Color(0xFF2B1A0C), style = Stroke(0.28f))
            drawRect(Color(0xFF1E1E1E), Offset(44.9f, top - 1.5f), androidx.compose.ui.geometry.Size(10.2f, 1.5f))
            drawRect(Color(0xFFFFE9A8), Offset(47.2f, top - 6.2f), androidx.compose.ui.geometry.Size(5.6f, 4.7f))
            drawRect(Color(0xFF1E1E1E), Offset(47.2f, top - 6.2f), androidx.compose.ui.geometry.Size(5.6f, 4.7f), style = Stroke(0.3f))
            drawLine(Color(0xFF1E1E1E), Offset(50f, top - 6.2f), Offset(50f, top - 1.5f), strokeWidth = 0.25f)
            drawPath(Path().apply { moveTo(46.4f, top - 6.2f); lineTo(50f, top - 9.8f); lineTo(53.6f, top - 6.2f); close() }, Color(0xFF8E2A20))
            drawCircle(Color(0xFF1E1E1E), 0.45f, Offset(50f, top - 10.1f))
            // The keeper's house.
            drawRect(Color(0xFFF6F1E4), Offset(58.5f, ground - 6.5f), androidx.compose.ui.geometry.Size(12f, 6.5f))
            drawPath(Path().apply { moveTo(57.6f, ground - 6.5f); lineTo(64.5f, ground - 10.6f); lineTo(71.4f, ground - 6.5f); close() }, Color(0xFF8E2A20))
            drawRect(Color(0xFF2B1A0C), Offset(58.5f, ground - 6.5f), androidx.compose.ui.geometry.Size(12f, 6.5f), style = Stroke(0.22f))
            for (x in listOf(60.4f, 67.2f)) drawRect(Color(0xFF3A4A55), Offset(x, ground - 4.8f), androidx.compose.ui.geometry.Size(1.6f, 2f))
            drawRect(Color(0xFF8E2A20), Offset(63.6f, ground - 3.6f), androidx.compose.ui.geometry.Size(1.8f, 3.6f))
            sil(Sil.PINE, 72.5f, ground - 8.6f, 7f, alpha = 0.9f)
            sil(Sil.PINE, 22f, ground - 6.8f, 5.6f, alpha = 0.9f)
            groove(Geo.path(Geo.wobble(Offset(20.5f, ground + 0.2f), Offset(79.5f, ground + 0.2f), 58, 0.35f)), wood, 0.35f, 0.8f)
            text("WEST QUODDY HEAD", Offset(W / 2, H - 8f), 2.7f)
            text("Lubec, Maine · the easternmost point in the U.S.", Offset(W / 2, H - 4.3f), 1.4f, Face.HAND, carved = false)
        },
        over = {
            text("START", geometry().start[0][0] + Offset(-1.25f, 2.9f), 1.3f, Face.LABEL)
            text("121", design.geometry.finish + Offset(3.2f, 0f), 1.3f, Face.LABEL, weight = FontWeight.Bold)
        },
    )

    // ---- Old Orchard Beach: round the Ferris wheel on the Pier ---------------------------------------------------
    val OLD_ORCHARD = BoardDesign(
        id = "oob", name = "Old Orchard Beach", place = "Saco Bay · York County",
        blurb = "Round maple with big holes and a salt-water-taffy track, two laps round the Ferris wheel at the end of the Pier.",
        aspect = 1f, wood = Wood.MAPLE, seed = 1898, bevel = 2.2f,
        shallow = Color(0xFF58B7C4), deep = Color(0xFF1C5F7E),
        shape = { w, h -> Path().apply { addOval(Rect(1.5f, 1.5f, w - 1.5f, h - 1.5f)) } },
        track = { w, h ->
            val c = Offset(w / 2, h / 2)
            val outer = Geo.circle(c, 41.5f, 99f, 338f, 600)
            val inner = Geo.circle(c, 32f, 108f, 318f, 500)
            val hop = Geo.chaikin(listOf(outer.last(), Geo.rotate(Offset(37f, 0f), 87f) + c, Geo.rotate(Offset(33f, 0f), 98f) + c, inner.first()), 3)
            val startC = c + Offset(1.5f, 45.3f)
            TrackSpec(
                listOf(Segment(outer, 60), Segment(inner, 60)), laneGap = 3.6f, holeR = 0.66f, connectors = listOf(hop),
                numbers = Numbers.PILLS, paint = TAFFY, groove = false,
                startHoles = listOf(listOf(startC + Offset(2.4f, -1.4f), startC + Offset(2.4f, 1.4f)), listOf(startC + Offset(-0.6f, -1.4f), startC + Offset(-0.6f, 1.4f))),
                finish = c + Geo.rotate(Offset(28.6f, 0f), 66f),
            )
        },
        decor = {
            val c = Offset(50f, 50f)
            val r = 25.4f
            val deepC = deep; val shallowC = shallow
            val steel = Color(0xFFFDF3DC)
            val disc = Path().apply { addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r)) }
            drawCircle(Color.Black.copy(alpha = 0.3f), r + 0.6f, c + Offset(0.4f, 0.6f))
            // Dusk over Saco Bay.
            drawCircle(Brush.verticalGradient(listOf(Color(0xFF2B3E73), Color(0xFF8E5A9A), Color(0xFFF29A7A), Color(0xFFFFD79A)), c.y - r, c.y + 12f), r, c)
            val deckY = c.y + 9.6f
            clipPath(disc) {
                val rnd = kotlin.random.Random(98)
                repeat(14) { drawCircle(Color.White.copy(alpha = 0.3f + rnd.nextFloat() * 0.5f), 0.12f + rnd.nextFloat() * 0.14f, Offset(c.x - r + rnd.nextFloat() * 2 * r, c.y - r + rnd.nextFloat() * 14f)) }
                drawRect(deepC, Offset(c.x - r, c.y + 12f), Size(2 * r, r))
                drawRect(Brush.verticalGradient(listOf(shallowC.copy(alpha = 0.85f), Color.Transparent), c.y + 12f, c.y + 19f), Offset(c.x - r, c.y + 12f), Size(2 * r, 7f))
                for (i in 0..4) drawPath(Geo.path(Geo.wobble(Offset(c.x - r, c.y + 13.6f + i * 1.5f), Offset(c.x + r, c.y + 14f + i * 1.5f), 60 + i, 0.5f)), Color.White.copy(alpha = 0.25f), style = Stroke(0.16f))
                // The Pier on its pilings, with the arcade and the fried-dough stand.
                for (i in 0..13) drawLine(Color(0xFF3A2A22), Offset(c.x - 25f + i * 3.9f, deckY), Offset(c.x - 25f + i * 3.9f, c.y + 15.5f + (i % 2)), strokeWidth = 0.42f)
                drawRect(Color(0xFF4A342A), Offset(c.x - r, deckY - 0.5f), Size(2 * r, 1.2f))
                drawRect(Color(0xFFF3E9D8), Offset(c.x + 12.5f, deckY - 5f), Size(7.5f, 4.5f))
                drawPath(Path().apply { moveTo(c.x + 11.6f, deckY - 5f); lineTo(c.x + 16.2f, deckY - 8f); lineTo(c.x + 20.9f, deckY - 5f); close() }, Color(0xFFC0392B))
                for (i in 0..2) drawRect(Color(0xFFFFD866), Offset(c.x + 13.6f + i * 2.1f, deckY - 3.8f), Size(1.2f, 1.6f))
                drawRect(Color(0xFFF3E9D8), Offset(c.x - 21f, deckY - 4f), Size(6f, 3.5f))
                drawPath(Path().apply { moveTo(c.x - 21.8f, deckY - 4f); lineTo(c.x - 18f, deckY - 6.4f); lineTo(c.x - 14.2f, deckY - 4f); close() }, Color(0xFF2A7F8E))
                drawRect(Color(0xFFFFD866), Offset(c.x - 19.2f, deckY - 3f), Size(2.4f, 1.4f))
            }
            // The wheel: rim, spokes, a light at every joint and twelve bright cars.
            val hub = Offset(c.x - 1f, c.y - 6.6f)
            val wr = 13.2f
            drawLine(steel, hub, Offset(hub.x - 5.6f, deckY - 0.5f), strokeWidth = 0.55f)
            drawLine(steel, hub, Offset(hub.x + 5.6f, deckY - 0.5f), strokeWidth = 0.55f)
            drawCircle(steel, wr, hub, style = Stroke(0.5f))
            drawCircle(steel.copy(alpha = 0.7f), wr - 1.7f, hub, style = Stroke(0.2f))
            val cars = listOf(Color(0xFFE5484D), Color(0xFFF2C230), Color(0xFF3FBCCB), Color(0xFFF08A3C), Color(0xFF7BC96F), Color(0xFFE56AA6))
            for (i in 0 until 12) {
                val p = hub + Geo.rotate(Offset(wr, 0f), i * 30f + 15f)
                drawLine(steel.copy(alpha = 0.85f), hub, p, strokeWidth = 0.22f)
                drawLine(steel, p, p + Offset(0f, 1.1f), strokeWidth = 0.2f)
                drawRoundRect(Color.Black.copy(alpha = 0.25f), p + Offset(-1.05f, 1.15f), Size(2.3f, 1.9f), CornerRadius(0.6f))
                drawRoundRect(cars[i % cars.size], p + Offset(-1.15f, 1.0f), Size(2.3f, 1.9f), CornerRadius(0.6f))
                drawCircle(Color(0xFFFFF1B8), 0.34f, hub + Geo.rotate(Offset(wr, 0f), i * 30f))
            }
            drawCircle(Color(0xFFE5484D), 1.35f, hub)
            drawCircle(steel, 0.5f, hub)
            drawCircle(Color(0xFF2B3E73), r, c, style = Stroke(0.6f))
            drawCircle(steel.copy(alpha = 0.6f), r - 1.1f, c, style = Stroke(0.18f))
            arcText("OLD ORCHARD BEACH", c, 21.4f, 90f, 2.2f, Face.TITLE, steel, clockwise = false)
            // The start block, down by the boardwalk.
            val s = c + Offset(1.5f, 45.3f)
            drawPath(roundRect(s.x - 2.5f, s.y - 3.1f, s.x + 4.3f, s.y + 3.1f, 0.9f), TAFFY)
            drawPath(roundRect(s.x - 2.5f, s.y - 3.1f, s.x + 4.3f, s.y + 3.1f, 0.9f), Color.Black.copy(alpha = 0.2f), style = Stroke(0.15f))
        },
        over = {
            val c = Offset(50f, 50f)
            text("FINISH", c + Geo.rotate(Offset(28.8f, 0f), 57f), 1.3f, Face.LABEL, rotation = -33f)
        },
    )

    // ---- Machias: a handful of wild blueberries ------------------------------------------------------------------
    val MACHIAS = BoardDesign(
        id = "machias", name = "Machias", place = "Washington County",
        blurb = "Brown ash with a blueberry-blue track winding in to a handful of wild Maine blueberries.",
        aspect = 1f, wood = Wood.ASH, seed = 1763, bevel = 2.2f,
        shape = { w, h -> Path().apply { addOval(Rect(1.5f, 1.5f, w - 1.5f, h - 1.5f)) } },
        track = { w, h ->
            val c = Offset(w / 2, h / 2)
            val spiral = (0..900).map { i ->
                val t = i / 900f
                c + Geo.rotate(Offset(42.5f - 17.5f * t, 0f), 80f - 860f * t)
            }
            TrackSpec(listOf(Segment(spiral, 120)), laneGap = 3.2f, holeR = 0.64f, numbers = Numbers.PILLS, paint = BERRY, groove = false, skunkLine = false,
                finish = c + Geo.rotate(Offset(24.3f, 0f), 80f - 860f - 21f))
        },
        decor = {
            val c = Offset(50f, 50f)
            val r = 20.6f
            val navy = Color(0xFF232C6B)
            drawCircle(Color.Black.copy(alpha = 0.3f), r + 0.6f, c + Offset(0.4f, 0.6f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF9E8), Color(0xFFEFE3C4)), c, r), r, c)
            fun leaf(base: Offset, tip: Offset, width: Float) {
                val mid = (base + tip) / 2f
                val d = tip - base
                val n = Offset(-d.y, d.x) / d.getDistance() * width
                val p = Path().apply { moveTo(base.x, base.y); quadraticTo(mid.x + n.x, mid.y + n.y, tip.x, tip.y); quadraticTo(mid.x - n.x, mid.y - n.y, base.x, base.y); close() }
                drawPath(p, Color(0xFF4E8B4A))
                drawPath(p, Color(0xFF2C5A30), style = Stroke(0.16f))
                drawLine(Color(0xFF2C5A30), base, tip, strokeWidth = 0.14f)
            }
            val o = c + Offset(0f, 1.6f)
            drawLine(Color(0xFF6B4A2A), o + Offset(-0.5f, -3f), o + Offset(-1.5f, -12.5f), strokeWidth = 0.4f)
            leaf(o + Offset(-1.2f, -9f), o + Offset(-9.5f, -11.5f), 2.1f)
            leaf(o + Offset(-1.4f, -11f), o + Offset(6.5f, -13.2f), 2.1f)
            leaf(o + Offset(-1.0f, -7f), o + Offset(8.5f, -8.2f), 1.9f)
            val berries = listOf(Triple(-5.6f, -1.6f, 4.2f), Triple(2.6f, -3.2f, 4.5f), Triple(7.6f, 2.8f, 3.8f), Triple(-0.8f, 4.6f, 4.4f), Triple(-8.4f, 5.2f, 3.2f), Triple(4.8f, 8.2f, 3.2f))
            for ((dx, dy, br) in berries) {
                val p = o + Offset(dx, dy)
                drawCircle(Color.Black.copy(alpha = 0.22f), br, p + Offset(0.35f, 0.5f))
                drawCircle(Brush.radialGradient(listOf(Color(0xFF8E9FEA), Color(0xFF4353AE), navy), p + Offset(-br * 0.3f, -br * 0.35f), br * 1.5f), br, p)
                drawCircle(Color.White.copy(alpha = 0.28f), br * 0.3f, p + Offset(-br * 0.4f, -br * 0.42f))
                // The little crown every blueberry wears.
                val crown = p + Offset(br * 0.28f, -br * 0.12f)
                drawCircle(Color(0xFF161B45), br * 0.2f, crown)
                engraveInk(Sil.STAR, Rect(crown.x - br * 0.42f, crown.y - br * 0.42f, crown.x + br * 0.42f, crown.y + br * 0.42f), Color(0xFF161B45))
            }
            drawCircle(navy, r, c, style = Stroke(0.6f))
            drawCircle(navy.copy(alpha = 0.45f), r - 1.1f, c, style = Stroke(0.18f))
            arcText("MACHIAS", c, 16.4f, 270f, 2.5f, Face.TITLE, navy)
            arcText("WILD BLUEBERRY COUNTRY", c, 17.8f, 90f, 1.4f, Face.LABEL, navy, clockwise = false)
        },
        over = {
            val g = geometry()
            val c = Offset(50f, 50f)
            text("START", g.start[0][0] + Offset(-6.2f, -0.4f), 1.3f, Face.LABEL)
            val out = (g.finish - c).let { it / it.getDistance() }
            text("121", g.finish + out * 2.7f, 1.3f, Face.LABEL, weight = FontWeight.Bold)
            // The skunk line, between 90 and 91.
            val m = (g.centre[90] + g.centre[91]) / 2f
            val n = (g.normal[90] + g.normal[91]) / 2f
            drawLine(Color(0xFF232C6B), m + n * 2.9f, m - n * 2.9f, strokeWidth = 0.5f)
        },
    )

    // ---- Dirigo: the whole state under the North Star -------------------------------------------------------------
    val DIRIGO = BoardDesign(
        id = "dirigo", name = "Dirigo", place = "The State of Maine",
        blurb = "A square of white pine with the whole state poured in spruce green under the North Star. Dirigo: \"I lead.\"",
        aspect = 1f, wood = Wood.PINE, seed = 1820, grainAngle = 3f, bevel = 2f,
        shallow = Color(0xFF58A070), deep = Color(0xFF1E4A34),
        shape = { w, h -> rounded(0.8f, 0.8f, w - 0.8f, h - 0.8f, 9f) },
        track = { w, h ->
            val c = Offset(w / 2, h / 2)
            fun lap(inset: Float, radius: Float, near: Offset, cover: Float): List<Offset> {
                val ring = Geo.roundedRect(inset, inset, w - inset, h - inset, radius, 0f, 700)
                val clean = ring.filterIndexed { k, p -> k == 0 || (p - ring[k - 1]).getDistance() > 0.01f }
                    .let { if ((it.first() - it.last()).getDistance() < 0.01f) it.dropLast(1) else it }
                val i = clean.indices.minBy { (clean[it] - near).getDistance() }
                val rot = clean.drop(i) + clean.take(i)
                return rot.take((rot.size * cover).toInt())
            }
            val outer = lap(7.2f, 13f, Offset(62f, h), 0.93f)
            val inner = lap(15.4f, 8f, outer.last() + (c - outer.last()) * 0.19f, 0.925f)
            TrackSpec(listOf(Segment(outer, 60), Segment(inner, 60)), laneGap = 3.2f, holeR = 0.64f, numbers = Numbers.NONE, skunkLine = false,
                connectors = listOf(listOf(outer.last(), inner.first())))
        },
        decor = {
            fun at(lon: Float, lat: Float) = Offset(35.2f + (lon + 71.1f) * 7.35f, 26.6f + (47.5f - lat) * 10.4f)
            val state = Geo.path(MAINE_OUTLINE.map { (lon, lat) -> at(lon, lat) }, close = true)
            inset(state, 0.7f)
            water(state, state.getBounds(), shallow, deep, wood)
            drawPath(state, wood.burn().copy(alpha = 0.75f), style = Stroke(0.3f))
            // Moosehead Lake and the big river, then Katahdin and the capital.
            val light = Color(0xFFBFE3CC)
            drawOval(light.copy(alpha = 0.8f), at(-69.72f, 45.78f), Size(1.5f, 3.1f))
            drawPath(Geo.path(Geo.chaikin(listOf(at(-69.6f, 45.6f), at(-69.75f, 45.0f), at(-69.65f, 44.6f), at(-69.78f, 44.3f), at(-69.8f, 43.85f)), 2)), light.copy(alpha = 0.6f), style = Stroke(0.28f))
            drawPath(Geo.path(Geo.chaikin(listOf(at(-68.6f, 46.1f), at(-68.55f, 45.5f), at(-68.7f, 44.95f), at(-68.8f, 44.5f)), 2)), light.copy(alpha = 0.6f), style = Stroke(0.28f))
            val kat = at(-68.92f, 45.9f)
            drawPath(Path().apply { moveTo(kat.x, kat.y - 1.5f); lineTo(kat.x + 1.5f, kat.y + 0.9f); lineTo(kat.x - 1.5f, kat.y + 0.9f); close() }, Color(0xFFF3EBDD))
            val aug = at(-69.78f, 44.31f)
            engraveInk(Sil.STAR, Rect(aug.x - 1.5f, aug.y - 1.5f, aug.x + 1.5f, aug.y + 1.5f), Color(0xFFE5B93C))
            // The North Star, and the motto.
            engraveInk(Sil.STAR, Rect(24.4f, 22.6f, 33f, 31.2f), Color(0xFFE5B93C))
            drawCircle(Color(0xFFE5B93C).copy(alpha = 0.18f), 6.4f, Offset(28.7f, 26.9f))
            text("DIRIGO", Offset(30.2f, 35f), 2.5f, Face.TITLE)
            text("“I lead”", Offset(30.2f, 38.5f), 1.6f, Face.HAND)
            sil(Sil.MOOSE, 64.5f, 25.5f, 13f, 11f, alpha = 0.85f)
            sil(Sil.SAILBOAT, 63.5f, 60.5f, 9f, alpha = 0.85f)
            text("THE PINE TREE STATE", Offset(63f, 74.4f), 1.45f, Face.LABEL)
            text("A State since 1820", Offset(63f, 77.2f), 1.5f, Face.HAND)
            sil(Sil.PINE, 24f, 63f, 6.5f, 9f, alpha = 0.85f)
            sil(Sil.PINE, 29.5f, 67f, 5f, 7f, alpha = 0.7f)
        },
        over = {
            val g = geometry()
            val c = Offset(50f, 50f)
            fun inward(p: Offset) = (c - p).let { it / it.getDistance() }
            for (i in 10..120 step 10) {
                val p = g.centre[i]
                text("$i", p + inward(p) * 4.3f, 1.5f, Face.LABEL, weight = FontWeight.Medium)
            }
            val m = (g.centre[90] + g.centre[91]) / 2f
            val n = (g.normal[90] + g.normal[91]) / 2f
            groove(Geo.path(listOf(m + n * 3.4f, m - n * 3.4f)), wood, 0.25f, 0.8f)
            val s = (g.start[0][0] + g.start[1][1]) / 2f
            text("START", s + inward(s) * 4.4f, 1.3f, Face.LABEL)
        },
    )

    val all: List<BoardDesign> = listOf(SEBEC, MOOSEHEAD, SCHOODIC, EASTPORT, RANGELEY, SEBAGO, BAR_HARBOR, KATAHDIN, CAMDEN, ALLAGASH, PEMAQUID, STONINGTON, BELGRADE, QUODDY, OLD_ORCHARD, MACHIAS, DIRIGO)

    fun byId(id: String): BoardDesign = all.firstOrNull { it.id == id } ?: SEBEC
}

/** A half-circle from [a] to [b], bulging towards [side] (+1 right, -1 left). */
private fun uTurn(a: Offset, b: Offset, side: Float): List<Offset> {
    val c = (a + b) / 2f
    val r = (b - a).getDistance() / 2f
    val start = Geo.angleDeg(a - c)
    return Geo.circle(c, r, start, if ((side > 0) == (b.y > a.y)) 180f else -180f, 60)
}

/** Monotone-chain hull of a set of points. */
private fun convexHull(points: List<Offset>): List<Offset> {
    val p = points.distinct().sortedWith(compareBy({ it.x }, { it.y }))
    if (p.size < 3) return p
    fun cross(o: Offset, a: Offset, b: Offset) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    val lower = mutableListOf<Offset>()
    for (pt in p) { while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), pt) <= 0) lower.removeAt(lower.size - 1); lower += pt }
    val upper = mutableListOf<Offset>()
    for (pt in p.reversed()) { while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), pt) <= 0) upper.removeAt(upper.size - 1); upper += pt }
    return lower.dropLast(1) + upper.dropLast(1)
}

private fun allagashRiver(w: Float, h: Float): List<Offset> {
    val pts = listOf(
        Offset(8f, 9f), Offset(28f, 9f), Offset(43f, 11f), Offset(51f, 19f), Offset(43f, 27f), Offset(22f, 27f), Offset(9f, 34f),
        Offset(12f, 45f), Offset(26f, 49f), Offset(50f, 44f), Offset(68f, 30f), Offset(84f, 29f), Offset(91f, 40f), Offset(84f, 51f), Offset(93f, h - 7f),
    )
    return Geo.resample(Geo.chaikin(pts, 4), 800)
}

/** The geometry of the design being painted (for labels placed against the track). */
internal fun BoardPainter.geometry(): TrackGeometry = design.geometry

/** Draws a silhouette in a flat ink colour without the carved highlight (for painted inlays). */
internal fun BoardPainter.engraveInk(s: Sil, rect: Rect, ink: Color) =
    com.geoguy89.cribbage.gfx.Carving.run { engrave(s, rect, wood, 0.95f, fill = ink, shine = false) }
