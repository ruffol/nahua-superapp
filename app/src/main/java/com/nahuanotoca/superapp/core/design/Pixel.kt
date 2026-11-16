package com.nahuanotoca.superapp.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nahuanotoca.superapp.R

/**
 * Dirección artística "pixel art" del estudio: fondos morados profundos, paneles con esquinas
 * escalonadas, bordes gruesos y tipografía de 8 bits.
 */

// --- Paleta ----------------------------------------------------------------

val PixelBg = Color(0xFF160C2B)
val PixelBgDeep = Color(0xFF0E071E)
val PixelPanel = Color(0xFF241545)
val PixelPanelAlt = Color(0xFF2E1B58)
val PixelPanelSoft = Color(0xFF3A2470)
val PixelBorder = Color(0xFF7C5CFF)
val PixelBorderSoft = Color(0xFF4A3380)
val PixelInk = Color(0xFFF2ECFF)
val PixelMuted = Color(0xFFB6A6E4)
val PixelGreen = Color(0xFF5BE49B)
val PixelGreenDeep = Color(0xFF1F8A5B)
val PixelPink = Color(0xFFFF7AB6)
val PixelYellow = Color(0xFFFFD166)
val PixelCyan = Color(0xFF6FE7F2)
val PixelRed = Color(0xFFFF5D6C)
val PixelPurple = Color(0xFF8A6FE8)
val PixelShadow = Color(0xFF0A0518)

// --- Tipografía ------------------------------------------------------------

val PixelDisplay = FontFamily(Font(R.font.press_start_2p, FontWeight.Normal))
val PixelBody = FontFamily(Font(R.font.vt323, FontWeight.Normal))

@Composable
fun PixelTitle(text: String, modifier: Modifier = Modifier, size: TextUnit = 11.sp, color: Color = PixelInk, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = align,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontFamily = PixelDisplay, fontSize = size, lineHeight = size * 1.7f),
    )
}

@Composable
fun PixelLabel(text: String, modifier: Modifier = Modifier, size: TextUnit = 7.sp, color: Color = PixelMuted) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontFamily = PixelDisplay, fontSize = size, lineHeight = size * 1.8f),
    )
}

@Composable
fun PixelBodyText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 19.sp,
    color: Color = PixelInk,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    align: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = align,
        style = TextStyle(fontFamily = PixelBody, fontSize = size, lineHeight = size * 1.05f),
    )
}

// --- Forma con esquinas escalonadas ---------------------------------------

class PixelCutShape(private val cut: Dp = 6.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(minOf(size.width, size.height) / 3f)
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(c, 0f)
            lineTo(w - c, 0f)
            lineTo(w - c, c)
            lineTo(w, c)
            lineTo(w, h - c)
            lineTo(w - c, h - c)
            lineTo(w - c, h)
            lineTo(c, h)
            lineTo(c, h - c)
            lineTo(0f, h - c)
            lineTo(0f, c)
            lineTo(c, c)
            close()
        }
        return Outline.Generic(path)
    }
}

// --- Fondo ----------------------------------------------------------------

@Composable
fun PixelBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxSize().background(PixelBg)) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 26.dp.toPx()
            val dot = 2.dp.toPx()
            var x = 0f
            while (x < size.width) {
                var y = 0f
                while (y < size.height) {
                    drawRect(
                        color = PixelBorderSoft.copy(alpha = 0.18f),
                        topLeft = Offset(x, y),
                        size = Size(dot, dot),
                    )
                    y += step
                }
                x += step
            }
        }
        content()
    }
}

// --- Paneles y botones -----------------------------------------------------

@Composable
fun PixelPanel(
    modifier: Modifier = Modifier,
    fill: Color = PixelPanel,
    border: Color = PixelBorder,
    borderWidth: Dp = 2.dp,
    shadow: Dp = 0.dp,
    cut: Dp = 6.dp,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = PixelCutShape(cut)
    Box(modifier = modifier) {
        if (shadow > 0.dp) {
            Box(
                Modifier
                    .matchParentSize()
                    .offset(y = shadow)
                    .clip(shape)
                    .background(PixelShadow),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(fill)
                .border(borderWidth, border, shape)
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = PixelPurple,
    textColor: Color = PixelInk,
    borderColor: Color = PixelInk.copy(alpha = 0.55f),
    enabled: Boolean = true,
    fontSize: TextUnit = 8.sp,
    expand: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val offsetY = if (pressed) 2.dp else 0.dp
    val activeColor = if (enabled) color else PixelBorderSoft

    Box(modifier = modifier) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = 3.dp)
                .clip(PixelCutShape(4.dp))
                .background(PixelShadow),
        )
        Row(
            modifier = Modifier
                .offset(y = offsetY)
                .then(if (expand) Modifier.fillMaxWidth() else Modifier)
                .clip(PixelCutShape(4.dp))
                .background(activeColor)
                .border(2.dp, if (enabled) borderColor else PixelBorderSoft, PixelCutShape(4.dp))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                )
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(6.dp))
            }
            PixelLabel(text = text, size = fontSize, color = if (enabled) textColor else PixelMuted)
            if (trailing != null) {
                Spacer(Modifier.width(6.dp))
                trailing()
            }
        }
    }
}

@Composable
fun PixelChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = PixelPurple,
    icon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(PixelCutShape(3.dp))
            .background(if (selected) color else PixelPanelAlt)
            .border(2.dp, if (selected) PixelInk.copy(alpha = 0.6f) else PixelBorderSoft, PixelCutShape(3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(5.dp))
        }
        PixelLabel(text = text, size = 7.sp, color = if (selected) PixelInk else PixelMuted)
    }
}

@Composable
fun PixelIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = PixelPanelAlt,
    border: Color = PixelBorderSoft,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(PixelCutShape(3.dp))
            .background(if (enabled) fill else PixelPanel)
            .border(2.dp, if (enabled) border else PixelBorderSoft, PixelCutShape(3.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun PixelTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    minLines: Int = 1,
    maxLines: Int = 4,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .clip(PixelCutShape(4.dp))
            .background(PixelPanel)
            .border(2.dp, if (focused) PixelGreen else PixelBorderSoft, PixelCutShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                PixelBodyText(placeholder, size = 18.sp, color = PixelMuted.copy(alpha = 0.8f))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(fontFamily = PixelBody, fontSize = 18.sp, color = PixelInk),
                cursorBrush = SolidColor(PixelGreen),
                visualTransformation = visualTransformation,
                minLines = minLines,
                maxLines = maxLines,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { focused = true },
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            trailing()
        }
    }
}

// --- Sprites ---------------------------------------------------------------

/**
 * Dibuja un sprite de pixel art a partir de un patrón de texto: cada carácter es un píxel y el
 * diccionario decide su color. Sirve para el avatar del asistente, el del usuario y los iconos.
 */
@Composable
fun PixelSprite(
    pattern: List<String>,
    palette: Map<Char, Color>,
    modifier: Modifier = Modifier,
    pixelSize: Dp = 3.dp,
) {
    val density = LocalDensity.current
    Canvas(modifier = modifier) {
        val step = with(density) { pixelSize.toPx() }
        pattern.forEachIndexed { row, line ->
            line.forEachIndexed { column, char ->
                val color = palette[char] ?: return@forEachIndexed
                drawRect(
                    color = color,
                    topLeft = Offset(column * step, row * step),
                    size = Size(step, step),
                )
            }
        }
    }
}

// --- Iconos pixel ----------------------------------------------------------

@Composable
fun PixelGlyph(
    rows: List<String>,
    modifier: Modifier = Modifier,
    color: Color = PixelInk,
    size: Dp = 18.dp,
) {
    val columns = rows.maxOfOrNull { it.length } ?: 1
    val pixel = size / columns.toFloat()
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        PixelSprite(pattern = rows, palette = mapOf('X' to color), pixelSize = pixel)
    }
}

fun rows(vararg lines: String): List<String> = lines.toList()

object PixelIcons {
    val send = rows(
        "........",
        "...X....",
        "...XX...",
        "...XXX..",
        "...XXXX.",
        "...XXX..",
        "...XX...",
        "...X....",
        "........",
    )
    val mic = rows(
        "...XXX...",
        "...XXX...",
        "...XXX...",
        "...XXX...",
        "...XXX...",
        ".X.XXX.X.",
        "....X....",
        "...XXX...",
        ".........",
    )
    val copy = rows(
        ".XXXXX...",
        ".X...X...",
        ".X...XXXX",
        ".X...X..X",
        ".XXXXX..X",
        ".....X..X",
        ".....XXXX",
    )
    val download = rows(
        "....X....",
        "....X....",
        "....X....",
        "..XXXXX..",
        "...XXX...",
        "....X....",
        ".........",
        "XXX...XXX",
        ".........",
    )
    val back = rows(
        "....X...",
        "...X....",
        "..X.....",
        ".X......",
        "..X.....",
        "...X....",
        "....X...",
    )
    val chevronUp = rows(
        "...X...",
        "..XXX..",
        ".XXXXX.",
        "XXXXXXX",
        ".......",
        ".......",
    )
    val close = rows(
        "X.....X",
        ".X...X.",
        "..X.X..",
        "...X...",
        "..X.X..",
        ".X...X.",
        "X.....X",
    )
    val gear = rows(
        "..X...X..",
        "..X.X.X..",
        "XXX.X.XXX",
        "...XXX...",
        ".XX.X.XX.",
        "...XXX...",
        "XXX.X.XXX",
        "..X.X.X..",
        "..X...X..",
    )
    val doc = rows(
        ".XXXXX..",
        ".X...XX.",
        ".X...X.X",
        ".X...XXX",
        ".X......",
        ".X......",
        ".X......",
        ".XXXXXX.",
    )
    val sparkle = rows(
        "...X...",
        "..XXX..",
        ".XXXXX.",
        "XXXXXXX",
        ".XXXXX.",
        "..XXX..",
        "...X...",
    )
}