package com.nahuanotoca.superapp.core.rag

import com.nahuanotoca.superapp.core.model.RagSettings
import com.nahuanotoca.superapp.core.model.TokenUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class ChatException(message: String, val statusCode: Int? = null) : IOException(message)

data class ChatMessage(val role: String, val content: String) {
    companion object {
        fun system(content: String): ChatMessage = ChatMessage("system", content)
        fun user(content: String): ChatMessage = ChatMessage("user", content)
        fun assistant(content: String): ChatMessage = ChatMessage("assistant", content)
    }
}

/** Respuesta del proveedor más el consumo real de tokens que reporta el campo `usage`. */
data class ChatReply(
    val text: String,
    val usage: TokenUsage,
    val model: String,
)

/**
 * Cliente mínimo compatible con OpenAI (funciona con OpenCode Go/Zen, OpenAI, OpenRouter, Ollama,
 * vLLM...). Sin SDK y sin streaming: una petición por turno. Devuelve el `usage` del proveedor para
 * poder mostrar cuántos tokens cuesta cada resumen.
 */
class ChatClient {

    /** Identificador de sesión propio, para proxies que lo pidan. Nunca contiene la API key. */
    private val sessionId: String = UUID.randomUUID().toString()

    /** Conexión en curso, para poder cortarla cuando el usuario pulsa DETENER. */
    @Volatile
    private var activeConnection: HttpURLConnection? = null

    private companion object {
        const val MAX_TOKENS_CAP = 16_000
    }

    /** Corta la petición en curso. El hilo de lectura recibe un IOException y termina. */
    fun cancelActive() {
        runCatching { activeConnection?.disconnect() }
        activeConnection = null
    }

    suspend fun complete(
        settings: RagSettings,
        messages: List<ChatMessage>,
        temperature: Double = 0.3,
        maxTokens: Int = 2400,
    ): Result<ChatReply> = withContext(Dispatchers.IO) {
        runCatching {
            require(messages.isNotEmpty()) { "La conversación está vacía" }

            val payload = JSONObject().apply {
                put("model", settings.model)
                put("temperature", temperature)
                put("max_tokens", maxTokens)
                put("stream", false)
                put(
                    "messages",
                    JSONArray().apply {
                        messages.forEach { message ->
                            put(JSONObject().put("role", message.role).put("content", message.content))
                        }
                    },
                )
            }

            val connection = openPost(settings)

            try {
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

                var status = connection.responseCode
                var stream = if (status in 200..299) connection.inputStream else connection.errorStream
                var body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

                // Si el proveedor rechaza con 400 debido a parámetros opcionales como max_tokens o temperature:
                if (status == 400 && (body.contains("max_tokens", ignoreCase = true) || body.contains("temperature", ignoreCase = true) || body.contains("unrecognized", ignoreCase = true))) {
                    val fallbackPayload = JSONObject().apply {
                        put("model", settings.model)
                        put("stream", false)
                        put(
                            "messages",
                            JSONArray().apply {
                                messages.forEach { message ->
                                    put(JSONObject().put("role", message.role).put("content", message.content))
                                }
                            },
                        )
                    }
                    val retryConn = openPost(settings)
                    try {
                        retryConn.outputStream.use { it.write(fallbackPayload.toString().toByteArray(Charsets.UTF_8)) }
                        status = retryConn.responseCode
                        stream = if (status in 200..299) retryConn.inputStream else retryConn.errorStream
                        body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    } finally {
                        retryConn.disconnect()
                    }
                }

                if (status !in 200..299) {
                    throw ChatException(describeError(status, body), status)
                }

                // Un proxy o una base URL equivocada devuelve HTML con status 200: mejor avisar que
                // dejar caer la excepción de JSON, porque el usuario no sabría qué corregir.
                if (!body.trimStart().startsWith("{")) {
                    throw ChatException(
                        "La API respondió algo que no es JSON. Revisa la Base URL: debe ser el endpoint " +
                            "compatible con OpenAI, normalmente terminando en /v1.",
                    )
                }

                val json = JSONObject(body)
                val content = json.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()

                if (content.isBlank()) {
                    val finish = json.optJSONArray("choices")?.optJSONObject(0)
                        ?.optString("finish_reason").orEmpty()
                    val reasoning = json.optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("message")?.optString("reasoning_content").orEmpty()

                    // Modelos de razonamiento (p. ej. deepseek-v4.1-flash) gastan el presupuesto
                    // pensando y devuelven content vacío con finish_reason=length. Se reintenta una
                    // vez con el doble de espacio en lugar de mostrar una respuesta en blanco.
                    if (finish == "length" && reasoning.isNotBlank() && maxTokens < MAX_TOKENS_CAP) {
                        val retry = complete(
                            settings = settings,
                            messages = messages,
                            temperature = temperature,
                            maxTokens = (maxTokens * 2).coerceAtMost(MAX_TOKENS_CAP),
                        )
                        return@runCatching retry.getOrThrow()
                    }
                    throw ChatException(
                        if (reasoning.isNotBlank()) {
                            "El modelo razonó pero no llegó a responder: se quedó sin presupuesto. " +
                                "Sube los tokens o usa un modelo sin razonamiento (por ejemplo " +
                                "claude-haiku-5-5)."
                        } else {
                            "El modelo respondió sin contenido. Revisa el modelo configurado."
                        },
                    )
                }

                ChatReply(
                    text = content.trim(),
                    usage = parseUsage(json, messages, content),
                    model = json.optString("model").ifBlank { settings.model },
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    /**
     * Igual que [complete] pero con "stream": true. Cada trozo de texto se entrega a [onDelta] en
     * cuanto llega, así el usuario ve la respuesta escribirse en vez de esperar 15 segundos.
     *
     * Si el proveedor ignora el streaming y devuelve un JSON completo, se detecta y se usa igual.
     */
    suspend fun completeStreaming(
        settings: RagSettings,
        messages: List<ChatMessage>,
        temperature: Double = 0.3,
        maxTokens: Int = 2400,
        onDelta: (String) -> Unit,
    ): Result<ChatReply> = withContext(Dispatchers.IO) {
        runCatching {
            require(messages.isNotEmpty()) { "La conversación está vacía" }

            val payload = JSONObject().apply {
                put("model", settings.model)
                put("temperature", temperature)
                put("max_tokens", maxTokens)
                put("stream", true)
                put(
                    "messages",
                    JSONArray().apply {
                        messages.forEach { message ->
                            put(JSONObject().put("role", message.role).put("content", message.content))
                        }
                    },
                )
            }

            val connection = openPost(settings)
            activeConnection = connection
            try {
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

                val status = connection.responseCode
                if (status !in 200..299) {
                    val body = connection.errorStream?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        .orEmpty()
                    throw ChatException(describeError(status, body), status)
                }

                val collected = StringBuilder()
                var usage = TokenUsage.Empty
                var modelName = settings.model
                var sawEvent = false

                connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) continue

                        if (!trimmed.startsWith("data:")) {
                            if (!sawEvent && trimmed.startsWith("{")) {
                                val json = runCatching { JSONObject(trimmed) }.getOrNull()
                                if (json != null) {
                                    val content = json.optJSONArray("choices")
                                        ?.optJSONObject(0)
                                        ?.optJSONObject("message")
                                        ?.optString("content")
                                        .orEmpty()
                                    if (content.isNotBlank()) {
                                        collected.append(content)
                                        onDelta(content)
                                    }
                                    usage = parseUsage(json, messages, content)
                                    modelName = json.optString("model").ifBlank { modelName }
                                }
                            }
                            continue
                        }

                        sawEvent = true
                        val data = trimmed.removePrefix("data:").trim()
                        if (data == "[DONE]") break

                        val json = runCatching { JSONObject(data) }.getOrNull() ?: continue
                        if (json.has("model")) {
                            modelName = json.optString("model").ifBlank { modelName }
                        }
                        json.optJSONObject("usage")?.let { reported ->
                            usage = TokenUsage(
                                promptTokens = reported.optInt("prompt_tokens", 0),
                                completionTokens = reported.optInt("completion_tokens", 0),
                            )
                        }
                        val delta = json.optJSONArray("choices")
                            ?.optJSONObject(0)
                            ?.optJSONObject("delta")
                            ?.optString("content")
                            .orEmpty()
                        if (delta.isNotEmpty()) {
                            collected.append(delta)
                            onDelta(delta)
                        }
                    }
                }

                val text = collected.toString().trim()
                if (text.isBlank()) {
                    // Mismo caso que en [complete]: el modelo pensó y no escribió. Se reintenta sin
                    // streaming con más presupuesto para no dejar al estudiante con una tarjeta vacía.
                    if (maxTokens < MAX_TOKENS_CAP) {
                        val retry = complete(
                            settings = settings,
                            messages = messages,
                            temperature = temperature,
                            maxTokens = (maxTokens * 2).coerceAtMost(MAX_TOKENS_CAP),
                        )
                        return@runCatching retry.getOrThrow()
                    }
                    throw ChatException(
                        "El modelo razonó pero no llegó a responder. Usa un modelo sin razonamiento " +
                            "o sube los tokens.",
                    )
                }
                val finalUsage = if (usage.totalTokens > 0) {
                    usage
                } else {
                    TokenUsage(
                        promptTokens = estimate(messages.joinToString(" ") { it.content }),
                        completionTokens = estimate(text),
                    )
                }
                ChatReply(text = text, usage = finalUsage, model = modelName)
            } finally {
                if (activeConnection === connection) activeConnection = null
                connection.disconnect()
            }
        }
    }

    /**
     * Abre una petición POST contra el endpoint configurado. Las cabeceras de sesión llevan un id
     * aleatorio por proceso: nunca se manda material de la API key fuera de Authorization.
     */
    private fun openPost(settings: RagSettings): HttpURLConnection =
        (URL(settings.chatEndpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 180_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer " + settings.apiKey.trim())
            setRequestProperty("X-OpenCode-Session", sessionId)
            setRequestProperty("X-OpenCode-Session-Id", sessionId)
            setRequestProperty("X-Session-ID", sessionId)
        }

    /**
     * Algunos proveedores no devuelven `usage` (o lo dejan en cero). En ese caso se estima con la
     * regla de ~4 caracteres por token para que el medidor de la mesa nunca mienta por omisión.
     */
    private fun parseUsage(json: JSONObject, messages: List<ChatMessage>, content: String): TokenUsage {
        val usage = json.optJSONObject("usage")
        val prompt = usage?.optInt("prompt_tokens", 0) ?: 0
        val completion = usage?.optInt("completion_tokens", 0) ?: 0
        if (prompt > 0 || completion > 0) {
            return TokenUsage(
                promptTokens = if (prompt > 0) prompt else estimate(messages.joinToString(" ") { it.content }),
                completionTokens = if (completion > 0) completion else estimate(content),
            )
        }
        return TokenUsage(
            promptTokens = estimate(messages.joinToString(" ") { it.content }),
            completionTokens = estimate(content),
        )
    }

    private fun estimate(text: String): Int = (text.length / 4).coerceAtLeast(1)

    private fun describeError(status: Int, body: String): String {
        val jsonError = runCatching { JSONObject(body) }.getOrNull()
        val apiMessage = jsonError?.optJSONObject("error")?.optString("message")
            ?: jsonError?.optString("message")
            ?: jsonError?.optString("error")
            ?: body.take(180)

        val hint = when (status) {
            400 -> if (apiMessage.isNotBlank()) "Error 400 de la API: $apiMessage" else "La petición fue rechazada. Revisa el modelo o los parámetros enviados."
            401, 403 -> "API key inválida o sin permisos."
            404 -> "Endpoint o modelo no encontrado. Revisa la URL base y el modelo."
            413 -> "El contexto es demasiado grande para ese modelo. Usa un modelo flash o reduce los documentos."
            429 -> "Límite de uso alcanzado. Intenta de nuevo en un momento o cambia a deepseek-v4.1-flash."
            in 500..599 -> "El proveedor tuvo un error interno."
            else -> "La API rechazó la solicitud."
        }
        return if (apiMessage.isBlank() || hint.startsWith("Error 400")) hint else "$hint ($apiMessage)"
    }
}