package com.geoguy89.cribbage.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoguy89.cribbage.board.BoardFonts
import com.geoguy89.cribbage.board.rememberBoardFonts
import com.geoguy89.cribbage.gfx.Carving.bevelEdge
import com.geoguy89.cribbage.gfx.Carving.woodGrain
import com.geoguy89.cribbage.gfx.Wood
import com.geoguy89.cribbage.gfx.burn
import com.geoguy89.cribbage.gfx.highlight
import com.geoguy89.cribbage.theme.BaseTheme
import com.geoguy89.cribbage.theme.Look

/** The current look and fonts, for every component below. */
class Ui(val look: Look, val fonts: BoardFonts) {
    val wood: Wood get() = look.base.signWood
    val paper: Color get() = look.base.paper
    val ink: Color get() = look.base.ink
    val accent: Color get() = look.accent
    /** Text that sits straight on the table. */
    val onTable: Color get() = if (look.base == BaseTheme.BIRCH_MORNING || look.base == BaseTheme.LOBSTER_SHACK) Color(0xFF2B2017) else Color(0xFFF6EBD2)
}

val LocalUi = staticCompositionLocalOf<Ui> { error("no Ui") }

@Composable
fun ProvideUi(look: Look, content: @Composable () -> Unit) {
    val fonts = rememberBoardFonts()
    val ui = remember(look, fonts) { Ui(look, fonts) }
    CompositionLocalProvider(LocalUi provides ui) { content() }
}

@Composable
fun Txt(
    text: String, modifier: Modifier = Modifier, size: TextUnit = 15.sp, color: Color = LocalUi.current.ink,
    font: FontFamily? = null, weight: FontWeight = FontWeight.Normal, align: TextAlign = TextAlign.Start, maxLines: Int = Int.MAX_VALUE,
    shadow: Boolean = false,
) {
    BasicText(
        text, modifier,
        style = TextStyle(
            color = color, fontSize = size, fontFamily = font ?: LocalUi.current.fonts.label, fontWeight = weight, textAlign = align,
            shadow = if (shadow) Shadow(Color.Black.copy(alpha = 0.6f), Offset(1f, 2f), 4f) else null,
            lineHeight = size * 1.25f,
        ),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Carved lettering: the burnt colour with a sliver of light underneath, like the board text. One-line text shrinks
 * until it fits the space it's given, so labels are never cut off.
 */
@Composable
fun Carved(text: String, size: TextUnit, modifier: Modifier = Modifier, wood: Wood = LocalUi.current.wood, align: TextAlign = TextAlign.Center, maxLines: Int = 1, font: FontFamily? = null) {
    var fit by remember(text, size) { mutableStateOf(size.value) }
    BasicText(
        text, modifier,
        style = TextStyle(
            color = wood.burn(), fontSize = fit.sp, fontFamily = font ?: LocalUi.current.fonts.title, fontWeight = FontWeight.Bold,
            textAlign = align, shadow = Shadow(wood.highlight().copy(alpha = 0.8f), Offset(0f, 2f), 1f),
        ),
        maxLines = maxLines, softWrap = maxLines > 1, overflow = TextOverflow.Clip,
        onTextLayout = { r -> if (maxLines == 1 && r.didOverflowWidth && fit > 8f) fit *= 0.92f },
    )
}

/** Paints a wooden plank behind a composable: grain, bevel, a shadow and (optionally) brass screws. */
fun Modifier.plank(wood: Wood, seed: Int = 1, radius: Dp = 8.dp, screws: Boolean = false, glow: Color? = null, pressed: Boolean = false): Modifier = drawBehind {
    val r = radius.toPx()
    val shape = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
    glow?.let { g -> for (i in 3 downTo 1) drawPath(shape, g.copy(alpha = 0.22f * i), style = Stroke(3.dp.toPx() * i)) }
    val dx = 2.dp.toPx(); val dy = 3.dp.toPx()
    if (!pressed) withTransform({ translate(dx, dy) }) { drawPath(shape, Color.Black.copy(alpha = 0.4f)) }
    drawPath(shape, Brush.verticalGradient(listOf(wood.light, wood.base, lerp(wood.base, wood.dark, 0.3f))))
    val k = 1.dp.toPx() * 1.6f
    clipPath(shape) {
        withTransform({ scale(k, k, Offset.Zero) }) { woodGrain(Rect(0f, 0f, size.width / k, size.height / k), wood, seed, 0f) }
        if (pressed) drawRect(Color.Black.copy(alpha = 0.15f))
    }
    withTransform({ scale(1f, 1f) }) { bevelEdgePx(shape, wood, 2.5.dp.toPx()) }
    if (screws) for (x in listOf(10.dp.toPx(), size.width - 10.dp.toPx())) {
        val c = Offset(x, size.height / 2)
        drawCircle(Color.Black.copy(alpha = 0.35f), 4.dp.toPx(), c + Offset(1f, 1.5f))
        drawCircle(Brush.radialGradient(listOf(Color(0xFFF2D27A), Color(0xFF9C7A2E)), c - Offset(1.5f, 1.5f), 5.dp.toPx()), 3.6.dp.toPx(), c)
        drawLine(Color(0xFF6B5420), c - Offset(2.5.dp.toPx(), -1.dp.toPx()), c + Offset(2.5.dp.toPx(), -1.dp.toPx()), strokeWidth = 1.dp.toPx())
    }
}

private fun DrawScope.bevelEdgePx(shape: Path, wood: Wood, w: Float) {
    clipPath(shape) {
        drawPath(shape, wood.dark.copy(alpha = 0.55f), style = Stroke(w * 2.2f))
        drawPath(shape, wood.highlight().copy(alpha = 0.55f), style = Stroke(w * 0.9f))
    }
    drawPath(shape, wood.dark, style = Stroke(1f))
}

/** A carved wooden sign. */
@Composable
fun WoodSign(text: String, modifier: Modifier = Modifier, size: TextUnit = 28.sp, wood: Wood = LocalUi.current.wood, seed: Int = 3) {
    Box(modifier.plank(wood, seed, 10.dp, screws = true).padding(horizontal = 28.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Carved(text, size, wood = wood)
    }
}

/** A small plaque: "Sebec Lake, ME" or "You: 34". */
@Composable
fun Plaque(text: String, modifier: Modifier = Modifier, size: TextUnit = 15.sp, wood: Wood = LocalUi.current.wood, seed: Int = 9, pegColor: Color? = null) {
    Row(modifier.plank(wood, seed, 6.dp, screws = pegColor == null).padding(horizontal = if (pegColor == null) 20.dp else 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (pegColor != null) {
            Canvas(Modifier.size(10.dp, 18.dp)) {
                val sz = this.size
                drawRoundRect(Color.Black.copy(alpha = 0.3f), Offset(2f, 3f), sz, CornerRadius(sz.width / 2))
                drawRoundRect(Brush.horizontalGradient(listOf(lerp(pegColor, Color.White, 0.4f), pegColor, lerp(pegColor, Color.Black, 0.4f))), Offset.Zero, sz, CornerRadius(sz.width / 2))
            }
            Spacer(Modifier.width(8.dp))
        }
        Carved(text, size, wood = wood, font = LocalUi.current.fonts.label)
    }
}

/** A wooden button that sinks when pressed. [primary] glows in the accent colour. */
@Composable
fun WoodButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true,
    size: TextUnit = 17.sp, icon: (@Composable () -> Unit)? = null, wood: Wood = LocalUi.current.wood,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(90))
    Box(
        modifier
            .scale(scale)
            .heightIn(min = 46.dp)
            .plank(wood, text.hashCode(), 9.dp, glow = if (primary && enabled) Color(0xFF7CFC6A) else null, pressed = pressed)
            .clickable(interaction, null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            // Labels shrink to fit the button rather than get cut off.
            Carved(text.uppercase(), size, Modifier.weight(1f, fill = false), wood = if (enabled) wood else Wood.DRIFTWOOD, font = LocalUi.current.fonts.label)
            if (icon != null) { Spacer(Modifier.width(8.dp)); icon() }
        }
    }
}

/** A pinned scrap of parchment, the tutorial helper's note paper. */
@Composable
fun Parchment(modifier: Modifier = Modifier, pins: Boolean = true, tight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val ui = LocalUi.current
    Column(
        modifier.drawBehind {
            // A slightly ragged edge.
            val p = Path()
            val n = 40
            val j = 3.dp.toPx()
            fun jag(i: Int) = (com.geoguy89.cribbage.board.pseudo(i * 7 + 3) - 0.5f) * j
            p.moveTo(0f, 0f)
            for (i in 0..n) p.lineTo(size.width * i / n, jag(i))
            for (i in 0..n) p.lineTo(size.width + jag(i + 50), size.height * i / n)
            for (i in n downTo 0) p.lineTo(size.width * i / n, size.height + jag(i + 100))
            for (i in n downTo 0) p.lineTo(jag(i + 150), size.height * i / n)
            p.close()
            val sx = 3.dp.toPx(); val sy = 4.dp.toPx()
            withTransform({ translate(sx, sy) }) { drawPath(p, Color.Black.copy(alpha = 0.35f)) }
            drawPath(p, Brush.radialGradient(listOf(ui.paper, lerp(ui.paper, Color(0xFFCBB27E), 0.55f)), Offset(size.width / 2, size.height / 2), size.maxDimension * 0.7f))
            if (pins) for (x in listOf(18.dp.toPx(), size.width - 18.dp.toPx())) {
                val c = Offset(x, 12.dp.toPx())
                drawCircle(Color.Black.copy(alpha = 0.3f), 6.dp.toPx(), c + Offset(2f, 3f))
                drawCircle(Brush.radialGradient(listOf(Color(0xFFE9D6A8), Color(0xFF8C6A3A)), c - Offset(2f, 2f), 8.dp.toPx()), 5.5.dp.toPx(), c)
            }
        }.padding(horizontal = 18.dp, vertical = if (tight) 8.dp else 14.dp),
        content = content,
    )
}

/** Handwritten text for the tutorial helper. */
@Composable
fun Hand(text: String, modifier: Modifier = Modifier, size: TextUnit = 17.sp, color: Color = Color(0xFF3B2414), align: TextAlign = TextAlign.Start, weight: FontWeight = FontWeight.Normal) {
    Txt(text, modifier, size, color, LocalUi.current.fonts.hand, weight, align)
}

/** The leather tab strip along the bottom. */
@Composable
fun LeatherTabs(tabs: List<Pair<String, () -> Unit>>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(48.dp).drawBehind {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF6B4226), Color(0xFF4A2B17))))
        // Stitching.
        val y = 5.dp.toPx()
        var x = 6.dp.toPx()
        while (x < size.width) { drawLine(Color(0xFFD9B98A).copy(alpha = 0.7f), Offset(x, y), Offset(x + 5.dp.toPx(), y), strokeWidth = 1.2.dp.toPx()); x += 9.dp.toPx() }
    }) {
        tabs.forEachIndexed { i, (label, action) ->
            Box(Modifier.weight(1f).fillMaxSize().clickable(onClick = action), contentAlignment = Alignment.Center) {
                Txt(label.uppercase(), size = 14.sp, color = Color(0xFFF1DDB8), weight = FontWeight.Medium, maxLines = 1)
            }
            if (i < tabs.size - 1) Box(Modifier.width(1.dp).fillMaxSize().padding(vertical = 10.dp).drawBehind { drawRect(Color.Black.copy(alpha = 0.35f)) })
        }
    }
}

/** A wooden slider for volumes: a groove with a turned knob. */
@Composable
fun WoodSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    Canvas(
        modifier.fillMaxWidth().height(36.dp)
            .pointerInput(Unit) { detectTapGestures { onChange((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> onChange((change.position.x / size.width).coerceIn(0f, 1f)) } },
    ) {
        val y = size.height / 2
        val pad = 12.dp.toPx()
        val x = pad + (size.width - pad * 2) * value
        drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(pad, y - 4.dp.toPx()), androidx.compose.ui.geometry.Size(size.width - pad * 2, 8.dp.toPx()), CornerRadius(4.dp.toPx()))
        drawRoundRect(ui.accent, Offset(pad, y - 3.dp.toPx()), androidx.compose.ui.geometry.Size(x - pad, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.35f), 11.dp.toPx(), Offset(x + 2f, y + 3f))
        drawCircle(Brush.radialGradient(listOf(ui.wood.light, ui.wood.base, ui.wood.dark), Offset(x - 3f, y - 3f), 14.dp.toPx()), 10.dp.toPx(), Offset(x, y))
    }
}

/** A brass toggle switch. */
@Composable
fun Toggle(on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    val k by animateFloatAsState(if (on) 1f else 0f, tween(160))
    Canvas(modifier.size(52.dp, 30.dp).clickable { onChange(!on) }) {
        drawRoundRect(lerp(Color(0xFF5A4A3A), ui.accent, k), size = size, cornerRadius = CornerRadius(size.height / 2))
        val c = Offset(size.height / 2 + (size.width - size.height) * k, size.height / 2)
        drawCircle(Color.Black.copy(alpha = 0.3f), size.height * 0.4f, c + Offset(1f, 2f))
        drawCircle(Brush.radialGradient(listOf(Color(0xFFF7E2A6), Color(0xFFB08A3A)), c - Offset(3f, 3f), size.height * 0.5f), size.height * 0.38f, c)
    }
}

/** A labelled settings row. */
@Composable
fun SettingRow(title: String, sub: String? = null, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(title, size = 16.sp, weight = FontWeight.Medium)
            if (sub != null) Txt(sub, size = 13.sp, color = LocalUi.current.ink.copy(alpha = 0.7f))
        }
        trailing()
    }
}

/** A titled parchment page with a scrolling body: Settings, Rules, Friends... */
@Composable
fun Page(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WoodButton("‹ Back", onBack, size = 14.sp)
            Spacer(Modifier.weight(1f))
            actions()
        }
        Spacer(Modifier.height(8.dp))
        WoodSign(title, size = 24.sp)
        Spacer(Modifier.height(12.dp))
        Parchment(Modifier.fillMaxWidth().weight(1f), pins = true) {
            Column(Modifier.fillMaxSize().verticalScrollCompat()) { content() }
        }
    }
}

/** Fills the screen behind a dialog and swallows taps. */
@Composable
fun Scrim(onDismiss: (() -> Unit)? = null, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = 0.55f)) }.clickable(remember { MutableInteractionSource() }, null) { onDismiss?.invoke() }, contentAlignment = Alignment.Center, content = content)
}

@Composable
fun Modifier.verticalScrollCompat(): Modifier = this.verticalScroll(androidx.compose.foundation.rememberScrollState())
