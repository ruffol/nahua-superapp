package com.nahuanotoca.superapp.feature.rag

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nahuanotoca.superapp.core.design.CoralSoft
import com.nahuanotoca.superapp.core.design.HeroGradientEnd
import com.nahuanotoca.superapp.core.design.Jade
import com.nahuanotoca.superapp.core.design.JadeSoft
import com.nahuanotoca.superapp.core.design.NahuaCard
import com.nahuanotoca.superapp.core.design.Violet
import com.nahuanotoca.superapp.core.model.RagAnswer
import com.nahuanotoca.superapp.core.model.RagDocument
import com.nahuanotoca.superapp.core.model.RagSettings
import com.nahuanotoca.superapp.core.util.formatRelativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RagScreen(
    viewModel: RagViewModel,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var question by rememberSaveable { mutableStateOf("") }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importPdf(uri)
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message -> onMessage(message) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        RagTopBar(
            onBack = onBack,
            onSettings = viewModel::openSettings,
            configured = state.settings.isConfigured,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { RagIntro() }

            if (!state.settings.isConfigured) {
                item { ConfigurePrompt(onConfigure = viewModel::openSettings) }
            }

            item {
                DocumentsHeader(
                    count = state.documents.size,
                    importing = state.isImporting,
                    onAdd = {
                        if (!state.isImporting) pdfPicker.launch(arrayOf("application/pdf"))
                    },
                )
            }

            if (state.documents.isEmpty() && !state.isLoadingDocuments) {
                item { EmptyDocuments(onAdd = { pdfPicker.launch(arrayOf("application/pdf")) }) }
            } else {
                items(state.documents, key = { it.id }) { document ->
                    DocumentRow(document = document, onDelete = { viewModel.deleteDocument(document.id) })
                }
            }

            item {
                AskCard(
                    question = question,
                    onQuestionChange = { question = it },
                    asking = state.isAsking,
                    enabled = state.documents.isNotEmpty() && state.settings.isConfigured,
                    onAsk = { viewModel.ask(question) },
                )
            }

            state.answer?.let { answer ->
                item { AnswerCard(answer = answer) }
            }

            state.error?.let { message ->
                item { ErrorCard(message = message, onDismiss = viewModel::dismissError) }
            }

            item { RagFootnote() }
        }
    }

    if (state.settingsOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::closeSettings,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            SettingsContent(
                settings = state.settings,
                onSave = { baseUrl, model, apiKey -> viewModel.saveSettings(baseUrl, model, apiKey) },
                onDismiss = viewModel::closeSettings,
            )
        }
    }
}

@Composable
private fun RagTopBar(onBack: () -> Unit, onSettings: () -> Unit, configured: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Regresar")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Nahua IA",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (configured) "RAG local · OpenCode Go" else "RAG local · configura tu API",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = "Configuración")
        }
    }
}

@Composable
private fun RagIntro() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Jade, HeroGradientEnd)))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Pregunta a tus PDFs",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "El PDF se procesa en tu teléfono. Solo los fragmentos más relevantes viajan a tu API para redactar la respuesta.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun ConfigurePrompt(onConfigure: () -> Unit) {
    Surface(
        onClick = onConfigure,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Falta tu API key",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "Usa OpenCode Go por defecto o cualquier endpoint compatible con OpenAI.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
            Text(
                text = "Configurar",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DocumentsHeader(count: Int, importing: Boolean, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Documentos",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (count == 0) "Aún no hay PDFs" else count.toString() + " PDF(s) indexados",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = onAdd,
            enabled = !importing,
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        ) {
            if (importing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text("Indexando", style = MaterialTheme.typography.labelLarge)
            } else {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Agregar PDF", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun EmptyDocuments(onAdd: () -> Unit) {
    NahuaCard(contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(JadeSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = Jade, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Importa tu primer PDF",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Contratos, apuntes, manuales… con texto seleccionable.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text("Elegir archivo", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun DocumentRow(document: RagDocument, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(JadeSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = Jade, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = document.pages.toString() + " pág · " + document.chunks + " fragmentos · " +
                        formatRelativeTime(document.addedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Eliminar documento",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AskCard(
    question: String,
    onQuestionChange: (String) -> Unit,
    asking: Boolean,
    enabled: Boolean,
    onAsk: () -> Unit,
) {
    NahuaCard(contentPadding = PaddingValues(16.dp)) {
        Text(
            text = "Haz una pregunta",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = question,
            onValueChange = onQuestionChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("¿Cuáles son las obligaciones del contrato?") },
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(16.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("¿De qué trata?", "Dame un resumen", "¿Qué fechas importan?")
                .take(2)
                .forEach { suggestion ->
                    Surface(
                        onClick = { onQuestionChange(suggestion) },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onAsk,
            enabled = enabled && !asking && question.trim().length >= 3,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            if (asking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text("Pensando…", style = MaterialTheme.typography.labelLarge)
            } else {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Preguntar", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (!enabled) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (question.length >= 3) "Necesitas configurar la API e importar un PDF." else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AnswerCard(answer: RagAnswer) {
    NahuaCard(contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Respuesta",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = answer.model,
                style = MaterialTheme.typography.labelSmall,
                color = Violet,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = answer.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (answer.sources.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Fuentes usadas",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            answer.sources.distinctBy { it.documentName + it.page }.forEach { source ->
                Text(
                    text = "• " + source.documentName + " · p. " + source.page,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Generado en " + (answer.elapsedMillis / 1000.0).toString().take(4) + " s",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = CoralSoft,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
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
private fun RagFootnote() {
    Text(
        text = "Los documentos se guardan solo en este dispositivo. La API recibe la pregunta y hasta 4 fragmentos.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    )
}

@Composable
private fun SettingsContent(
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
            .imePadding(),
    ) {
        Text(
            text = "Conecta tu API",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Por defecto usa OpenCode Go. También funciona con OpenAI, OpenRouter u Ollama.",
            style = MaterialTheme.typography.bodyMedium,
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
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RagSettings.MODEL_PRESETS.take(2).forEach { preset ->
                FilterChip(
                    selected = model == preset,
                    onClick = { model = preset },
                    label = { Text(preset, style = MaterialTheme.typography.labelSmall) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Jade,
                        selectedLabelColor = Color.White,
                    ),
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
            text = "Se guarda en el almacenamiento privado de la app. Para producción usa Keystore.",
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