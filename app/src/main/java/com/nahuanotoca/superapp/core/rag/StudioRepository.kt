package com.nahuanotoca.superapp.core.rag

import android.content.Context
import com.nahuanotoca.superapp.core.model.ChatRole
import com.nahuanotoca.superapp.core.model.ChatTurn
import com.nahuanotoca.superapp.core.model.Mesa
import com.nahuanotoca.superapp.core.model.RagSource
import com.nahuanotoca.superapp.core.model.TokenUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persistencia de las mesas de trabajo, su historial y su contador de tokens.
 *
 * Se usa JSON en `filesDir/studio` en lugar de Room porque el volumen es mínimo (decenas de mesas,
 * decenas de turnos) y así no se agrega otro procesador de anotaciones al build.
 */
class StudioRepository(context: Context) {

    private val root: File = File(context.filesDir, "studio").apply { mkdirs() }
    private val mesasFile = File(root, "mesas.json")
    private val chatsFolder = File(root, "chats").apply { mkdirs() }
    private val mutex = Mutex()

    private val _mesas = MutableStateFlow<List<Mesa>>(emptyList())
    val mesas: StateFlow<List<Mesa>> = _mesas.asStateFlow()

    suspend fun load(): List<Mesa> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val list = runCatching { readMesas() }.getOrDefault(emptyList())
            _mesas.value = list.sortedByDescending { it.updatedAt }
            _mesas.value
        }
    }

    suspend fun create(name: String, subject: String, context: String): Mesa = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val mesa = Mesa(
                id = "mesa_" + now.toString(36) + "_" + (0..0xFFFF).random().toString(16),
                name = name.trim().ifBlank { "Mesa sin título" },
                subject = subject.trim(),
                context = context.trim(),
                accent = _mesas.value.size % 6,
                createdAt = now,
                updatedAt = now,
            )
            val updated = _mesas.value + mesa
            persist(updated)
            _mesas.value = updated.sortedByDescending { it.updatedAt }
            mesa
        }
    }

    suspend fun update(mesa: Mesa) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = _mesas.value.map { if (it.id == mesa.id) mesa.copy(updatedAt = System.currentTimeMillis()) else it }
            persist(updated)
            _mesas.value = updated.sortedByDescending { it.updatedAt }
            Unit
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = _mesas.value.filterNot { it.id == id }
            persist(updated)
            _mesas.value = updated.sortedByDescending { it.updatedAt }
            File(chatsFolder, id + ".json").delete()
            Unit
        }
    }

    suspend fun recordUsage(mesaId: String, usage: TokenUsage) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = _mesas.value.map { mesa ->
                if (mesa.id == mesaId) {
                    mesa.copy(
                        usage = mesa.usage.plus(usage),
                        requests = mesa.requests + 1,
                        updatedAt = System.currentTimeMillis(),
                    )
                } else {
                    mesa
                }
            }
            persist(updated)
            _mesas.value = updated.sortedByDescending { it.updatedAt }
            Unit
        }
    }

    suspend fun turns(mesaId: String): List<ChatTurn> = withContext(Dispatchers.IO) {
        val file = File(chatsFolder, mesaId + ".json")
        if (!file.exists()) return@withContext emptyList()
        runCatching { readTurns(file) }.getOrDefault(emptyList())
    }

    suspend fun appendTurn(mesaId: String, turn: ChatTurn) = withContext(Dispatchers.IO) {
        val file = File(chatsFolder, mesaId + ".json")
        val turns = (runCatching { readTurns(file) }.getOrDefault(emptyList()) + turn)
            .takeLast(MAX_TURNS)
        file.writeText(turnsToJson(turns).toString())
        Unit
    }

    suspend fun clearTurns(mesaId: String) = withContext(Dispatchers.IO) {
        File(chatsFolder, mesaId + ".json").delete()
        Unit
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            mesasFile.delete()
            chatsFolder.listFiles()?.forEach { it.delete() }
            _mesas.value = emptyList()
            Unit
        }
    }

    // --- serialización -----------------------------------------------------

    private fun persist(mesas: List<Mesa>) {
        val array = JSONArray().apply {
            mesas.forEach { mesa ->
                put(
                    JSONObject()
                        .put("id", mesa.id)
                        .put("name", mesa.name)
                        .put("subject", mesa.subject)
                        .put("context", mesa.context)
                        .put("accent", mesa.accent)
                        .put("createdAt", mesa.createdAt)
                        .put("updatedAt", mesa.updatedAt)
                        .put("promptTokens", mesa.usage.promptTokens)
                        .put("completionTokens", mesa.usage.completionTokens)
                        .put("requests", mesa.requests),
                )
            }
        }
        mesasFile.writeText(array.toString())
    }

    private fun readMesas(): List<Mesa> {
        if (!mesasFile.exists()) return emptyList()
        val array = JSONArray(mesasFile.readText())
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                add(
                    Mesa(
                        id = json.optString("id"),
                        name = json.optString("name", "Mesa"),
                        subject = json.optString("subject", ""),
                        context = json.optString("context", ""),
                        accent = json.optInt("accent", 0),
                        createdAt = json.optLong("createdAt", 0L),
                        updatedAt = json.optLong("updatedAt", 0L),
                        usage = TokenUsage(
                            promptTokens = json.optInt("promptTokens", 0),
                            completionTokens = json.optInt("completionTokens", 0),
                        ),
                        requests = json.optInt("requests", 0),
                    ),
                )
            }
        }
    }

    private fun turnsToJson(turns: List<ChatTurn>): JSONArray = JSONArray().apply {
        turns.forEach { turn ->
            put(
                JSONObject()
                    .put("id", turn.id)
                    .put("role", turn.role.name)
                    .put("text", turn.text)
                    .put("taskId", turn.taskId ?: JSONObject.NULL)
                    .put("model", turn.model)
                    .put("promptTokens", turn.usage.promptTokens)
                    .put("completionTokens", turn.usage.completionTokens)
                    .put("elapsed", turn.elapsedMillis)
                    .put("createdAt", turn.createdAt)
                    .put(
                        "sources",
                        JSONArray().apply {
                            turn.sources.forEach { source ->
                                put(
                                    JSONObject()
                                        .put("doc", source.documentName)
                                        .put("page", source.page)
                                        .put("snippet", source.snippet),
                                )
                            }
                        },
                    ),
            )
        }
    }

    private fun readTurns(file: File): List<ChatTurn> {
        val array = JSONArray(file.readText())
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val sources = mutableListOf<RagSource>()
                val sourcesJson = json.optJSONArray("sources")
                if (sourcesJson != null) {
                    for (s in 0 until sourcesJson.length()) {
                        val item = sourcesJson.optJSONObject(s) ?: continue
                        sources += RagSource(
                            documentName = item.optString("doc", "Documento"),
                            page = item.optInt("page", 1),
                            snippet = item.optString("snippet", ""),
                        )
                    }
                }
                add(
                    ChatTurn(
                        id = json.optString("id"),
                        role = runCatching { ChatRole.valueOf(json.optString("role", "ASSISTANT")) }
                            .getOrDefault(ChatRole.ASSISTANT),
                        text = json.optString("text", ""),
                        taskId = json.optString("taskId", "").ifBlank { null },
                        model = json.optString("model", ""),
                        usage = TokenUsage(
                            promptTokens = json.optInt("promptTokens", 0),
                            completionTokens = json.optInt("completionTokens", 0),
                        ),
                        sources = sources,
                        elapsedMillis = json.optLong("elapsed", 0L),
                        createdAt = json.optLong("createdAt", 0L),
                    ),
                )
            }
        }
    }

    private companion object {
        const val MAX_TURNS = 80
    }
}
