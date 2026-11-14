package com.geoguy89.cribbage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.geoguy89.cribbage.gfx.Carving.engrave
import com.geoguy89.cribbage.gfx.Carving.hole
import com.geoguy89.cribbage.gfx.Carving.peg
import com.geoguy89.cribbage.gfx.Carving.woodGrain
import com.geoguy89.cribbage.gfx.Sil
import com.geoguy89.cribbage.gfx.Wood
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test

/** The app's logo (the launcher icon, drawn at full detail) to docs/logo.png. */
class LogoRenderTest {
    @Test
    fun logo() {
        val px = 1024
        ImageComposeScene(px, px, Density(1f)) {
            Canvas(Modifier.fillMaxSize()) {
                val k = size.width / 100f
                withTransform({ scale(k, k, Offset.Zero) }) {
                    val tile = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, 100f, 100f, CornerRadius(22f))) }
                    clipPath(tile) {
                        drawRect(Brush.linearGradient(listOf(Wood.CHERRY.light, Wood.CHERRY.base, Wood.CHERRY.dark), Offset.Zero, Offset(100f, 100f)))
                        woodGrain(Rect(0f, 0f, 100f, 100f), Wood.CHERRY, 23, 8f)
                        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)), Offset(50f, 50f), 75f))
                    }
                    val c = Offset(50f, 50f)
                    // Walnut ring with two rows of peg holes, like a round board.
                    drawCircle(Color.Black.copy(alpha = 0.35f), 41f, c + Offset(1f, 1.6f))
                    drawCircle(Brush.radialGradient(listOf(Wood.STAINED.light, Wood.STAINED.base, Wood.STAINED.dark), c - Offset(10f, 12f), 60f), 40f, c)
                    for (ring in listOf(36.5f, 32.5f)) for (i in 0 until 36) {
                        val a = i / 36f * 2 * PI.toFloat()
                        hole(c + Offset(cos(a), sin(a)) * ring, 0.95f, Wood.STAINED)
                    }
                    drawCircle(Color(0xFF3FBCCB), 34.5f, c, style = Stroke(0.6f))
                    // The teal medallion and the moose.
                    drawCircle(Color.Black.copy(alpha = 0.3f), 28.6f, c + Offset(0.5f, 0.8f))
                    drawCircle(Brush.radialGradient(listOf(Color(0xFF7FDCE6), Color(0xFF3FBCCB), Color(0xFF2B98A7)), c - Offset(8f, 10f), 40f), 28f, c)
                    drawCircle(Color(0xFF1C5E66), 28f, c, style = Stroke(0.7f))
                    drawCircle(Color(0xFF1C5E66), 25.5f, c, style = Stroke(0.3f))
                    engrave(Sil.MOOSE, Rect(29f, 27f, 73f, 71f), Wood.STAINED, 1f, fill = Color(0xFF173C42), shine = false)
                    // Two pegs mid-race.
                    val a1 = -0.55f; val a2 = -0.95f
                    peg(c + Offset(cos(a1), sin(a1)) * 36.5f, 1.9f, Color(0xFFC62828))
                    peg(c + Offset(cos(a2), sin(a2)) * 32.5f, 1.9f, Color(0xFF1E5AA8))
                }
            }
        }.use { scene ->
            val bytes = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
            File("../docs").mkdirs()
            File("../docs/logo.png").writeBytes(bytes)
        }
    }
}
