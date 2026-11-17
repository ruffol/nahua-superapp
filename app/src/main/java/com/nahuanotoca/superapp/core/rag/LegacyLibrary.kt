package com.nahuanotoca.superapp.core.rag

import org.json.JSONArray
import org.json.JSONObject

/**
 * Lector del índice JSON anterior (una carpeta por mesa con `doc_<id>.meta.json` y
 * `doc_<id>.chunks.json`). Se usa una sola vez para migrar a Room sin perder documentos.
 *
 * Puro Kotlin para poder probarlo con cadenas de ejemplo.
 */
object LegacyLibrary {

    data class LegacyFigure(val page: Int, val fileName: String)

    data class LegacyDocument(
        val id: String,
        val name: String,
        val pages: Int,
        val chunks: Int,
        val characters: Int,
        val addedAt: Long,
        val sha256: String,
        val version: Int,
        val figures: List<LegacyFigure>,
    )

    data class LegacyChunk(val id: String, val page: Int, val text: String)

    fun parseMeta(json: String): LegacyDocument? = runCatching {
        val item = JSONObject(json)
        val figures = mutableListOf<LegacyFigure>()
        item.optJSONArray("figures")?.let { array ->
            for (index in 0 until array.length()) {
                val figure = array.optJSONObject(index) ?: continue
                val fileName = figure.optString("file")
                if (fileName.isBlank()) continue
                figures += LegacyFigure(page = figure.optInt("page", 1), fileName = fileName)
            }
        }
        LegacyDocument(
            id = item.getString("id"),
            name = item.optString("name", "Documento"),
            pages = item.optInt("pages", 0),
            chunks = item.optInt("chunks", 0),
            characters = item.optInt("characters", 0),
            addedAt = item.optLong("addedAt", 0L),
            sha256 = item.optString("sha256", ""),
            version = item.optInt("version", 1),
            figures = figures,
        )
    }.getOrNull()

    fun parseChunks(json: String, documentId: String): List<LegacyChunk> = runCatching {
        val array = JSONArray(json)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val text = item.optString("text")
                val page = item.optInt("page", 1)
                val id = item.optString("id").ifBlank {
                    documentId + ":" + page + ":" + CacheKey.sha256(text).take(12)
                }
                add(LegacyChunk(id = id, page = page, text = text))
            }
        }
    }.getOrDefault(emptyList())
}
