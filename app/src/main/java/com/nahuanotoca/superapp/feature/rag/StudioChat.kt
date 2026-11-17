package com.nahuanotoca.superapp.feature.rag

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.nahuanotoca.superapp.core.design.PixelBackdrop
import com.nahuanotoca.superapp.core.design.PixelBodyText
import com.nahuanotoca.superapp.core.design.PixelButton
import com.nahuanotoca.superapp.core.design.PixelChip
import com.nahuanotoca.superapp.core.design.PixelCyan
import com.nahuanotoca.superapp.core.design.PixelGlyph
import com.nahuanotoca.superapp.core.design.PixelGreen
import com.nahuanotoca.superapp.core.design.PixelGreenDeep
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
import com.nahuanotoca.superapp.core.design.pixelUser
import com.nahuanotoca.superapp.core.design.pixelUserPalette
import com.nahuanotoca.superapp.core.model.ChatRole
import com.nahuanotoca.superapp.core.model.ChatTurn
import com.nahuanotoca.superapp.core.model.StudioTask
import com.nahuanotoca.superapp.core.model.StudioTasks
import com.nahuanotoca.superapp.core.rag.ExportFormat

private const val PREVIEW_CHARS = 430

/** Primer trozo de una respuesta larga, cortado en límite de palabra. */
internal fun previewOf(text: String): String {
    if (text.length <= PREVIEW_CHARS) return text
    val cut = text.take(PREVIEW_CHARS)
    val boundary = cut.lastIndexOfAny(charArrayOf(' ', '\n', '.', ','))
    return (if (boundary > PREVIEW_CHARS / 2) cut.take(boundary) else cut).trimEnd() + "…"
}

@Composable
internal fun ChatTurnCard(
    turn: ChatTurn,
    onCopy: () -> Unit,
    onExport: (ExportFormat) -> Unit,
    onRegenerate: (() -> Unit)? = null,
) {
    if (turn.role == ChatRole.USER) {
        UserBubble(turn)
        return
    }
    AssistantCard(turn = turn, onCopy = onCopy, onExport = onExport, onRegenerate = onRegenerate)
}

@Composable
private fun UserBubble(turn: ChatTurn) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .padding(end = 4.dp),
        ) {
            PixelPanel(
                fill = PixelGreen,
                border = PixelGreenDeep,
                shadow = 3.dp,
                cut = 5.dp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
            ) {
                PixelBodyText(text = turn.text, size = 19.sp, color = Color(0xFF0C2A1B))
            }
        }
        PixelSprite(
            pattern = pixelUser,
            palette = pixelUserPalette,
            modifier = Modifier.size(28.dp),
            pixelSize = 1.8.dp,
        )
    }
}

@Composable
private fun AssistantCard(
    turn: ChatTurn,
    onCopy: () -> Unit,
    onExport: (ExportFormat) -> Unit,
    onRegenerate: (() -> Unit)? = null,
) {
    val task = StudioTasks.find(turn.taskId)
    val trimmed = previewOf(turn.text)
    val truncated = trimmed.length < turn.text.length
    var readerOpen by rememberSaveable(turn.id) { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.Top) {
        PixelSprite(
            pattern = pixelMascot,
            palette = pixelMascotPalette,
            modifier = Modifier
                .padding(top = 6.dp)
                .size(30.dp),
            pixelSize = 2.dp,
        )
        Spacer(Modifier.width(8.dp))
        PixelPanel(
            fill = PixelPanelAlt,
            border = if (turn.cacheHit) PixelGreen else PixelPurple,
            shadow = 3.dp,
            cut = 6.dp,
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (task != null) {
                    Row(
                        modifier = Modifier
                            .background(taskAccent(task.id).copy(alpha = 0.22f))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PixelLabel(text = task.label.uppercase(), size = 6.sp, color = taskAccent(task.id))
                    }
                    Spacer(Modifier.width(6.dp))
                }
                PixelBodyText(
                    text = turn.model.ifBlank { "modelo" },
                    size = 14.sp,
                    color = PixelMuted,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (turn.cacheHit) {
                    PixelLabel(text = "CACHÉ", size = 6.sp, color = PixelGreen)
                } else if (turn.usage.totalTokens > 0) {
                    PixelLabel(text = formatTokens(turn.usage.totalTokens) + " TOK", size = 6.sp, color = PixelYellow)
                }
                if (turn.weak) {
                    Spacer(Modifier.width(6.dp))
                    PixelLabel(text = "EVIDENCIA DÉBIL", size = 5.sp, color = PixelYellow)
                }
            }
            Spacer(Modifier.height(8.dp))
            MarkdownLite(text = trimmed, color = PixelInk)
            if (truncated) {
                Spacer(Modifier.height(6.dp))
                PixelBodyText(
                    text = turn.text.length.toString() + " caracteres · desplázate dentro del recuadro",
                    size = 14.sp,
                    color = PixelMuted,
                )
                Spacer(Modifier.height(8.dp))
                PixelButton(
                    text = "VER TODO",
                    onClick = { readerOpen = true },
                    color = PixelPurple,
                    fontSize = 7.sp,
                    leading = { PixelGlyph(PixelIcons.chevronUp, size = 12.dp, color = PixelInk) },
                )
            }

            if (turn.sources.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(PixelPurple.copy(alpha = 0.5f)))
                Spacer(Modifier.height(8.dp))
                PixelLabel(text = "FUENTES", size = 6.sp, color = PixelGreen)
                Spacer(Modifier.height(4.dp))
                turn.sources.distinctBy { it.documentName to it.page }.take(4).forEach { source ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(5.dp).background(PixelGreen))
                        Spacer(Modifier.width(6.dp))
                        PixelBodyText(
                            text = source.documentName + " · p. " + source.page,
                            size = 15.sp,
                            color = PixelMuted,
                            maxLines = 1,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelButton(
                    text = "COPIAR",
                    onClick = onCopy,
                    color = PixelPanel,
                    fontSize = 6.sp,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    leading = { PixelGlyph(PixelIcons.copy, size = 11.dp, color = PixelGreen) },
                )
                PixelButton(
                    text = "PDF",
                    onClick = { onExport(ExportFormat.PDF) },
                    color = PixelPanel,
                    fontSize = 6.sp,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    leading = { PixelGlyph(PixelIcons.download, size = 11.dp, color = PixelPink) },
                )
                PixelButton(
                    text = "MD",
                    onClick = { onExport(ExportFormat.MARKDOWN) },
                    color = PixelPanel,
                    fontSize = 6.sp,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                )
                PixelButton(
                    text = "TXT",
                    onClick = { onExport(ExportFormat.TEXT) },
                    color = PixelPanel,
                    fontSize = 6.sp,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                )
                if (onRegenerate != null) {
                    PixelButton(
                        text = "REGENERAR",
                        onClick = onRegenerate,
                        color = PixelPanelAlt,
                        fontSize = 6.sp,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }

    if (readerOpen) {
        ReaderDialog(
            title = task?.label ?: "Respuesta",
            text = turn.text,
            model = turn.model,
            tokens = turn.usage.totalTokens,
            sources = turn.sources.map { it.documentName + " · p. " + it.page },
            onCopy = onCopy,
            onExport = onExport,
            onDismiss = { readerOpen = false },
        )
    }
}

@Composable
private fun ReaderDialog(
    title: String,
    text: String,
    model: String,
    tokens: Int,
    sources: List<String>,
    onCopy: () -> Unit,
    onExport: (ExportFormat) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        PixelBackdrop {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelTitle(text = title.uppercase(), size = 9.sp, color = PixelYellow, modifier = Modifier.weight(1f))
                    PixelIconButton(onClick = onDismiss, fill = PixelPanelAlt) {
                        PixelGlyph(PixelIcons.close, size = 14.dp, color = PixelMuted)
                    }
                }
                Spacer(Modifier.height(8.dp))
                PixelPanel(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(14.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        MarkdownLite(text = text, color = PixelInk)
                        if (sources.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            PixelLabel(text = "FUENTES", size = 6.sp, color = PixelGreen)
                            Spacer(Modifier.height(4.dp))
                            sources.distinct().forEach { source ->
                                PixelBodyText(text = "· " + source, size = 15.sp, color = PixelMuted)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PixelButton(
                            text = "COPIAR",
                            onClick = onCopy,
                            modifier = Modifier.weight(1f),
                            expand = true,
                            color = PixelPanelAlt,
                            fontSize = 6.sp,
                            contentPadding = PaddingValues(vertical = 8.dp),
                        )
                        PixelButton(
                            text = "PDF",
                            onClick = { onExport(ExportFormat.PDF) },
                            modifier = Modifier.weight(1f),
                            color = PixelPurple,
                            fontSize = 6.sp,
                            contentPadding = PaddingValues(vertical = 8.dp),
                        )
                        PixelButton(
                            text = "MD",
                            onClick = { onExport(ExportFormat.MARKDOWN) },
                            modifier = Modifier.weight(1f),
                            color = PixelPanel,
                            fontSize = 6.sp,
                            contentPadding = PaddingValues(vertical = 8.dp),
                        )
                        PixelButton(
                            text = "TXT",
                            onClick = { onExport(ExportFormat.TEXT) },
                            modifier = Modifier.weight(1f),
                            color = PixelPanel,
                            fontSize = 6.sp,
                            contentPadding = PaddingValues(vertical = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    PixelBodyText(
                        text = model + if (tokens > 0) " · " + formatTokens(tokens) + " tokens" else "",
                        size = 14.sp,
                        color = PixelMuted,
                    )
                }
            }
        }
    }
}

/** Markdown mínimo con tipografía pixel: encabezados, viñetas y tablas legibles. */
@Composable
internal fun MarkdownLite(text: String, color: Color) {
    val lines = remember(text) { text.replace("\r\n", "\n").split("\n") }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Spacer(Modifier.height(4.dp))
                line.startsWith("|") -> PixelBodyText(
                    text = line.trim('|').replace("|", " · ").trim(),
                    size = 16.sp,
                    color = PixelMuted,
                )
                line.startsWith("#") -> PixelTitle(
                    text = stripMarks(line.trimStart('#').trim()).uppercase(),
                    size = 8.sp,
                    color = PixelYellow,
                )
                line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ") -> Row {
                    Box(
                        Modifier
                            .padding(top = 7.dp)
                            .size(6.dp)
                            .background(PixelPurple),
                    )
                    Spacer(Modifier.width(7.dp))
                    PixelBodyText(text = stripMarks(line.substring(1).trim()), size = 19.sp, color = color)
                }
                else -> PixelBodyText(text = stripMarks(line), size = 19.sp, color = color)
            }
        }
    }
}

internal fun strategyLabel(strategy: String): String = when (strategy) {
    "busqueda" -> "BÚSQUEDA"
    "cobertura" -> "COBERTURA"
    "balanceado" -> "BALANCE"
    "dos-pasadas" -> "2 PASADAS"
    else -> ""
}

private fun stripMarks(text: String): String = text
    .replace("**", "")
    .replace("__", "")
    .replace("\u0060", "")

/** Respuesta que va llegando en vivo (streaming), con el cursor al final. */
@Composable
internal fun StreamingCard(text: String) {
    val visible = if (text.length > 1_600) "…" + text.takeLast(1_600) else text
    Row(verticalAlignment = Alignment.Top) {
        PixelSprite(
            pattern = pixelMascot,
            palette = pixelMascotPalette,
            modifier = Modifier
                .padding(top = 6.dp)
                .size(30.dp),
            pixelSize = 2.dp,
        )
        Spacer(Modifier.width(8.dp))
        PixelPanel(
            fill = PixelPanelAlt,
            border = PixelGreen,
            shadow = 3.dp,
            cut = 6.dp,
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelLabel(text = "GENERANDO", size = 6.sp, color = PixelGreen)
                Spacer(Modifier.width(6.dp))
                PixelBodyText(text = "recibiendo texto…", size = 13.sp, color = PixelMuted)
            }
            Spacer(Modifier.height(6.dp))
            MarkdownLite(text = visible, color = PixelInk)
            Spacer(Modifier.height(4.dp))
            PixelBodyText(text = "▌", size = 18.sp, color = PixelGreen)
        }
    }
}

@Composable
internal fun WorkingPanel(label: String, onCancel: (() -> Unit)? = null) {
    val transition = rememberInfiniteTransition(label = "dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    val dots = (phase.toInt() % 3) + 1

    Row(verticalAlignment = Alignment.CenterVertically) {
        PixelSprite(
            pattern = pixelMascot,
            palette = pixelMascotPalette,
            modifier = Modifier.size(30.dp),
            pixelSize = 2.dp,
        )
        Spacer(Modifier.width(8.dp))
        PixelPanel(
            modifier = Modifier.weight(1f),
            fill = PixelPanelAlt,
            border = PixelPurple,
            contentPadding = PaddingValues(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelLabel(
                    text = (label.ifBlank { "Trabajando" }).uppercase() + ".".repeat(dots),
                    size = 7.sp,
                    color = PixelYellow,
                    modifier = Modifier.weight(1f),
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = PixelGreen,
                )
            }
            Spacer(Modifier.height(4.dp))
            PixelBodyText(
                text = "Buscando en tus PDFs y redactando con tu API.",
                size = 15.sp,
                color = PixelMuted,
            )
            if (onCancel != null) {
                Spacer(Modifier.height(8.dp))
                PixelButton(
                    text = "DETENER",
                    onClick = onCancel,
                    color = PixelPanel,
                    fontSize = 6.sp,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudioTools(enabled: Boolean, onRun: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelLabel(text = "HERRAMIENTAS", size = 7.sp, color = PixelYellow)
            Spacer(Modifier.width(8.dp))
            PixelBodyText(
                text = "mandan toda la mesa, no palabras clave",
                size = 14.sp,
                color = PixelMuted,
                modifier = Modifier.weight(1f),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StudioTasks.all.forEach { task ->
                TaskTile(task = task, enabled = enabled, onClick = { onRun(task.id) })
            }
        }
    }
}

@Composable
private fun TaskTile(task: StudioTask, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .background(if (enabled) taskAccent(task.id).copy(alpha = 0.18f) else PixelPanel)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(if (enabled) taskAccent(task.id) else PixelMuted),
        )
        Spacer(Modifier.width(7.dp))
        Column {
            PixelLabel(
                text = task.label.uppercase(),
                size = 6.sp,
                color = if (enabled) PixelInk else PixelMuted,
            )
            PixelBodyText(text = task.hint, size = 13.sp, color = PixelMuted, maxLines = 1)
        }
    }
}

@Composable
internal fun StudioComposer(
    question: String,
    onQuestionChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onExport: () -> Unit,
    onConfigure: () -> Unit,
    onRunTask: (String) -> Unit,
    enabled: Boolean,
    working: Boolean,
    tokens: Int,
    cacheHits: Int,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val dictation = rememberVoiceDictation()
    val currentQuestion by rememberUpdatedState(question)

    val appendSpoken: (String) -> Unit = { spoken ->
        val base = currentQuestion.trim()
        onQuestionChange(if (base.isEmpty()) spoken else base + " " + spoken)
    }
    val latestAppend by rememberUpdatedState(appendSpoken)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            dictation.start { spoken -> latestAppend(spoken) }
        } else {
            onMessage("Sin permiso de micrófono no puedo dictar")
        }
    }

    LaunchedEffect(dictation.error) {
        dictation.error?.let { onMessage(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .background(PixelPanel.copy(alpha = 0.96f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (dictation.listening) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(PixelRed))
                Spacer(Modifier.width(6.dp))
                PixelLabel(text = "ESCUCHANDO… HABLA AHORA", size = 6.sp, color = PixelRed)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelIconButton(onClick = onAttach, enabled = enabled) {
                PixelGlyph(PixelIcons.doc, size = 16.dp, color = PixelGreen)
            }
            Spacer(Modifier.width(6.dp))
            PixelTextField(
                value = question,
                onValueChange = onQuestionChange,
                modifier = Modifier.weight(1f),
                placeholder = "Pregunta o pide un análisis…",
                maxLines = 3,
                trailing = {
                    PixelIconButton(onClick = onSend, enabled = enabled && question.trim().length >= 3) {
                        PixelGlyph(PixelIcons.send, size = 15.dp, color = if (enabled) PixelGreen else PixelMuted)
                    }
                },
            )
            Spacer(Modifier.width(6.dp))
            PixelIconButton(
                onClick = {
                    if (dictation.listening) {
                        dictation.stop()
                    } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        dictation.start { spoken -> latestAppend(spoken) }
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                fill = if (dictation.listening) PixelRed else PixelPanelAlt,
                enabled = !working,
            ) {
                PixelGlyph(PixelIcons.mic, size = 16.dp, color = if (dictation.listening) PixelInk else PixelYellow)
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelChip(
                text = "RESUMEN",
                selected = false,
                onClick = { onRunTask(StudioTasks.SUMMARY) },
                color = PixelCyan,
            )
            PixelChip(
                text = "PREGUNTAS",
                selected = false,
                onClick = { onRunTask(StudioTasks.QUESTIONS) },
                color = PixelPurple,
            )
            PixelChip(
                text = "DESCARGAR",
                selected = false,
                onClick = onExport,
                color = PixelPink,
            )
            PixelChip(
                text = "CONFIGURAR",
                selected = false,
                onClick = onConfigure,
                color = PixelYellow,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelBodyText(
                text = formatTokens(tokens) + " tokens · " + cacheHits + " aciertos de caché",
                size = 14.sp,
                color = PixelMuted,
            )
        }
    }
}