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
                    throw ChatException("El modelo respondió sin contenido. Revisa el modelo configurado.")
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