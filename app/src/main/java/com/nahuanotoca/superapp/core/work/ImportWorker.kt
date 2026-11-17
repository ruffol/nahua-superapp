package com.nahuanotoca.superapp.core.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nahuanotoca.superapp.NahuaApplication
import com.nahuanotoca.superapp.core.AppContainer
import kotlinx.coroutines.flow.first
import com.nahuanotoca.superapp.core.rag.OcrMerge
import com.nahuanotoca.superapp.core.rag.TextChunker
import com.nahuanotoca.superapp.core.util.Digest

/**
 * Indexa un archivo (PDF o EPUB) en segundo plano con WorkManager.
 *
 * Se encola **un worker por archivo**, encadenados: uno lento no bloquea al resto y ninguno se
 * acerca al límite de 10 minutos de WorkManager. El estado sobrevive a cerrar la app.
 *
 * Entrada: mesa, uri, nombre visible, índice y total (para el progreso).
 * Salida: indexado / duplicado / error y las páginas, fragmentos y figuras del documento.
 */
class ImportWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val mesaId = inputData.getString(KEY_MESA)?.takeIf { it.isNotBlank() } ?: return Result.failure()
        val uriString = inputData.getString(KEY_URI) ?: return Result.failure()
        val name = inputData.getString(KEY_NAME)?.takeIf { it.isNotBlank() } ?: "documento"
        val index = inputData.getInt(KEY_INDEX, 1)
        val total = inputData.getInt(KEY_TOTAL, 1)

        setProgressAsync(progressFor(index, total, name, STAGE_READING))

        val application = applicationContext as? NahuaApplication ?: return Result.failure()
        val container = application.container
        val resolver = applicationContext.contentResolver
        val uri = Uri.parse(uriString)

        return try {
            val sha = resolver.openInputStream(uri)?.use { Digest.sha256(it) }
            if (sha != null && container.rag.findByHash(mesaId, sha) != null) {
                return Result.success(workDataOf(KEY_DUPLICATE to true))
            }

            val isEpub = name.endsWith(".epub", ignoreCase = true) ||
                resolver.getType(uri)?.contains("epub", ignoreCase = true) == true

            setProgressAsync(progressFor(index, total, name, STAGE_EXTRACTING))
            var extracted = if (isEpub) {
                container.epubExtractor.extract(uri)
            } else {
                container.pdfExtractor.extract(uri)
            }

            // PDF escaneado: se renderiza cada página y se reconoce con ML Kit en el dispositivo.
            if (!isEpub && OcrMerge.needsOcr(extracted.pages)) {
                setProgressAsync(progressFor(index, total, name, STAGE_OCR))
                val recognized = runCatching {
                    container.ocrExtractor.extract(uri, maxPages = OCR_MAX_PAGES) { done, pages ->
                        setProgressAsync(
                            progressFor(index, total, name, STAGE_OCR + " " + done + "/" + pages),
                        )
                    }
                }.getOrDefault(emptyList())

                if (recognized.isNotEmpty()) {
                    extracted = extracted.copy(pages = OcrMerge.merge(extracted.pages, recognized))
                }
            }

            if (extracted.pages.isEmpty() || extracted.pages.all { it.isBlank() }) {
                return Result.failure(
                    workDataOf(
                        KEY_ERROR to "«" + name + "» no tiene texto ni siquiera tras OCR. " +
                            "Revisa que el escaneo sea legible.",
                    ),
                )
            }

            setProgressAsync(progressFor(index, total, name, STAGE_CHUNKING))
            val chunks = TextChunker.chunk(extracted.pages)
            if (chunks.isEmpty()) {
                return Result.failure(
                    workDataOf(KEY_ERROR to "«" + name + "» no produjo fragmentos de texto."),
                )
            }

            setProgressAsync(progressFor(index, total, name, STAGE_INDEXING))
            val document = container.rag.import(mesaId, name, chunks, extracted.figures, sha.orEmpty())

            // Vectores opcionales: si falla el proveedor, la importación sigue con BM25.
            runCatching {
                embedDocument(container, mesaId, document.id, name, index, total)
            }

            Result.success(
                workDataOf(
                    KEY_INDEXED to true,
                    KEY_PAGES to extracted.totalPages,
                    KEY_CHUNKS to chunks.size,
                    KEY_FIGURES to document.figures,
                ),
            )
        } catch (error: Exception) {
            Result.failure(
                workDataOf(KEY_ERROR to "No pudimos leer " + name + ": " + (error.message ?: "error desconocido")),
            )
        }
    }

    private suspend fun embedDocument(
        container: AppContainer,
        mesaId: String,
        documentId: String,
        name: String,
        index: Int,
        total: Int,
    ) {
        val settings = container.ragSettings.settings.first()
        if (!settings.embeddingsConfigured) return

        val chunks = container.rag.chunksOf(documentId)
        if (chunks.isEmpty()) return

        val batchSize = 32
        val batches = (chunks.size + batchSize - 1) / batchSize
        for (batch in 0 until batches) {
            if (isStopped) return
            val slice = chunks.subList(batch * batchSize, minOf((batch + 1) * batchSize, chunks.size))
            setProgressAsync(
                progressFor(index, total, name, STAGE_EMBEDDING + " " + (batch + 1) + "/" + batches),
            )
            val embedded = container.embeddingClient.embed(settings, slice.map { it.text }).getOrNull()
                ?: return
            container.rag.saveEmbeddings(
                mesaId = mesaId,
                model = settings.embeddingModel,
                items = slice.mapIndexed { position, chunk -> chunk.id to embedded[position] },
            )
        }
    }

    private fun progressFor(index: Int, total: Int, name: String, stage: String): Data = workDataOf(
        KEY_INDEX to index,
        KEY_TOTAL to total,
        KEY_NAME to name,
        KEY_STAGE to stage,
    )

    companion object {
        const val KEY_MESA = "mesa"
        const val KEY_URI = "uri"
        const val KEY_NAME = "name"
        const val KEY_INDEX = "index"
        const val KEY_TOTAL = "total"
        const val KEY_STAGE = "stage"
        const val KEY_INDEXED = "indexed"
        const val KEY_DUPLICATE = "duplicate"
        const val KEY_ERROR = "error"
        const val KEY_PAGES = "pages"
        const val KEY_CHUNKS = "chunks"
        const val KEY_FIGURES = "figures"

        const val STAGE_READING = "leyendo"
        const val STAGE_EXTRACTING = "extrayendo"
        const val STAGE_CHUNKING = "fragmentando"
        const val STAGE_OCR = "ocr"
        const val STAGE_EMBEDDING = "vectorizando"
        const val STAGE_INDEXING = "guardando"
        const val OCR_MAX_PAGES = 120

        fun uniqueName(mesaId: String): String = "import-" + mesaId
    }
}