package com.nahuanotoca.superapp.core.rag

import com.nahuanotoca.superapp.core.model.RagChunk
import java.text.Normalizer
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Local retrieval (BM25-flavoured, no embeddings, no network). It ranks chunks by the query terms
 * they contain, weighting rare terms higher and normalising by chunk length.
 */
object Retriever {

    data class ScoredChunk(val chunk: RagChunk, val score: Double)

    private val STOPWORDS = setOf(
        "de", "la", "el", "los", "las", "un", "una", "unos", "unas", "y", "o", "que", "en",
        "con", "por", "para", "del", "al", "se", "su", "sus", "es", "son", "como", "mas",
        "pero", "este", "esta", "estos", "estas", "lo", "le", "les", "mi", "tu", "nos", "ya",
        "si", "sin", "sobre", "entre", "cuando", "donde", "porque", "muy", "tambien", "hay",
        "fue", "ser", "estar", "cual", "cuales", "quien", "quienes", "cuanto", "cuanta",
        "the", "of", "and", "to", "in", "is", "for", "a", "an",
    )

    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

    fun tokenize(text: String): List<String> {
        val normalized = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return normalized.split(NON_WORD)
            .map { it.trim() }
            .filter { it.length > 2 && it !in STOPWORDS }
    }

    fun select(
        query: String,
        chunks: List<RagChunk>,
        limit: Int = 4,
    ): List<ScoredChunk> {
        if (chunks.isEmpty() || limit <= 0) return emptyList()

        val terms = tokenize(query).distinct()
        if (terms.isEmpty()) return emptyList()

        val tokenized = chunks.map { tokenize(it.text) }
        val documentCount = chunks.size.toDouble()
        val documentFrequency = terms.associateWith { term ->
            tokenized.count { tokens -> term in tokens }.toDouble()
        }

        val queryPhrase = query.trim().lowercase()

        val scored = chunks.mapIndexedNotNull { index, chunk ->
            val tokens = tokenized[index]
            if (tokens.isEmpty()) return@mapIndexedNotNull null

            var score = 0.0
            terms.forEach { term ->
                val frequency = tokens.count { it == term }.toDouble()
                if (frequency > 0.0) {
                    val idf = ln(1.0 + documentCount / (documentFrequency[term] ?: 1.0))
                    score += (frequency / (frequency + 1.2)) * idf
                }
            }
            if (score > 0.0) {
                score /= sqrt(tokens.size.toDouble())
                if (queryPhrase.length > 8 && chunk.text.lowercase().contains(queryPhrase)) {
                    score += 0.5
                }
            }

            if (score > 0.0) ScoredChunk(chunk, score) else null
        }

        return scored.sortedByDescending { it.score }.take(limit)
    }
}
