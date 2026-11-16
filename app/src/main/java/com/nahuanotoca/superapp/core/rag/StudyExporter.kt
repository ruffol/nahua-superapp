package com.nahuanotoca.superapp.core.rag

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.nahuanotoca.superapp.core.model.RagSource
import com.nahuanotoca.superapp.core.model.TokenUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportFormat(val label: String, val extension: String, val mimeType: String) {
    PDF("PDF", "pdf", "application/pdf"),
    MARKDOWN("Markdown", "md", "text/markdown"),
    TEXT("Texto", "txt", "text/plain"),
}

/**
 * Herramientas de creación del chat: la misma respuesta se puede bajar como PDF maquetado, como
 * Markdown (para Obsidian/Notion) o como texto plano.
 */
class StudyExporter(private val context: Context) {

    suspend fun write(
        title: String,
        body: String,
        format: ExportFormat,
        model: String = "",
        mesaName: String = "",
        sources: List<RagSource> = emptyList(),
        usage: TokenUsage = TokenUsage.Empty,
    ): File = withContext(Dispatchers.IO) {
        when (format) {
            ExportFormat.PDF -> ReportWriter(context).write(
                ReportRequest(
                    mesaName = mesaName.ifBlank { "Mesa de trabajo" },
                    subject = "",
                    mesaContext = "",
                    title = title,
                    body = body,
                    model = model,
                    usage = usage,
                    documents = sources.map { it.documentName }.distinct(),
                    sources = sources,
                ),
            )

            ExportFormat.MARKDOWN -> {
                val folder = File(context.cacheDir, "exports").apply { mkdirs() }
                val file = File(folder, slug(title) + "-" + stamp() + ".md")
                file.writeText(body)
                file
            }

            ExportFormat.TEXT -> {
                val folder = File(context.cacheDir, "exports").apply { mkdirs() }
                val file = File(folder, slug(title) + "-" + stamp() + ".txt")
                file.writeText(body.replace("**", "").replace("##", "").replace("#", ""))
                file
            }
        }
    }

    fun share(file: File, format: ExportFormat, title: String) {
        val uri = runCatching {
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        }.getOrNull() ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "Compartir " + format.label).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun slug(text: String): String = text.lowercase()
        .replace(Regex("[^a-z0-9áéíóúñ]+"), "-")
        .trim('-')
        .take(48)
        .ifBlank { "analisis" }

    private fun stamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
}