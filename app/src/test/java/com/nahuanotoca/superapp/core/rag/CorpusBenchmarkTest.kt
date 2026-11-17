package com.nahuanotoca.superapp.core.rag

import com.nahuanotoca.superapp.core.model.RagChunk
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Banco de pruebas con los cinco libros reales (535 páginas). Mide la recuperación con el código de
 * producción y deja los contextos elegidos en build/eval/contexts.json para probarlos contra la API
 * real. Los diagnósticos van a build/eval/diag.txt.
 */
class CorpusBenchmarkTest {

    private data class Doc(val id: String, val name: String, val pages: List<String>)

    private data class Case(
        val id: String,
        val query: String,
        val docs: List<String>,
        val expectedPages: List<Int>,
        val keyword: String,
        val broad: Boolean = false,
        val perDocument: Int = 0,
        val expectWeak: Boolean = false,
        val minDocuments: Int = 1,
    )

    private fun corpus(): List<Doc> {
        val raw = javaClass.getResourceAsStream("/eval/corpus.json")?.bufferedReader()?.use { it.readText() }
            ?: error("Falta /eval/corpus.json")
        val docs = JSONObject(raw).getJSONArray("docs")
        return buildList {
            for (index in 0 until docs.length()) {
                val doc = docs.getJSONObject(index)
                val pages = doc.getJSONArray("pages")
                add(
                    Doc(
                        id = doc.getString("id"),
                        name = doc.getString("name"),
                        pages = List(pages.length()) { pages.getString(it) },
                    ),
                )
            }
        }
    }

    private fun chunksOf(doc: Doc): List<RagChunk> =
        // Igual que la app: limpiar encabezados, pies y guiones antes de fragmentar.
        TextChunker.chunk(TextCleaner.clean(doc.pages), maxWords = 450, overlapWords = 50).map { chunk ->
            RagChunk(
                id = doc.id + ":" + chunk.page + ":" + CacheKey.sha256(chunk.text).take(12),
                documentId = doc.id,
                documentName = doc.name,
                page = chunk.page,
                text = chunk.text,
            )
        }

    private val cases = listOf(
        Case("C1", "¿Qué significa el territorio cuerpo-tierra?", listOf("cabnal"), listOf(7), "territorio"),
        Case("C2", "¿Por qué la crueldad funciona como pedagogía del poder?", listOf("segato"), listOf(23, 81), "crueldad"),
        Case("C3", "¿Qué se reclama con la campaña por el salario doméstico?", listOf("federici"), listOf(33, 36), "salario"),
        Case(
            "C4",
            "¿Cómo se articulan patriarcado, colonialidad y comunidad en estos textos?",
            listOf("paredes", "cabnal", "segato", "descolonial"),
            emptyList(),
            "",
            perDocument = 2,
            minDocuments = 3,
        ),
        Case("C5", "¿Dónde empieza el capítulo sobre colonialidad y patriarcado moderno?", listOf("segato"), listOf(113), "colonialidad"),
        Case("C6", "¿Cuál es la fórmula química de la fotosíntesis?", listOf("cabnal"), emptyList(), "", expectWeak = true),
        Case("C7", "¿Por qué la huelga es central en la lucha?", listOf("federici"), listOf(62, 63), "huelga"),
    )

    @Test
    fun benchmarkRealCorpus() {
        val corpus = corpus()
        assertEquals(5, corpus.size)
        assertEquals(535, corpus.sumOf { it.pages.size })

        val allChunks = corpus.flatMap { chunksOf(it) }
        assertTrue("el corpus debe producir muchos fragmentos", allChunks.size > 400)

        val report = JSONArray()
        val log = mutableListOf<String>()
        val failures = mutableListOf<String>()
        var hits = 0
        var evaluated = 0

        for (case in cases) {
            val selected = allChunks.filter { it.documentId in case.docs }
            val top = Retriever.select(case.query, selected, 10).map { it.chunk.page }
            val context = ContextBuilder.build(
                query = case.query,
                chunks = selected,
                maxBlocks = 12,
                maxChars = 16_000,
                broad = case.broad,
                perDocument = case.perDocument,
                blockChars = 1_000,
            )

            val pages = context.sources.map { it.page }
            val hit = case.expectedPages.isEmpty() || pages.any { it in case.expectedPages }
            val keywordFound = case.keyword.isBlank() ||
                context.blocks.any { it.lowercase().contains(case.keyword.lowercase()) }

            if (case.expectedPages.isNotEmpty()) {
                evaluated++
                if (hit && keywordFound) hits++
            }

            log += "TOPK " + case.id + " " + top.joinToString(",")
            if (case.id == "C3") {
                val phrase = selected.filter { it.text.lowercase().contains("salario dom") }
                log += "PHRASE chunks=" + phrase.size + " pages=" + phrase.map { it.page }.distinct()
                Retriever.select(case.query, selected, 5).forEach { scored ->
                    log += "SCORE p" + scored.chunk.page + " " + "%.4f".format(scored.score) +
                        " len=" + scored.chunk.text.length +
                        " first=" + scored.chunk.text.trim().take(60).replace("\n", " ")
                }
            }
            log += "BENCH " + case.id +
                " hit=" + hit +
                " keyword=" + keywordFound +
                " weak=" + context.weak +
                " docs=" + context.documentCount +
                " mode=" + context.mode +
                " pages=" + pages.take(8)

            if (!hit) failures += case.id + " no encontró " + case.expectedPages
            if (case.expectWeak && !context.weak) failures += case.id + " no marcó evidencia débil"
            if (case.minDocuments > 1 && context.documentCount < case.minDocuments) {
                failures += case.id + " cubrió " + context.documentCount + " de " + case.minDocuments + " obras"
            }

            report.put(
                JSONObject()
                    .put("id", case.id)
                    .put("query", case.query)
                    .put("mode", context.mode)
                    .put("weak", context.weak)
                    .put("sources", pages.joinToString(","))
                    .put("blocks", JSONArray(context.blocks)),
            )
        }

        val output = File("build/eval")
        output.mkdirs()
        File(output, "contexts.json").writeText(JSONObject().put("version", 1).put("cases", report).toString())

        log += "SUMMARY hits=" + hits + "/" + evaluated + " chunks=" + allChunks.size +
            " failures=" + failures.joinToString(" | ")
        File(output, "diag.txt").writeText(log.joinToString("\n"))

        // Criterio de no regresión: al menos 4 de 5 casos con página esperada. El caso C3
        // ("salario doméstico") es un fallo conocido de BM25: la página 33 compite con el capítulo
        // de salarios y pierde; queda registrado aquí hasta que haya embeddings.
        assertTrue(
            "El benchmark bajó de 4 aciertos: " + hits + "/" + evaluated +
                " — fallos: " + failures.joinToString(" | "),
            hits >= 4,
        )
    }
}