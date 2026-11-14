package com.geoguy89.cribbage.board

import androidx.compose.ui.geometry.Offset
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
        track = { w, h -> TrackSpec(listOf(Segment(loop(10f, 11f, w - 10f, h - 11f, (h - 22f) / 2f, 0.07f), 120)), laneGap = 2.7f, holeR = 0.56f, numbers = Numbers.FIVES, numberSide = -1) },
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

    val all: List<BoardDesign> = listOf(SEBEC, MOOSEHEAD, SCHOODIC, EASTPORT, RANGELEY, SEBAGO, BAR_HARBOR, KATAHDIN, CAMDEN, ALLAGASH)

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
