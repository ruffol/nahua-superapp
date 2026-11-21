package com.nahuanotoca.superapp.feature.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nahuanotoca.superapp.R
import com.nahuanotoca.superapp.core.design.Clay
import com.nahuanotoca.superapp.core.design.PixelBackdrop
import com.nahuanotoca.superapp.core.design.PixelBg
import com.nahuanotoca.superapp.core.design.PixelBodyText
import com.nahuanotoca.superapp.core.design.PixelBorderSoft
import com.nahuanotoca.superapp.core.design.PixelCutShape
import com.nahuanotoca.superapp.core.design.PixelGlyph
import com.nahuanotoca.superapp.core.design.PixelInk
import com.nahuanotoca.superapp.core.design.PixelLabel
import com.nahuanotoca.superapp.core.design.PixelMuted
import com.nahuanotoca.superapp.core.design.PixelPanel
import com.nahuanotoca.superapp.core.design.PixelShadow
import com.nahuanotoca.superapp.core.design.PixelSpriteImage
import com.nahuanotoca.superapp.core.design.PixelSurface
import com.nahuanotoca.superapp.core.design.PixelTitle
import com.nahuanotoca.superapp.core.design.PixelUiGlyphs
import com.nahuanotoca.superapp.core.model.Service

/**
 * Inicio en pixel art de barro: el mapa offline de Colima vive en un pergamino que se desenrolla al
 * tocar y debajo va el panal de aplicaciones en dos filas de cuatro hexágonos.
 *
 * Sin buscador, sin saludo, sin lector de QR y sin el resumen de cifras: la portada se queda en lo
 * que se toca. Las seis aplicaciones ocupadas van primero y las dos últimas celdas quedan vacías,
 * marcadas como «Pronto», para que la próxima app entre sin rehacer el panal.
 */

private data class HomeApp(
    val serviceId: String,
    val title: String,
    @param:DrawableRes val art: Int,
    val fill: Color,
    val accent: Color,
    val roundArt: Boolean = false,
)

private val homeApps = listOf(
    HomeApp(
        serviceId = "yolcatl",
        title = "Yōlcatl",
        art = R.drawable.px_dog,
        fill = Color(0xFF7A3D27),
        accent = Color(0xFFE0A05A),
        roundArt = true,
    ),
    HomeApp(
        serviceId = "studio",
        title = "RAG",
        art = R.drawable.px_icon_rag,
        fill = Color(0xFF3F5A32),
        accent = Color(0xFF9FCF6E),
    ),
    HomeApp(
        serviceId = "mapas",
        title = "Mapa",
        art = R.drawable.px_icon_map,
        fill = Color(0xFF8A5A22),
        accent = Color(0xFFF0BE6A),
    ),
    HomeApp(
        serviceId = "bazar",
        title = "Bazar",
        art = R.drawable.px_icon_stall,
        fill = Color(0xFF7E2C44),
        accent = Color(0xFFE585A0),
    ),
    HomeApp(
        serviceId = "wallet",
        title = "Cartera",
        art = R.drawable.px_icon_coins,
        fill = Color(0xFF2F4E5E),
        accent = Color(0xFF86BCCE),
    ),
    HomeApp(
        serviceId = "gba",
        title = "GBA",
        art = R.drawable.px_icon_gba,
        fill = Color(0xFF4A3B6B),
        accent = Color(0xFFB49BE8),
    ),
)

/** Celdas vacías al final del panal: el sitio ya está hecho para las próximas apps. */
private const val EMPTY_SLOTS = 2
private const val EMPTY_SLOT_LABEL = "Espacio libre para una futura app"

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenService: (Service) -> Unit,
    onSeeAllServices: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val servicesById = remember(viewModel.allActiveServices) {
        viewModel.allActiveServices.associateBy { it.id }
    }
    val mapService = servicesById["mapas"]
    val openMap: () -> Unit = {
        if (mapService != null) onOpenService(mapService) else onSeeAllServices()
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val mapHeight = screenHeight * 0.33f

    PixelBackdrop {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 46.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    HomeMapHero(
                        viewModel = viewModel,
                        expandedHeight = mapHeight,
                        onOpenMap = openMap,
                        onMessage = onMessage,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(start = 14.dp, end = 14.dp, top = 8.dp)
                            .fillMaxWidth(),
                    )
                }

                item {
                    HomeAppsHexGrid(
                        servicesById = servicesById,
                        onOpenService = onOpenService,
                        onSeeAll = onSeeAllServices,
                    )
                }

                item {
                    PixelBodyText(
                        text = "Nahua · barro, volcán y cal de Colima",
                        size = 14.sp,
                        color = PixelMuted,
                        align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            StatusBarScrim(modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

/** Velo sobre la barra de estado para que los iconos del sistema se lean al hacer scroll. */
@Composable
private fun StatusBarScrim(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    val scrimHeight = with(density) { (statusBarPx + 14.dp.roundToPx()).toDp() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(scrimHeight)
            .background(
                Brush.verticalGradient(
                    colors = listOf(PixelBg, PixelBg.copy(alpha = 0.92f), Color.Transparent),
                ),
            ),
    )
}

// --- Panal de aplicaciones --------------------------------------------------

@Composable
private fun HomeAppsHexGrid(
    servicesById: Map<String, Service>,
    onOpenService: (Service) -> Unit,
    onSeeAll: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelTitle(
                text = "Aplicaciones",
                size = 9.sp,
                modifier = Modifier.weight(1f),
            )
            PixelSurface(
                onClick = onSeeAll,
                fill = PixelPanel,
                border = PixelBorderSoft,
                cut = 4.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PixelLabel(text = "Ver todas", size = 6.sp, color = PixelInk)
                    Spacer(Modifier.width(4.dp))
                    PixelGlyph(PixelUiGlyphs.chevron, color = PixelInk, size = 8.dp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
        ) {
            val gap = 8.dp
            // Cuatro hexágonos por fila: seis apps repartidas 4 + 2 y los dos huecos al final.
            val hexWidth = (maxWidth - gap * 3) / 4
            val hexHeight = hexWidth * HEX_RATIO

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ServiceHexRow(
                    apps = homeApps.take(4),
                    hexWidth = hexWidth,
                    hexHeight = hexHeight,
                    gap = gap,
                    servicesById = servicesById,
                    onOpenService = onOpenService,
                )
                Spacer(Modifier.height(gap))
                ServiceHexRow(
                    apps = homeApps.drop(4),
                    hexWidth = hexWidth,
                    hexHeight = hexHeight,
                    gap = gap,
                    servicesById = servicesById,
                    onOpenService = onOpenService,
                    trailingSlots = EMPTY_SLOTS,
                )
            }
        }
    }
}

@Composable
private fun ServiceHexRow(
    apps: List<HomeApp>,
    hexWidth: Dp,
    hexHeight: Dp,
    gap: Dp,
    servicesById: Map<String, Service>,
    onOpenService: (Service) -> Unit,
    trailingSlots: Int = 0,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        apps.forEach { app ->
            val service = servicesById[app.serviceId] ?: return@forEach
            ServiceHex(
                app = app,
                hexWidth = hexWidth,
                hexHeight = hexHeight,
                onClick = { onOpenService(service) },
            )
        }
        repeat(trailingSlots) {
            EmptyHexSlot(hexWidth = hexWidth, hexHeight = hexHeight)
        }
    }
}

/**
 * Celda vacía del panal: la silueta ya está puesta y atenuada, con un «+» y «Pronto», para que la
 * próxima app sólo cambie el contenido y nadie tenga que reacomodar la rejilla.
 */
@Composable
private fun EmptyHexSlot(hexWidth: Dp, hexHeight: Dp) {
    val hexagon = remember { HexagonShape }
    Box(modifier = Modifier.size(hexWidth, hexHeight)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = 3.dp)
                .clip(hexagon)
                .background(PixelShadow.copy(alpha = 0.45f)),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(hexagon)
                .background(PixelPanel.copy(alpha = 0.45f))
                .border(2.dp, PixelBorderSoft.copy(alpha = 0.85f), hexagon)
                .semantics { contentDescription = EMPTY_SLOT_LABEL },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // En un hexágono de 84 dp la palabra no cabe legible: la cruz sola ya dice «aquí
            // cabe otra app», y el nombre del hueco va en la descripción accesible.
            PixelGlyph(rows = PlusGlyph, color = PixelMuted, size = hexWidth * 0.34f)
        }
    }
}

/** Cruz de 7×7 para la celda libre. */
private val PlusGlyph = listOf(
    ".......",
    "..XXX..",
    "..XXX..",
    "XXXXXXX",
    "XXXXXXX",
    "..XXX..",
    "..XXX..",
)

@Composable
private fun ServiceHex(
    app: HomeApp,
    hexWidth: Dp,
    hexHeight: Dp,
    onClick: () -> Unit,
) {
    val hexagon = remember { HexagonShape }
    Box(modifier = Modifier.size(hexWidth, hexHeight)) {
        // Sombra desplazada, el mismo truco del cartucho que usan las demás piezas.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = 3.dp)
                .clip(hexagon)
                .background(PixelShadow),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(hexagon)
                .background(app.fill)
                .border(2.dp, app.accent, hexagon)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (app.roundArt) {
                Box(
                    modifier = Modifier
                        .size(hexWidth * 0.46f)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(Clay, Color(0xFF4A2718))))
                        .border(2.dp, app.accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    PixelSpriteImage(app.art, Modifier.fillMaxSize())
                }
            } else {
                PixelSpriteImage(
                    res = app.art,
                    modifier = Modifier
                        .size(hexWidth * 0.46f)
                        .padding(2.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            PixelTitle(
                text = app.title,
                size = 6.4.sp,
                color = PixelInk,
                align = TextAlign.Center,
            )
        }
    }
}

/** Alto de un hexágono de punta arriba respecto de su ancho: 2/√3. */
private const val HEX_RATIO = 1.1547f

/**
 * Hexágono de punta arriba, el que permite apilar filas corridas media celda. Se corta el
 * contenido y se dibuja el borde con la misma silueta, así que el PNG del sprite no se sale.
 */
private object HexagonShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val width = size.width
        val height = size.height
        val path = Path().apply {
            moveTo(width * 0.5f, 0f)
            lineTo(width, height * 0.25f)
            lineTo(width, height * 0.75f)
            lineTo(width * 0.5f, height)
            lineTo(0f, height * 0.75f)
            lineTo(0f, height * 0.25f)
            close()
        }
        return Outline.Generic(path)
    }
}
