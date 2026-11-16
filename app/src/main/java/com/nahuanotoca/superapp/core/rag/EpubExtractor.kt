package com.nahuanotoca.superapp.core.rag

import android.content.Context
import android.net.Uri
import java.io.IOException

/**
 * Extrae un EPUB del almacenamiento. Cada capítulo se trata como una "página" para que las citas
 * sigan funcionando igual que con los PDFs (documento + número).
 */
class EpubExtractor(private val context: Context) {

    fun extract(uri: Uri): ExtractedPdf {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("No se pudo abrir el EPUB")

        input.use { stream ->
            val chapters = EpubParser.parse(stream)
            if (chapters.isEmpty()) throw IOException("El EPUB no tiene capítulos legibles")

            return ExtractedPdf(
                pages = chapters.map { chapter -> chapter.title + "\n" + chapter.text },
                totalPages = chapters.size,
                truncated = false,
                figures = emptyList(),
            )
        }
    }
}
