package com.nahuanotoca.superapp.feature.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nahuanotoca.superapp.R
import com.nahuanotoca.superapp.core.design.PixelBackdrop
import com.nahuanotoca.superapp.core.design.PixelBg
import com.nahuanotoca.superapp.core.design.PixelBody
import com.nahuanotoca.superapp.core.design.PixelBodyText
import com.nahuanotoca.superapp.core.design.PixelBorder
import com.nahuanotoca.superapp.core.design.PixelBorderSoft
import com.nahuanotoca.superapp.core.design.PixelButton
import com.nahuanotoca.superapp.core.design.PixelCream
import com.nahuanotoca.superapp.core.design.PixelCreamEdge
import com.nahuanotoca.superapp.core.design.PixelCreamInk
import com.nahuanotoca.superapp.core.design.PixelCutShape
import com.nahuanotoca.superapp.core.design.PixelFrame
import com.nahuanotoca.superapp.core.design.PixelGlyph
import com.nahuanotoca.superapp.core.design.PixelInk
import com.nahuanotoca.superapp.core.design.PixelLabel
import com.nahuanotoca.superapp.core.design.PixelMuted
import com.nahuanotoca.superapp.core.design.PixelPanel
import com.nahuanotoca.superapp.core.design.PixelPanelDeep
import com.nahuanotoca.superapp.core.design.PixelSpriteImage
import com.nahuanotoca.superapp.core.design.PixelSun
import com.nahuanotoca.superapp.core.design.PixelSurface
import com.nahuanotoca.superapp.core.design.PixelTitle
import com.nahuanotoca.superapp.core.design.PixelUiGlyphs
import com.nahuanotoca.superapp.core.design.PixelVioletDeep
import com.nahuanotoca.superapp.core.location.LocationFix
import com.nahuanotoca.superapp.core.model.Money
import com.nahuanotoca.superapp.core.model.Service

/**
 * Inicio en pixel art: mapa de Colima, saludo con el Tlalchichi, carrusel de aplicaciones,
 * tarjetas de resumen y tip del día. Todo dibujado con la paleta y las formas del estudio
 * para que combine con el resto de la app.
 */

private data class HomeApp(
    val serviceId: String,
    val title: String,
    val subtitle: String,
    @param:DrawableRes val art: Int,
    val fill: Color,
    val deep: Color,
    val accent: Color,
    val button: Color,
    val roundArt: Boolean = false,
)

private val homeApps = listOf(
    HomeApp(
        serviceId = "tamagochi",
        title = "Tlalchichi",
        subtitle = "Cuida y juega con tu Tlalchichi",
        art = R.drawable.px_dog,
        fill = Color(0xFF1C6E3D),
        deep = Color(0xFF14522C),
        accent = Color(0xFF57E58A),
        button = Color(0xFF23C46E),
        roundArt = true,
    ),
    HomeApp(
        serviceId = "studio",
        title = "RAG",
        subtitle = "Pregunta, estudia y explora",
        art = R.drawable.px_icon_rag,
        fill = Color(0xFF262C93),
        deep = Color(0xFF1A1F73),
        accent = Color(0xFF6B8AFF),
        button = Color(0xFF3F6DFB),
    ),
    HomeApp(
        serviceId = "mapas",
        title = "Mapa Offline",
        subtitle = "Explora sin conexión",
        art = R.drawable.px_icon_map,
        fill = Color(0xFF9A4D12),
        deep = Color(0xFF7A3B0D),
        accent = Color(0xFFF4A03C),
        button = Color(0xFFFE9D27),
    ),
    HomeApp(
        serviceId = "tianguis",
        title = "Bazar Trueque",
        subtitle = "Compra, vende o intercambia",
        art = R.drawable.px_icon_stall,
        fill = Color(0xFF94114A),
        deep = Color(0xFF7A0E3C),
        accent = Color(0xFFFF5C93),
        button = Color(0xFFFC3973),
    ),
    HomeApp(
        serviceId = "wallet",
        title = "Cartera Virtual",
        subtitle = "Tus pagos y beneficios",
        art = R.drawable.px_icon_coins,
        fill = Color(0xFF08626D),
        deep = Color(0xFF05505A),
        accent = Color(0xFF2FE0D8),
        button = Color(0xFF0FA2B8),
    ),
)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenService: (Service) -> Unit,
    onSeeAllServices: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenSearch: () -> Unit,
    onScan: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val wallet by viewModel.wallet.collectAsStateWithLifecycle()
    val unread by viewModel.unreadCount.collectAsStateWithLifecycle()
    val location by viewModel.currentLocation.collectAsStateWithLifecycle()

    val servicesById = remember(viewModel.allActiveServices) {
        viewModel.allActiveServices.associateBy { it.id }
    }
    val mapService = servicesById["mapas"]
    val openMap: () -> Unit = {
        if (mapService != null) onOpenService(mapService) else onSeeAllServices()
    }

    PixelBackdrop {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 54.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    HomeHero(
                        location = location,
                        onRecenter = {
                            viewModel.refreshLocation()
                            onMessage("Ubicación actualizada")
                        },
                        onOpenMap = openMap,
                    )
                }

                item {
                    HomeGreeting(
                        firstName = viewModel.profile.name.substringBefore(' '),
                        onOpenSearch = onOpenSearch,
                        onScan = onScan,
                    )
                }

                item {
                    HomeAppsRail(
                        servicesById = servicesById,
                        onOpenService = onOpenService,
                        onSeeAll = onSeeAllServices,
                    )
                }

                item {
                    HomeStats(
                        balanceCents = wallet.balanceCents,
                        unreadCount = unread,
                        nearbyCount = servicesById.size,
                        onOpenWallet = onOpenWallet,
                        onOpenInbox = onOpenInbox,
                        onOpenMap = openMap,
                    )
                }

                item { HomeTip(onExplore = onSeeAllServices) }

                item {
                    PixelBodyText(
                        text = "Nahua · todo en una sola app",
                        size = 15.sp,
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

// --- Encabezado con el mapa ------------------------------------------------

@Composable
private fun HomeHero(
    location: LocationFix?,
    onRecenter: () -> Unit,
    onOpenMap: () -> Unit,
) {
    Column(
        modifier = Modifier
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp),
    ) {
        PixelFrame(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp),
            fill = Color(0xFF2F8B4C),
            border = PixelBorder,
            borderWidth = 3.dp,
            cut = 10.dp,
            shadow = 4.dp,
        ) {
            Box(Modifier.fillMaxSize()) {
                PixelCityMap(Modifier.fillMaxSize())

                LocationChip(
                    location = location,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    HeroIconButton(PixelUiGlyphs.crosshair, "Centrar ubicación", onRecenter)
                    HeroIconButton(PixelUiGlyphs.expand, "Abrir mapa", onOpenMap)
                }

                PixelLocationPin(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = (-6).dp)
                        .size(88.dp),
                )
            }
        }
    }
}

@Composable
private fun LocationChip(location: LocationFix?, modifier: Modifier = Modifier) {
    val weather = if (location != null) "24°C · Despejado" else "Modo local · Colima"
    PixelSurface(
        modifier = modifier.width(184.dp),
        fill = PixelPanelDeep.copy(alpha = 0.95f),
        border = PixelBorder,
        borderWidth = 2.dp,
        cut = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PixelSpriteImage(R.drawable.px_pin, Modifier.size(22.dp))
                Spacer(Modifier.height(2.dp))
                PixelSpriteImage(R.drawable.px_sun, Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column {
                PixelBodyText("Tu ubicación", size = 14.sp, color = PixelMuted, maxLines = 1)
                PixelTitle("Colima, Col.", size = 8.5.sp, color = PixelInk)
                PixelBodyText(weather, size = 14.sp, color = PixelMuted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun HeroIconButton(glyph: List<String>, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(PixelCutShape(4.dp))
            .background(PixelPanelDeep.copy(alpha = 0.92f))
            .border(2.dp, PixelBorder, PixelCutShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PixelGlyph(glyph, color = PixelInk, size = 17.dp)
    }
}

// --- Saludo y búsqueda -----------------------------------------------------

@Composable
private fun HomeGreeting(
    firstName: String,
    onOpenSearch: () -> Unit,
    onScan: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(PixelVioletDeep)
                    .border(2.dp, PixelBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PixelSpriteImage(
                    res = R.drawable.px_dog,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = "Tlalchichi",
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                PixelSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp),
                    fill = PixelCream,
                    border = PixelCreamEdge,
                    borderWidth = 2.dp,
                    cut = 8.dp,
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                        PixelTitle("¡Hola, $firstName!", size = 9.5.sp, color = PixelCreamInk)
                        Spacer(Modifier.height(5.dp))
                        PixelBodyText(
                            text = "¿Qué te gustaría hacer hoy?",
                            size = 18.sp,
                            color = PixelCreamInk,
                        )
                    }
                }
                BubbleTail(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 16.dp)
                        .size(width = 9.dp, height = 18.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        HomeSearchBar(onOpenSearch = onOpenSearch, onScan = onScan)
    }
}

@Composable
private fun BubbleTail(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val tail = Path().apply {
            moveTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height * 0.68f)
            close()
        }
        drawPath(tail, PixelCream)
        drawPath(tail, PixelCreamEdge, style = Stroke(width = 1.5f * density))
    }
}

@Composable
private fun HomeSearchBar(onOpenSearch: () -> Unit, onScan: () -> Unit) {
    PixelSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        fill = PixelPanel,
        border = PixelBorderSoft,
        borderWidth = 2.dp,
        cut = 6.dp,
        onClick = onOpenSearch,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelGlyph(PixelUiGlyphs.search, color = PixelMuted, size = 16.dp)
            Spacer(Modifier.width(8.dp))
            PixelBodyText(
                text = "Busca servicios, pagos o personas",
                size = 17.sp,
                color = PixelMuted,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(PixelCutShape(4.dp))
                    .background(PixelVioletDeep)
                    .border(2.dp, PixelBorder, PixelCutShape(4.dp))
                    .clickable(onClick = onScan),
                contentAlignment = Alignment.Center,
            ) {
                PixelGlyph(PixelUiGlyphs.scan, color = PixelInk, size = 15.dp)
            }
        }
    }
}

// --- Carrusel de aplicaciones ----------------------------------------------

@Composable
private fun HomeAppsRail(
    servicesById: Map<String, Service>,
    onOpenService: (Service) -> Unit,
    onSeeAll: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
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

        Spacer(Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(homeApps, key = { it.serviceId }) { app ->
                val service = servicesById[app.serviceId] ?: return@items
                HomeAppCard(app = app, onClick = { onOpenService(service) })
            }
        }
    }
}

@Composable
private fun HomeAppCard(app: HomeApp, onClick: () -> Unit) {
    PixelFrame(
        modifier = Modifier
            .width(134.dp)
            .height(212.dp),
        fill = app.fill,
        border = app.accent,
        borderWidth = 2.dp,
        cut = 8.dp,
        shadow = 3.dp,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PixelTitle(
                text = app.title,
                size = 6.8.sp,
                color = PixelInk,
                align = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(PixelCutShape(4.dp))
                    .background(app.deep),
                contentAlignment = Alignment.Center,
            ) {
                if (app.roundArt) {
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF7B45E0), Color(0xFF3B1E72)),
                                ),
                            )
                            .border(2.dp, app.accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        PixelSpriteImage(app.art, Modifier.fillMaxSize())
                    }
                } else {
                    PixelSpriteImage(app.art, Modifier.fillMaxSize().padding(5.dp))
                }
            }

            Spacer(Modifier.height(6.dp))

            PixelBodyText(
                text = app.subtitle,
                size = 15.sp,
                color = PixelInk,
                maxLines = 3,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.weight(1f))

            PixelButton(
                text = "Abrir",
                onClick = onClick,
                color = app.button,
                textColor = Color.White,
                borderColor = PixelInk.copy(alpha = 0.5f),
                fontSize = 6.sp,
                expand = true,
                contentPadding = PaddingValues(vertical = 7.dp),
                trailing = {
                    PixelGlyph(PixelUiGlyphs.play, color = Color.White, size = 8.dp)
                },
            )
        }
    }
}

// --- Resumen del día -------------------------------------------------------

@Composable
private fun HomeStats(
    balanceCents: Long,
    unreadCount: Int,
    nearbyCount: Int,
    onOpenWallet: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenMap: () -> Unit,
) {
    val unreadLabel = when {
        unreadCount <= 0 -> "Al día"
        unreadCount == 1 -> "1 nueva"
        else -> "$unreadCount nuevas"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatPanel(
            art = R.drawable.px_icon_coins,
            title = "Saldo",
            value = Money.format(balanceCents),
            action = "Ver detalle",
            accent = PixelSun,
            onClick = onOpenWallet,
            modifier = Modifier.weight(1f),
        )
        StatPanel(
            art = R.drawable.px_icon_bell,
            title = "Avisos",
            value = unreadLabel,
            action = "Ver todas",
            accent = Color(0xFFFF9BC5),
            onClick = onOpenInbox,
            modifier = Modifier.weight(1f),
        )
        StatPanel(
            art = R.drawable.px_icon_store,
            title = "Servicios",
            value = "$nearbyCount cerca",
            action = "Ver mapa",
            accent = Color(0xFF7DE8F0),
            onClick = onOpenMap,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatPanel(
    @DrawableRes art: Int,
    title: String,
    value: String,
    action: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PixelFrame(
        modifier = modifier.height(92.dp),
        fill = PixelPanel,
        border = PixelBorder,
        borderWidth = 2.dp,
        cut = 8.dp,
        shadow = 3.dp,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelSpriteImage(art, Modifier.size(26.dp))
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    PixelLabel(text = title, size = 4.8.sp, color = PixelMuted)
                    Spacer(Modifier.height(3.dp))
                    PixelTitle(text = value, size = 5.8.sp, color = accent)
                }
            }
            Spacer(Modifier.weight(1f))
            PixelButton(
                text = action,
                onClick = onClick,
                color = PixelVioletDeep,
                borderColor = PixelBorder,
                fontSize = 4.8.sp,
                expand = true,
                contentPadding = PaddingValues(vertical = 6.dp),
            )
        }
    }
}

// --- Tip ------------------------------------------------------------------

@Composable
private fun HomeTip(onExplore: () -> Unit) {
    PixelFrame(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(104.dp),
        fill = Color(0xFF2A1460),
        border = PixelBorder,
        borderWidth = 2.dp,
        cut = 8.dp,
        shadow = 3.dp,
        onClick = onExplore,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelSpriteImage(R.drawable.px_icon_bulb, Modifier.size(38.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = PixelSun)) { append("Tip: ") }
                            append("Descubre el poder de tu Super App")
                        },
                        style = TextStyle(
                            fontFamily = PixelBody,
                            fontSize = 17.sp,
                            lineHeight = 18.sp,
                            color = PixelInk,
                        ),
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(2.dp))
                    PixelBodyText(
                        text = "Todo lo que necesitas, en un solo lugar.",
                        size = 14.sp,
                        color = PixelMuted,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            PixelButton(
                text = "Explorar",
                onClick = onExplore,
                modifier = Modifier.align(Alignment.End),
                color = PixelVioletDeep,
                borderColor = PixelBorder,
                fontSize = 5.4.sp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                leading = { PixelGlyph(PixelUiGlyphs.sparkle, color = PixelSun, size = 9.dp) },
            )
        }
    }
}
