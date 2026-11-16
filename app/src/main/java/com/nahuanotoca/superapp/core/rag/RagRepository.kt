package com.nahuanotoca.superapp.core.rag

import android.content.Context
import com.nahuanotoca.superapp.core.model.RagChunk
import com.nahuanotoca.superapp.core.model.RagDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * On-device store for imported documents. Metadata and chunks live under filesDir/rag so the PDF
 * content never leaves the phone; only the top-k snippets are sent to the configured LLM.
 */
class RagRepository(context: Context) {

    private val directory: File = File(context.filesDir, "rag").apply { mkdirs() }

    suspend fun documents(): List<RagDocument> = withContext(Dispatchers.IO) {
        directory.listFiles { file -> file.name.endsWith(META_SUFFIX) }
            ?.mapNotNull { readMeta(it) }
            ?.sortedByDescending { it.addedAt }
            ?: emptyList()
    }

    suspend fun import(
        name: String,
        chunks: List<TextChunker.Chunk>,
    ): RagDocument = withContext(Dispatchers.IO) {
        require(chunks.isNotEmpty()) { "El documento no produjo fragmentos de texto" }

        val id = "doc_" + System.currentTimeMillis().toString(36) + "_" + (0..0xFFFF).random().toString(16)
        val addedAt = System.currentTimeMillis()
        val characters = chunks.sumOf { it.text.length }

        val chunksJson = JSONArray().apply {
            chunks.forEach { chunk ->
                put(
                    JSONObject()
                        .put("doc", name)
                        .put("page", chunk.page)
                        .put("text", chunk.text),
                )
            }
        }
        File(directory, id + CHUNKS_SUFFIX).writeText(chunksJson.toString())

        val document = RagDocument(
            id = id,
            name = name,
            pages = chunks.maxOf { it.page },
            chunks = chunks.size,
            characters = characters,
            addedAt = addedAt,
        )

        val meta = JSONObject()
            .put("id", document.id)
            .put("name", document.name)
            .put("pages", document.pages)
            .put("chunks", document.chunks)
            .put("characters", document.characters)
            .put("addedAt", document.addedAt)
        File(directory, id + META_SUFFIX).writeText(meta.toString())

        document
    }

    suspend fun chunks(): List<RagChunk> = withContext(Dispatchers.IO) {
        directory.listFiles { file -> file.name.endsWith(CHUNKS_SUFFIX) }
            ?.flatMap { file -> readChunks(file) }
            ?: emptyList()
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(directory, id + META_SUFFIX).delete()
        File(directory, id + CHUNKS_SUFFIX).delete()
        Unit
    }

    private fun readMeta(file: File): RagDocument? = runCatching {
        val json = JSONObject(file.readText())
        RagDocument(
            id = json.getString("id"),
            name = json.getString("name"),
            pages = json.optInt("pages", 0),
            chunks = json.optInt("chunks", 0),
            characters = json.optInt("characters", 0),
            addedAt = json.optLong("addedAt", 0L),
        )
    }.getOrNull()

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
