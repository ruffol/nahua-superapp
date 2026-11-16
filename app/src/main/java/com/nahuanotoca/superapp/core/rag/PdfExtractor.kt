package com.nahuanotoca.superapp.core.rag

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

data class ExtractedPdf(
    val pages: List<String>,
    val totalPages: Int,
    val truncated: Boolean,
)

/**
 * Extracts selectable text from a PDF entirely on device. Scanned/image-only PDFs produce empty
 * pages; the UI surfaces that as an actionable error instead of hallucinating answers.
 */
class PdfExtractor(private val context: Context) {

    private val initialized = AtomicBoolean(false)

    fun extract(uri: Uri, maxPages: Int = 300): ExtractedPdf {
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

                val pages = (1..lastPage).map { page ->
                    stripper.startPage = page
                    stripper.endPage = page
                    stripper.getText(document)
                }

                return ExtractedPdf(
                    pages = pages,
                    totalPages = totalPages,
                    truncated = totalPages > lastPage,
                )
            }
        }
    }

    private fun ensureInitialized() {
        if (initialized.compareAndSet(false, true)) {
            PDFBoxResourceLoader.init(context.applicationContext)
        }
    }
}
