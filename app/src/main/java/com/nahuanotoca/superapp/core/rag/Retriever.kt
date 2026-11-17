package com.nahuanotoca.superapp.core.rag

import com.nahuanotoca.superapp.core.model.RagChunk
import java.text.Normalizer
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Recuperación local (BM25 ligero, sin embeddings ni red). Dos cambios importantes frente a la
 * versión que sólo comparaba palabras exactas:
 *
 * 1. Coincidencia por raíz: "obligacion" encuentra "obligaciones", "contamina" encuentra
 *    "contaminación". Sin esto, casi cualquier pregunta del estudiante fallaba.
 * 2. El nombre del documento también puntúa, porque en apuntes el título suele anunciar el tema.
 *
 * Cuando no hay ninguna coincidencia, [ContextBuilder] hace cobertura por documento en lugar de
 * rendirse: es lo que evita el "no encontré pasajes relevantes" con un PDF que sí tiene texto.
 */
object Retriever {

    data class ScoredChunk(val chunk: RagChunk, val score: Double)

    private val STOPWORDS = setOf(
        "de", "la", "el", "los", "las", "un", "una", "unos", "unas", "y", "o", "que", "en",
        "con", "por", "para", "del", "al", "se", "su", "sus", "es", "son", "como", "mas",
        "pero", "este", "esta", "estos", "estas", "lo", "le", "les", "mi", "tu", "nos", "ya",
        "si", "sin", "sobre", "entre", "cuando", "donde", "porque", "muy", "tambien", "hay",
        "fue", "ser", "estar", "cual", "cuales", "quien", "quienes", "cuanto", "cuanta",
        // Muletillas de petición: no describen el contenido del documento.
        "dame", "dime", "hazme", "haz", "hacer", "explica", "explicame", "resume", "resumen",
        "documento", "documentos", "pdf", "texto", "archivo", "puedes", "podrias", "ayuda",
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

    /** Coincidencia exacta o por raíz compartida (plurales, género, derivados). */
    fun matchesTerm(term: String, token: String): Boolean {
        if (term == token) return true
        val shortest = minOf(term.length, token.length)
        if (shortest < 4) return false
        if (token.startsWith(term) || term.startsWith(token)) return true
        return commonPrefix(term, token) >= 5
    }

    fun select(
        query: String,
        chunks: List<RagChunk>,
        limit: Int = 8,
    ): List<ScoredChunk> {
        if (chunks.isEmpty() || limit <= 0) return emptyList()

        val terms = tokenize(query).distinct()
        if (terms.isEmpty()) return emptyList()

        val tokenized = chunks.map { tokenize(it.text) }
        val nameTokens = chunks.map { tokenize(it.documentName) }
        val documentCount = chunks.size.toDouble()
        val documentFrequency = terms.associateWith { term ->
            tokenized.count { tokens -> tokens.any { matchesTerm(term, it) } }.toDouble()
        }

        val queryPhrase = query.trim().lowercase()
        // Pares consecutivos de términos con contenido ("salario doméstico"): si aparecen juntos en
        // el fragmento, suben, porque describen mejor la pregunta que las palabras sueltas.
        val bigrams = terms.zipWithNext()


        val scored = chunks.mapIndexedNotNull { index, chunk ->
            val tokens = tokenized[index]
            if (tokens.isEmpty()) return@mapIndexedNotNull null

            var score = 0.0
            terms.forEach { term ->
                val frequency = tokens.count { matchesTerm(term, it) }.toDouble()
                if (frequency > 0.0) {
                    val idf = ln(1.0 + documentCount / (documentFrequency[term] ?: 1.0))
                    score += (frequency / (frequency + 1.2)) * idf
                }
            }

            nameTokens[index].forEach { nameToken ->
                terms.forEach { term ->
                    if (matchesTerm(term, nameToken)) score += 0.25
                }
            }

            if (score > 0.0) {
                score /= sqrt(tokens.size.toDouble())
                if (queryPhrase.length > 8 && chunk.text.lowercase().contains(queryPhrase)) {
                    score += 0.5
                }
                bigrams.forEach { (first, second) ->
                    val paired = tokens.zipWithNext().any { (left, right) ->
                        matchesTerm(first, left) && matchesTerm(second, right)
                    }
                    if (paired) score += 0.45
                }
            }

            if (score > 0.0) ScoredChunk(chunk, score) else null
        }

        return scored.sortedByDescending { it.score }.take(limit)
    }

    private fun commonPrefix(a: String, b: String): Int {
        val limit = minOf(a.length, b.length)
        var index = 0
        while (index < limit && a[index] == b[index]) index++
        return index
    }
}