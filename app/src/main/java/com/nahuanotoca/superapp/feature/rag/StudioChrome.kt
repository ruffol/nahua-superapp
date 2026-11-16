package com.nahuanotoca.superapp.feature.rag

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nahuanotoca.superapp.core.design.PixelBodyText
import com.nahuanotoca.superapp.core.design.PixelButton
import com.nahuanotoca.superapp.core.design.PixelChip
import com.nahuanotoca.superapp.core.design.PixelGlyph
import com.nahuanotoca.superapp.core.design.PixelIcons
import com.nahuanotoca.superapp.core.design.PixelIconButton
import com.nahuanotoca.superapp.core.design.PixelInk
import com.nahuanotoca.superapp.core.design.PixelLabel
import com.nahuanotoca.superapp.core.design.PixelMuted
import com.nahuanotoca.superapp.core.design.PixelPanel
import com.nahuanotoca.superapp.core.design.PixelPanelAlt
import com.nahuanotoca.superapp.core.design.PixelPink
import com.nahuanotoca.superapp.core.design.PixelPurple
import com.nahuanotoca.superapp.core.design.PixelRed
import com.nahuanotoca.superapp.core.design.PixelSprite
import com.nahuanotoca.superapp.core.design.PixelTextField
import com.nahuanotoca.superapp.core.design.PixelTitle
import com.nahuanotoca.superapp.core.design.PixelYellow
import com.nahuanotoca.superapp.core.design.pixelMascot
import com.nahuanotoca.superapp.core.design.pixelMascotPalette
import com.nahuanotoca.superapp.core.model.Mesa
import com.nahuanotoca.superapp.core.model.RagSettings
import com.nahuanotoca.superapp.core.model.StudioTasks
import java.util.Locale

/**
 * Piezas compartidas del estudio en estilo pixel art: barra superior con la mascota, diálogos de
 * mesa y de API, avisos de error y utilidades de formato.
 */

@Composable
internal fun StudioTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelIconButton(onClick = onBack, fill = PixelPanelAlt) {
            PixelGlyph(PixelIcons.back, size = 16.dp, color = PixelInk)
        }
        Spacer(Modifier.width(10.dp))
        PixelSprite(
            pattern = pixelMascot,
            palette = pixelMascotPalette,
            modifier = Modifier.size(34.dp),
            pixelSize = 2.2.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            PixelTitle(text = title, size = 9.sp, color = PixelInk)
            Spacer(Modifier.height(2.dp))
            PixelBodyText(text = subtitle, size = 15.sp, color = PixelMuted, maxLines = 1)
        }
        PixelIconButton(onClick = onSettings) {
            PixelGlyph(PixelIcons.gear, size = 18.dp, color = PixelYellow)
        }
    }
}

@Composable
internal fun MesaEditorDialog(
    mesa: Mesa?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var name by remember(mesa) { mutableStateOf(mesa?.name.orEmpty()) }
    var subject by remember(mesa) { mutableStateOf(mesa?.subject.orEmpty()) }
    var context by remember(mesa) { mutableStateOf(mesa?.context.orEmpty()) }

    Dialog(onDismissRequest = onDismiss) {
        PixelPanel(border = PixelPink, shadow = 5.dp) {
            PixelTitle(
                text = if (mesa == null) "NUEVA MESA" else "EDITAR MESA",
                size = 10.sp,
                color = PixelYellow,
            )
            Spacer(Modifier.height(4.dp))
            PixelBodyText(
                text = "Una mesa por materia o exposición.",
                size = 16.sp,
                color = PixelMuted,
            )
            Spacer(Modifier.height(12.dp))
            PixelLabel(text = "NOMBRE", size = 6.sp)
            Spacer(Modifier.height(4.dp))
            PixelTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Historia de México",
                maxLines = 1,
            )
            Spacer(Modifier.height(10.dp))
            PixelLabel(text = "MATERIA O TEMA", size = 6.sp)
            Spacer(Modifier.height(4.dp))
            PixelTextField(
                value = subject,
                onValueChange = { subject = it },
                placeholder = "Exposición del bloque 3",
                maxLines = 1,
            )
            Spacer(Modifier.height(10.dp))
            PixelLabel(text = "CONTEXTO PARA EL MODELO", size = 6.sp)
            Spacer(Modifier.height(4.dp))
            PixelTextField(
                value = context,
                onValueChange = { context = it },
                placeholder = "Exposición de 10 min, tono formal...",
                minLines = 3,
                maxLines = 4,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelButton(
                    text = "CANCELAR",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    expand = true,
                    color = PixelPanelAlt,
                )
                PixelButton(
                    text = if (mesa == null) "CREAR" else "GUARDAR",
                    onClick = { onSave(name, subject, context) },
                    modifier = Modifier.weight(1f),
                    enabled = name.trim().isNotEmpty(),
                )
            }
        }
    }
}

@Composable
internal fun SettingsDialog(
    settings: RagSettings,
    onSave: (String, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var baseUrl by remember(settings) { mutableStateOf(settings.baseUrl) }
    var model by remember(settings) { mutableStateOf(settings.model) }
    var apiKey by remember(settings) { mutableStateOf(settings.apiKey) }
    var showKey by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        PixelPanel(border = PixelYellow, shadow = 5.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelTitle(text = "CONECTA TU API", size = 10.sp, color = PixelYellow, modifier = Modifier.weight(1f))
                PixelIconButton(onClick = onDismiss, fill = PixelPanelAlt) {
                    PixelGlyph(PixelIcons.close, size = 14.dp, color = PixelMuted)
                }
            }
            Spacer(Modifier.height(4.dp))
            PixelBodyText(
                text = "El análisis lo genera el modelo de tu API. Sin llave no hay respuestas.",
                size = 16.sp,
                color = PixelMuted,
            )
            Spacer(Modifier.height(12.dp))
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                PixelLabel(text = "BASE URL", size = 6.sp)
                Spacer(Modifier.height(4.dp))
                PixelTextField(value = baseUrl, onValueChange = { baseUrl = it }, maxLines = 1)
                Spacer(Modifier.height(10.dp))
                PixelLabel(text = "MODELO", size = 6.sp)
                Spacer(Modifier.height(4.dp))
                PixelTextField(value = model, onValueChange = { model = it }, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                RagSettings.MODEL_PRESETS.forEach { preset ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        PixelChip(
                            text = preset + if (preset == RagSettings.DEFAULT_MODEL) " · OK" else "",
                            selected = model == preset,
                            onClick = { model = preset },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                PixelLabel(text = "API KEY", size = 6.sp)
                Spacer(Modifier.height(4.dp))
                PixelTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    placeholder = "sk-...",
                    maxLines = 1,
                    visualTransformation = if (showKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailing = {
                        PixelButton(
                            text = if (showKey) "OCULTAR" else "VER",
                            onClick = { showKey = !showKey },
                            color = PixelPanelAlt,
                            fontSize = 6.sp,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        )
                    },
                )
                Spacer(Modifier.height(6.dp))
                PixelBodyText(
                    text = "Se guarda en el almacenamiento privado del teléfono.",
                    size = 14.sp,
                    color = PixelMuted,
                )
            }
            Spacer(Modifier.height(14.dp))
            PixelButton(
                text = "GUARDAR",
                onClick = { onSave(baseUrl, model, apiKey) },
                expand = true,
                modifier = Modifier.fillMaxWidth(),
                color = PixelPurple,
            )
        }
    }
}

@Composable
internal fun StudioError(message: String, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    PixelPanel(border = PixelRed, fill = Color(0xFF3A1220), contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "!",
                style = TextStyle(
                    color = PixelRed,
                    fontSize = with(density) { 22.sp },
                ),
            )
            Spacer(Modifier.width(8.dp))
            PixelBodyText(text = message, size = 16.sp, color = Color(0xFFFFD5DC), modifier = Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            PixelButton(
                text = "OK",
                onClick = onDismiss,
                color = PixelRed,
                fontSize = 6.sp,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
            )
        }
    }
}

/** Copia el texto completo al portapapeles del sistema. */
internal fun copyToClipboard(context: Context, label: String, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText(label, text))
}

internal fun formatTokens(tokens: Int): String = when {
    tokens <= 0 -> "0"
    tokens < 1_000 -> tokens.toString()
    tokens < 1_000_000 -> String.format(Locale.US, "%.1fk", tokens / 1000.0)
    else -> String.format(Locale.US, "%.2fM", tokens / 1_000_000.0)
}

internal fun taskAccent(id: String): Color = when (id) {
    StudioTasks.SUMMARY -> com.nahuanotoca.superapp.core.design.PixelCyan
    StudioTasks.IDEAS -> PixelYellow
    StudioTasks.CORRELATE -> PixelPink
    StudioTasks.PRESENTATION -> com.nahuanotoca.superapp.core.design.PixelGreen
    StudioTasks.GLOSSARY -> PixelPurple
    else -> PixelRed
}