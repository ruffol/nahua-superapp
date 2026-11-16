package com.nahuanotoca.superapp.feature.rag

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nahuanotoca.superapp.core.design.Amber
import com.nahuanotoca.superapp.core.design.AmberSoft
import com.nahuanotoca.superapp.core.design.Coral
import com.nahuanotoca.superapp.core.design.CoralSoft
import com.nahuanotoca.superapp.core.design.HeroGradientEnd
import com.nahuanotoca.superapp.core.design.Jade
import com.nahuanotoca.superapp.core.design.JadeSoft
import com.nahuanotoca.superapp.core.design.NahuaCard
import com.nahuanotoca.superapp.core.design.Sky
import com.nahuanotoca.superapp.core.design.SkySoft
import com.nahuanotoca.superapp.core.design.Teal
import com.nahuanotoca.superapp.core.design.TealSoft
import com.nahuanotoca.superapp.core.design.Violet
import com.nahuanotoca.superapp.core.design.VioletSoft
import com.nahuanotoca.superapp.core.model.ChatRole
import com.nahuanotoca.superapp.core.model.ChatTurn
import com.nahuanotoca.superapp.core.model.Mesa
import com.nahuanotoca.superapp.core.model.RagDocument
import com.nahuanotoca.superapp.core.model.RagFigure
import com.nahuanotoca.superapp.core.model.RagSettings
import com.nahuanotoca.superapp.core.model.StudioTask
import com.nahuanotoca.superapp.core.model.StudioTasks
import com.nahuanotoca.superapp.core.model.TokenUsage
import com.nahuanotoca.superapp.core.rag.ReportSharer
import com.nahuanotoca.superapp.core.util.formatRelativeTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Pantalla del estudio. Dos vistas en una: lista de mesas (materias) y detalle de una mesa con
 * documentos, herramientas de estudio, conversación, tokens y exportación a PDF.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    viewModel: StudioViewModel,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StudioEvent.Toast -> onMessage(event.text)
                is StudioEvent.SharePdf -> ReportSharer.share(context, File(event.path), event.title)
            }
        }
    }

    BackHandler(enabled = state.activeMesaId != null) { viewModel.closeMesa() }

    val activeMesa = state.activeMesa
    if (activeMesa == null) {
        MesasScreen(state = state, viewModel = viewModel, onBack = onBack)
    } else {
        MesaDetailScreen(
            state = state,
            mesa = activeMesa,
            viewModel = viewModel,
            onBack = viewModel::closeMesa,
        )
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
                onSave = viewModel::saveSettings,
                onDismiss = viewModel::closeSettings,
            )
        }
    }

    if (state.editorOpen) {
        MesaEditorDialog(
            mesa = state.editingMesa,
            onDismiss = viewModel::closeEditor,
            onSave = viewModel::saveMesa,
        )
    }
}

// ---------------------------------------------------------------------------
// Lista de mesas
// ---------------------------------------------------------------------------

@Composable
internal fun MesasScreen(
    state: StudioUiState,
    viewModel: StudioViewModel,
    onBack: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Mesa?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "Nahuanotoka",
            subtitle = "Mesas de trabajo · " + if (state.isConfigured) state.settings.model else "falta tu API key",
            onBack = onBack,
            onSettings = viewModel::openSettings,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ProblemHero() }

            if (!state.isConfigured) {
                item { ConfigurePrompt(onConfigure = viewModel::openSettings) }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Tus mesas",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Una mesa por materia o exposición",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = { viewModel.openEditor() },
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Nueva mesa", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            if (state.mesas.isEmpty() && !state.isLoading) {
                item {
                    NahuaCard(contentPadding = PaddingValues(18.dp)) {
                        Text(
                            text = "Crea tu primera mesa",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Por ejemplo: “Historia de México · exposición del bloque 3”. Ahí agregas los " +
                                "PDFs y todo lo que generes se queda en esa materia.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.openEditor() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("Crear mesa", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            items(state.mesas, key = { it.id }) { mesa ->
                MesaCard(
                    mesa = mesa,
                    documents = state.documentCounts[mesa.id] ?: 0,
                    onClick = { viewModel.openMesa(mesa.id) },
                    onDelete = { pendingDelete = mesa },
                    onEdit = { viewModel.openEditor(mesa) },
                )
            }

            item {
                Text(
                    text = "Tus PDFs y tus conversaciones se quedan en el teléfono. Sólo la pregunta y los " +
                        "fragmentos elegidos viajan a tu API.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                )
            }
        }
    }

    pendingDelete?.let { mesa ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("¿Eliminar " + mesa.name + "?") },
            text = {
                Text(
                    "Se borran sus PDFs indexados, la conversación y el contador de tokens. " +
                        "Esta acción no se puede deshacer.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMesa(mesa.id)
                        pendingDelete = null
                    },
                ) {
                    Text("Eliminar", color = Coral)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
internal fun ProblemHero() {
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
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "De 10 PDFs a una exposición",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Tienes los PDFs de la materia y una exposición en tres días. Aquí los conviertes en " +
                "resúmenes con página, ideas propias, el cruce entre documentos y un PDF listo para imprimir.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.88f),
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroStep("1", "Agrega tus PDFs", Modifier.weight(1f))
            HeroStep("2", "Pide el análisis", Modifier.weight(1f))
            HeroStep("3", "Exporta a PDF", Modifier.weight(1f))
        }
    }
}

@Composable
internal fun HeroStep(number: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun MesaCard(
    mesa: Mesa,
    documents: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    val accent = accentColor(mesa.accent)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.MenuBook, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = mesa.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (mesa.subject.isBlank()) "Sin materia" else mesa.subject,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = "Editar mesa",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatPill(documents.toString() + " PDF", accent)
                Spacer(Modifier.width(8.dp))
                StatPill(formatTokens(mesa.usage.totalTokens) + " tokens", accent)
                Spacer(Modifier.width(8.dp))
                StatPill(mesa.requests.toString() + " consultas", MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatRelativeTime(mesa.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (mesa.context.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = mesa.context,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun StatPill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
internal fun ConfigurePrompt(onConfigure: () -> Unit) {
    Surface(
        onClick = onConfigure,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Conecta DeepSeek Flash",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "El análisis lo genera el modelo de tu API (por defecto deepseek-v4.1-flash en " +
                        "OpenCode Go). Sin llave no hay respuestas.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
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

// ---------------------------------------------------------------------------
// Detalle de la mesa
// ---------------------------------------------------------------------------

@Composable
internal fun MesaDetailScreen(
    state: StudioUiState,
    mesa: Mesa,
    viewModel: StudioViewModel,
    onBack: () -> Unit,
) {
    val listState = rememberLazyListState()
    var question by rememberSaveable(mesa.id) { mutableStateOf("") }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.importPdfs(uris) }

    LaunchedEffect(state.turns.size, state.isWorking) {
        runCatching { listState.animateScrollToItem(listState.layoutInfo.totalItemsCount) }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = mesa.name,
            subtitle = buildString {
                append(if (mesa.subject.isBlank()) "Mesa de trabajo" else mesa.subject)
                append(" · ")
                append(formatTokens(mesa.usage.totalTokens))
                append(" tokens")
            },
            onBack = onBack,
            onSettings = viewModel::openSettings,
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TokenCard(mesa) }
            item { ContextCard(mesa = mesa, onEdit = { viewModel.openEditor(mesa) }) }

            if (!state.isConfigured) {
                item { ConfigurePrompt(onConfigure = viewModel::openSettings) }
            }
            item {
                DocumentsSection(
                    documents = state.documents,
                    selected = state.selectedDocumentIds,
                    importing = state.isImporting,
                    onAdd = { pdfPicker.launch(arrayOf("application/pdf")) },
                    onToggle = viewModel::toggleDocumentSelection,
                    onDelete = viewModel::deleteDocument,
                    onClearSelection = viewModel::clearSelection,
                )
            }
            if (state.figures.isNotEmpty()) {
                item { FiguresSection(state.figures) }
            }

            item {
                StudyTools(
                    enabled = state.documents.isNotEmpty() && state.isConfigured && !state.isWorking,
                    working = state.isWorking,
                    onRun = viewModel::runTask,
                )
            }

            if (state.turns.isEmpty() && state.pendingTurn == null && !state.isWorking) {
                item { ConversationIntro() }
            }

            items(state.turns, key = { it.id }) { turn -> TurnBlock(turn) }

            state.pendingTurn?.let { pending ->
                item { TurnBlock(pending) }
            }

            if (state.isWorking) {
                item { WorkingCard(state.workingLabel) }
            }

            state.error?.let { message ->
                item { ErrorCard(message = message, onDismiss = viewModel::dismissError) }
            }
        }

        Composer(
            question = question,
            onQuestionChange = { question = it },
            onSend = {
                viewModel.ask(question)
                question = ""
            },
            onExport = viewModel::exportPdf,
            onClear = viewModel::clearChat,
            enabled = state.documents.isNotEmpty() && state.isConfigured && !state.isWorking,
            working = state.isWorking,
            hasTurns = state.turns.isNotEmpty(),
            tokens = mesa.usage.totalTokens,
        )
    }
}

@Composable
internal fun TokenCard(mesa: Mesa) {
    NahuaCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Consumo de la mesa",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = mesa.requests.toString() + " consultas",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TokenStat("Entrada", mesa.usage.promptTokens, Violet, Modifier.weight(1f))
            TokenStat("Salida", mesa.usage.completionTokens, Jade, Modifier.weight(1f))
            TokenStat("Total", mesa.usage.totalTokens, Sky, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun TokenStat(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = formatTokens(value),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ContextCard(mesa: Mesa, onEdit: () -> Unit) {
    Surface(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VioletSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = null, tint = Violet, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Contexto de la mesa",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (mesa.context.isBlank()) {
                        "Define materia, tema de la exposición, público y tono. Mejora las respuestas."
                    } else {
                        mesa.context
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (mesa.context.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun DocumentsSection(
    documents: List<RagDocument>,
    selected: Set<String>,
    importing: Boolean,
    onAdd: () -> Unit,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClearSelection: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Documentos",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = when {
                        documents.isEmpty() -> "Aún no hay PDFs en esta mesa"
                        selected.isEmpty() -> documents.size.toString() + " PDF(s) · base completa"
                        else -> selected.size.toString() + " de " + documents.size + " seleccionados"
                    },
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

        if (documents.isNotEmpty() && selected.isNotEmpty()) {
            TextButton(onClick = onClearSelection, contentPadding = PaddingValues(0.dp)) {
                Text(
                    text = "Usar todos los documentos",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        documents.forEach { document ->
            val isSelected = document.id in selected
            Surface(
                onClick = { onToggle(document.id) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) JadeSoft else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) Jade else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                ),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Jade else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        } else {
                            Icon(
                                Icons.Rounded.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = document.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = buildString {
                                append(document.pages).append(" pág · ")
                                append(document.chunks).append(" fragmentos")
                                if (document.figures > 0) append(" · ").append(document.figures).append(" figuras")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onDelete(document.id) }) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Eliminar documento",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        Txt(
            "Toca un PDF para incluirlo o excluirlo. Si no seleccionas ninguno, la mesa usa todos.",
        )
    }
}

@Composable
internal fun FiguresSection(figures: List<RagFigure>) {
    var expanded by remember { mutableStateOf<RagFigure?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Figuras de tus PDFs",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Imágenes incrustadas en los documentos. Úsalas en tus diapositivas.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(figures, key = { it.path }) { figure ->
                Column(
                    modifier = Modifier
                        .width(118.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { expanded = figure },
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp),
                    ) {
                        FigureImage(path = figure.path, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "p. " + figure.page + " · " + figure.documentName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    expanded?.let { figure ->
        Dialog(onDismissRequest = { expanded = null }) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        text = figure.documentName + " · p. " + figure.page,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    FigureImage(
                        path = figure.path,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        crop = false,
                    )
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { expanded = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("Cerrar")
                    }
                }
            }
        }
    }
}

@Composable
internal fun FigureImage(path: String, modifier: Modifier = Modifier, crop: Boolean = true) {
    val image by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { decodeFigure(path, if (crop) 420 else 1200) }
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
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
    }
}

internal fun decodeFigure(path: String, maxSide: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}.getOrNull()