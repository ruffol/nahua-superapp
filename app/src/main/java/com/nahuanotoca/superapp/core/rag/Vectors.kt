package com.nahuanotoca.superapp.core.rag

import com.nahuanotoca.superapp.core.model.RagChunk
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Codificación de vectores a BLOB para Room y utilidades de similitud. Puro Kotlin. */
object VectorCodec {

    fun encode(values: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    fun decode(bytes: ByteArray): FloatArray {
        if (bytes.size < 4) return FloatArray(0)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val values = FloatArray(bytes.size / 4)
        for (index in values.indices) values[index] = buffer.float
        return values
    }

    fun cosine(a: FloatArray, b: FloatArray): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val size = minOf(a.size, b.size)
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (index in 0 until size) {
            dot += a[index] * b[index]
            normA += a[index] * a[index]
            normB += b[index] * b[index]
        }
        if (normA == 0.0 || normB == 0.0) return 0.0
        return dot / (sqrt(normA) * sqrt(normB))
    }
}

/**
 * Fusión híbrida con Reciprocal Rank Fusion: combina el ranking léxico (BM25) y el vectorial sin
 * tener que normalizar puntajes incompatibles. `k` amortigua los primeros puestos.
 */
object HybridRetriever {

    fun fuse(
        query: String,
        chunks: List<RagChunk>,
        vectors: Map<String, FloatArray>,
        queryVector: FloatArray,
        limit: Int,
        k: Int = 60,
    ): List<Retriever.ScoredChunk> {
        if (chunks.isEmpty() || limit <= 0) return emptyList()
        if (vectors.isEmpty() || queryVector.isEmpty()) return Retriever.select(query, chunks, limit)

        val lexical = Retriever.select(query, chunks, limit * 4)
        val vectorScores = chunks
            .mapNotNull { chunk ->
                val vector = vectors[chunk.id] ?: return@mapNotNull null
                val similarity = VectorCodec.cosine(queryVector, vector)
                if (similarity <= 0.0) null else chunk to similarity
            }
            .sortedByDescending { it.second }
            .take(limit * 4)

        if (lexical.isEmpty() && vectorScores.isEmpty()) return emptyList()

        val fused = mutableMapOf<String, Double>()
        val byId = mutableMapOf<String, RagChunk>()

        lexical.forEachIndexed { rank, scored ->
            byId[scored.chunk.id] = scored.chunk
            fused[scored.chunk.id] = (fused[scored.chunk.id] ?: 0.0) + 1.0 / (k + rank + 1)
        }
        vectorScores.forEachIndexed { rank, pair ->
            byId[pair.first.id] = pair.first
            fused[pair.first.id] = (fused[pair.first.id] ?: 0.0) + 1.0 / (k + rank + 1)
        }

        return fused.entries
            .sortedByDescending { it.value }
            .take(limit)
            .mapNotNull { entry ->
                byId[entry.key]?.let { chunk -> Retriever.ScoredChunk(chunk, entry.value) }
            }
    }
}
