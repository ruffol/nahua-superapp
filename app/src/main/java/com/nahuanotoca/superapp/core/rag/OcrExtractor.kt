package com.nahuanotoca.superapp.core.rag

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume

/**
 * OCR local para PDFs escaneados. Renderiza cada página con `PdfRenderer` (sin dependencias extra)
 * y la pasa por ML Kit Text Recognition, que corre **en el dispositivo** con el modelo empaquetado.
 *
 * Es lento (1–3 s por página), así que se usa sólo cuando el PDF no trae texto seleccionable y con un
 * tope de páginas.
 */
class OcrExtractor(private val context: Context) {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun extract(
        uri: Uri,
        maxPages: Int = 120,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<String> = withContext(Dispatchers.IO) {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("No se pudo abrir el PDF para OCR")

        val renderer = try {
            PdfRenderer(descriptor)
        } catch (error: Exception) {
            descriptor.close()
            throw IOException("El PDF no se puede renderizar para OCR", error)
        }

        try {
            val total = renderer.pageCount.coerceAtMost(maxPages)
            val pages = mutableListOf<String>()

            for (index in 0 until total) {
                val page = renderer.openPage(index)
                val bitmap = try {
                    val width = 1_000
                    val height = (page.height * (width.toFloat() / page.width)).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(Color.WHITE)
                    }
                } catch (error: Exception) {
                    page.close()
                    pages += ""
                    onProgress(index + 1, total)
                    continue
                }

                try {
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pages += recognize(bitmap)
                } finally {
                    bitmap.recycle()
                    page.close()
                }
                onProgress(index + 1, total)
            }

            pages
        } finally {
            renderer.close()
            descriptor.close()
        }
    }

    private suspend fun recognize(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resume(result.text)
            }
            .addOnFailureListener {
                if (continuation.isActive) continuation.resume("")
            }
    }
}

/**
 * Decide cuándo hace falta OCR y mezcla el texto reconocido con el extraído. Puro Kotlin para poder
 * probarlo sin Android.
 */
object OcrMerge {

    const val MIN_TEXT_CHARS = 40

    /** ¿Vale la pena OCR? Cuando la mayoría de las páginas no traen texto seleccionable. */
    fun needsOcr(pages: List<String>, minChars: Int = MIN_TEXT_CHARS, threshold: Double = 0.7): Boolean {
        if (pages.isEmpty()) return true
        val empty = pages.count { it.trim().length < minChars }
        return empty.toDouble() / pages.size >= threshold
    }

    /** Reemplaza sólo las páginas sin texto; lo que ya venía del PDF se conserva. */
    fun merge(pages: List<String>, ocrPages: List<String>, minChars: Int = MIN_TEXT_CHARS): List<String> {
        if (ocrPages.isEmpty()) return pages
        return pages.mapIndexed { index, original ->
            val recognized = ocrPages.getOrNull(index).orEmpty().trim()
            if (original.trim().length >= minChars || recognized.isEmpty()) original else recognized
        }
    }
}
