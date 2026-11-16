package com.nahuanotoca.superapp.feature.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.nahuanotoca.superapp.core.design.MapCityGround
import com.nahuanotoca.superapp.core.design.MapGround
import com.nahuanotoca.superapp.core.design.MapGroundDark
import com.nahuanotoca.superapp.core.design.MapGroundLight
import com.nahuanotoca.superapp.core.design.MapMountain
import com.nahuanotoca.superapp.core.design.MapMountainLight
import com.nahuanotoca.superapp.core.design.MapPin
import com.nahuanotoca.superapp.core.design.MapRoad
import com.nahuanotoca.superapp.core.design.MapRoadLine
import com.nahuanotoca.superapp.core.design.MapRoof
import com.nahuanotoca.superapp.core.design.MapRoofDark
import com.nahuanotoca.superapp.core.design.MapShadow
import com.nahuanotoca.superapp.core.design.MapSkyBottom
import com.nahuanotoca.superapp.core.design.MapSkyTop
import com.nahuanotoca.superapp.core.design.MapSnow
import com.nahuanotoca.superapp.core.design.MapWall
import com.nahuanotoca.superapp.core.design.MapWallDark
import com.nahuanotoca.superapp.core.design.MapWallSlate
import com.nahuanotoca.superapp.core.design.MapWater
import com.nahuanotoca.superapp.core.design.MapWaterDeep

/**
 * Ilustración del mapa: la ciudad de Colima en pixel art, con el volcán, la catedral, el río y
 * las calles. Se dibuja con rectángulos alineados a una rejilla para conservar las aristas duras
 * del estilo; así el encabezado escala a cualquier pantalla sin cargar imágenes.
 */
@Composable
fun PixelCityMap(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val u = (h / 46f).coerceAtLeast(1.6f)
        val horizon = h * 0.34f

        drawSky(w, h, horizon, u)
        drawVolcano(w, horizon, u)
        drawGround(w, h, horizon)
        drawCity(w, h, horizon, u)
        drawRiver(w, h, horizon, u)
        drawCathedral(Offset(w * 0.73f, horizon + u * 9.5f), u * 0.82f)
        drawTrees(w, h, horizon, u)
    }
}

private fun DrawScope.drawSky(w: Float, h: Float, horizon: Float, u: Float) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(MapSkyTop, MapSkyBottom),
            startY = 0f,
            endY = horizon,
        ),
        size = Size(w, horizon),
    )

    cloud(Offset(w * 0.20f, h * 0.13f), u * 2.2f)
    cloud(Offset(w * 0.42f, h * 0.06f), u * 2.8f)
    cloud(Offset(w * 0.58f, h * 0.18f), u * 1.7f)
}

private fun DrawScope.cloud(center: Offset, unit: Float) {
    val shade = Color(0xFFD6E4F7)
    drawRect(shade, Offset(center.x - unit * 2f, center.y), Size(unit * 5f, unit * 1.1f))
    drawRect(Color.White, Offset(center.x - unit * 1.4f, center.y - unit * 0.8f), Size(unit * 3.2f, unit * 1.1f))
    drawRect(Color.White, Offset(center.x - unit * 2.6f, center.y + unit * 0.2f), Size(unit * 1.6f, unit * 0.9f))
}

private fun DrawScope.drawVolcano(w: Float, horizon: Float, u: Float) {
    val peakY = horizon * 0.06f
    val baseY = horizon + u * 2.2f
    val peakX = w * 0.90f
    val half = w * 0.17f

    val body = Path().apply {
        moveTo(peakX - half, baseY)
        lineTo(peakX, peakY)
        lineTo(peakX + half, baseY)
        close()
    }
    drawPath(body, MapMountain)

    val light = Path().apply {
        moveTo(peakX, peakY)
        lineTo(peakX + half, baseY)
        lineTo(peakX + half * 0.32f, baseY)
        close()
    }
    drawPath(light, MapMountainLight)

    val snow = Path().apply {
        moveTo(peakX, peakY)
        lineTo(peakX + half * 0.26f, peakY + (baseY - peakY) * 0.20f)
        lineTo(peakX + half * 0.10f, peakY + (baseY - peakY) * 0.15f)
        lineTo(peakX, peakY + (baseY - peakY) * 0.22f)
        lineTo(peakX - half * 0.12f, peakY + (baseY - peakY) * 0.14f)
        lineTo(peakX - half * 0.26f, peakY + (baseY - peakY) * 0.21f)
        close()
    }
    drawPath(snow, MapSnow)

    val second = Path().apply {
        moveTo(w * 0.48f, baseY)
        lineTo(w * 0.57f, horizon * 0.34f)
        lineTo(w * 0.66f, baseY)
        close()
    }
    drawPath(second, MapMountainLight)
}

private fun DrawScope.drawGround(w: Float, h: Float, horizon: Float) {
    drawRect(MapGround, Offset(0f, horizon), Size(w, h - horizon))
}

private fun DrawScope.drawCity(w: Float, h: Float, horizon: Float, u: Float) {
    val top = horizon + u * 0.4f
    val cellW = w / 4f
    val cellH = (h - top) / 3f

    // Suelo urbano
    drawRect(MapGround, Offset(0f, top), Size(w, h - top))

    // Calles horizontales
    for (r in 0..3) {
        val y = top + r * cellH
        drawRect(MapRoad, Offset(0f, y - u * 0.45f), Size(w, u * 0.9f))
        if (r in 1..2) {
            var dash = u * 0.6f
            while (dash < w) {
                drawRect(MapRoadLine, Offset(dash, y - u * 0.1f), Size(u * 0.9f, u * 0.2f))
                dash += u * 2.4f
            }
        }
    }
    // Calles verticales
    for (c in 0..4) {
        val x = c * cellW
        drawRect(MapRoad, Offset(x - u * 0.45f, top), Size(u * 0.9f, h - top))
    }

    // Manzanas
    for (r in 0 until 3) {
        for (c in 0 until 4) {
            val x0 = c * cellW + u * 0.55f
            val y0 = top + r * cellH + u * 0.45f
            val bw = cellW - u * 1.0f
            val bh = cellH - u * 0.9f
            if (bw <= 0f || bh <= 0f) continue

            val seed = r * 4 + c
            drawRect(MapGroundLight, Offset(x0, y0), Size(bw, bh))
            if (seed % 4 == 2) {
                park(x0, y0, bw, bh, u, seed)
            } else {
                blockBuildings(x0, y0, bw, bh, u, seed)
            }
        }
    }
}

private fun DrawScope.blockBuildings(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    u: Float,
    seed: Int,
) {
    // Explanada de tierra y dos edificios con aire alrededor
    val plazaX = x + width * 0.10f
    val plazaY = y + height * 0.16f
    val plazaW = width * 0.80f
    val plazaH = height * 0.66f
    drawRect(MapCityGround, Offset(plazaX, plazaY), Size(plazaW, plazaH))
    drawRect(MapCityGround.copy(alpha = 0.65f), Offset(plazaX, plazaY + plazaH * 0.82f), Size(plazaW, plazaH * 0.18f))

    val walls = listOf(MapWall, MapWallSlate, MapWallDark)
    val roofs = listOf(MapRoof, MapRoofDark, MapRoofDark)
    val count = 2 + (seed % 2)

    for (i in 0 until count) {
        val slotWidth = plazaW / count
        val buildingWidth = slotWidth * 0.66f
        val tall = 0.55f + ((seed + i) % 3) * 0.13f
        val buildingHeight = plazaH * tall
        val bx = plazaX + i * slotWidth + (slotWidth - buildingWidth) / 2f
        val by = plazaY + plazaH - buildingHeight

        drawRect(walls[(seed + i) % walls.size], Offset(bx, by), Size(buildingWidth, buildingHeight))
        drawRect(
            roofs[(seed + i) % roofs.size],
            Offset(bx - u * 0.20f, by - u * 0.6f),
            Size(buildingWidth + u * 0.40f, u * 0.6f),
        )

        // Un par de ventanas por edificio
        val window = u * 0.42f
        drawRect(Color(0xFF3A4380), Offset(bx + buildingWidth * 0.22f, by + buildingHeight * 0.26f), Size(window, window))
        drawRect(Color(0xFF3A4380), Offset(bx + buildingWidth * 0.62f, by + buildingHeight * 0.26f), Size(window, window))
        if (buildingHeight > plazaH * 0.7f) {
            drawRect(Color(0xFF3A4380), Offset(bx + buildingWidth * 0.22f, by + buildingHeight * 0.56f), Size(window, window))
            drawRect(Color(0xFF3A4380), Offset(bx + buildingWidth * 0.62f, by + buildingHeight * 0.56f), Size(window, window))
        }
    }
}

private fun DrawScope.park(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    u: Float,
    seed: Int,
) {
    drawRect(MapGroundLight, Offset(x, y), Size(width, height))
    treeAt(Offset(x + width * 0.30f, y + height * 0.75f), u * 0.85f)
    treeAt(Offset(x + width * 0.68f, y + height * 0.55f), u * 0.7f)
    if (seed % 2 == 0) {
        drawRect(MapWater, Offset(x + width * 0.52f, y + height * 0.80f), Size(width * 0.30f, height * 0.16f))
    }
}

private fun DrawScope.drawRiver(w: Float, h: Float, horizon: Float, u: Float) {
    val river = Path().apply {
        moveTo(w * 0.300f, horizon)
        lineTo(w * 0.352f, horizon)
        lineTo(w * 0.300f, h * 0.62f)
        lineTo(w * 0.232f, h)
        lineTo(w * 0.178f, h)
        lineTo(w * 0.248f, h * 0.62f)
        close()
    }
    drawPath(river, MapWater)
    drawPath(river, MapWaterDeep.copy(alpha = 0.5f), style = Stroke(width = u * 0.45f))

    // Puentes sobre las avenidas
    val bridges = listOf(horizon + (h - horizon) * 0.33f, horizon + (h - horizon) * 0.66f)
    bridges.forEach { bridgeY ->
        drawRect(MapRoad, Offset(w * 0.14f, bridgeY), Size(w * 0.22f, u * 0.85f))
        drawRect(MapRoadLine, Offset(w * 0.14f, bridgeY + u * 0.30f), Size(w * 0.22f, u * 0.22f))
    }
}

private fun DrawScope.drawCathedral(origin: Offset, u: Float) {
    val bx = origin.x
    val by = origin.y

    // Plaza
    drawRect(Color(0xFFD8CFAE), Offset(bx - u * 11f, by - u * 1.2f), Size(u * 22f, u * 2.4f))

    // Nave central
    drawRect(MapWallDark, Offset(bx - u * 7.4f, by - u * 8.2f), Size(u * 14.8f, u * 8.2f))
    drawRect(MapWall, Offset(bx - u * 7f, by - u * 8f), Size(u * 14f, u * 8f))

    val nave = Path().apply {
        moveTo(bx - u * 7.8f, by - u * 8f)
        lineTo(bx, by - u * 11.8f)
        lineTo(bx + u * 7.8f, by - u * 8f)
        close()
    }
    drawPath(nave, MapRoof)

    // Torres
    listOf(-1f, 1f).forEach { side ->
        val towerX = bx + side * u * 7.4f
        drawRect(MapWall, Offset(towerX - u * 1.9f, by - u * 13.6f), Size(u * 3.8f, u * 13.6f))
        drawRect(MapWallDark, Offset(towerX - u * 1.9f, by - u * 13.6f), Size(u * 0.7f, u * 13.6f))
        val spire = Path().apply {
            moveTo(towerX - u * 2.4f, by - u * 13.4f)
            lineTo(towerX, by - u * 17.4f)
            lineTo(towerX + u * 2.4f, by - u * 13.4f)
            close()
        }
        drawPath(spire, MapRoof)
        drawRect(Color(0xFF3B2C6E), Offset(towerX - u * 0.6f, by - u * 11.8f), Size(u * 1.2f, u * 1.8f))
    }

    // Puerta y rosetón
    drawRect(Color(0xFF5A3418), Offset(bx - u * 1.7f, by - u * 3.8f), Size(u * 3.4f, u * 3.8f))
    drawRect(Color(0xFF2C6BF2), Offset(bx - u * 1.0f, by - u * 3.0f), Size(u * 2.0f, u * 2.4f))
    drawCircle(MapShadow, radius = u * 1.6f, center = Offset(bx, by - u * 5.8f))
    drawCircle(Color(0xFF2C6BF2).copy(alpha = 0.65f), radius = u * 0.85f, center = Offset(bx, by - u * 5.8f))
}

private fun DrawScope.drawTrees(w: Float, h: Float, horizon: Float, u: Float) {
    val spots = listOf(
        Offset(w * 0.035f, horizon + u * 1.2f),
        Offset(w * 0.94f, h - u * 1.6f),
        Offset(w * 0.50f, horizon + u * 0.8f),
        Offset(w * 0.80f, h - u * 1.2f),
    )
    spots.forEach { treeAt(it, u * 1.0f) }
}

private fun DrawScope.treeAt(base: Offset, unit: Float) {
    drawRect(Color(0xFF4A2C17), Offset(base.x - unit * 0.22f, base.y - unit * 0.55f), Size(unit * 0.44f, unit * 0.55f))
    val crown = Path().apply {
        moveTo(base.x - unit * 1.1f, base.y - unit * 0.5f)
        lineTo(base.x, base.y - unit * 2.2f)
        lineTo(base.x + unit * 1.1f, base.y - unit * 0.5f)
        close()
    }
    drawPath(crown, MapShadow)
}

/**
 * Pin azul de ubicación sobre el mapa con anillos que laten, como en el mockup.
 */
@Composable
fun PixelLocationPin(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pin-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pin-pulse-value",
    )

    Canvas(modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        repeat(3) { index ->
            val progress = (pulse + index / 3f) % 1f
            drawCircle(
                color = MapPin.copy(alpha = 0.30f * (1f - progress)),
                radius = radius * (0.34f + progress * 0.62f),
                center = center,
            )
        }

        val headRadius = radius * 0.26f
        val headCenter = Offset(center.x, center.y - headRadius * 0.75f)
        drawCircle(MapPin, headRadius, headCenter)
        val tip = Path().apply {
            moveTo(headCenter.x - headRadius * 0.72f, headCenter.y + headRadius * 0.55f)
            lineTo(headCenter.x + headRadius * 0.72f, headCenter.y + headRadius * 0.55f)
            lineTo(center.x, headCenter.y + headRadius * 2.05f)
            close()
        }
        drawPath(tip, MapPin)
        drawCircle(Color.White, headRadius * 0.44f, headCenter)
    }
}
