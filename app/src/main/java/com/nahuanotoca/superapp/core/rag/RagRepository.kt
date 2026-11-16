package com.nahuanotoca.superapp.core.rag

import android.content.Context
import android.graphics.Bitmap
import com.nahuanotoca.superapp.core.model.RagChunk
import com.nahuanotoca.superapp.core.model.RagDocument
import com.nahuanotoca.superapp.core.model.RagFigure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Almacén local de documentos por mesa de trabajo: cada mesa tiene su carpeta
 * filesDir/rag/mesaId con el texto indexado y las figuras extraídas.
 * El PDF original nunca se copia: sólo su texto y sus imágenes.
 */
class RagRepository(private val context: Context) {

    private val root: File = File(context.filesDir, "rag").apply { mkdirs() }

    private fun folder(mesaId: String): File = File(root, mesaId).apply { mkdirs() }

    private fun safeId(mesaId: String): String = mesaId.ifBlank { "default" }

    suspend fun documents(mesaId: String): List<RagDocument> = withContext(Dispatchers.IO) {
        val scope = safeId(mesaId)
        folder(scope).listFiles { file -> file.name.endsWith(META_SUFFIX) }
            ?.mapNotNull { readMeta(it, scope) }
            ?.sortedByDescending { it.addedAt }
            ?: emptyList()
    }

    suspend fun import(
        mesaId: String,
        name: String,
        chunks: List<TextChunker.Chunk>,
        figures: List<ExtractedFigure> = emptyList(),
    ): RagDocument = withContext(Dispatchers.IO) {
        require(chunks.isNotEmpty()) { "El documento no produjo fragmentos de texto" }

        val scope = safeId(mesaId)
        val id = "doc_" + System.currentTimeMillis().toString(36) + "_" + (0..0xFFFF).random().toString(16)
        val addedAt = System.currentTimeMillis()
        val characters = chunks.sumOf { it.text.length }
        val target = folder(scope)

        val chunksJson = JSONArray().apply {
            chunks.forEach { chunk ->
                put(JSONObject().put("doc", name).put("page", chunk.page).put("text", chunk.text))
            }
        }
        File(target, id + CHUNKS_SUFFIX).writeText(chunksJson.toString())

        val figuresJson = JSONArray().apply {
            figures.forEachIndexed { index, figure ->
                val fileName = "fig_" + id + "_" + index + ".jpg"
                val saved = runCatching {
                    FileOutputStream(File(target, fileName)).use { out ->
                        figure.bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
                    }
                }.getOrDefault(false)
                if (saved) {
                    put(JSONObject().put("file", fileName).put("page", figure.page))
                }
                figure.bitmap.recycle()
            }
        }

        val document = RagDocument(
            id = id,
            mesaId = scope,
            name = name,
            pages = chunks.maxOf { it.page },
            chunks = chunks.size,
            characters = characters,
            addedAt = addedAt,
            figures = figuresJson.length(),
        )

        val meta = JSONObject()
            .put("id", document.id)
            .put("mesa", document.mesaId)
            .put("name", document.name)
            .put("pages", document.pages)
            .put("chunks", document.chunks)
            .put("characters", document.characters)
            .put("addedAt", document.addedAt)
            .put("figures", figuresJson)
        File(target, id + META_SUFFIX).writeText(meta.toString())

        document
    }

    suspend fun chunks(mesaId: String): List<RagChunk> = withContext(Dispatchers.IO) {
        folder(safeId(mesaId)).listFiles { file -> file.name.endsWith(CHUNKS_SUFFIX) }
            ?.flatMap { file -> readChunks(file) }
            ?: emptyList()
    }

    /** Figuras de la mesa, ordenadas por documento y página, listas para mostrar como galería. */
    suspend fun figures(mesaId: String): List<RagFigure> = withContext(Dispatchers.IO) {
        val target = folder(safeId(mesaId))
        target.listFiles { file -> file.name.endsWith(META_SUFFIX) }
            ?.flatMap { metaFile -> readFigures(metaFile, target) }
            ?.sortedWith(compareBy({ it.documentName }, { it.page }))
            ?: emptyList()
    }

    suspend fun delete(mesaId: String, id: String) = withContext(Dispatchers.IO) {
        val target = folder(safeId(mesaId))
        val metaFile = File(target, id + META_SUFFIX)
        readFigures(metaFile, target).forEach { figure ->
            runCatching { File(figure.path).delete() }
        }
        metaFile.delete()
        File(target, id + CHUNKS_SUFFIX).delete()
        Unit
    }

    suspend fun deleteAll(mesaId: String) = withContext(Dispatchers.IO) {
        folder(safeId(mesaId)).deleteRecursively()
        Unit
    }

    /** ¿Quedan documentos del formato anterior (sin mesas) en la raíz? */
    fun hasLegacyDocuments(): Boolean =
        root.listFiles { file -> file.name.endsWith(META_SUFFIX) }?.isNotEmpty() == true

    /** Mueve los documentos previos a la primera mesa para no perderlos al actualizar. */
    suspend fun migrateLegacy(mesaId: String): Int = withContext(Dispatchers.IO) {
        val target = folder(safeId(mesaId))
        val legacy = root.listFiles { file ->
            file.isFile && (file.name.endsWith(META_SUFFIX) || file.name.endsWith(CHUNKS_SUFFIX))
        } ?: return@withContext 0

        var moved = 0
        legacy.forEach { file ->
            val destination = File(target, file.name)
            if (destination.exists()) destination.delete()
            if (file.renameTo(destination)) moved++
        }
        moved
    }

    private fun readMeta(file: File, mesaId: String): RagDocument? = runCatching {
        val json = JSONObject(file.readText())
        RagDocument(
            id = json.getString("id"),
            mesaId = json.optString("mesa", mesaId),
            name = json.getString("name"),
            pages = json.optInt("pages", 0),
            chunks = json.optInt("chunks", 0),
            characters = json.optInt("characters", 0),
            addedAt = json.optLong("addedAt", 0L),
            figures = json.optJSONArray("figures")?.length() ?: 0,
        )
    }.getOrNull()

    private fun readFigures(metaFile: File, folder: File): List<RagFigure> = runCatching {
        if (!metaFile.exists()) return@runCatching emptyList()
        val json = JSONObject(metaFile.readText())
        val documentId = json.optString("id")
        val documentName = json.optString("name", "Documento")
        val array = json.optJSONArray("figures") ?: return@runCatching emptyList()
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val fileName = item.optString("file")
                if (fileName.isBlank()) continue
                val file = File(folder, fileName)
                if (!file.exists()) continue
                add(
                    RagFigure(
                        documentId = documentId,
                        documentName = documentName,
                        page = item.optInt("page", 1),
                        path = file.absolutePath,
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun readChunks(file: File): List<RagChunk> = runCatching {
        val array = JSONArray(file.readText())
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    RagChunk(
                        documentId = file.nameWithoutExtension.substringBefore('.'),
                        documentName = item.optString("doc", "Documento"),
                        page = item.optInt("page", 1),
                        text = item.optString("text"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val META_SUFFIX = ".meta.json"
        const val CHUNKS_SUFFIX = ".chunks.json"
    }
}