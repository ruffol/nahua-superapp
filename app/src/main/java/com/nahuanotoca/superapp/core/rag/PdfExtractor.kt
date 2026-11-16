package com.nahuanotoca.superapp.core.rag

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Figura encontrada dentro de un PDF (diagrama, mapa, foto de diapositiva...). */
data class ExtractedFigure(
    val page: Int,
    val bitmap: Bitmap,
)

data class ExtractedPdf(
    val pages: List<String>,
    val totalPages: Int,
    val truncated: Boolean,
    val figures: List<ExtractedFigure> = emptyList(),
)

/**
 * Extrae, en una sola pasada y en el teléfono:
 * - el texto seleccionable de cada página (para el índice y las citas),
 * - las figuras incrustadas (para poder verlas mientras estudias).
 *
 * Los PDFs escaneados no producen texto: la UI lo reporta como error accionable en lugar de
 * inventar respuestas.
 */
class PdfExtractor(private val context: Context) {

    private val initialized = AtomicBoolean(false)

    fun extract(uri: Uri, maxPages: Int = 300, maxFigures: Int = 12): ExtractedPdf {
        ensureInitialized()

        val resolver = context.contentResolver
        val input = resolver.openInputStream(uri)
            ?: throw IOException("No se pudo abrir el documento")

        input.use { stream ->
            PDDocument.load(stream).use { document ->
                val totalPages = document.numberOfPages
                if (totalPages <= 0) throw IOException("El PDF no tiene páginas")

                val lastPage = totalPages.coerceAtMost(maxPages)
                val stripper = PDFTextStripper()
                val figures = mutableListOf<ExtractedFigure>()

                val pages = (1..lastPage).map { page ->
                    stripper.startPage = page
                    stripper.endPage = page
                    val text = stripper.getText(document)
                    collectFigures(document, page, figures, maxFigures)
                    text
                }

                return ExtractedPdf(
                    pages = pages,
                    totalPages = totalPages,
                    truncated = totalPages > lastPage,
                    figures = figures,
                )
            }
        }
    }

    /**
     * Se ignoran iconos y viñetas (menos de 160 px) y se reescala lo enorme para no llenar la
     * memoria de un teléfono de gama media. Cualquier imagen corrupta se salta sin abortar el PDF.
     */
    private fun collectFigures(
        document: PDDocument,
        pageNumber: Int,
        sink: MutableList<ExtractedFigure>,
        limit: Int,
    ) {
        if (sink.size >= limit) return
        val page = runCatching { document.getPage(pageNumber - 1) }.getOrNull() ?: return
        val resources = page.resources ?: return
        val names = runCatching { resources.xObjectNames }.getOrNull() ?: return

        for (name in names) {
            if (sink.size >= limit) return
            val xObject = runCatching { resources.getXObject(name) }.getOrNull()
            if (xObject !is PDImageXObject) continue
            if (xObject.width < MIN_FIGURE_SIDE || xObject.height < MIN_FIGURE_SIDE) continue
            val bitmap = runCatching { xObject.image }.getOrNull() ?: continue
            sink += ExtractedFigure(page = pageNumber, bitmap = scaleDown(bitmap))
        }
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_FIGURE_SIDE) return bitmap
        val ratio = MAX_FIGURE_SIDE.toFloat() / longest
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun ensureInitialized() {
        if (initialized.compareAndSet(false, true)) {
            PDFBoxResourceLoader.init(context.applicationContext)
        }
    }

    private companion object {
        const val MIN_FIGURE_SIDE = 160
        const val MAX_FIGURE_SIDE = 900
    }
}
