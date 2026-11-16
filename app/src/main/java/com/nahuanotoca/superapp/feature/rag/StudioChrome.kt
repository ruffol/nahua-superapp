package com.nahuanotoca.superapp.feature.rag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nahuanotoca.superapp.core.design.Amber
import com.nahuanotoca.superapp.core.design.Coral
import com.nahuanotoca.superapp.core.design.CoralSoft
import com.nahuanotoca.superapp.core.design.Jade
import com.nahuanotoca.superapp.core.design.Sky
import com.nahuanotoca.superapp.core.design.Teal
import com.nahuanotoca.superapp.core.design.Violet
import com.nahuanotoca.superapp.core.model.Mesa
import com.nahuanotoca.superapp.core.model.RagSettings
import com.nahuanotoca.superapp.core.model.StudioTasks
import java.util.Locale

/**
 * Piezas compartidas del estudio: barra superior, diálogos, formato de tokens y el mapeo de
 * colores e iconos de cada herramienta.
 */

@Composable
internal fun MesaEditorDialog(
    mesa: Mesa?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var name by remember(mesa) { mutableStateOf(mesa?.name.orEmpty()) }
    var subject by remember(mesa) { mutableStateOf(mesa?.subject.orEmpty()) }
    var context by remember(mesa) { mutableStateOf(mesa?.context.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mesa == null) "Nueva mesa de trabajo" else "Editar mesa") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    placeholder = { Text("Historia de México") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Materia o tema") },
                    placeholder = { Text("Exposición del bloque 3") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = context,
                    onValueChange = { context = it },
                    label = { Text("Contexto para el modelo") },
                    placeholder = {
                        Text("Exposición de 10 min para compañeros, tono formal, enfocada en causas económicas.")
                    },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, subject, context) },
                enabled = name.trim().isNotEmpty(),
            ) {
                Text(if (mesa == null) "Crear" else "Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
internal fun SettingsContent(
    settings: RagSettings,
    onSave: (String, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var baseUrl by remember(settings) { mutableStateOf(settings.baseUrl) }
    var model by remember(settings) { mutableStateOf(settings.model) }
    var apiKey by remember(settings) { mutableStateOf(settings.apiKey) }
    var showKey by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .padding(horizontal = 22.dp)
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Conecta tu API",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "DeepSeek V4.1 Flash es el modelo por defecto: barato, rápido y con contexto " +
                "suficiente para una mesa entera. También funciona OpenAI, OpenRouter u Ollama.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Base URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("Modelo") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(8.dp))
        RagSettings.MODEL_PRESETS.forEach { preset ->
            Surface(
                onClick = { model = preset },
                shape = RoundedCornerShape(50),
                color = if (model == preset) Jade else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
            ) {
                Text(
                    text = preset + if (preset == RagSettings.DEFAULT_MODEL) "  · recomendado" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (model == preset) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (showKey) "Ocultar" else "Mostrar",
                    )
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "La llave se guarda en el almacenamiento privado de la app.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { onSave(baseUrl, model, apiKey) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Jade),
        ) {
            Text("Guardar configuración", style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancelar")
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
internal fun TopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Regresar")
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = "Configuración")
        }
    }
}

@Composable
internal fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = CoralSoft,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF8A2C12),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = Color(0xFF8A2C12), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
internal fun Txt(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal fun formatTokens(tokens: Int): String = when {
    tokens <= 0 -> "0"
    tokens < 1000 -> tokens.toString()
    tokens < 1_000_000 -> String.format(Locale.US, "%.1fk", tokens / 1000.0)
    else -> String.format(Locale.US, "%.2fM", tokens / 1_000_000.0)
}

internal fun accentColor(accent: Int): Color = when (accent % 6) {
    0 -> Jade
    1 -> Violet
    2 -> Sky
    3 -> Coral
    4 -> Amber
    else -> Teal
}

internal fun taskAccent(id: String): Color = when (id) {
    StudioTasks.SUMMARY -> Jade
    StudioTasks.IDEAS -> Amber
    StudioTasks.CORRELATE -> Violet
    StudioTasks.PRESENTATION -> Sky
    StudioTasks.GLOSSARY -> Teal
    else -> Coral
}

internal fun taskIcon(id: String): ImageVector = when (id) {
    StudioTasks.SUMMARY -> Icons.Rounded.MenuBook
    StudioTasks.IDEAS -> Icons.Rounded.Lightbulb
    StudioTasks.CORRELATE -> Icons.Rounded.CompareArrows
    StudioTasks.PRESENTATION -> Icons.Rounded.RecordVoiceOver
    StudioTasks.GLOSSARY -> Icons.Rounded.Bookmark
    else -> Icons.Rounded.Quiz
}