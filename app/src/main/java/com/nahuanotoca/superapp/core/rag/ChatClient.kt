package com.nahuanotoca.superapp.core.rag

import com.nahuanotoca.superapp.core.model.RagSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ChatException(message: String, val statusCode: Int? = null) : IOException(message)

/**
 * Minimal OpenAI-compatible chat client (works with OpenCode Go/Zen, OpenRouter, Ollama, vLLM...).
 * No SDK, no streaming: one request per question keeps the dependency surface small.
 */
class ChatClient {

    suspend fun ask(
        settings: RagSettings,
        question: String,
        contextBlocks: List<String>,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("model", settings.model)
                put("temperature", 0.2)
                put("stream", false)
                put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                        .put(
                            JSONObject()
                                .put("role", "user")
                                .put("content", buildUserPrompt(question, contextBlocks)),
                        ),
                )
            }

            val connection = (URL(settings.chatEndpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 90_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer " + settings.apiKey.trim())
            }

            try {
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

                if (status !in 200..299) {
                    throw ChatException(describeError(status, body), status)
                }

                val content = JSONObject(body)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()

                if (content.isBlank()) {
                    throw ChatException("La API respondió sin contenido. Revisa el modelo configurado.")
                }
                content.trim()
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun buildUserPrompt(question: String, blocks: List<String>): String = buildString {
        appendLine("Contexto extraído de mis documentos:")
        blocks.forEachIndexed { index, block ->
            appendLine()
            appendLine("[Fragmento " + (index + 1) + "]")
            appendLine(block)
        }
        appendLine()
        appendLine("Pregunta: " + question)
        appendLine()
        append("Responde en español, citando [Documento, p. N] cuando uses el contexto.")
    }

    private fun describeError(status: Int, body: String): String {
        val apiMessage = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull().orEmpty()

        val hint = when (status) {
            401, 403 -> "API key inválida o sin permisos."
            404 -> "Endpoint o modelo no encontrado. Revisa la URL base y el modelo."
            429 -> "Límite de uso alcanzado. Intenta de nuevo en un momento."
            in 500..599 -> "El proveedor tuvo un error interno."
            else -> "La API rechazó la solicitud."
        }
        return if (apiMessage.isBlank()) hint else hint + " (" + apiMessage.take(160) + ")"
    }

    private companion object {
        const val SYSTEM_PROMPT: String =
            "Eres Nahuanotoka, un asistente que responde en español usando únicamente el contexto " +
                "entregado. Si la respuesta no está en el contexto, dilo con claridad y sugiere qué " +
                "documento revisar. No inventes datos, cifras ni citas. Sé breve y concreto."
    }
}