package com.nahuanotoca.superapp.core.model

/**
 * Configuration for the OpenAI-compatible endpoint used by the RAG feature.
 * Defaults target OpenCode Go (Zen subscription), but any compatible API works.
 */
data class RagSettings(
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val apiKey: String = "",
    val topK: Int = 4,
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()

    val chatEndpoint: String
        get() {
            val base = baseUrl.trim().trimEnd('/')
            return if (base.endsWith("/chat/completions")) base else base + "/chat/completions"
        }

    companion object {
        const val DEFAULT_BASE_URL: String = "https://opencode.ai/zen/go/v1"
        const val DEFAULT_MODEL: String = "deepseek-v4-pro"

        val MODEL_PRESETS: List<String> = listOf(
            "deepseek-v4-pro",
            "kimi-k2.7-code",
            "glm-5.1",
            "mimo-v2.5-pro",
        )
    }
}

data class RagDocument(
    val id: String,
    val name: String,
    val pages: Int,
    val chunks: Int,
    val characters: Int,
    val addedAt: Long,
)

data class RagChunk(
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
