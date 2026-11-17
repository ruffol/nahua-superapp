package com.nahuanotoca.superapp.core.rag

import com.nahuanotoca.superapp.core.model.RagChunk
import com.nahuanotoca.superapp.core.model.RagSource
import com.nahuanotoca.superapp.core.model.RetrievalResult
import com.nahuanotoca.superapp.core.model.RetrievedEvidence
import kotlin.math.ln

/**
 * Decide qué fragmentos viajan al modelo y con qué evidencia.
 *
 * Tres estrategias:
 * - [MODE_SEARCH]: consulta puntual, BM25 por raíz.
 * - [MODE_COVERAGE]: tarea global, fragmentos repartidos entre todos los libros.
 * - [MODE_BALANCED]: varios libros, los mejores fragmentos de cada uno intercalados.
 *
 * El bloque ya no se recorta a ciegas: se elige la **ventana de oraciones** con mayor densidad de
 * términos de la consulta, se añade el inicio del fragmento siguiente cuando la ventana terminó en
 * el borde, se deduplican solapes y cada fragmento se devuelve como [RetrievedEvidence] con score,
 * página y estrategia.
 */
object ContextBuilder {

    data class Context(
        val blocks: List<String>,
        val sources: List<RagSource>,
        val mode: String,
        val weak: Boolean,
        val documentCount: Int,
        val items: List<RetrievedEvidence> = emptyList(),
    ) {
        val isEmpty: Boolean get() = blocks.isEmpty()

        fun toResult(query: String, indexVersion: String = ""): RetrievalResult = RetrievalResult(
            query = query,
            items = items,
            mode = mode,
            weak = weak,
            indexVersion = indexVersion,
        )
    }

    const val MODE_SEARCH = "busqueda"
    const val MODE_COVERAGE = "cobertura"
    const val MODE_BALANCED = "balanceado"

    private val SENTENCE = Regex("(?<=[.!?…])\\s+|\\n+")
    private val WORD = Regex("\\W+")

    data class Window(val text: String, val reachedEnd: Boolean)

    fun build(
        query: String,
        chunks: List<RagChunk>,
        maxBlocks: Int = 8,
        maxChars: Int = 14_000,
        broad: Boolean = false,
        documentIds: Set<String> = emptySet(),
        perDocument: Int = 0,
        blockChars: Int = 1_200,
        includeNeighbors: Boolean = true,
        deduplicate: Boolean = true,
    ): Context {
        val scoped = if (documentIds.isEmpty()) chunks else chunks.filter { it.documentId in documentIds }
        if (scoped.isEmpty()) return Context(emptyList(), emptyList(), MODE_COVERAGE, true, 0)

        val picked: List<RagChunk>
        val mode: String
        var weak = false

        if (broad) {
            picked = coverage(scoped, maxBlocks)
            mode = MODE_COVERAGE
        } else if (perDocument > 0) {
            val balanced = balanced(scoped, query, perDocument)
            if (balanced.isEmpty()) {
                picked = coverage(scoped, maxBlocks)
                mode = MODE_COVERAGE
                weak = true
            } else {
                picked = balanced
                mode = MODE_BALANCED
            }
        } else {
            val hits = Retriever.select(query, scoped, maxBlocks).map { it.chunk }
            if (hits.isEmpty()) {
                picked = coverage(scoped, maxBlocks)
                mode = MODE_COVERAGE
                weak = true
            } else {
                picked = hits
                mode = MODE_SEARCH
            }
        }

        val terms = Retriever.tokenize(query)
        val items = mutableListOf<RetrievedEvidence>()
        val chosenTexts = mutableListOf<String>()
        val documentsSeen = mutableSetOf<String>()
        val documentTotal = picked.map { it.documentId }.distinct().size
        var budget = 0

        picked.forEach { chunk ->
            // En la primera vuelta se toma una sola ventana por obra: así ninguna queda fuera por
            // presupuesto antes de haber aportado evidencia.
            val allowSecondWindow = documentTotal <= 1 || documentsSeen.size >= documentTotal
            val chunkWindows = windows(chunk.text, blockChars, terms, if (allowSecondWindow) 2 else 1)
            chunkWindows.forEachIndexed { windowIndex, window ->
                if (items.size >= maxBlocks * 2) return@forEachIndexed
                var text = window.text

                if (includeNeighbors && window.reachedEnd) {
                    val position = scoped.indexOf(chunk)
                    val next = scoped.getOrNull(position + 1)
                    if (next != null && next.documentId == chunk.documentId && next.page == chunk.page) {
                        text = text + " … " + next.text.take(280).trim()
                    }
                }

                if (deduplicate && mostlyContained(text, chosenTexts)) return@forEachIndexed

                val blockLength = text.length + chunk.documentName.length + 16
                if (items.isNotEmpty() && budget + blockLength > maxChars) return@forEachIndexed

                documentsSeen += chunk.documentId
                items += RetrievedEvidence(
                    chunkId = chunk.id.ifBlank { fallbackId(chunk) } +
                        (if (windowIndex == 0) "" else "-w" + windowIndex),
                    documentId = chunk.documentId,
                    documentTitle = chunk.documentName,
                    page = chunk.page,
                    text = text,
                    score = scoreOf(query, chunk),
                    strategy = mode,
                )
                chosenTexts += text
                budget += blockLength
            }
        }

        val blocks = items.map { evidence ->
            "[" + labelOf(evidence.documentTitle) + ", p. " + evidence.page + "] " + evidence.text
        }
        val sources = items
            .distinctBy { it.documentId to it.page }
            .map { RagSource(labelOf(it.documentTitle), it.page, it.text.take(180)) }

        return Context(
            blocks = blocks,
            sources = sources,
            mode = mode,
            weak = weak,
            documentCount = items.map { it.documentId }.distinct().size,
            items = items,
        )
    }

    /**
     * Ventanas de oraciones: si el fragmento cabe completo se usa tal cual; si no, se eligen hasta
     * [maxWindows] regiones continuas con mayor densidad de términos (ponderando los raros). Así una
     * página con dos pasajes relevantes lejanos no pierde el segundo por quedarse con una sola ventana.
     */
    fun windows(text: String, maxChars: Int, terms: List<String>, maxWindows: Int = 2): List<Window> {
        if (maxChars <= 0 || text.length <= maxChars) return listOf(Window(text, true))

        val sentences = text.split(SENTENCE).filter { it.isNotBlank() }
        if (sentences.isEmpty()) return listOf(Window(text.take(maxChars).trim(), false))

        val tokens = sentences.map { Retriever.tokenize(it) }
        val realTerms = if (terms.isEmpty()) Retriever.tokenize(text).distinct().take(8) else terms
        if (realTerms.isEmpty()) {
            return listOf(Window(text.take(maxChars).substringBeforeLast(' ').trimEnd(), false))
        }

        val documentFrequency = realTerms.associateWith { term ->
            tokens.count { sentenceTokens -> sentenceTokens.any { Retriever.matchesTerm(term, it) } }
        }
        val weight = realTerms.associateWith { term ->
            val df = documentFrequency[term] ?: 0
            1.0 + ln(sentences.size.toDouble() / maxOf(1, df).toDouble())
        }
        val score = tokens.map { sentenceTokens ->
            realTerms.sumOf { term ->
                if (sentenceTokens.any { Retriever.matchesTerm(term, it) }) weight[term] ?: 1.0 else 0.0
            }
        }

        val used = BooleanArray(sentences.size)
        val result = mutableListOf<Window>()
        repeat(maxWindows.coerceAtLeast(1)) {
            var bestStart = -1
            var bestEnd = -1
            var bestScore = 0.0
            for (start in sentences.indices) {
                if (used[start] || score[start] == 0.0) continue
                var length = 0
                var accumulated = 0.0
                var index = start
                while (index < sentences.size && !used[index]) {
                    val next = sentences[index].length + 1
                    if (length + next > maxChars) break
                    length += next
                    accumulated += score[index]
                    index++
                }
                if (accumulated > bestScore) {
                    bestScore = accumulated
                    bestStart = start
                    bestEnd = index
                }
            }
            if (bestStart < 0) return@repeat
            val textOut = (bestStart until bestEnd).joinToString(" ") { sentences[it].trim() }
            for (index in bestStart until bestEnd) used[index] = true
            result += Window(textOut, bestEnd >= sentences.size)
        }

        return if (result.isEmpty()) listOf(Window(text.take(maxChars).substringBeforeLast(' ').trimEnd(), false)) else result
    }

    /** Primera ventana de [windows]; se conserva para pruebas y usos simples. */
    fun window(text: String, maxChars: Int, terms: List<String>): Window = windows(text, maxChars, terms, 1).first()

    /** Porcentaje de n-gramas del candidato que ya estaban en un texto elegido. */
    fun mostlyContained(candidate: String, chosen: List<String>, threshold: Double = 0.6): Boolean {
        val shingles = shingles(candidate)
        if (shingles.isEmpty()) return false
        return chosen.any { other ->
            val existing = shingles(other)
            if (existing.isEmpty()) false
            else shingles.count { it in existing } >= (shingles.size * threshold).toInt()
        }
    }

    private fun shingles(text: String, size: Int = 6): Set<String> {
        val words = text.lowercase().split(WORD).filter { it.isNotBlank() }
        if (words.size < size) return if (words.isEmpty()) emptySet() else setOf(words.joinToString(" "))
        return (0..words.size - size).map { words.subList(it, it + size).joinToString(" ") }.toSet()
    }

    /** Nombre corto para citar: sin extensión y sin rutas. */
    fun labelOf(name: String): String {
        val trimmed = name.substringAfterLast('/').substringAfterLast('\\')
        val withoutExtension = listOf(".pdf", ".epub").firstOrNull { trimmed.endsWith(it, ignoreCase = true) }
            ?.let { trimmed.dropLast(it.length) }
            ?: trimmed
        return if (withoutExtension.length > 48) withoutExtension.take(48) + "…" else withoutExtension
    }

    private fun scoreOf(query: String, chunk: RagChunk): Double =
        Retriever.select(query, listOf(chunk), 1).firstOrNull()?.score ?: 0.0

    private fun fallbackId(chunk: RagChunk): String =
        chunk.documentId + ":" + chunk.page + ":" + CacheKey.sha256(chunk.text).take(12)

    /**
     * Para cada documento se toman sus [perDocument] fragmentos mejor puntuados y luego se
     * intercalan en rondas: primero el mejor de cada libro, después el segundo, etc.
     */
    fun balanced(chunks: List<RagChunk>, query: String, perDocument: Int): List<RagChunk> {
        if (perDocument <= 0) return emptyList()
        val byDocument = chunks.groupBy { it.documentId }.toSortedMap()
        val ranked = byDocument.map { (_, list) ->
            Retriever.select(query, list, perDocument).map { it.chunk }
        }
        if (ranked.all { it.isEmpty() }) return emptyList()

        val limit = perDocument * ranked.size
        val result = mutableListOf<RagChunk>()
        var round = 0
        while (result.size < limit) {
            var appended = false
            ranked.forEach { list ->
                if (round < list.size) {
                    result += list[round]
                    appended = true
                }
            }
            if (!appended) break
            round++
        }
        return result
    }

    /**
     * Muestreo de cobertura: reparte el presupuesto entre documentos y recorre cada uno de forma
     * uniforme. Devuelve los fragmentos intercalados (uno de cada documento por vuelta).
     */
    fun coverage(chunks: List<RagChunk>, maxBlocks: Int): List<RagChunk> {
        if (chunks.isEmpty() || maxBlocks <= 0) return emptyList()

        val byDocument = chunks.groupBy { it.documentId }.toSortedMap()
        val total = chunks.size.toDouble()

        val quotas = linkedMapOf<String, Int>()
        byDocument.forEach { (id, list) ->
            val share = (maxBlocks * (list.size / total)).toInt().coerceAtLeast(1)
            quotas[id] = share.coerceAtMost(list.size)
        }

        var assigned = quotas.values.sum()
        while (assigned > maxBlocks) {
            val candidate = quotas.entries
                .filter { it.value > 1 }
                .maxByOrNull { it.value }
                ?: break
            quotas[candidate.key] = candidate.value - 1
            assigned--
        }
        var cursor = 0
        val ids = byDocument.keys.toList()
        while (assigned < maxBlocks && ids.isNotEmpty()) {
            val id = ids[cursor % ids.size]
            val size = byDocument.getValue(id).size
            if (quotas.getValue(id) < size) {
                quotas[id] = quotas.getValue(id) + 1
                assigned++
            }
            cursor++
            if (quotas.values.sum() >= byDocument.values.sumOf { it.size }) break
        }

        val perDocument = ids.associateWith { id ->
            val list = byDocument.getValue(id)
            val quota = (quotas[id] ?: 1).coerceIn(1, list.size)
            if (quota >= list.size) {
                list
            } else {
                (0 until quota).map { index ->
                    val position = ((index + 0.5) * list.size / quota).toInt().coerceIn(0, list.size - 1)
                    list[position]
                }
            }
        }

        val result = mutableListOf<RagChunk>()
        val indexes = ids.associateWith { 0 }.toMutableMap()
        var guard = 0
        while (result.size < maxBlocks && guard < maxBlocks * ids.size + 8) {
            var appended = false
            ids.forEach { id ->
                if (result.size >= maxBlocks) return@forEach
                val list = perDocument.getValue(id)
                val index = indexes.getValue(id)
                if (index < list.size) {
                    result += list[index]
                    indexes[id] = index + 1
                    appended = true
                }
            }
            if (!appended) break
            guard++
        }
        return result
    }
}