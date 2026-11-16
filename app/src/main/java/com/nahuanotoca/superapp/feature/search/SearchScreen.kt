package com.nahuanotoca.superapp.feature.search

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nahuanotoca.superapp.core.data.catalog.ServiceCatalog
import com.nahuanotoca.superapp.core.design.PixelAppGlyphs
import com.nahuanotoca.superapp.core.design.PixelBodyText
import com.nahuanotoca.superapp.core.design.PixelBorder
import com.nahuanotoca.superapp.core.design.PixelButton
import com.nahuanotoca.superapp.core.design.PixelCutShape
import com.nahuanotoca.superapp.core.design.PixelEmptyState
import com.nahuanotoca.superapp.core.design.PixelFrame
import com.nahuanotoca.superapp.core.design.PixelGlyph
import com.nahuanotoca.superapp.core.design.PixelInk
import com.nahuanotoca.superapp.core.design.PixelMuted
import com.nahuanotoca.superapp.core.design.PixelPanel
import com.nahuanotoca.superapp.core.design.PixelRow
import com.nahuanotoca.superapp.core.design.PixelScreen
import com.nahuanotoca.superapp.core.design.PixelTextField
import com.nahuanotoca.superapp.core.design.PixelUiGlyphs
import com.nahuanotoca.superapp.core.model.Service
import com.nahuanotoca.superapp.feature.services.ServicesViewModel
import kotlinx.coroutines.delay

/**
 * Buscador en pixel art: campo de texto con foco automático y resultados como filas del catálogo.
 */
@Composable
fun SearchScreen(
    viewModel: ServicesViewModel,
    onBack: () -> Unit,
    onOpenService: (Service) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val results = remember(state.query, state.category) {
        ServiceCatalog.search(state.query).filter {
            state.category == null || it.category == state.category
        }
    }

    LaunchedEffect(Unit) {
        delay(250)
        runCatching { focusRequester.requestFocus() }
    }

    PixelScreen(
        title = "Buscar",
        subtitle = results.size.toString() + " resultados",
        onBack = onBack,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PixelTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                placeholder = "Busca servicios, pagos o gente",
                focusRequester = focusRequester,
                leading = {
                    PixelGlyph(PixelUiGlyphs.search, color = PixelMuted, size = 15.dp)
                },
                trailing = {
                    if (state.query.isNotEmpty()) {
                        PixelButton(
                            text = "Limpiar",
                            onClick = { viewModel.setQuery("") },
                            color = PixelPanel,
                            borderColor = PixelBorder,
                            fontSize = 4.8.sp,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 5.dp),
                        )
                    }
                },
            )

            if (results.isEmpty()) {
                PixelEmptyState(
                    title = "Sin resultados",
                    message = "No encontramos \"" + state.query + "\". Prueba con \"luz\", \"mapa\" o \"tianguis\".",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(results, key = { it.id }) { service ->
                        PixelFrame(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(62.dp),
                            fill = PixelPanel,
                            border = PixelBorder,
                            borderWidth = 2.dp,
                            cut = 6.dp,
                            onClick = { onOpenService(service) },
                        ) {
                            PixelRow(
                                title = service.name,
                                subtitle = service.category.label + " · " + service.tagline,
                                glyph = PixelAppGlyphs.forService(service),
                                accent = service.accent,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
