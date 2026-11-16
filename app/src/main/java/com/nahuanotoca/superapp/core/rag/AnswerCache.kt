package com.nahuanotoca.superapp.core.rag

import android.content.Context
import com.nahuanotoca.superapp.core.model.RagSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CachedAnswer(
    val key: String,
    val text: String,
    val model: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val sources: List<RagSource>,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val totalTokens: Int get() = promptTokens + completionTokens
}

data class CacheStats(
    val entries: Int = 0,
    val tokensSaved: Int = 0,
)

/**
 * Caché de respuestas por mesa: si repites una tarea con el mismo contexto, la app devuelve la
 * respuesta guardada, no gasta tokens y lo marca como "acierto de caché".
 *
 * Vive en filesDir/studio/cache/<mesa>.json, con tope de entradas y caducidad.
 */
class AnswerCache(context: Context) {

    private val folder: File = File(context.filesDir, "studio/cache").apply { mkdirs() }
    private val mutex = Mutex()

    private fun file(mesaId: String): File = File(folder, mesaId.ifBlank { "default" } + ".json")

    suspend fun get(mesaId: String, key: String): CachedAnswer? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            read(file(mesaId)).firstOrNull { it.key == key && now - it.createdAt < TTL_MILLIS }
        }
    }

    suspend fun put(mesaId: String, answer: CachedAnswer) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val entries = read(file(mesaId))
                .filterNot { it.key == answer.key }
                .plus(answer)
                .sortedByDescending { it.createdAt }
                .take(MAX_ENTRIES)
            write(file(mesaId), entries)
            Unit
        }
    }

    suspend fun stats(mesaId: String): CacheStats = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val valid = read(file(mesaId)).filter { now - it.createdAt < TTL_MILLIS }
            CacheStats(entries = valid.size, tokensSaved = valid.sumOf { it.totalTokens })
        }
    }

    suspend fun clear(mesaId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            file(mesaId).delete()
            Unit
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            folder.listFiles()?.forEach { it.delete() }
            Unit
        }
    }

    private fun read(file: File): List<CachedAnswer> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val sources = mutableListOf<RagSource>()
                    item.optJSONArray("sources")?.let { array ->
                        for (s in 0 until array.length()) {
                            val source = array.optJSONObject(s) ?: continue
                            sources += RagSource(
                                documentName = source.optString("doc"),
                                page = source.optInt("page", 1),
                                snippet = source.optString("snippet"),
                            )
                        }
                    }
                    add(
                        CachedAnswer(
                            key = item.optString("key"),
                            text = item.optString("text"),
                            model = item.optString("model"),
                            promptTokens = item.optInt("promptTokens", 0),
                            completionTokens = item.optInt("completionTokens", 0),
                            sources = sources,
                            createdAt = item.optLong("createdAt", 0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun write(file: File, entries: List<CachedAnswer>) {
        val array = JSONArray().apply {
            entries.forEach { entry ->
                put(
                    JSONObject()
                        .put("key", entry.key)
                        .put("text", entry.text)
                        .put("model", entry.model)
                        .put("promptTokens", entry.promptTokens)
                        .put("completionTokens", entry.completionTokens)
                        .put("createdAt", entry.createdAt)
                        .put("sources", JSONArray().apply {
                            entry.sources.forEach { source ->
                                put(
                                    JSONObject()
                                        .put("doc", source.documentName)
                                        .put("page", source.page)
                                        .put("snippet", source.snippet),
                                )
                            }
                        }),
                )
            }
        }
        file.writeText(array.toString())
    }

    private companion object {
        const val MAX_ENTRIES = 40
        const val TTL_MILLIS = 14L * 24 * 60 * 60 * 1000
    }
}
