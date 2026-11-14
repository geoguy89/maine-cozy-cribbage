package com.geoguy89.cribbage.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.geoguy89.cribbage.board.BoardFonts
import com.geoguy89.cribbage.board.rememberBoardFonts
import com.geoguy89.cribbage.game.Card
import com.geoguy89.cribbage.game.Suit
import com.geoguy89.cribbage.gfx.Carving.engrave
import com.geoguy89.cribbage.gfx.Carving.pinecone
import com.geoguy89.cribbage.gfx.Sil
import com.geoguy89.cribbage.gfx.Wood

const val CARD_ASPECT = 0.7f

private val PAPER = Color(0xFFFBF3DF)
private val RED = Color(0xFFB3261E)
private val BLACK = Color(0xFF1F1A17)
private val GOLD = Color(0xFFC9A13B)

/** Suit symbols drawn as shapes (fonts vary too much between devices), in a 100 x 100 box. */
object SuitShapes {
    private fun parse(d: String) = androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toPath()
    val HEART by lazy { parse("M50,92 L12,52 Q-2,34 12,18 Q28,4 44,18 L50,25 L56,18 Q72,4 88,18 Q102,34 88,52 Z") }
    val DIAMOND by lazy { parse("M50,2 Q66,30 90,50 Q66,70 50,98 Q34,70 10,50 Q34,30 50,2 Z") }
    val SPADE by lazy { parse("M50,4 Q62,26 86,44 Q102,60 86,74 Q72,84 56,72 Q58,86 68,96 L32,96 Q42,86 44,72 Q28,84 14,74 Q-2,60 14,44 Q38,26 50,4 Z") }
    val CLUB by lazy {
        parse("M50,6 A20,20 0 1,1 49.9,6 Z M27,40 A20,20 0 1,1 26.9,40 Z M73,40 A20,20 0 1,1 72.9,40 Z " +
            "M44,52 L56,52 Q58,82 70,96 L30,96 Q42,82 44,52 Z")
    }
    fun of(s: Suit) = when (s) { Suit.HEARTS -> HEART; Suit.DIAMONDS -> DIAMOND; Suit.SPADES -> SPADE; Suit.CLUBS -> CLUB }
}

fun DrawScope.suit(s: Suit, rect: Rect, color: Color = if (s.red) RED else BLACK, flip: Boolean = false) {
    withTransform({
        translate(rect.left, rect.top)
        scale(rect.width / 100f, rect.height / 100f, Offset.Zero)
        if (flip) rotate(180f, Offset(50f, 50f))
    }) { drawPath(SuitShapes.of(s), color) }
}

/** Standard pip positions for 2-10 (x, y in a 0-1 box; y > 0.5 pips are drawn upside down). */
private val PIPS: Map<Int, List<Pair<Float, Float>>> = mapOf(
    2 to listOf(0.5f to 0.12f, 0.5f to 0.88f),
    3 to listOf(0.5f to 0.12f, 0.5f to 0.5f, 0.5f to 0.88f),
    4 to listOf(0.2f to 0.12f, 0.8f to 0.12f, 0.2f to 0.88f, 0.8f to 0.88f),
    5 to listOf(0.2f to 0.12f, 0.8f to 0.12f, 0.5f to 0.5f, 0.2f to 0.88f, 0.8f to 0.88f),
    6 to listOf(0.2f to 0.12f, 0.8f to 0.12f, 0.2f to 0.5f, 0.8f to 0.5f, 0.2f to 0.88f, 0.8f to 0.88f),
    7 to listOf(0.2f to 0.12f, 0.8f to 0.12f, 0.5f to 0.31f, 0.2f to 0.5f, 0.8f to 0.5f, 0.2f to 0.88f, 0.8f to 0.88f),
    8 to listOf(0.2f to 0.12f, 0.8f to 0.12f, 0.5f to 0.31f, 0.2f to 0.5f, 0.8f to 0.5f, 0.5f to 0.69f, 0.2f to 0.88f, 0.8f to 0.88f),
    9 to listOf(0.2f to 0.1f, 0.8f to 0.1f, 0.2f to 0.37f, 0.8f to 0.37f, 0.5f to 0.5f, 0.2f to 0.63f, 0.8f to 0.63f, 0.2f to 0.9f, 0.8f to 0.9f),
    10 to listOf(0.2f to 0.1f, 0.8f to 0.1f, 0.5f to 0.24f, 0.2f to 0.37f, 0.8f to 0.37f, 0.2f to 0.63f, 0.8f to 0.63f, 0.5f to 0.76f, 0.2f to 0.9f, 0.8f to 0.9f),
)

/** A card face. [dim] greys out a card that can't be played; [glow] rings it in the theme's accent. */
@Composable
fun CardFace(card: Card, modifier: Modifier = Modifier, dim: Boolean = false, glow: Color? = null) {
    val measurer = rememberTextMeasurer(16)
    val fonts = rememberBoardFonts()
    Canvas(modifier.aspectRatio(CARD_ASPECT)) { drawCard(card, measurer, fonts, dim, glow) }
}

@Composable
fun CardBack(modifier: Modifier = Modifier, color: Color = Color(0xFF7A2E22)) {
    Canvas(modifier.aspectRatio(CARD_ASPECT)) { drawCardBack(color) }
}

fun DrawScope.cardShape(): Path = Path().apply {
    addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(size.width * 0.08f)))
}

fun DrawScope.drawCard(card: Card, measurer: TextMeasurer, fonts: BoardFonts, dim: Boolean = false, glow: Color? = null) {
    val w = size.width; val h = size.height
    val shape = cardShape()
    glow?.let { g ->
        for (i in 3 downTo 1) drawPath(shape, g.copy(alpha = 0.18f * i), style = Stroke(w * 0.05f * i))
    }
    drawPath(shape, Color.Black.copy(alpha = 0.25f), style = Stroke(w * 0.035f))
    drawPath(shape, Brush.linearGradient(listOf(PAPER, lerp(PAPER, Color(0xFFE6D3A8), 0.5f)), Offset.Zero, Offset(w, h)))
    clipPath(shape) {
        // Aged paper: a darker rim.
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x33A07840)), Offset(w / 2, h / 2), maxOf(w, h) * 0.75f))
    }
    drawPath(shape, Color(0xFF8C7350).copy(alpha = 0.6f), style = Stroke(w * 0.012f))
    val ink = if (card.suit.red) RED else BLACK
    // Corner indexes, top left and (upside down) bottom right.
    val idx = TextStyle(color = ink, fontFamily = fonts.label, fontWeight = FontWeight.SemiBold, fontSize = (w * 0.2f / (density * fontScale)).sp)
    val label = measurer.measure(card.rankLabel, idx)
    for (flip in listOf(false, true)) {
        rotate(if (flip) 180f else 0f, Offset(w / 2, h / 2)) {
            drawText(label, topLeft = Offset(w * 0.15f - label.size.width / 2f, h * 0.025f))
            suit(card.suit, Rect(w * 0.07f, h * 0.18f, w * 0.23f, h * 0.18f + w * 0.16f), ink)
        }
    }
    val inner = Rect(w * 0.24f, h * 0.1f, w * 0.76f, h * 0.9f)
    when {
        card.rank == 1 -> {
            val s = w * 0.42f
            suit(card.suit, Rect(w / 2 - s / 2, h / 2 - s / 2, w / 2 + s / 2, h / 2 + s / 2), ink)
            // A little pinecone flourish under the ace of spades, as old decks dressed it up.
            if (card.suit == Suit.SPADES) pinecone(Offset(w / 2, h * 0.79f), w * 0.16f, 0f, Wood.WALNUT, 0.7f)
        }
        card.rank <= 10 -> {
            val pip = w * 0.17f
            for ((px, py) in PIPS.getValue(card.rank)) {
                val c = Offset(inner.left + inner.width * px, inner.top + inner.height * py)
                suit(card.suit, Rect(c.x - pip / 2, c.y - pip / 2, c.x + pip / 2, c.y + pip / 2), ink, flip = py > 0.55f)
            }
        }
        else -> court(card, inner, ink)
    }
    if (dim) drawPath(shape, Color(0xFF3A2E22).copy(alpha = 0.45f))
}

/**
 * Court cards, Maine style: the Jack is a woodsman's pine, the Queen a lighthouse, the King a bull moose, each in a
 * framed panel tinted by suit, with a suit sign above and below.
 */
private fun DrawScope.court(card: Card, r: Rect, ink: Color) {
    val panel = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(r, CornerRadius(r.width * 0.08f))) }
    val tint = if (card.suit.red) Color(0xFFF4D9C6) else Color(0xFFD9E2D3)
    drawPath(panel, Brush.verticalGradient(listOf(tint, lerp(tint, Color.White, 0.5f), tint), r.top, r.bottom))
    drawPath(panel, GOLD, style = Stroke(r.width * 0.05f))
    drawPath(panel, ink.copy(alpha = 0.7f), style = Stroke(r.width * 0.015f))
    clipPath(panel) {
        // Hills and water behind the figure.
        val hill = Path().apply {
            moveTo(r.left, r.bottom - r.height * 0.3f)
            quadraticTo(r.left + r.width * 0.3f, r.bottom - r.height * 0.42f, r.left + r.width * 0.6f, r.bottom - r.height * 0.32f)
            quadraticTo(r.left + r.width * 0.85f, r.bottom - r.height * 0.26f, r.right, r.bottom - r.height * 0.34f)
            lineTo(r.right, r.bottom); lineTo(r.left, r.bottom); close()
        }
        drawPath(hill, ink.copy(alpha = 0.18f))
        val sil = when (card.rank) { 11 -> Sil.PINE; 12 -> Sil.LIGHTHOUSE; else -> Sil.MOOSE }
        val s = r.width * if (card.rank == 13) 0.95f else 0.78f
        val c = Offset(r.center.x, r.center.y + r.height * 0.06f)
        engrave(sil, Rect(c.x - s / 2, c.y - s / 2, c.x + s / 2, c.y + s / 2), Wood.WALNUT, 0.95f, fill = ink, shine = false)
        if (card.rank == 11) {
            // A felling axe leaning on the pine.
            val a = Offset(c.x + s * 0.28f, c.y + s * 0.1f)
            drawLine(Color(0xFF6E4527), a, a + Offset(-s * 0.18f, s * 0.42f), strokeWidth = s * 0.05f)
            drawPath(Path().apply { moveTo(a.x - s * 0.04f, a.y - s * 0.02f); lineTo(a.x + s * 0.14f, a.y - s * 0.08f); lineTo(a.x + s * 0.12f, a.y + s * 0.1f); close() }, Color(0xFF6B7178))
        }
        if (card.rank == 12) {
            // Beams from the lamp.
            val lamp = Offset(c.x, c.y - s * 0.28f)
            for (side in listOf(-1f, 1f)) drawPath(Path().apply { moveTo(lamp.x, lamp.y); lineTo(lamp.x + side * s * 0.6f, lamp.y - s * 0.12f); lineTo(lamp.x + side * s * 0.6f, lamp.y + s * 0.08f); close() }, Color(0xFFFFE08A).copy(alpha = 0.6f))
        }
        if (card.rank == 13) {
            // A little gold crown between the antlers.
            val top = Offset(c.x + s * 0.24f, c.y - s * 0.36f)
            val crown = Path().apply {
                moveTo(top.x - s * 0.09f, top.y + s * 0.06f); lineTo(top.x - s * 0.09f, top.y - s * 0.03f); lineTo(top.x - s * 0.045f, top.y + s * 0.01f)
                lineTo(top.x, top.y - s * 0.06f); lineTo(top.x + s * 0.045f, top.y + s * 0.01f); lineTo(top.x + s * 0.09f, top.y - s * 0.03f); lineTo(top.x + s * 0.09f, top.y + s * 0.06f); close()
            }
            drawPath(crown, GOLD)
        }
    }
    val pip = r.width * 0.2f
    suit(card.suit, Rect(r.left + r.width * 0.06f, r.top + r.width * 0.06f, r.left + r.width * 0.06f + pip, r.top + r.width * 0.06f + pip), ink)
    suit(card.suit, Rect(r.right - r.width * 0.06f - pip, r.bottom - r.width * 0.06f - pip, r.right - r.width * 0.06f, r.bottom - r.width * 0.06f), ink, flip = true)
}

/** The back of a card: a plaid weave in the theme colour with a carved pinecone medallion. */
fun DrawScope.drawCardBack(color: Color) {
    val w = size.width; val h = size.height
    val shape = cardShape()
    drawPath(shape, Color.Black.copy(alpha = 0.25f), style = Stroke(w * 0.035f))
    drawPath(shape, PAPER)
    val inset = w * 0.07f
    val inner = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(inset, inset, w - inset, h - inset, CornerRadius(w * 0.05f))) }
    clipPath(inner) {
        drawRect(color)
        val s = w / 6f
        var x = 0f; var i = 0
        while (x < w) { if (i % 2 == 0) drawRect(Color.Black.copy(alpha = 0.28f), Offset(x, 0f), Size(s, h)); x += s; i++ }
        var y = 0f; i = 0
        while (y < h) { if (i % 2 == 0) drawRect(Color.Black.copy(alpha = 0.28f), Offset(0f, y), Size(w, s)); y += s; i++ }
        x = s * 0.5f
        while (x < w) { drawLine(Color(0xFFE8C46A).copy(alpha = 0.35f), Offset(x, 0f), Offset(x, h), strokeWidth = w * 0.01f); x += s * 2 }
        y = s * 0.5f
        while (y < h) { drawLine(Color(0xFFE8C46A).copy(alpha = 0.35f), Offset(0f, y), Offset(w, y), strokeWidth = w * 0.01f); y += s * 2 }
    }
    drawPath(inner, GOLD.copy(alpha = 0.8f), style = Stroke(w * 0.015f))
    val c = Offset(w / 2, h / 2)
    drawCircle(PAPER, w * 0.24f, c)
    drawCircle(GOLD, w * 0.24f, c, style = Stroke(w * 0.02f))
    pinecone(c + Offset(0f, w * 0.03f), w * 0.36f, 0f, Wood.WALNUT, 0.9f)
    drawPath(shape, Color(0xFF8C7350).copy(alpha = 0.6f), style = Stroke(w * 0.012f))
}

@Composable
internal fun rememberCardMeasurer() = rememberTextMeasurer(16).let { remember { it } }
