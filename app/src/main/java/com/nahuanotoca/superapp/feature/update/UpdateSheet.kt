package com.nahuanotoca.superapp.feature.update

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nahuanotoca.superapp.core.design.PixelAppGlyphs
import com.nahuanotoca.superapp.core.design.PixelBodyText
import com.nahuanotoca.superapp.core.design.PixelBorder
import com.nahuanotoca.superapp.core.design.PixelBorderSoft
import com.nahuanotoca.superapp.core.design.PixelButton
import com.nahuanotoca.superapp.core.design.PixelCutShape
import com.nahuanotoca.superapp.core.design.PixelGlyph
import com.nahuanotoca.superapp.core.design.PixelGreen
import com.nahuanotoca.superapp.core.design.PixelInk
import com.nahuanotoca.superapp.core.design.PixelMuted
import com.nahuanotoca.superapp.core.design.PixelPanel
import com.nahuanotoca.superapp.core.design.PixelPanelAlt
import com.nahuanotoca.superapp.core.design.PixelPanelDeep
import com.nahuanotoca.superapp.core.design.PixelRed
import com.nahuanotoca.superapp.core.design.PixelSun
import com.nahuanotoca.superapp.core.design.PixelTitle

/**
 * Panel de actualizaciones en pixel art: consulta GitHub, muestra las notas y descarga el APK.
 */
@Composable
fun UpdateSheet(
    viewModel: UpdateViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (state.release == null && !state.upToDate && !state.checking) viewModel.check()
    }

    Dialog(onDismissRequest = onDismiss) {
        PixelPanel(
            modifier = Modifier.fillMaxWidth(),
            fill = PixelPanelDeep,
            border = PixelBorder,
            borderWidth = 3.dp,
            cut = 8.dp,
            contentPadding = PaddingValues(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(PixelCutShape(3.dp))
                        .background(PixelGreen.copy(alpha = 0.18f))
                        .border(2.dp, PixelGreen.copy(alpha = 0.6f), PixelCutShape(3.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    PixelGlyph(PixelAppGlyphs.clock, color = PixelGreen, size = 18.dp)
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    PixelTitle(text = "Actualizaciones", size = 9.sp)
                    Spacer(Modifier.height(3.dp))
                    PixelBodyText(
                        text = "Versión instalada " + state.currentVersion,
                        size = 15.sp,
                        color = PixelMuted,
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                when {
                    state.checking -> {
                        PixelBodyText(
                            text = "Buscando la última versión en GitHub…",
                            size = 17.sp,
                            color = PixelMuted,
                        )
                    }

                    state.release != null -> {
                        val release = state.release!!
                        PixelTitle(text = "Nueva versión", size = 8.sp)
                        Spacer(Modifier.height(4.dp))
                        PixelBodyText(
                            text = "v" + release.versionName + if (release.sizeBytes > 0) {
                                " · " + (release.sizeBytes / (1024 * 1024)) + " MB"
                            } else {
                                ""
                            },
                            size = 16.sp,
                            color = PixelGreen,
                            maxLines = 1,
                        )
                        if (release.notes.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(PixelCutShape(4.dp))
                                    .background(PixelPanelAlt)
                                    .padding(10.dp),
                            ) {
                                PixelBodyText(
                                    text = release.notes,
                                    size = 15.sp,
                                    color = PixelInk,
                                )
                            }
                        }
                    }

                    state.upToDate -> {
                        PixelTitle(text = "Estás al día", size = 8.sp)
                        Spacer(Modifier.height(6.dp))
                        PixelBodyText(
                            text = "No hay versiones nuevas publicadas. Vuelve a revisar cuando quieras.",
                            size = 16.sp,
                            color = PixelMuted,
                        )
                    }

                    state.error != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(PixelCutShape(4.dp))
                                .background(PixelRed.copy(alpha = 0.18f))
                                .border(2.dp, PixelRed.copy(alpha = 0.6f), PixelCutShape(4.dp))
                                .padding(10.dp),
                        ) {
                            PixelBodyText(
                                text = state.error.orEmpty(),
                                size = 16.sp,
                                color = PixelInk,
                            )
                        }
                    }

                    else -> {
                        PixelBodyText(
                            text = "Busca la última versión publicada en GitHub y actualízala sin pasar un APK.",
                            size = 16.sp,
                            color = PixelMuted,
                        )
                    }
                }

                state.message?.let { message ->
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(PixelCutShape(4.dp))
                            .background(PixelGreen.copy(alpha = 0.14f))
                            .border(2.dp, PixelGreen.copy(alpha = 0.55f), PixelCutShape(4.dp))
                            .padding(10.dp),
                    ) {
                        PixelBodyText(text = message, size = 15.sp, color = PixelInk)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (state.downloading) {
                val progress = state.progress
                PixelProgressBar(progress = (progress ?: 0) / 100f)
                Spacer(Modifier.height(6.dp))
                PixelBodyText(
                    text = if (progress != null) "Descargando… $progress%" else "Descargando…",
                    size = 15.sp,
                    color = PixelMuted,
                    maxLines = 1,
                )
                Spacer(Modifier.height(10.dp))
            }

            when {
                state.release != null && !state.downloading -> {
                    PixelButton(
                        text = "Descargar e instalar",
                        onClick = viewModel::downloadAndInstall,
                        expand = true,
                        color = PixelGreen,
                        textColor = Color(0xFF04240F),
                        fontSize = 6.2.sp,
                        contentPadding = PaddingValues(vertical = 11.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    PixelBodyText(
                        text = "Se descarga desde GitHub Releases y se instala con el instalador de Android.",
                        size = 13.sp,
                        color = PixelMuted,
                        maxLines = 3,
                    )
                }

                state.message != null -> {
                    PixelButton(
                        text = "OK",
                        onClick = viewModel::dismissMessage,
                        expand = true,
                        color = PixelPanelAlt,
                        borderColor = PixelBorderSoft,
                        fontSize = 6.sp,
                        contentPadding = PaddingValues(vertical = 9.dp),
                    )
                }

                state.error != null -> {
                    PixelButton(
                        text = "Reintentar",
                        onClick = viewModel::check,
                        expand = true,
                        color = PixelPanelAlt,
                        borderColor = PixelBorderSoft,
                        fontSize = 6.sp,
                        contentPadding = PaddingValues(vertical = 9.dp),
                    )
                }

                !state.checking -> {
                    PixelButton(
                        text = if (state.upToDate) "Volver a buscar" else "Buscar actualizaciones",
                        onClick = viewModel::check,
                        expand = true,
                        color = PixelPanelAlt,
                        borderColor = PixelBorderSoft,
                        fontSize = 5.8.sp,
                        contentPadding = PaddingValues(vertical = 9.dp),
                    )
                }
            }

            if (state.message != null && state.message!!.contains("Instalar apps desconocidas")) {
                Spacer(Modifier.height(6.dp))
                PixelButton(
                    text = "Abrir ajustes de instalación",
                    onClick = viewModel::openInstallSettings,
                    expand = true,
                    color = PixelSun,
                    textColor = Color(0xFF3A2A00),
                    fontSize = 5.2.sp,
                    contentPadding = PaddingValues(vertical = 8.dp),
                )
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PixelCutShape(3.dp))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                PixelBodyText(text = "Cerrar", size = 16.sp, color = PixelMuted)
            }
        }
    }
}

@Composable
private fun PixelProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(PixelCutShape(3.dp))
            .background(PixelPanelAlt)
            .border(2.dp, PixelBorderSoft, PixelCutShape(3.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(10.dp)
                .align(Alignment.CenterStart)
                .padding(horizontal = 2.dp)
                .background(PixelGreen),
        )
    }
}
