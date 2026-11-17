package com.nahuanotoca.superapp.core.rag

import com.nahuanotoca.superapp.core.model.RagChunk
import com.nahuanotoca.superapp.core.model.StudioTasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComparisonQualityTest {

    private fun chunk(document: String, page: Int, text: String): RagChunk = RagChunk(
        documentId = "id_" + document,
        documentName = document + ".pdf",
        page = page,
        text = text,
    )

    private val works = listOf(
        "Cabnal" to "El territorio cuerpo-tierra se defiende desde la comunidad y el Ayni.",
        "Paredes" to "La comunidad como cuerpo político transformador y la despatriarcalización.",
        "Segato" to "La pedagogía de la crueldad sostiene la guerra contra las mujeres.",
        "FeminismoDescolonial" to "La colonialidad y el patriarcado moderno articulan el poder.",
        "Federici" to "El salario doméstico y la huelga contra el trabajo reproductivo.",
    )

    @Test
    fun correlationKeepsEveryWorkInTheContext() {
        val chunks = works.flatMap { (name, text) ->
            (1..4).map { page -> chunk(name, page, text + " Pasaje " + page + ".") }
        }

        val context = ContextBuilder.build(
            query = "Compara las obras de esta mesa",
            chunks = chunks,
            broad = true,
            maxBlocks = 12,
            perDocument = 2,
            maxChars = 16_000,
            blockChars = 1_000,
        )

        assertEquals(5, context.documentCount)
        works.forEach { (name, _) ->
            assertTrue(
                "falta " + name,
                context.items.any { it.documentTitle.startsWith(name) },
            )
        }
    }

    @Test
    fun firstRoundTakesOneWindowBeforeSecondWindows() {
        val chunks = works.flatMap { (name, text) ->
            (1..4).map { page -> chunk(name, page, text + " Pasaje " + page + ".") }
        }

        val context = ContextBuilder.build(
            query = "comunidad territorio crueldad salario colonialidad",
            chunks = chunks,
            broad = true,
            maxBlocks = 12,
            perDocument = 2,
            maxChars = 16_000,
            blockChars = 120,
        )

        val firstFive = context.items.take(5).map { it.documentTitle }
        assertEquals(5, firstFive.distinct().size)
    }

    @Test
    fun labelsDropTheFileExtension() {
        assertEquals("Cabnal 2010", ContextBuilder.labelOf("Cabnal 2010.pdf"))
        assertEquals("Libro", ContextBuilder.labelOf("ruta/Libro.epub"))
        assertEquals("Sin extension", ContextBuilder.labelOf("Sin extension"))
    }

    @Test
    fun correlationTaskDemandsATableAndExplicitSilence() {
        val instruction = StudioTasks.find(StudioTasks.CORRELATE)?.instruction.orEmpty()

        assertTrue(instruction.contains("tabla"))
        assertTrue(instruction.contains("no se pronuncia"))
        assertTrue(instruction.contains("NO resumas"))
    }
}
