package com.nahuanotoca.superapp.feature.rag

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nahuanotoca.superapp.core.design.PixelBodyText
import com.nahuanotoca.superapp.core.design.PixelButton
import com.nahuanotoca.superapp.core.design.PixelChip
import com.nahuanotoca.superapp.core.design.PixelCyan
import com.nahuanotoca.superapp.core.design.PixelGlyph
import com.nahuanotoca.superapp.core.design.PixelGreen
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
import com.nahuanotoca.superapp.core.design.PixelTitle
import com.nahuanotoca.superapp.core.design.PixelYellow
import com.nahuanotoca.superapp.core.design.pixelMascot
import com.nahuanotoca.superapp.core.design.pixelMascotPalette
import com.nahuanotoca.superapp.core.model.ChatRole
import com.nahuanotoca.superapp.core.model.Mesa
import com.nahuanotoca.superapp.core.model.RagDocument
import com.nahuanotoca.superapp.core.model.RagFigure
import com.nahuanotoca.superapp.core.rag.ExportFormat
import com.nahuanotoca.superapp.core.rag.ReportSharer
import com.nahuanotoca.superapp.core.rag.StudyExporter
import com.nahuanotoca.superapp.core.util.formatRelativeTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Estudio en estilo pixel art: lista de mesas y detalle con documentos, herramientas, chat
 * colapsable, dictado local y exportación de cada respuesta.
 */
@Composable
fun StudioScreen(
    viewModel: StudioViewModel,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val exporter = remember { StudyExporter(context) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StudioEvent.Toast -> onMessage(event.text)
                is StudioEvent.SharePdf -> ReportSharer.share(context, File(event.path), event.title)
                is StudioEvent.ShareFile -> exporter.share(File(event.path), event.format, event.title)
            }
        }
    }

    BackHandler(enabled = state.activeMesaId != null) { viewModel.closeMesa() }

    com.nahuanotoca.superapp.core.design.PixelBackdrop {
        val mesa = state.activeMesa
        if (mesa == null) {
            MesasScreen(state = state, viewModel = viewModel, onBack = onBack)
        } else {
            MesaDetailScreen(
                state = state,
                mesa = mesa,
                viewModel = viewModel,
                onBack = viewModel::closeMesa,
                onCopy = { text ->
                    copyToClipboard(context, "Nahuanotoka", text)
                    onMessage("Respuesta copiada")
                },
                onMessage = onMessage,
            )
        }

        if (state.settingsOpen) {
            SettingsDialog(
                settings = state.settings,
                onSave = viewModel::saveSettings,
                onDismiss = viewModel::closeSettings,
            )
        }

        if (state.editorOpen) {
            MesaEditorDialog(
                mesa = state.editingMesa,
                onDismiss = viewModel::closeEditor,
                onSave = viewModel::saveMesa,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Lista de mesas
// ---------------------------------------------------------------------------

@Composable
private fun MesasScreen(
    state: StudioUiState,
    viewModel: StudioViewModel,
    onBack: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Mesa?>(null) }

    Column(Modifier.fillMaxSize()) {
        StudioTopBar(
            title = "NAHUANOTOKA",
            subtitle = if (state.isConfigured) "estudio · " + state.settings.model else "estudio · falta tu API key",
            onBack = onBack,
            onSettings = viewModel::openSettings,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { HeroPanel() }

            if (!state.isConfigured) {
                item { ConfigurePrompt(onConfigure = viewModel::openSettings) }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelLabel(text = "TUS MESAS", size = 8.sp, color = PixelYellow)
                    Spacer(Modifier.weight(1f))
                    PixelButton(
                        text = "+ NUEVA",
                        onClick = { viewModel.openEditor() },
                        color = PixelPurple,
                        fontSize = 7.sp,
                    )
                }
            }

            if (state.mesas.isEmpty() && !state.isLoading) {
                item { EmptyMesas(onCreate = { viewModel.openEditor() }) }
            }

            items(state.mesas, key = { it.id }) { mesa ->
                MesaCard(
                    mesa = mesa,
                    documents = state.documentCounts[mesa.id] ?: 0,
                    onClick = { viewModel.openMesa(mesa.id) },
                    onEdit = { viewModel.openEditor(mesa) },
                    onDelete = { pendingDelete = mesa },
                )
            }

            item {
                PixelBodyText(
                    text = "Tus PDFs y tu historial viven en el teléfono. Solo la pregunta y los fragmentos " +
                        "elegidos viajan a tu API.",
                    size = 14.sp,
                    color = PixelMuted,
                )
            }
        }
    }

    pendingDelete?.let { mesa ->
        Dialog(onDismissRequest = { pendingDelete = null }) {
            PixelPanel(border = PixelRed, shadow = 4.dp) {
                PixelTitle(text = "¿BORRAR MESA?", size = 9.sp, color = PixelRed)
                Spacer(Modifier.height(6.dp))
                PixelBodyText(
                    text = "«" + mesa.name + "» se borra con sus PDFs indexados, su chat y su contador de tokens.",
                    size = 17.sp,
                    color = PixelInk,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButton(
                        text = "CANCELAR",
                        onClick = { pendingDelete = null },
                        modifier = Modifier.weight(1f),
                        expand = true,
                        color = PixelPanelAlt,
                        fontSize = 7.sp,
                    )
                    PixelButton(
                        text = "BORRAR",
                        onClick = {
                            viewModel.deleteMesa(mesa.id)
                            pendingDelete = null
                        },
                        modifier = Modifier.weight(1f),
                        color = PixelRed,
                        fontSize = 7.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroPanel() {
    PixelPanel(
        fill = com.nahuanotoca.superapp.core.design.PixelPanelSoft,
        border = PixelGreen,
        shadow = 4.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelSprite(
                pattern = pixelMascot,
                palette = pixelMascotPalette,
                modifier = Modifier.size(44.dp),
                pixelSize = 3.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                PixelTitle(text = "DE 10 PDFS A UNA EXPOSICIÓN", size = 9.sp, color = PixelYellow)
                Spacer(Modifier.height(4.dp))
                PixelBodyText(
                    text = "Resúmenes con página, ideas propias, el cruce entre documentos y un PDF listo " +
                        "para imprimir. Una mesa por materia.",
                    size = 16.sp,
                    color = PixelInk,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HeroStep("1", "AGREGA PDFs")
            HeroStep("2", "PIDE ANÁLISIS")
            HeroStep("3", "DESCARGA")
        }
    }
}

@Composable
private fun HeroStep(number: String, label: String) {
    Row(
        modifier = Modifier
            .background(PixelInk.copy(alpha = 0.10f))
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelLabel(text = number, size = 6.sp, color = PixelGreen)
        Spacer(Modifier.width(5.dp))
        PixelLabel(text = label, size = 6.sp, color = PixelInk)
    }
}

@Composable
private fun MesaCard(
    mesa: Mesa,
    documents: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    PixelPanel(
        border = PixelPurple,
        shadow = 3.dp,
        modifier = Modifier.clickable(onClick = onClick),
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(PixelPurple.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                PixelGlyph(PixelIcons.doc, size = 18.dp, color = PixelGreen)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                PixelTitle(text = mesa.name, size = 8.sp, color = PixelInk)
                PixelBodyText(
                    text = if (mesa.subject.isBlank()) "sin materia" else mesa.subject,
                    size = 15.sp,
                    color = PixelMuted,
                    maxLines = 1,
                )
            }
            PixelIconButton(onClick = onEdit, fill = PixelPanel, border = PixelPurple) {
                PixelGlyph(PixelIcons.gear, size = 14.dp, color = PixelCyan)
            }
            Spacer(Modifier.width(6.dp))
            PixelIconButton(onClick = onDelete, fill = PixelPanel, border = PixelRed) {
                PixelGlyph(PixelIcons.close, size = 13.dp, color = PixelRed)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelChip(text = documents.toString() + " PDF", selected = false, onClick = onClick, color = PixelCyan)
            PixelChip(
                text = formatTokens(mesa.usage.totalTokens) + " TOK",
                selected = false,
                onClick = onClick,
                color = PixelYellow,
            )
            if (mesa.cacheHits > 0) {
                PixelChip(
                    text = mesa.cacheHits.toString() + " CACHÉ",
                    selected = false,
                    onClick = onClick,
                    color = PixelGreen,
                )
            }
            PixelBodyText(text = formatRelativeTime(mesa.updatedAt), size = 14.sp, color = PixelMuted)
        }
        if (mesa.context.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            PixelBodyText(text = mesa.context, size = 15.sp, color = PixelMuted, maxLines = 2)
        }
    }
}

@Composable
private fun EmptyMesas(onCreate: () -> Unit) {
    PixelPanel(border = PixelYellow, shadow = 3.dp) {
        PixelTitle(text = "CREA TU PRIMERA MESA", size = 8.sp, color = PixelYellow)
        Spacer(Modifier.height(4.dp))
        PixelBodyText(
            text = "Por ejemplo: “Historia de México · exposición del bloque 3”. Ahí agregas los PDFs y " +
                "todo lo que generes se queda en esa materia.",
            size = 16.sp,
            color = PixelInk,
        )
        Spacer(Modifier.height(10.dp))
        PixelButton(text = "CREAR MESA", onClick = onCreate, color = PixelPurple, modifier = Modifier.fillMaxWidth(), expand = true)
    }
}

@Composable
private fun ConfigurePrompt(onConfigure: () -> Unit) {
    PixelPanel(border = PixelPink, shadow = 3.dp, modifier = Modifier.clickable(onClick = onConfigure)) {
        PixelTitle(text = "CONECTA DEEPSEEK FLASH", size = 8.sp, color = PixelPink)
        Spacer(Modifier.height(4.dp))
        PixelBodyText(
            text = "El análisis lo genera el modelo de tu API (por defecto deepseek-v4.1-flash en OpenCode " +
                "Go). Sin llave no hay respuestas.",
            size = 15.sp,
            color = PixelInk,
        )
        Spacer(Modifier.height(8.dp))
        PixelButton(text = "CONFIGURAR", onClick = onConfigure, color = PixelPurple, fontSize = 7.sp)
    }
}

// ---------------------------------------------------------------------------
// Detalle de la mesa
// ---------------------------------------------------------------------------

@Composable
private fun MesaDetailScreen(
    state: StudioUiState,
    mesa: Mesa,
    viewModel: StudioViewModel,
    onBack: () -> Unit,
    onCopy: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    var question by rememberSaveable(mesa.id) { mutableStateOf("") }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.importPdfs(uris) }

    val documentTypes = arrayOf("application/pdf", "application/epub+zip")

    LaunchedEffect(state.turns.size, state.isWorking) {
        runCatching { listState.animateScrollToItem(listState.layoutInfo.totalItemsCount) }
    }

    Column(Modifier.fillMaxSize()) {
        StudioTopBar(
            title = mesa.name,
            subtitle = (if (mesa.subject.isBlank()) "mesa de trabajo" else mesa.subject) +
                " · " + formatTokens(mesa.usage.totalTokens) + " tok",
            onBack = onBack,
            onSettings = viewModel::openSettings,
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { StatsPanel(mesa = mesa, cacheHits = state.cacheStats.entries, onClearCache = viewModel::clearCache) }
            item { ContextPanel(mesa = mesa, onEdit = { viewModel.openEditor(mesa) }) }

            if (!state.isConfigured) {
                item { ConfigurePrompt(onConfigure = viewModel::openSettings) }
            }

            item {
                DocumentsPanel(
                    documents = state.documents,
                    selected = state.selectedDocumentIds,
                    importing = state.isImporting,
                    onAdd = { pdfPicker.launch(documentTypes) },
                    onToggle = viewModel::toggleDocumentSelection,
                    onDelete = viewModel::deleteDocument,
                    onClearSelection = viewModel::clearSelection,
                    onVectorize = if (state.settings.embeddingsConfigured) viewModel::vectorizeMesa else null,
                )
            }

            state.importProgress?.let { progress ->
                item { ImportProgressPanel(progress) }
            }

            if (state.figures.isNotEmpty()) {
                item { FiguresPanel(state.figures) }
            }

            item {
                StudioTools(
                    enabled = state.documents.isNotEmpty() && state.isConfigured && !state.isWorking,
                    onRun = viewModel::runTask,
                )
            }

            if (state.turns.isEmpty() && state.pendingTurn == null && !state.isWorking) {
                item { ChatHint() }
            }

            val lastAssistantId = state.turns.lastOrNull { it.role == ChatRole.ASSISTANT }?.id
            items(state.turns, key = { it.id }) { turn ->
                ChatTurnCard(
                    turn = turn,
                    onCopy = { onCopy(turn.text) },
                    onExport = { format -> viewModel.exportTurn(turn.id, format) },
                    onRegenerate = if (turn.id == lastAssistantId) viewModel::regenerateLast else null,
                )
            }

            state.pendingTurn?.let { pending ->
                item { ChatTurnCard(turn = pending, onCopy = {}, onExport = {}) }
            }

            if (state.streamingText.isNotBlank()) {
                item { StreamingCard(state.streamingText) }
            }

            if (state.isWorking) {
                item { WorkingPanel(state.workingLabel, viewModel::cancelGeneration) }
            }

            state.error?.let { message ->
                item { StudioError(message = message, onDismiss = viewModel::dismissError) }
            }
        }

        StudioComposer(
            question = question,
            onQuestionChange = { question = it },
            onSend = {
                viewModel.ask(question)
                question = ""
            },
            onAttach = { pdfPicker.launch(documentTypes) },
            onExport = viewModel::exportPdf,
            onConfigure = viewModel::openSettings,
            onRunTask = viewModel::runTask,
            enabled = state.documents.isNotEmpty() && state.isConfigured && !state.isWorking,
            working = state.isWorking,
            tokens = mesa.usage.totalTokens,
            cacheHits = mesa.cacheHits,
            onMessage = onMessage,
        )
    }
}

@Composable
private fun StatsPanel(mesa: Mesa, cacheHits: Int, onClearCache: () -> Unit) {
    PixelPanel(cut = 4.dp, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelLabel(text = "CONSUMO DE LA MESA", size = 7.sp, color = PixelCyan)
            Spacer(Modifier.weight(1f))
            PixelBodyText(text = mesa.requests.toString() + " consultas", size = 14.sp, color = PixelMuted)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatBox("ENTRADA", mesa.usage.promptTokens, PixelPurple, Modifier.weight(1f))
            StatBox("SALIDA", mesa.usage.completionTokens, PixelGreen, Modifier.weight(1f))
            StatBox("TOTAL", mesa.usage.totalTokens, PixelCyan, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelChip(
                text = "CACHÉ " + mesa.cacheHits,
                selected = mesa.cacheHits > 0,
                onClick = {},
                color = PixelGreen,
            )
            Spacer(Modifier.width(6.dp))
            PixelBodyText(
                text = formatTokens(mesa.cachedTokensSaved) + " tok ahorrados · " + cacheHits + " respuestas",
                size = 14.sp,
                color = PixelMuted,
                modifier = Modifier.weight(1f),
            )
            if (cacheHits > 0) {
                PixelButton(
                    text = "VACIAR",
                    onClick = onClearCache,
                    color = PixelPanelAlt,
                    fontSize = 6.sp,
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        PixelTitle(text = formatTokens(value), size = 9.sp, color = color)
        PixelLabel(text = label, size = 5.sp, color = PixelMuted)
    }
}

@Composable
private fun ContextPanel(mesa: Mesa, onEdit: () -> Unit) {
    PixelPanel(
        fill = PixelPanelAlt,
        border = PixelCyan,
        cut = 4.dp,
        modifier = Modifier.clickable(onClick = onEdit),
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelLabel(text = "CONTEXTO DE LA MESA", size = 6.sp, color = PixelCyan)
            Spacer(Modifier.weight(1f))
            PixelLabel(text = "EDITAR", size = 6.sp, color = PixelMuted)
        }
        Spacer(Modifier.height(4.dp))
        PixelBodyText(
            text = if (mesa.context.isBlank()) {
                "Define materia, tema de la exposición, público y tono. Mejora las respuestas."
            } else {
                mesa.context
            },
            size = 16.sp,
            color = if (mesa.context.isBlank()) PixelMuted else PixelInk,
            maxLines = 3,
        )
    }
}

@Composable
private fun DocumentsPanel(
    documents: List<RagDocument>,
    selected: Set<String>,
    importing: Boolean,
    onAdd: () -> Unit,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClearSelection: () -> Unit,
    onVectorize: (() -> Unit)? = null,
) {
    PixelPanel(cut = 4.dp, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                PixelLabel(text = "DOCUMENTOS", size = 7.sp, color = PixelPink)
                PixelBodyText(
                    text = when {
                        documents.isEmpty() -> "aún no hay PDFs en esta mesa"
                        selected.isEmpty() -> documents.size.toString() + " PDF · base completa"
                        else -> selected.size.toString() + " de " + documents.size + " seleccionados"
                    },
                    size = 14.sp,
                    color = PixelMuted,
                )
            }
            if (onVectorize != null) {
                PixelButton(
                    text = "VECTORES",
                    onClick = onVectorize,
                    enabled = !importing,
                    color = PixelPurple,
                    fontSize = 6.sp,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            PixelButton(
                text = if (importing) "INDEXANDO…" else "+ PDF",
                onClick = onAdd,
                enabled = !importing,
                color = PixelGreen,
                textColor = androidx.compose.ui.graphics.Color(0xFF0C2A1B),
                fontSize = 7.sp,
            )
        }

        if (documents.isNotEmpty() && selected.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            PixelButton(
                text = "USAR TODOS",
                onClick = onClearSelection,
                color = PixelPanelAlt,
                fontSize = 6.sp,
            )
        }

        documents.forEach { document ->
            val isSelected = document.id in selected
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isSelected) PixelGreen.copy(alpha = 0.18f) else PixelPanel)
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(if (isSelected) PixelGreen else PixelPanelAlt)
                        .clickable { onToggle(document.id) },
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f).clickable { onToggle(document.id) }) {
                    PixelBodyText(text = document.name, size = 16.sp, color = PixelInk, maxLines = 1)
                    PixelBodyText(
                        text = buildString {
                            append(document.pages).append(" pág · ")
                            append(document.chunks).append(" frag")
                            if (document.figures > 0) append(" · ").append(document.figures).append(" fig")
                        },
                        size = 13.sp,
                        color = PixelMuted,
                    )
                }
                PixelIconButton(onClick = { onDelete(document.id) }, fill = PixelPanel, border = PixelRed) {
                    PixelGlyph(PixelIcons.close, size = 11.dp, color = PixelRed)
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        PixelBodyText(
            text = "Toca un PDF para incluirlo o excluirlo de la correlación.",
            size = 13.sp,
            color = PixelMuted,
        )
    }
}

@Composable
private fun FiguresPanel(figures: List<RagFigure>) {
    var expanded by remember { mutableStateOf<RagFigure?>(null) }

    PixelPanel(cut = 4.dp, contentPadding = PaddingValues(12.dp)) {
        PixelLabel(text = "FIGURAS DE TUS PDFS", size = 7.sp, color = PixelYellow)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(figures, key = { it.path }) { figure ->
                Column(
                    modifier = Modifier
                        .width(96.dp)
                        .clickable { expanded = figure },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(86.dp)
                            .background(PixelPanelAlt),
                    ) {
                        FigureImage(path = figure.path, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(3.dp))
                    PixelBodyText(
                        text = "p. " + figure.page + " · " + figure.documentName,
                        size = 12.sp,
                        color = PixelMuted,
                        maxLines = 1,
                    )
                }
            }
        }
    }

    expanded?.let { figure ->
        Dialog(onDismissRequest = { expanded = null }) {
            PixelPanel(border = PixelYellow, shadow = 4.dp) {
                PixelTitle(text = "P. " + figure.page, size = 8.sp, color = PixelYellow)
                Spacer(Modifier.height(8.dp))
                FigureImage(
                    path = figure.path,
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    crop = false,
                )
                Spacer(Modifier.height(10.dp))
                PixelButton(
                    text = "CERRAR",
                    onClick = { expanded = null },
                    expand = true,
                    color = PixelPanelAlt,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ImportProgressPanel(progress: ImportProgress) {
    PixelPanel(border = PixelCyan, cut = 4.dp, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelLabel(text = "INDEXANDO", size = 7.sp, color = PixelCyan)
            Spacer(Modifier.weight(1f))
            PixelBodyText(
                text = progress.book.toString() + " de " + progress.books + " libros · " +
                    progress.pages + " páginas",
                size = 14.sp,
                color = PixelMuted,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(PixelPanelAlt),
        ) {
            val fraction = (progress.book.toFloat() / progress.books.coerceAtLeast(1)).coerceIn(0.05f, 1f)
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(10.dp)
                    .background(PixelGreen),
            )
        }
        Spacer(Modifier.height(6.dp))
        PixelBodyText(
            text = progress.name + if (progress.stage.isBlank()) "" else " · " + progress.stage,
            size = 14.sp,
            color = PixelMuted,
            maxLines = 1,
        )
    }
}

@Composable
private fun ChatHint() {
    PixelPanel(fill = PixelPanelAlt, border = PixelPurple, cut = 4.dp) {
        PixelTitle(text = "PREGUNTA CON TUS PALABRAS", size = 8.sp, color = PixelGreen)
        Spacer(Modifier.height(4.dp))
        PixelBodyText(
            text = "“resumen”, “explícame el tema 3” o “compara las dos lecturas”. Las respuestas citan " +
                "documento y página, y puedes copiarlas o bajarlas en PDF, MD o TXT.",
            size = 16.sp,
            color = PixelMuted,
        )
    }
}

// ---------------------------------------------------------------------------
// Figuras
// ---------------------------------------------------------------------------

@Composable
private fun FigureImage(path: String, modifier: Modifier = Modifier, crop: Boolean = true) {
    val image by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { decodeFigure(path, if (crop) 380 else 1100) }
    }
    val bitmap = image
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            PixelBodyText(text = "…", size = 16.sp, color = PixelMuted)
        }
    }
}

private fun decodeFigure(path: String, maxSide: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}.getOrNull()