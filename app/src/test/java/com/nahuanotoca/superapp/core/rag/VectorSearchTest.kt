package com.nahuanotoca.superapp.core.rag

import com.nahuanotoca.superapp.core.model.RagChunk
import com.nahuanotoca.superapp.core.model.RagSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorSearchTest {

    @Test
    fun encodesAndDecodesVectors() {
        val values = floatArrayOf(0.5f, -1.25f, 0f, 3f)

        val decoded = VectorCodec.decode(VectorCodec.encode(values))

        assertEquals(values.size, decoded.size)
        values.indices.forEach { assertEquals(values[it], decoded[it], 0.0001f) }
    }

    @Test
    fun cosineMeasuresSimilarity() {
        assertEquals(1.0, VectorCodec.cosine(floatArrayOf(1f, 0f), floatArrayOf(1f, 0f)), 0.0001)
        assertEquals(0.0, VectorCodec.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 0.0001)
        assertEquals(0.0, VectorCodec.cosine(floatArrayOf(0f, 0f), floatArrayOf(1f, 0f)), 0.0001)
    }

    @Test
    fun rrfFusesLexicalAndVectorHits() {
        val a = RagChunk(id = "a", documentId = "d", documentName = "Libro", page = 1, text = "comunidad territorio cuerpo")
        val b = RagChunk(id = "b", documentId = "d", documentName = "Libro", page = 2, text = "huelga salario trabajo")
        val c = RagChunk(id = "c", documentId = "d", documentName = "Libro", page = 3, text = "colonialidad patriarcado poder")

        val fused = HybridRetriever.fuse(
            query = "comunidad cuerpo",
            chunks = listOf(a, b, c),
            vectors = mapOf(
                "a" to floatArrayOf(1f, 0f),
                "c" to floatArrayOf(0.6f, 0.8f),
            ),
            queryVector = floatArrayOf(1f, 0f),
            limit = 2,
        )

        assertEquals(2, fused.size)
        assertEquals("a", fused.first().chunk.id)
        assertTrue(fused.any { it.chunk.id == "c" })
    }

    @Test
    fun fusionFallsBackToBm25WithoutVectors() {
        val chunk = RagChunk(id = "a", documentId = "d", documentName = "Libro", page = 1, text = "comunidad")

        val fused = HybridRetriever.fuse("comunidad", listOf(chunk), emptyMap(), floatArrayOf(1f), 3)

        assertEquals(1, fused.size)
    }

    @Test
    fun embeddingsAreOptionalUntilConfigured() {
        val plain = RagSettings(apiKey = "sk-test")
        assertFalse(plain.embeddingsConfigured)
        assertEquals("", plain.embeddingEndpoint)

        val configured = RagSettings(
            apiKey = "sk-test",
            embeddingBaseUrl = "https://api.openai.com/v1",
        )
        assertTrue(configured.embeddingsConfigured)
        assertEquals("https://api.openai.com/v1/embeddings", configured.embeddingEndpoint)
        assertEquals("sk-test", configured.embeddingKey)
    }

    @Test
    fun embeddingEndpointIsNotDuplicated() {
        val settings = RagSettings(
            apiKey = "sk-test",
            embeddingBaseUrl = "https://api.example.com/v1/embeddings",
        )

        assertEquals("https://api.example.com/v1/embeddings", settings.embeddingEndpoint)
    }
}