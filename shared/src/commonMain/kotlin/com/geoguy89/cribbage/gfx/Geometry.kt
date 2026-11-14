package com.geoguy89.cribbage.gfx

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Polyline helpers for laying out peg tracks and drawing engraved lines. All in board units. */
object Geo {
    fun circle(c: Offset, r: Float, startDeg: Float = 0f, sweepDeg: Float = 360f, n: Int = 240): List<Offset> =
        ellipse(c, r, r, startDeg, sweepDeg, n)

    /** Points round an ellipse; angles in degrees, 0 = east, clockwise on screen (y down). */
    fun ellipse(c: Offset, rx: Float, ry: Float, startDeg: Float = 0f, sweepDeg: Float = 360f, n: Int = 240): List<Offset> =
        (0..n).map { i ->
            val a = (startDeg + sweepDeg * i / n) * PI / 180
            Offset(c.x + rx * cos(a).toFloat(), c.y + ry * sin(a).toFloat())
        }

    /**
     * A rounded rectangle's perimeter, clockwise, starting [startFrac] of the way along the top edge from the left
     * corner (0 = just after the top-left corner).
     */
    fun roundedRect(l: Float, t: Float, r: Float, b: Float, radius: Float, startFrac: Float = 0f, n: Int = 400): List<Offset> {
        val pts = mutableListOf<Offset>()
        fun arc(cx: Float, cy: Float, from: Float) {
            for (i in 0..12) {
                val a = (from + 90f * i / 12) * PI / 180
                pts += Offset(cx + radius * cos(a).toFloat(), cy + radius * sin(a).toFloat())
            }
        }
        pts += Offset(l + radius, t); pts += Offset(r - radius, t)
        arc(r - radius, t + radius, -90f)
        pts += Offset(r, b - radius)
        arc(r - radius, b - radius, 0f)
        pts += Offset(l + radius, b)
        arc(l + radius, b - radius, 90f)
        pts += Offset(l, t + radius)
        arc(l + radius, t + radius, 180f)
        val closed = resample(pts + pts.first(), n)
        val shift = (startFrac * (closed.size - 1)).toInt()
        val ring = closed.dropLast(1)
        return (ring.drop(shift) + ring.take(shift)) + ring[shift % ring.size]
    }

    /** A straight line as a run of points (so it can be joined to curves and smoothed). */
    fun line(a: Offset, b: Offset, n: Int = 20): List<Offset> = (0..n).map { a + (b - a) * (it / n.toFloat()) }

    /** Evenly spaced points along a polyline. */
    fun resample(pts: List<Offset>, n: Int): List<Offset> {
        val cum = FloatArray(pts.size)
        for (i in 1 until pts.size) cum[i] = cum[i - 1] + (pts[i] - pts[i - 1]).getDistance()
        val total = cum.last()
        val out = ArrayList<Offset>(n + 1)
        var j = 0
        for (k in 0..n) {
            val d = total * k / n
            while (j < pts.size - 2 && cum[j + 1] < d) j++
            val seg = (cum[j + 1] - cum[j]).coerceAtLeast(1e-6f)
            val f = ((d - cum[j]) / seg).coerceIn(0f, 1f)
            out += pts[j] + (pts[j + 1] - pts[j]) * f
        }
        return out
    }

    fun length(pts: List<Offset>): Float {
        var s = 0f
        for (i in 1 until pts.size) s += (pts[i] - pts[i - 1]).getDistance()
        return s
    }

    /** The point [d] units along a polyline, and the unit tangent there. */
    fun at(pts: List<Offset>, d: Float): Pair<Offset, Offset> {
        var acc = 0f
        for (i in 1 until pts.size) {
            val seg = (pts[i] - pts[i - 1]).getDistance()
            if (acc + seg >= d || i == pts.size - 1) {
                val f = if (seg == 0f) 0f else ((d - acc) / seg).coerceIn(0f, 1f)
                val p = pts[i - 1] + (pts[i] - pts[i - 1]) * f
                // Average the direction over a short stretch so corners turn smoothly.
                val a = pointAt(pts, d - 1.2f)
                val b = pointAt(pts, d + 1.2f)
                val t = (b - a).let { v -> val m = v.getDistance(); if (m == 0f) Offset(1f, 0f) else v / m }
                return p to t
            }
            acc += seg
        }
        return pts.last() to Offset(1f, 0f)
    }

    private fun pointAt(pts: List<Offset>, d: Float): Offset {
        if (d <= 0f) return pts.first() + (pts[1] - pts[0]).let { v -> v / v.getDistance().coerceAtLeast(1e-6f) } * d
        var acc = 0f
        for (i in 1 until pts.size) {
            val seg = (pts[i] - pts[i - 1]).getDistance()
            if (acc + seg >= d) return pts[i - 1] + (pts[i] - pts[i - 1]) * ((d - acc) / seg.coerceAtLeast(1e-6f))
            acc += seg
        }
        val n = pts.size
        return pts.last() + (pts[n - 1] - pts[n - 2]).let { v -> v / v.getDistance().coerceAtLeast(1e-6f) } * (d - acc)
    }

    /** Corner-cutting smoothing; [closed] wraps the ends. */
    fun chaikin(pts: List<Offset>, rounds: Int = 2, closed: Boolean = false): List<Offset> {
        var p = pts
        repeat(rounds) {
            val out = mutableListOf<Offset>()
            if (!closed) out += p.first()
            val n = if (closed) p.size else p.size - 1
            for (i in 0 until n) {
                val a = p[i]; val b = p[(i + 1) % p.size]
                out += a * 0.75f + b * 0.25f
                out += a * 0.25f + b * 0.75f
            }
            if (!closed) out += p.last() else out += out.first()
            p = out
        }
        return p
    }

    /** A wobbly line, for engraved map roads and rivers. */
    fun wobble(a: Offset, b: Offset, seed: Int, amp: Float, n: Int = 24): List<Offset> {
        val d = b - a
        val len = d.getDistance()
        val nrm = Offset(-d.y / len, d.x / len)
        val r = kotlin.random.Random(seed)
        val p1 = r.nextFloat() * 6f; val p2 = r.nextFloat() * 6f
        return (0..n).map { i ->
            val t = i.toFloat() / n
            val w = sin(t * PI * 2 + p1).toFloat() * 0.6f + sin(t * PI * 5 + p2).toFloat() * 0.4f
            a + d * t + nrm * (w * amp * sin(t * PI).toFloat())
        }
    }

    fun path(pts: List<Offset>, close: Boolean = false): Path = Path().apply {
        if (pts.isEmpty()) return@apply
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        if (close) close()
    }

    fun angleDeg(v: Offset): Float = (atan2(v.y, v.x) * 180 / PI).toFloat()
    fun dist(a: Offset, b: Offset): Float = hypot(a.x - b.x, a.y - b.y)
    fun rotate(v: Offset, deg: Float): Offset {
        val a = deg * PI / 180
        val c = cos(a).toFloat(); val s = sin(a).toFloat()
        return Offset(v.x * c - v.y * s, v.x * s + v.y * c)
    }
}
