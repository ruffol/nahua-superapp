package com.nahuanotoca.superapp.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nahuanotoca.superapp.core.design.CoralSoft
import com.nahuanotoca.superapp.core.design.HeroGradientEnd
import com.nahuanotoca.superapp.core.design.Jade
import com.nahuanotoca.superapp.core.design.JadeSoft
import com.nahuanotoca.superapp.core.design.NahuaCard
import com.nahuanotoca.superapp.core.design.SectionHeader
import com.nahuanotoca.superapp.core.design.TransactionRow
import com.nahuanotoca.superapp.core.design.ServiceTile
import com.nahuanotoca.superapp.core.design.Sky
import com.nahuanotoca.superapp.core.design.SkySoft
import com.nahuanotoca.superapp.core.design.Violet
import com.nahuanotoca.superapp.core.design.VioletSoft
import com.nahuanotoca.superapp.core.model.Money
import com.nahuanotoca.superapp.core.model.Service
import com.nahuanotoca.superapp.core.model.UserProfile
import com.nahuanotoca.superapp.core.model.WalletTransaction

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
    var isRotaryMode by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            HomeHeader(
                profile = viewModel.profile,
                balanceCents = wallet.balanceCents,
                unreadCount = unread,
                onOpenInbox = onOpenInbox,
                onOpenSearch = onOpenSearch,
                onOpenWallet = onOpenWallet,
            )
        }

        // Selector moderno de aplicaciones: Aro de teléfono antiguo / Cuadrícula
        item {
            Column(modifier = Modifier.padding(top = 2.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionHeader(
                        title = "Aplicaciones",
                        actionLabel = "Ver todas",
                        onAction = onSeeAllServices,
                    )

                    Surface(
                        onClick = { isRotaryMode = !isRotaryMode },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (isRotaryMode) Icons.Rounded.Search else Icons.Rounded.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (isRotaryMode) "Cuadrícula" else "Aro Telefónico",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (isRotaryMode) {
                    RotaryAppDial(
                        services = viewModel.allActiveServices,
                        onOpenService = onOpenService,
                    )
                } else {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        ServiceGrid(
                            services = viewModel.featuredServices,
                            onOpenService = onOpenService,
                        )
                    }
                }
            }
        }

        // Mapa offline de Colima y ubicación actual en el menú principal
        item {
            HomeOfflineMapCard(
                viewModel = viewModel,
                onOpenFullMap = {
                    val mapService = viewModel.allActiveServices.firstOrNull { it.id == "mapas" }
                    if (mapService != null) onOpenService(mapService) else onSeeAllServices()
                },
                onMessage = onMessage,
            )
        }

        item { PromoCarousel(onMessage = onMessage) }

        item {
            QuickActions(
                onScan = onScan,
                onTopUp = { onMessage("Recarga disponible en Cartera") },
                onSend = { onMessage("Transferencias llegan en la próxima versión") },
            )
        }

        item {
            RecentActivity(
                transactions = wallet.transactions.take(3),
                onOpenWallet = onOpenWallet,
            )
        }
        item {
            Text(
                text = "Nahua · todo en una sola app",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HomeHeader(
    profile: UserProfile,
    balanceCents: Long,
    unreadCount: Int,
    onOpenInbox: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenWallet: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .background(Brush.linearGradient(listOf(Jade, HeroGradientEnd))),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 54.dp, y = (-58).dp)
                .size(190.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-40).dp, y = 40.dp)
                .size(140.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f)),
        )

        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hola,",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    Text(
                        text = profile.name.substringBefore(' '),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                    )
                }
                Box {
                    IconButton(onClick = onOpenInbox) {
                        Icon(
                            imageVector = Icons.Rounded.Notifications,
                            contentDescription = "Avisos",
                            tint = Color.White,
                        )
                    }
                    if (unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 8.dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF6B4A)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = unreadCount.coerceAtMost(9).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable(onClick = onOpenInbox),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = profile.avatarInitials,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Surface(
                onClick = onOpenSearch,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.14f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Busca servicios, pagos o personas",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Saldo Nahua",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                    Text(
                        text = Money.format(balanceCents),
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                    )
                }
                Surface(
                    onClick = onOpenWallet,
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.18f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Ver cartera",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

private data class Promo(
    val kicker: String,
    val title: String,
    val body: String,
    val accent: Color,
    val background: Color,
)

private val promos = listOf(
    Promo(
        kicker = "NUEVO",
        title = "Paga la luz en 10 segundos",
        body = "Escanea tu recibo y listo.",
        accent = Sky,
        background = SkySoft,
    ),
    Promo(
        kicker = "PROMO",
        title = "3x1 en café",
        body = "Paga con tu cartera Nahua y acumula puntos.",
        accent = Violet,
        background = VioletSoft,
    ),
    Promo(
        kicker = "ESTUDIO",
        title = "Tus PDFs, listos para exponer",
        body = "Nahuanotoka arma el resumen, las ideas y el guion de tu exposición.",
        accent = Jade,
        background = JadeSoft,
    ),
)

@Composable
private fun PromoCarousel(onMessage: (String) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { promos.size })

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 12.dp,
        ) { page ->
            val promo = promos[page]
            Surface(
                onClick = { onMessage(promo.title + ": promoción de demostración") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = promo.background,
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = promo.accent.copy(alpha = 0.16f),
                    ) {
                        Text(
                            text = promo.kicker,
                            style = MaterialTheme.typography.labelSmall,
                            color = promo.accent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = promo.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = promo.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(promos.size) { index ->
                val selected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = if (selected) 18.dp else 6.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) Jade else MaterialTheme.colorScheme.outline,
                        ),
                )
            }
        }
    }
}

@Composable
private fun ServiceGrid(
    services: List<Service>,
    onOpenService: (Service) -> Unit,
) {
    val rows = services.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { service ->
                    Box(modifier = Modifier.weight(1f)) {
                        ServiceTile(
                            service = service,
                            onClick = { onOpenService(service) },
                        )
                    }
                }
                repeat(4 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickActions(
    onScan: () -> Unit,
    onTopUp: () -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuickAction(
            icon = Icons.Rounded.QrCodeScanner,
            label = "Escanear",
            tint = Sky,
            background = SkySoft,
            onClick = onScan,
            modifier = Modifier.weight(1f),
        )
        QuickAction(
            icon = Icons.Rounded.ArrowDownward,
            label = "Recargar",
            tint = Jade,
            background = JadeSoft,
            onClick = onTopUp,
            modifier = Modifier.weight(1f),
        )
        QuickAction(
            icon = Icons.Rounded.ArrowUpward,
            label = "Enviar",
            tint = CoralSoft.let { Color(0xFFC2410C) },
            background = CoralSoft,
            onClick = onSend,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(background),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun RecentActivity(
    transactions: List<WalletTransaction>,
    onOpenWallet: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        SectionHeader(
            title = "Actividad reciente",
            actionLabel = "Ver todo",
            onAction = onOpenWallet,
        )
        Spacer(Modifier.height(6.dp))
        NahuaCard(contentPadding = PaddingValues(vertical = 4.dp)) {
            if (transactions.isEmpty()) {
                Text(
                    text = "Todavía no hay movimientos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                )
            } else {
                transactions.forEachIndexed { index, transaction ->
                    TransactionRow(transaction)
                    if (index != transactions.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}
