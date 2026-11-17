package com.nahuanotoca.superapp.core.model

/**
 * Configuración del endpoint compatible con OpenAI que usa el estudio.
 * Por defecto apunta a OpenCode Go (Zen) con DeepSeek V4.1 Flash: barato, rápido y con 1M de
 * contexto, que es lo que hace viable mandar varios PDFs en una sola consulta.
 */
data class RagSettings(
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val apiKey: String = "",
    val topK: Int = 8,
    /** Endpoint de embeddings (opcional). Si está vacío, el estudio usa sólo BM25. */
    val embeddingBaseUrl: String = "",
    val embeddingModel: String = DEFAULT_EMBEDDING_MODEL,
    val embeddingApiKey: String = "",
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()

    /** La búsqueda vectorial es opcional: requiere un proveedor con /embeddings configurado. */
    val embeddingsConfigured: Boolean
        get() = embeddingBaseUrl.isNotBlank() && embeddingModel.isNotBlank() &&
            (embeddingApiKey.isNotBlank() || apiKey.isNotBlank())

    val embeddingEndpoint: String
        get() {
            val base = embeddingBaseUrl.trim().trimEnd('/')
            return when {
                base.isEmpty() -> ""
                base.endsWith("/embeddings") -> base
                else -> base + "/embeddings"
            }
        }

    val embeddingKey: String
        get() = embeddingApiKey.ifBlank { apiKey }

    val chatEndpoint: String
        get() {
            val base = baseUrl.trim().trimEnd('/')
            return if (base.endsWith("/chat/completions")) base else base + "/chat/completions"
        }

    companion object {
        const val DEFAULT_BASE_URL: String = "https://opencode.ai/zen/go/v1"
        const val DEFAULT_MODEL: String = "deepseek-v4.1-flash"
        const val DEFAULT_EMBEDDING_MODEL: String = "text-embedding-3-small"

        /**
         * Modelos sugeridos. Flash primero porque es el más barato para estudiar con muchos PDFs.
         * El campo sigue siendo editable para cualquier endpoint compatible (OpenAI, Ollama, vLLM).
         */
        val MODEL_PRESETS: List<String> = listOf(
            "deepseek-v4.1-flash",
            "deepseek-flash",
            "deepseek-v4-pro",
            "kimi-k2.7-code",
            "glm-5.1",
            "mimo-v2.5-pro",
        )
    }
}

data class RagDocument(
    val id: String,
    val mesaId: String,
    val name: String,
    val pages: Int,
    val chunks: Int,
    val characters: Int,
    val addedAt: Long,
    val figures: Int = 0,
    val sha256: String = "",
    val version: Int = 1,
)

/** Imagen incrustada en un PDF, guardada como JPEG dentro de la mesa. */
data class RagFigure(
    val documentId: String,
    val documentName: String,
    val page: Int,
    val path: String,
)

data class RagChunk(
    val id: String = "",
    val documentId: String,
    val documentName: String,
    val page: Int,
    val text: String,
)

data class RagSource(
    val documentName: String,
    val page: Int,
    val snippet: String,
)

data class RagAnswer(
    val text: String,
    val sources: List<RagSource>,
    val model: String,
    val elapsedMillis: Long,
)