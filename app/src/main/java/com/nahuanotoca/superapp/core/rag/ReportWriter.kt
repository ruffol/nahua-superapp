package com.nahuanotoca.superapp.core.rag

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.nahuanotoca.superapp.core.model.RagSource
import com.nahuanotoca.superapp.core.model.TokenUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Todo lo que necesita el PDF final. */
data class ReportRequest(
    val mesaName: String,
    val subject: String,
    val mesaContext: String,
    val title: String,
    val body: String,
    val model: String,
    val usage: TokenUsage,
    val documents: List<String>,
    val sources: List<RagSource>,
)

/**
 * Escribe el análisis (Markdown) como PDF paginado A4 usando android.graphics.pdf.PdfDocument.
 *
 * Sin librerías nuevas: el PDF se dibuja con Canvas, con encabezados, viñetas, tablas y numeración
 * de página. El archivo queda en la caché privada y se comparte con FileProvider.
 */
class ReportWriter(private val context: Context) {

    suspend fun write(request: ReportRequest): File = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        val sheet = Sheet(document, request)
        sheet.render()

        val folder = File(context.cacheDir, "reports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val slug = sanitize(request.title).ifBlank { "analisis" }
        val file = File(folder, slug + "-" + stamp + ".pdf")

        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        file
    }

    private fun sanitize(text: String): String = text.lowercase()
        .replace(Regex("[^a-z0-9áéíóúñ]+"), "-")
        .trim('-')
        .take(48)

    /** Motor de dibujo: mantiene el cursor vertical, cambia de página y pinta encabezado y pie. */
    private class Sheet(
        private val document: PdfDocument,
        private val request: ReportRequest,
    ) {
        private val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(14, 124, 102)
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.12f
        }
        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(16, 22, 28)
            textSize = 21f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val subtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(93, 106, 120)
            textSize = 9.5f
            typeface = Typeface.SANS_SERIF
        }
        private val h2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(16, 22, 28)
            textSize = 13.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val h3 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(14, 124, 102)
            textSize = 11.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(28, 34, 42)
            textSize = 10.2f
            typeface = Typeface.SANS_SERIF
        }
        private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(28, 34, 42)
            textSize = 8.8f
            typeface = Typeface.SANS_SERIF
        }
        private val cellHeader = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(16, 22, 28)
            textSize = 8.8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val quote = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(93, 106, 120)
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        }
        private val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(228, 232, 240)
            strokeWidth = 1f
        }
        private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(214, 220, 230)
            strokeWidth = 0.6f
        }
        private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(14, 124, 102)
            strokeWidth = 2.5f
            strokeCap = Paint.Cap.ROUND
        }

        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = 0f

        fun render() {
            startPage(first = true)
            drawCover()
            renderMarkdown(request.body)
            drawSources()
            finishPage()
        }

        private fun startPage(first: Boolean) {
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val created = document.startPage(info)
            page = created
            canvas = created.canvas
            y = if (first) MARGIN_TOP + 120f else MARGIN_TOP
            if (!first) drawRunningHeader()
        }

        private fun finishPage() {
            val current = canvas ?: return
            drawFooter(current)
            page?.let { document.finishPage(it) }
            canvas = null
            page = null
        }

        private fun ensureSpace(needed: Float) {
            if (y + needed > PAGE_HEIGHT - MARGIN_BOTTOM) {
                finishPage()
                startPage(first = false)
            }
        }

        private fun lineHeight(paint: Paint): Float = paint.textSize * 1.5f

        private fun drawCover() {
            val c = canvas ?: return
            c.drawRect(MARGIN_LEFT, MARGIN_TOP - 26f, PAGE_WIDTH - MARGIN_RIGHT, MARGIN_TOP - 23f, accent)
            c.drawText("NAHUANOTOKA · MESA DE TRABAJO", MARGIN_LEFT, MARGIN_TOP + 2f, heading)
            y = MARGIN_TOP + 34f

            wrap(clean(request.title), titlePaint, CONTENT_WIDTH).forEach { text ->
                c.drawText(text, MARGIN_LEFT, y, titlePaint)
                y += lineHeight(titlePaint)
            }
            y += 6f

            val meta = buildString {
                append(request.mesaName)
                if (request.subject.isNotBlank()) append(" · ").append(request.subject)
                append(" · ").append(request.documents.size).append(" documento(s)")
                append(" · ")
                append(SimpleDateFormat("d MMM yyyy, HH:mm", Locale.forLanguageTag("es-MX")).format(Date()))
            }
            c.drawText(meta, MARGIN_LEFT, y, subtitle)
            y += 20f

            if (request.mesaContext.isNotBlank()) {
                val contextLines = wrap(clean(request.mesaContext), quote, CONTENT_WIDTH - 16f)
                val boxHeight = contextLines.size * lineHeight(quote) + 14f
                c.drawRect(
                    MARGIN_LEFT,
                    y - 11f,
                    PAGE_WIDTH - MARGIN_RIGHT,
                    y - 11f + boxHeight,
                    Paint().apply { color = Color.rgb(245, 247, 250) },
                )
                contextLines.forEach { text ->
                    c.drawText(text, MARGIN_LEFT + 8f, y, quote)
                    y += lineHeight(quote)
                }
                y += 14f
            }
            c.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, rule)
            y += 18f
        }

        private fun drawRunningHeader() {
            val c = canvas ?: return
            c.drawText("NAHUANOTOKA", MARGIN_LEFT, MARGIN_TOP - 16f, heading)
            val label = clean(request.title)
            val available = CONTENT_WIDTH - 90f
            val text = if (subtitle.measureText(label) > available) label.take(52) + "…" else label
            val width = subtitle.measureText(text)
            c.drawText(text, PAGE_WIDTH - MARGIN_RIGHT - width, MARGIN_TOP - 16f, subtitle)
            c.drawLine(MARGIN_LEFT, MARGIN_TOP - 8f, PAGE_WIDTH - MARGIN_RIGHT, MARGIN_TOP - 8f, rule)
        }

        private fun drawFooter(c: Canvas) {
            val footerY = PAGE_HEIGHT - 30f
            c.drawLine(MARGIN_LEFT, footerY - 10f, PAGE_WIDTH - MARGIN_RIGHT, footerY - 10f, rule)
            val left = "Nahuanotoka · " + request.model + " · " + formatTokens(request.usage) + " tokens"
            c.drawText(left, MARGIN_LEFT, footerY, subtitle)
            val right = "Página " + pageNumber
            val width = subtitle.measureText(right)
            c.drawText(right, PAGE_WIDTH - MARGIN_RIGHT - width, footerY, subtitle)
        }

        private fun formatTokens(usage: TokenUsage): String {
            val total = usage.totalTokens
            if (total < 1000) return total.toString()
            val thousands = total / 1000.0
            return String.format(Locale.US, "%.1fk", thousands)
        }

        private fun drawSources() {
            if (request.sources.isEmpty()) return
            if (canvas == null) return
            ensureSpace(60f)
            y += 12f
            canvas?.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, rule)
            y += 18f
            canvas?.drawText("Fuentes citadas", MARGIN_LEFT, y, h2)
            y += lineHeight(h2) + 2f
            request.sources
                .distinctBy { it.documentName to it.page }
                .take(24)
                .forEach { source ->
                    ensureSpace(lineHeight(subtitle))
                    canvas?.drawText(
                        "• " + clean(source.documentName) + " · p. " + source.page,
                        MARGIN_LEFT + 2f,
                        y,
                        subtitle,
                    )
                    y += lineHeight(subtitle)
                }
        }

        // --- Markdown -----------------------------------------------------

        private fun renderMarkdown(markdown: String) {
            val lines = markdown.replace("\r\n", "\n").split("\n")
            var index = 0
            while (index < lines.size) {
                val trimmed = lines[index].trimEnd()
                val text = trimmed.trim()
                val bulletMatch = BULLET.find(text)
                val numberedMatch = NUMBERED.find(text)

                when {
                    text.isEmpty() -> {
                        y += 6f
                        index++
                    }
                    text.startsWith("|") -> {
                        index = renderTable(lines, index)
                    }
                    text.startsWith("###") -> {
                        drawParagraph(clean(text.removePrefix("###")), h3, 8f)
                        index++
                    }
                    text.startsWith("##") -> {
                        drawParagraph(clean(text.removePrefix("##")), h2, 12f)
                        index++
                    }
                    text.startsWith("#") -> {
                        drawParagraph(clean(text.removePrefix("#")), h2, 12f)
                        index++
                    }
                    text.startsWith("> ") -> {
                        drawParagraph(clean(text.removePrefix("> ")), quote, 10f, indent = 10f)
                        index++
                    }
                    bulletMatch != null -> {
                        drawBullet(BULLET.replaceFirst(text, ""))
                        index++
                    }
                    numberedMatch != null -> {
                        val marker = numberedMatch.groupValues[1]
                        drawBullet(text.substring(numberedMatch.value.length).trim(), marker + ".")
                        index++
                    }
                    text == "---" || text == "***" -> {
                        ensureSpace(14f)
                        y += 4f
                        canvas?.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, rule)
                        y += 12f
                        index++
                    }
                    else -> {
                        drawParagraph(clean(text), body, 4f)
                        index++
                    }
                }
            }
        }

        private fun drawParagraph(text: String, paint: Paint, spacing: Float, indent: Float = 0f) {
            if (text.isBlank()) return
            ensureSpace(lineHeight(paint) * 2)
            wrap(text, paint, CONTENT_WIDTH - indent).forEach { line ->
                ensureSpace(lineHeight(paint))
                canvas?.drawText(line, MARGIN_LEFT + indent, y, paint)
                y += lineHeight(paint)
            }
            y += spacing
        }

        private fun drawBullet(text: String, marker: String = "•") {
            val indent = 14f
            val lines = wrap(clean(text), body, CONTENT_WIDTH - indent)
            lines.forEachIndexed { position, line ->
                ensureSpace(lineHeight(body))
                val c = canvas ?: return@forEachIndexed
                if (position == 0) c.drawText(marker, MARGIN_LEFT + 2f, y, body)
                c.drawText(line, MARGIN_LEFT + indent, y, body)
                y += lineHeight(body)
            }
            y += 2f
        }

        private fun renderTable(lines: List<String>, start: Int): Int {
            val rows = mutableListOf<List<String>>()
            var index = start
            while (index < lines.size && lines[index].trim().startsWith("|")) {
                val cells = lines[index].trim().trim('|').split('|').map { it.trim() }
                val separator = cells.isNotEmpty() && cells.all { cell ->
                    cell.isEmpty() || cell.matches(Regex("^:?-{2,}:?$"))
                }
                if (!separator) rows += cells
                index++
            }
            if (rows.isEmpty()) return index

            val columns = rows.maxOf { it.size }.coerceAtLeast(1)
            val columnWidth = CONTENT_WIDTH / columns

            rows.forEachIndexed { rowIndex, cells ->
                val paint = if (rowIndex == 0) cellHeader else cellPaint
                val wrapped = (0 until columns).map { column ->
                    wrap(clean(cells.getOrElse(column) { "" }), paint, columnWidth - 8f)
                }
                val rowHeight = wrapped.maxOf { it.size } * lineHeight(paint) + 8f
                ensureSpace(rowHeight)
                val c = canvas ?: return@forEachIndexed
                wrapped.forEachIndexed { column, cellLines ->
                    cellLines.forEachIndexed { lineIndex, cellText ->
                        c.drawText(
                            cellText,
                            MARGIN_LEFT + column * columnWidth + 4f,
                            y + lineIndex * lineHeight(paint),
                            paint,
                        )
                    }
                }
                y += rowHeight
                c.drawLine(MARGIN_LEFT, y - 3f, MARGIN_LEFT + CONTENT_WIDTH, y - 3f, grid)
            }
            y += 10f
            return index
        }

        private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
            if (text.isEmpty()) return emptyList()
            if (paint.measureText(text) <= maxWidth) return listOf(text)

            val result = mutableListOf<String>()
            val words = text.split(' ')
            var current = StringBuilder()

            words.forEach { word ->
                if (word.isEmpty()) return@forEach
                val candidate = if (current.isEmpty()) word else current.toString() + " " + word
                if (paint.measureText(candidate) <= maxWidth) {
                    current = StringBuilder(candidate)
                } else {
                    if (current.isNotEmpty()) result += current.toString()
                    if (paint.measureText(word) <= maxWidth) {
                        current = StringBuilder(word)
                    } else {
                        var rest = word
                        while (paint.measureText(rest) > maxWidth && rest.length > 1) {
                            var cut = rest.length
                            while (cut > 1 && paint.measureText(rest.substring(0, cut)) > maxWidth) cut--
                            result += rest.substring(0, cut)
                            rest = rest.substring(cut)
                        }
                        current = StringBuilder(rest)
                    }
                }
            }
            if (current.isNotEmpty()) result += current.toString()
            return result
        }

        /** Quita marcas de Markdown y emojis: la fuente del PDF no los dibuja. */
        private fun clean(text: String): String {
            var out = text.replace("**", "").replace("__", "")
            out = out.replace('`', ' ').replace('*', ' ')
            out = out.replace(Regex("^#+\\s*"), "")
            return stripSurrogates(out).replace(Regex("\\s+"), " ").trim()
        }

        private fun stripSurrogates(text: String): String {
            val builder = StringBuilder(text.length)
            var index = 0
            while (index < text.length) {
                val char = text[index]
                if (Character.isHighSurrogate(char)) {
                    index++
                    if (index < text.length && Character.isLowSurrogate(text[index])) index++
                    continue
                }
                val code = char.code
                val skip = code in 0xFE00..0xFE0F || code == 0x200D || code in 0x2600..0x27BF
                if (!skip) builder.append(char)
                index++
            }
            return builder.toString()
        }

        private companion object {
            const val PAGE_WIDTH = 595
            const val PAGE_HEIGHT = 842
            const val MARGIN_LEFT = 48f
            const val MARGIN_RIGHT = 48f
            const val MARGIN_TOP = 56f
            const val MARGIN_BOTTOM = 54f
            const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT

            val BULLET = Regex("^[-*•]\\s+")
            val NUMBERED = Regex("^(\\d{1,2})[.)]\\s+")
        }
    }
}

/** Comparte el PDF generado con cualquier app instalada (WhatsApp, Drive, impresión...). */
object ReportSharer {

    fun share(context: Context, file: File, title: String) {
        val uri = runCatching {
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        }.getOrNull() ?: return

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "Compartir tu PDF").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
