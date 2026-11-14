package com.geoguy89.cribbage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.geoguy89.cribbage.board.BoardView
import com.geoguy89.cribbage.board.Boards
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every board, rendered offscreen to build/screenshots/board_<id>.png. */
class BoardRenderTest {
    @Test
    fun everyTrackHas120Holes() {
        val problems = mutableListOf<String>()
        for (b in Boards.all) try {
            val g = b.geometry
            assertEquals(120, (1..120).count { g.holes[0][it] != g.holes[1][it] }, b.id)
            // Holes don't overlap one another.
            val named = (1..120).flatMap { listOf("A$it" to g.holes[0][it], "B$it" to g.holes[1][it]) } + g.start.flatten().mapIndexed { i, o -> "start$i" to o } + ("finish" to g.finish)
            val all = named.map { it.second }
            var closest = Float.MAX_VALUE
            var pair = ""
            for (i in all.indices) for (j in i + 1 until all.size) {
                val d = (all[i] - all[j]).getDistance()
                if (d < closest) { closest = d; pair = "${named[i].first}/${named[j].first}" }
            }
            assertTrue(closest > b.trackSpec.holeR * 2.1f, "${b.id}: holes $pair only $closest apart")
            // And they all sit on the board.
            assertTrue(all.all { it.x in 1f..99f && it.y in 1f..(b.height - 1f) }, "${b.id}: a hole is off the board")
        } catch (e: AssertionError) { problems += e.message ?: b.id }
        assertTrue(problems.isEmpty(), problems.joinToString("; "))
    }

    @Test
    fun renderBoards() {
        val only = System.getenv("BOARD")
        for (b in Boards.all.filter { only == null || it.id == only }) {
            val w = 1400
            val h = (w / b.aspect).toInt() + 80
            ImageComposeScene(w, h, Density(2f)) {
                Box(Modifier.fillMaxSize().background(Color(0xFF6B4A2E)).padding(20.dp)) {
                    BoardView(b, listOf(37, 52), listOf(31, 46), Modifier.fillMaxSize(), ambient = false, animatePegs = false)
                }
            }.use { scene ->
                var t = 0L
                repeat(40) { scene.render(t); t += 50_000_000L; Thread.sleep(60) }
                val bytes = scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes
                File("build/screenshots").mkdirs()
                File("build/screenshots/board_${b.id}.png").writeBytes(bytes)
            }
        }
    }
}
