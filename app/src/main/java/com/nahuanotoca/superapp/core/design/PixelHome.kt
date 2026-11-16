package com.nahuanotoca.superapp.core.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Superficies, sprites y glifos del nuevo inicio en pixel art.
 *
 * Los sprites viven en res/drawable-nodpi para que no los reescale la densidad del dispositivo:
 * la ilustración ya está dibujada a mano píxel por píxel y solo se recorta, nunca se difumina.
 */

// --- Paleta del inicio -------------------------------------------------------

val PixelCream = Color(0xFFF5E2BB)
val PixelCreamEdge = Color(0xFF5E3A1E)
val PixelCreamInk = Color(0xFF3B2412)
val PixelDeep = Color(0xFF1B1009)
val PixelViolet = Color(0xFFB5643F)
val PixelVioletDeep = Color(0xFF5F3120)
val PixelPanelDeep = Color(0xFF3A2117)
val PixelSun = Color(0xFFF0C05A)

// Escena del mapa
val MapSkyTop = Color(0xFF2C4BB8)
val MapSkyBottom = Color(0xFF74AEEF)
val MapGround = Color(0xFF2F8B4C)
val MapGroundDark = Color(0xFF1E6B39)
val MapGroundLight = Color(0xFF46A863)
val MapRoad = Color(0xFFC7D2E6)
val MapRoadLine = Color(0xFFF4F7FC)
val MapWater = Color(0xFF3FA9E0)
val MapWaterDeep = Color(0xFF2C86C4)
val MapMountain = Color(0xFF4A2C86)
val MapMountainLight = Color(0xFF6A48B8)
val MapSnow = Color(0xFFF1EEFF)
val MapWall = Color(0xFFF2CB8C)
val MapWallDark = Color(0xFFD9A968)
val MapRoof = Color(0xFFA94A34)
val MapRoofDark = Color(0xFF7E3524)
val MapShadow = Color(0xFF1B7A3D)
val MapPin = Color(0xFF2C6BF2)
val MapCityGround = Color(0xFF8A7350)
val MapWallSlate = Color(0xFF9FB2CE)

// --- Sprites ----------------------------------------------------------------

@Composable
fun PixelSpriteImage(
    @DrawableRes res: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    tint: Color? = null,
) {
    Image(
        painter = painterResource(res),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        colorFilter = tint?.let { ColorFilter.tint(it) },
    )
}

// --- Superficies ------------------------------------------------------------

/** Panel que se ajusta a su contenido, con esquinas escalonadas y borde grueso. */
@Composable
fun PixelSurface(
    modifier: Modifier = Modifier,
    fill: Color = PixelPanel,
    border: Color = PixelBorder,
    borderWidth: Dp = 2.dp,
    cut: Dp = 6.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = PixelCutShape(cut)
    var surface = modifier
        .clip(shape)
        .background(fill)
        .border(borderWidth, border, shape)
    if (onClick != null) {
        surface = surface.clickable(onClick = onClick)
    }
    Box(modifier = surface, content = content)
}

/**
 * Panel de tamaño fijo con sombra desplazada, el efecto "cartucho" del mockup.
 * El llamador define el tamaño (width/height o fillMaxWidth + height).
 */
@Composable
fun PixelFrame(
    modifier: Modifier = Modifier,
    fill: Color = PixelPanel,
    border: Color = PixelBorder,
    borderWidth: Dp = 2.dp,
    cut: Dp = 6.dp,
    shadow: Dp = 3.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = PixelCutShape(cut)
    var surface = modifier
    if (shadow > 0.dp) {
        // La sombra se dibuja detrás del panel desplazada hacia abajo, sin exigir tamaño fijo:
        // así el mismo componente sirve para tarjetas de altura fija y para listas que crecen.
        surface = surface.drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = (outline as androidx.compose.ui.graphics.Outline.Generic).path
            translate(top = shadow.toPx()) {
                drawPath(path, PixelShadow)
            }
        }
    }
    surface = surface
        .clip(shape)
        .background(fill)
        .border(borderWidth, border, shape)
    if (onClick != null) {
        surface = surface.clickable(onClick = onClick)
    }
    Box(modifier = surface, content = content)
}

// --- Glifos ----------------------------------------------------------------

/** Iconos de 9x9 píxeles para botones y cabeceras del inicio. */
object PixelUiGlyphs {

    val search = rows(
        ".XXXXX...",
        "X.....X..",
        "X.....X..",
        "X.....X..",
        "X.....X..",
        ".XXXXX...",
        "....XX...",
        ".....XX..",
        "......XX.",
    )

    val scan = rows(
        "XXX...XXX",
        "X.X...X.X",
        "XXX...XXX",
        ".........",
        "..X...X..",
        ".........",
        "XXX...XXX",
        "X.X...X.X",
        "XXX...XXX",
    )

    val crosshair = rows(
        "..XXXXX..",
        ".XX...XX.",
        ".X..X..X.",
        "XX.XXX.XX",
        "XX.XXX.XX",
        "XX.XXX.XX",
        ".X..X..X.",
        ".XX...XX.",
        "..XXXXX..",
    )

    val expand = rows(
        "XXX...XXX",
        "X.......X",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        "X.......X",
        "XXX...XXX",
    )

    val play = rows(
        "XX.....",
        "XXXX...",
        "XXXXXX.",
        "XXXXXXX",
        "XXXXXX.",
        "XXXX...",
        "XX.....",
    )

    val chevron = rows(
        "XX.....",
        ".XX....",
        "..XX...",
        "...XX..",
        "..XX...",
        ".XX....",
        "XX.....",
    )

    val arrowUp = rows(
        "...X...",
        "..XXX..",
        ".XXXXX.",
        "XXXXXXX",
        ".......",
        ".......",
        ".......",
    )

    val arrowDown = rows(
        ".......",
        ".......",
        ".......",
        "XXXXXXX",
        ".XXXXX.",
        "..XXX..",
        "...X...",
    )

    val chevronLeft = rows(
        ".....XX",
        "....XX.",
        "...XX..",
        "..XX...",
        "...XX..",
        "....XX.",
        ".....XX",
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
