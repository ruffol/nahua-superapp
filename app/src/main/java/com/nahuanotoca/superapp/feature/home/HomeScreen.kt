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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nahuanotoca.superapp.R
import com.nahuanotoca.superapp.core.design.Bougainvillea
import com.nahuanotoca.superapp.core.design.Clay
import com.nahuanotoca.superapp.core.design.Forest
import com.nahuanotoca.superapp.core.design.Maize
import com.nahuanotoca.superapp.core.design.Ochre
import com.nahuanotoca.superapp.core.design.PixelBackdrop
import com.nahuanotoca.superapp.core.design.PixelBg
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
import com.nahuanotoca.superapp.core.design.VolcanicAsh
import com.nahuanotoca.superapp.core.model.Money
import com.nahuanotoca.superapp.core.model.Service

/**
 * Inicio en pixel art de barro: el mapa offline de Colima ocupa un tercio de la pantalla, saludo
 * con el Tlalchichi, carrusel de aplicaciones y el resumen del día. Sin buscador ni tip: la app es
 * pequeña y el inicio se queda en lo esencial.
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
        fill = Color(0xFF7A3D27),
        deep = Color(0xFF5C2D1C),
        accent = Color(0xFFE0A05A),
        button = Color(0xFFC97B3F),
        roundArt = true,
    ),
    HomeApp(
        serviceId = "studio",
        title = "RAG",
        subtitle = "Pregunta, estudia y explora",
        art = R.drawable.px_icon_rag,
        fill = Color(0xFF3F5A32),
        deep = Color(0xFF2E4325),
        accent = Color(0xFF9FCF6E),
        button = Color(0xFF6E9B4C),
    ),
    HomeApp(
        serviceId = "mapas",
        title = "Mapa Offline",
        subtitle = "Explora sin conexión",
        art = R.drawable.px_icon_map,
        fill = Color(0xFF8A5A22),
        deep = Color(0xFF6B4519),
        accent = Color(0xFFF0BE6A),
        button = Color(0xFFD79A45),
    ),
    HomeApp(
        serviceId = "tianguis",
        title = "Bazar Trueque",
        subtitle = "Compra, vende o intercambia",
        art = R.drawable.px_icon_stall,
        fill = Color(0xFF7E2C44),
        deep = Color(0xFF5F1F32),
        accent = Color(0xFFE585A0),
        button = Color(0xFFC94F74),
    ),
    HomeApp(
        serviceId = "wallet",
        title = "Cartera Virtual",
        subtitle = "Tus pagos y beneficios",
        art = R.drawable.px_icon_coins,
        fill = Color(0xFF2F4E5E),
        deep = Color(0xFF223A47),
        accent = Color(0xFF86BCCE),
        button = Color(0xFF4E7FA8),
    ),
)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenService: (Service) -> Unit,
    onSeeAllServices: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenInbox: () -> Unit,
    onScan: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val wallet by viewModel.wallet.collectAsStateWithLifecycle()
    val unread by viewModel.unreadCount.collectAsStateWithLifecycle()

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
                        onOpenMap = openMap,
                        onMessage = onMessage,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(start = 14.dp, end = 14.dp, top = 8.dp)
                            .fillMaxWidth()
                            .height(mapHeight),
                    )
                }

                item {
                    HomeGreeting(
                        firstName = viewModel.profile.name.substringBefore(' '),
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

// --- Saludo ----------------------------------------------------------------

@Composable
private fun HomeGreeting(
    firstName: String,
    onScan: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(74.dp)
                .clip(CircleShape)
                .background(Clay)
                .border(2.dp, Ochre, CircleShape),
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
                Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) {
                    PixelTitle("¡Hola, ${firstName}!", size = 9.sp, color = PixelCreamInk)
                    Spacer(Modifier.height(4.dp))
                    PixelBodyText(
                        text = "¿Qué te gustaría hacer hoy?",
                        size = 17.sp,
                        color = PixelCreamInk,
                    )
                }
            }
            BubbleTail(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 14.dp)
                    .size(width = 9.dp, height = 18.dp),
            )
        }

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .size(46.dp)
                .clip(PixelCutShape(5.dp))
                .background(PixelPanelDeep)
                .border(2.dp, PixelBorder, PixelCutShape(5.dp))
                .clickable(onClick = onScan),
            contentAlignment = Alignment.Center,
        ) {
            PixelGlyph(PixelUiGlyphs.scan, color = Maize, size = 18.dp)
        }
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

        Spacer(Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 6.dp),
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
            .height(210.dp),
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
                    .height(82.dp)
                    .clip(PixelCutShape(4.dp))
                    .background(app.deep),
                contentAlignment = Alignment.Center,
            ) {
                if (app.roundArt) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Clay, Color(0xFF4A2718)),
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
                textColor = Color(0xFF2A1508),
                borderColor = PixelInk.copy(alpha = 0.45f),
                fontSize = 6.sp,
                expand = true,
                contentPadding = PaddingValues(vertical = 7.dp),
                trailing = {
                    PixelGlyph(PixelUiGlyphs.play, color = Color(0xFF2A1508), size = 8.dp)
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
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatPanel(
            art = R.drawable.px_icon_coins,
            title = "Saldo",
            value = Money.format(balanceCents),
            action = "Ver detalle",
            accent = Maize,
            onClick = onOpenWallet,
            modifier = Modifier.weight(1f),
        )
        StatPanel(
            art = R.drawable.px_icon_bell,
            title = "Avisos",
            value = unreadLabel,
            action = "Ver todas",
            accent = Bougainvillea,
            onClick = onOpenInbox,
            modifier = Modifier.weight(1f),
        )
        StatPanel(
            art = R.drawable.px_icon_store,
            title = "Servicios",
            value = "$nearbyCount cerca",
            action = "Ver mapa",
            accent = Ochre,
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
