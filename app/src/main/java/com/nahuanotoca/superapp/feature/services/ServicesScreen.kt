package com.nahuanotoca.superapp.feature.services

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nahuanotoca.superapp.core.design.PixelAppGlyphs
import com.nahuanotoca.superapp.core.design.PixelBadge
import com.nahuanotoca.superapp.core.design.PixelBgDeep
import com.nahuanotoca.superapp.core.design.PixelBodyText
import com.nahuanotoca.superapp.core.design.PixelBorder
import com.nahuanotoca.superapp.core.design.PixelBorderSoft
import com.nahuanotoca.superapp.core.design.PixelButton
import com.nahuanotoca.superapp.core.design.PixelChip
import com.nahuanotoca.superapp.core.design.PixelCutShape
import com.nahuanotoca.superapp.core.design.PixelEmptyState
import com.nahuanotoca.superapp.core.design.PixelFrame
import com.nahuanotoca.superapp.core.design.PixelGlyph
import com.nahuanotoca.superapp.core.design.PixelIconButton
import com.nahuanotoca.superapp.core.design.PixelInk
import com.nahuanotoca.superapp.core.design.PixelLabel
import com.nahuanotoca.superapp.core.design.PixelMuted
import com.nahuanotoca.superapp.core.design.PixelPanel
import com.nahuanotoca.superapp.core.design.PixelPanelDeep
import com.nahuanotoca.superapp.core.design.PixelScreen
import com.nahuanotoca.superapp.core.design.PixelSectionHeader
import com.nahuanotoca.superapp.core.design.PixelSurface
import com.nahuanotoca.superapp.core.design.PixelTitle
import com.nahuanotoca.superapp.core.design.PixelUiGlyphs
import com.nahuanotoca.superapp.core.design.PixelViolet
import com.nahuanotoca.superapp.core.model.Service
import com.nahuanotoca.superapp.core.model.ServiceCategory

/**
 * Catálogo de servicios en pixel art: buscador, filtros por categoría y cuadrícula de accesos.
 */
@Composable
fun ServicesScreen(
    viewModel: ServicesViewModel,
    onOpenService: (Service) -> Unit,
    onOpenSearch: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val total = state.groups.sumOf { it.services.size }

    PixelScreen(
        title = "Servicios",
        subtitle = "Todo tu día, en una sola app",
        actions = {
            PixelIconButton(onClick = onOpenSearch, modifier = Modifier.size(38.dp)) {
                PixelGlyph(PixelUiGlyphs.search, color = PixelInk, size = 16.dp)
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                PixelSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(48.dp),
                    fill = PixelPanel,
                    border = PixelBorderSoft,
                    borderWidth = 2.dp,
                    cut = 5.dp,
                    onClick = onOpenSearch,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PixelGlyph(PixelUiGlyphs.search, color = PixelMuted, size = 15.dp)
                        Spacer(Modifier.width(8.dp))
                        PixelBodyText(
                            text = "Buscar en $total servicios",
                            size = 17.sp,
                            color = PixelMuted,
                            maxLines = 1,
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PixelChip(
                        text = "Todos",
                        selected = state.category == null,
                        onClick = { viewModel.setCategory(null) },
                    )
                    ServiceCategory.entries.forEach { category ->
                        PixelChip(
                            text = category.label,
                            selected = state.category == category,
                            onClick = {
                                viewModel.setCategory(if (state.category == category) null else category)
                            },
                        )
                    }
                }
            }

            if (state.isEmpty) {
                item {
                    PixelEmptyState(
                        title = "Nada por aquí",
                        message = "Prueba con otra categoría o revisa más tarde: seguimos sumando servicios.",
                    )
                }
            }

            state.groups.forEach { group ->
                item(key = "header_" + group.category.name) {
                    PixelSectionHeader(
                        title = group.category.label,
                        count = group.services.size,
                    )
                }
                item(key = "grid_" + group.category.name) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        group.services.chunked(4).forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                row.forEach { service ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        ServicePixelTile(
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
            }

            item {
                PixelFrame(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    fill = PixelPanelDeep,
                    border = PixelBorder,
                    borderWidth = 2.dp,
                    cut = 8.dp,
                    shadow = 3.dp,
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        PixelTitle(text = "¿Tienes un negocio?", size = 8.sp)
                        Spacer(Modifier.height(6.dp))
                        PixelBodyText(
                            text = "Publica tu mini app dentro de Nahua y llega a miles de usuarios con pagos integrados.",
                            size = 16.sp,
                            color = PixelMuted,
                            maxLines = 3,
                        )
                        Spacer(Modifier.height(10.dp))
                        PixelButton(
                            text = "Ver guía",
                            onClick = { onMessage("La guía vive en docs/MINIAPP_SDK.md del repositorio") },
                            color = PixelViolet,
                            fontSize = 5.6.sp,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            leading = {
                                PixelGlyph(PixelUiGlyphs.chevron, color = PixelInk, size = 8.dp)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServicePixelTile(
    service: Service,
    onClick: () -> Unit,
) {
    val accent = service.accent
    Column(
        modifier = Modifier
            .clip(PixelCutShape(5.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(PixelCutShape(4.dp))
                .background(accent.copy(alpha = 0.30f))
                .border(2.dp, accent.copy(alpha = 0.90f), PixelCutShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            PixelGlyph(
                rows = PixelAppGlyphs.forService(service),
                color = lerp(accent, Color.White, 0.68f),
                size = 24.dp,
            )
            if (service.badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(1.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(PixelCutShape(2.dp))
                            .background(accent)
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                    ) {
                        PixelLabel(text = service.badge, size = 4.4.sp, color = PixelBgDeep)
                    }
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        PixelBodyText(
            text = service.name,
            size = 14.sp,
            color = PixelInk,
            maxLines = 2,
            align = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
