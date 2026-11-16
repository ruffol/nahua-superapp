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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudyTools(enabled: Boolean, working: Boolean, onRun: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Herramientas de estudio",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Cada botón manda el contexto completo de la mesa, no sólo palabras clave.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StudioTasks.all.forEach { task ->
                TaskTile(
                    task = task,
                    enabled = enabled,
                    working = working,
                    onClick = { onRun(task.id) },
                )
            }
        }
    }
}

@Composable
internal fun TaskTile(task: StudioTask, enabled: Boolean, working: Boolean, onClick: () -> Unit) {
    val accent = taskAccent(task.id)
    Surface(
        onClick = { if (enabled) onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (enabled) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, accent.copy(alpha = if (enabled) 0.35f else 0.12f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (working) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = accent,
                )
            } else {
                Icon(taskIcon(task.id), contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(7.dp))
            Text(
                text = task.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun ConversationIntro() {
    NahuaCard(contentPadding = PaddingValues(18.dp)) {
        Text(
            text = "Pregunta con tus palabras",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Puedes escribir “resumen”, “explícame el tema 3” o pedir el cruce entre dos " +
                "documentos. Las respuestas citan documento y página.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun TurnBlock(turn: ChatTurn) {
    if (turn.role == ChatRole.USER) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(0.9f),
            ) {
                Text(
                    text = turn.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        return
    }

    val task = StudioTasks.find(turn.taskId)
    NahuaCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (task != null) {
                StatPill(task.label, taskAccent(task.id))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = turn.model.ifBlank { "modelo" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (turn.usage.totalTokens > 0) {
                Text(
                    text = formatTokens(turn.usage.totalTokens) + " tok",
                    style = MaterialTheme.typography.labelSmall,
                    color = Violet,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        MarkdownText(turn.text, MaterialTheme.colorScheme.onSurface)

        if (turn.sources.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Fuentes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            turn.sources.distinctBy { it.documentName to it.page }.take(8).forEach { source ->
                Text(
                    text = "• " + source.documentName + " · p. " + source.page,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
internal fun MarkdownText(text: String, color: Color) {
    val lines = remember(text) { text.replace("\r\n", "\n").split("\n") }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        lines.forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Spacer(Modifier.height(4.dp))
                line.startsWith("|") -> Text(
                    text = line.trim('|').replace("|", " · ").trim(),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                )
                line.startsWith("###") -> Text(
                    text = plain(line.removePrefix("###")),
                    style = MaterialTheme.typography.titleSmall,
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                )
                line.startsWith("##") -> Text(
                    text = plain(line.removePrefix("##")),
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                    fontWeight = FontWeight.Bold,
                )
                line.startsWith("#") -> Text(
                    text = plain(line.removePrefix("#")),
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                    fontWeight = FontWeight.Bold,
                )
                line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ") -> Row {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = inline(line.substring(1).trim()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                    )
                }
                line == "---" -> HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                else -> Text(
                    text = inline(line),
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                )
            }
        }
    }
}

internal fun plain(text: String): String = text
    .replace("**", "")
    .replace("__", "")
    .replace("`", "")
    .trim()

/** Negritas de Markdown sin agregar una librería de render. */
internal fun inline(text: String): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var bold = false
    var index = 0
    while (index < text.length) {
        if (index + 1 < text.length && text[index] == '*' && text[index + 1] == '*') {
            bold = !bold
            index += 2
            continue
        }
        val start = index
        while (index < text.length && !(index + 1 < text.length && text[index] == '*' && text[index + 1] == '*')) {
            index++
        }
        val chunk = text.substring(start, index)
        if (bold) {
            builder.withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(chunk) }
        } else {
            builder.append(chunk)
        }
    }
    return builder.toAnnotatedString()
}

@Composable
internal fun WorkingCard(label: String) {
    NahuaCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = label.ifBlank { "Trabajando" } + "…",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Buscando en tus documentos y redactando. Puede tardar unos segundos.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun Composer(
    question: String,
    onQuestionChange: (String) -> Unit,
    onSend: () -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit,
    enabled: Boolean,
    working: Boolean,
    hasTurns: Boolean,
    tokens: Int,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp, shadowElevation = 12.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            OutlinedTextField(
                value = question,
                onValueChange = onQuestionChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Pregunta o pide un análisis…") },
                minLines = 1,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    IconButton(onClick = onSend, enabled = enabled && question.trim().length >= 3) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Enviar",
                            tint = if (enabled && question.trim().length >= 3) Jade else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTokens(tokens) + " tokens usados",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (hasTurns) {
                    TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Limpiar", style = MaterialTheme.typography.labelSmall)
                    }
                }
                TextButton(onClick = onExport, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PDF", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
