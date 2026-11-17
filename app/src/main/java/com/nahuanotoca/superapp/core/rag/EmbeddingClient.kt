package com.nahuanotoca.superapp.core.rag

import com.nahuanotoca.superapp.core.model.RagSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente del endpoint `/embeddings` (compatible con OpenAI). Se usa sólo si el usuario configuró
 * un proveedor de embeddings; si falla, el estudio sigue funcionando con BM25.
 */
class EmbeddingClient {

    suspend fun embed(settings: RagSettings, texts: List<String>): Result<List<FloatArray>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(texts.isNotEmpty()) { "No hay textos que vectorizar" }
                require(settings.embeddingsConfigured) { "Configura el endpoint de embeddings" }

                val payload = JSONObject()
                    .put("model", settings.embeddingModel)
                    .put("input", JSONArray(texts))

                val connection = (URL(settings.embeddingEndpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20_000
                    readTimeout = 120_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Authorization", "Bearer " + settings.embeddingKey)
                }

                try {
                    connection.outputStream.use { out ->
                        out.write(payload.toString().toByteArray(Charsets.UTF_8))
                    }

                    val status = connection.responseCode
                    val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        .orEmpty()

                    if (status !in 200..299) {
                        throw ChatException(describeError(status, body), status)
                    }

                    val data = JSONObject(body).optJSONArray("data")
                        ?: throw ChatException("El proveedor no devolvió embeddings")

                    val vectors = arrayOfNulls<FloatArray>(texts.size)
                    for (index in 0 until data.length()) {
                        val item = data.optJSONObject(index) ?: continue
                        val position = item.optInt("index", index).coerceIn(0, texts.size - 1)
                        val embedding = item.optJSONArray("embedding") ?: continue
                        val values = FloatArray(embedding.length()) { offset ->
                            embedding.optDouble(offset, 0.0).toFloat()
                        }
                        vectors[position] = values
                    }

                    val missing = vectors.indexOfFirst { it == null }
                    if (missing >= 0) {
                        throw ChatException("Faltan vectores para " + (vectors.size - missing) + " fragmentos")
                    }
                    vectors.map { it ?: FloatArray(0) }
                } finally {
                    connection.disconnect()
                }
            }
        }

    private fun describeError(status: Int, body: String): String {
        val message = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull().orEmpty()

        val hint = when (status) {
            401, 403 -> "API key de embeddings inválida o sin permisos."
            404 -> "Endpoint o modelo de embeddings no encontrado."
            429 -> "Límite del proveedor de embeddings."
            in 500..599 -> "El proveedor de embeddings falló."
            else -> "El proveedor de embeddings rechazó la solicitud."
        }
        return if (message.isBlank()) hint else hint + " (" + message.take(160) + ")"
    }
}
