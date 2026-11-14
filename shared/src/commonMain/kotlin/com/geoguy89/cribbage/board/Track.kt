package com.geoguy89.cribbage.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.geoguy89.cribbage.gfx.Geo

/** A stretch of track: a centreline and how many holes (per lane) are drilled along it. */
class Segment(val pts: List<Offset>, val holes: Int)

enum class Numbers { NONE, TENS, FIVES, PILLS }

/**
 * A peg track: 120 holes per lane laid along [segments] in groups of five, two lanes either side of the centreline,
 * two start holes per lane and a shared game hole.
 */
class TrackSpec(
    val segments: List<Segment>,
    val laneGap: Float,
    val holeR: Float,
    /** Grooves that join segments but carry no holes (U-turns, the hop between rings). */
    val connectors: List<List<Offset>> = emptyList(),
    val numbers: Numbers = Numbers.TENS,
    /** A painted stripe down the middle of the track (the Eastport board's teal). */
    val paint: Color? = null,
    /** Explicit start holes [lane][back, front]; otherwise they're placed behind the first hole. */
    val startHoles: List<List<Offset>>? = null,
    val finish: Offset? = null,
    /** Which side the numbers go: +1 beside lane 0, -1 beside lane 1. */
    val numberSide: Int = 1,
    val groove: Boolean = true,
    val skunkLine: Boolean = true,
    /**
     * Space each lane's holes along that lane's own length rather than pairing them across the centreline. Needed
     * where the track winds both ways (a river), so the inside of each bend doesn't crowd.
     */
    val independentLanes: Boolean = false,
)

/** Where every hole is, worked out once per design. */
class TrackGeometry(spec: TrackSpec) {
    /** [lane][score] for score 1..120 (index 0 unused). */
    val holes: List<List<Offset>>
    val start: List<List<Offset>>
    val finish: Offset
    /** Centre of the track at each hole and the direction there (index 1..120). */
    val centre: List<Offset>
    val normal: List<Offset>
    val spacing: Float
    val laneLines: List<List<Offset>>

    init {
        val total = spec.segments.sumOf { it.holes }
        require(total == 120) { "track has $total holes" }
        val lanes = listOf(MutableList(121) { Offset.Zero }, MutableList(121) { Offset.Zero })
        val cen = MutableList(121) { Offset.Zero }
        val nor = MutableList(121) { Offset.Zero }
        var index = 1
        var minSpacing = Float.MAX_VALUE
        val laneLinesOut = mutableListOf<List<Offset>>()
        for (seg in spec.segments) {
            val len = Geo.length(seg.pts)
            // Groups of five within this segment get a wider gap between them.
            val gapsInside = (index + 1 until index + seg.holes).count { (it - 1) % 5 == 0 }
            val s = len / ((seg.holes - 1) + 0.7f * gapsInside + 1.2f)
            minSpacing = minOf(minSpacing, s)
            var d = s * 0.6f
            val laneTracks = if (spec.independentLanes) listOf(1f, -1f).map { side ->
                val fine = Geo.resample(seg.pts, 600)
                fine.indices.map { k -> val (p, t) = Geo.at(fine, Geo.length(fine) * k / (fine.size - 1)); p + Offset(-t.y, t.x) * (spec.laneGap / 2 * side) }
            } else null
            val first = index
            for (i in 0 until seg.holes) {
                if (i > 0 && (index - 1) % 5 == 0) d += 0.7f * s
                val (p, t) = Geo.at(seg.pts, d)
                val n = Offset(-t.y, t.x)
                cen[index] = p
                nor[index] = n
                lanes[0][index] = p + n * (spec.laneGap / 2)
                lanes[1][index] = p - n * (spec.laneGap / 2)
                index++
                d += s
            }
            if (laneTracks != null) for (lane in 0..1) {
                val lt = laneTracks[lane]
                val ll = Geo.length(lt)
                val ls = ll / ((seg.holes - 1) + 0.7f * gapsInside + 1.2f)
                var ld = ls * 0.6f
                for (i in 0 until seg.holes) {
                    val g = first + i
                    if (i > 0 && (g - 1) % 5 == 0) ld += 0.7f * ls
                    lanes[lane][g] = Geo.at(lt, ld).first
                    ld += ls
                }
            }
            for (side in listOf(1f, -1f)) laneLinesOut += seg.pts.indices.map { k ->
                val (p, t) = Geo.at(seg.pts, Geo.length(seg.pts.subList(0, k + 1)))
                p + Offset(-t.y, t.x) * (spec.laneGap * 0.5f * side)
            }
        }
        spacing = minSpacing
        holes = lanes
        centre = cen
        normal = nor
        laneLines = laneLinesOut
        val first = spec.segments.first().pts
        val (p0, t0) = Geo.at(first, minSpacing * 0.6f)
        val n0 = Offset(-t0.y, t0.x)
        start = spec.startHoles ?: listOf(1f, -1f).map { side ->
            listOf(p0 - t0 * (minSpacing * 3.0f) + n0 * (spec.laneGap / 2 * side), p0 - t0 * (minSpacing * 1.9f) + n0 * (spec.laneGap / 2 * side))
        }
        finish = spec.finish ?: run {
            val last = spec.segments.last().pts
            val (pl, tl) = Geo.at(last, Geo.length(last) - minSpacing * 0.6f)
            pl + tl * (minSpacing * 2.2f)
        }
    }

    /** Where a peg at [score] sits: -1 and 0 are the start holes, 121 the game hole. */
    fun position(lane: Int, score: Int): Offset = when {
        score <= -1 -> start[lane][0]
        score == 0 -> start[lane][1]
        score >= 121 -> finish
        else -> holes[lane][score]
    }

    /** Fractional position for animation: glides hole to hole. */
    fun position(lane: Int, score: Float): Offset {
        val a = kotlin.math.floor(score).toInt()
        val f = score - a
        val p = position(lane, a)
        if (f <= 0.001f) return p
        val q = position(lane, a + 1)
        return p + (q - p) * f
    }
}
