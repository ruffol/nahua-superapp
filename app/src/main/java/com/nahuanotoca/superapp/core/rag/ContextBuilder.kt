package com.nahuanotoca.superapp.core.rag

import com.nahuanotoca.superapp.core.model.RagChunk
import com.nahuanotoca.superapp.core.model.RagSource

/**
 * Decide qué fragmentos viajan al modelo. Es el corazón de que un PDF de 8 páginas "sirva":
 *
 * - Pregunta puntual ("¿cuándo se paga la renta?") -> búsqueda BM25 por término.
 * - Trabajo global ("resumen", "correlaciona", "ideas") -> cobertura: se toman fragmentos
 *   repartidos de TODOS los documentos, en orden round-robin, para que la mesa entera quepa en el
 *   contexto en vez de depender de palabras clave que el estudiante no conoce.
 * - Si la búsqueda no encuentra nada, NO se rinde: cae a cobertura y marca weak=true para que el
 *   modelo confiese que está leyendo lo más cercano en vez de inventar.
 */
object ContextBuilder {

    data class Context(
        val blocks: List<String>,
        val sources: List<RagSource>,
        val mode: String,
        val weak: Boolean,
        val documentCount: Int,
    ) {
        val isEmpty: Boolean get() = blocks.isEmpty()
    }

    const val MODE_SEARCH = "busqueda"
    const val MODE_COVERAGE = "cobertura"
    const val MODE_BALANCED = "balanceado"

    fun build(
        query: String,
        chunks: List<RagChunk>,
        maxBlocks: Int = 8,
        maxChars: Int = 14_000,
        broad: Boolean = false,
        documentIds: Set<String> = emptySet(),
        perDocument: Int = 0,
        blockChars: Int = 1_200,
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
            // Búsqueda balanceada: los N mejores fragmentos de CADA libro, para no dejar fuera
            // ninguna obra cuando se cruzan varios documentos.
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

        // Presupuesto de caracteres: nunca se rebasa aunque haya muchos fragmentos.
        val blocks = mutableListOf<String>()
        val used = mutableListOf<RagChunk>()
        var budget = 0
        picked.forEach { chunk ->
            val body = if (chunk.text.length > blockChars) {
                chunk.text.take(blockChars).substringBeforeLast(' ') + "…"
            } else {
                chunk.text
            }
            val block = "[" + chunk.documentName + ", p. " + chunk.page + "] " + body
            if (blocks.isNotEmpty() && budget + block.length > maxChars) return@forEach
            blocks += block
            used += chunk
            budget += block.length
        }

        val sources = used
            .distinctBy { it.documentId to it.page }
            .map { RagSource(it.documentName, it.page, it.text.take(180)) }

        return Context(
            blocks = blocks,
            sources = sources,
            mode = mode,
            weak = weak,
            documentCount = used.map { it.documentId }.distinct().size,
        )
    }

    /**
     * Para cada documento se toman sus [perDocument] fragmentos mejor puntuados y luego se
     * intercalan en rondas: primero el mejor de cada libro, después el segundo, etc. Así una obra
     * larga no acapara el contexto y la correlación ve todas las fuentes.
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
     * uniforme. Devuelve los fragmentos intercalados (uno de cada documento por vuelta) para que el
     * modelo vea la mesa completa y no sólo el primer PDF.
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

        // Ajusta el presupuesto: recorta de los que tienen más y reparte entre los que tienen menos.
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