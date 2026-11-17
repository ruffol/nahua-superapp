package com.nahuanotoca.superapp.core.rag

import com.nahuanotoca.superapp.core.model.RagChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextWindowTest {

    private fun chunk(document: String, page: Int, text: String, id: String = "c-" + document + "-" + page): RagChunk =
        RagChunk(id = id, documentId = document, documentName = document, page = page, text = text)

    private val filler = "Relleno introductorio sin terminos utiles. ".repeat(30)

    @Test
    fun windowKeepsTheSentenceWithTheQueryTerm() {
        val target = "La nacionalizacion del petroleo ocurrio en 1938 y cambio la economia."
        val text = filler + target + " " + filler

        val window = ContextBuilder.window(text, 400, listOf("petroleo", "nacionalizacion"))

        assertTrue(window.text.contains("1938"))
        assertTrue(window.text.length <= 400)
    }

    @Test
    fun windowReturnsFullTextWhenItFits() {
        val window = ContextBuilder.window("Texto corto.", 400, listOf("texto"))

        assertEquals("Texto corto.", window.text)
        assertTrue(window.reachedEnd)
    }

    @Test
    fun contextUsesWindowInsteadOfBlindTruncation() {
        val target = "La deuda externa se renegocio en 1982 con el Fondo Monetario Internacional."
        val chunkText = filler + target + " " + filler.repeat(2)
        val context = ContextBuilder.build(
            query = "deuda externa Fondo Monetario",
            chunks = listOf(chunk("Historia", 1, chunkText)),
            maxBlocks = 1,
            blockChars = 400,
            maxChars = 2_000,
        )

        assertEquals(1, context.items.size)
        assertTrue(context.blocks.first().contains("1982"))
        assertTrue(context.items.first().text.length <= 400)
    }

    @Test
    fun evidenceCarriesChunkIdScoreAndStrategy() {
        val context = ContextBuilder.build(
            query = "renta mensual",
            chunks = listOf(
                chunk("Contrato", 2, "El inquilino paga la renta mensual los primeros cinco dias."),
            ),
            maxBlocks = 3,
        )

        val item = context.items.first()
        assertEquals("c-Contrato-2", item.chunkId)
        assertTrue(item.score > 0.0)
        assertEquals(ContextBuilder.MODE_SEARCH, item.strategy)
        assertEquals(2, item.page)
        assertFalse(context.weak)
    }

    @Test
    fun duplicateOverlapIsRemoved() {
        val base = "El patriarcado y la colonialidad se articulan en el cuerpo de las mujeres. ".repeat(6)
        val context = ContextBuilder.build(
            query = "patriarcado colonialidad cuerpo",
            chunks = listOf(
                chunk("Libro A", 3, base),
                chunk("Libro A", 3, base),
                chunk("Libro B", 4, "La comunidad como cuerpo politico transformador es la propuesta."),
            ),
            maxBlocks = 5,
            blockChars = 1_200,
        )

        assertEquals(2, context.items.size)
    }

    @Test
    fun neighborIsAppendedWhenWindowEndsAtChunkBoundary() {
        val context = ContextBuilder.build(
            query = "territorio cuerpo",
            chunks = listOf(
                chunk("Cabnal", 7, "Recuperacion y defensa de nuestro territorio cuerpo-tierra.", "a"),
                chunk("Cabnal", 7, "Este planteamiento lo asumimos como consigna politica.", "b"),
            ),
            maxBlocks = 4,
            blockChars = 1_200,
            includeNeighbors = true,
        )

        val first = context.items.first()
        assertTrue(first.text.contains("territorio cuerpo-tierra"))
        assertTrue(first.text.contains("consigna politica"))
    }

    @Test
    fun dedupeCanBeDisabled() {
        val base = "Texto repetido sobre comunidad y territorio. ".repeat(8)
        val context = ContextBuilder.build(
            query = "comunidad territorio",
            chunks = listOf(chunk("A", 1, base), chunk("A", 1, base)),
            maxBlocks = 4,
            deduplicate = false,
        )

        assertEquals(2, context.items.size)
    }

    @Test
    fun resultExposesEvidenceForTheUi() {
        val context = ContextBuilder.build(
            query = "comunidad",
            chunks = listOf(chunk("Paredes", 5, "La comunidad como cuerpo politico transformador.")),
        )

        val result = context.toResult("comunidad", indexVersion = "v1")
        assertEquals("v1", result.indexVersion)
        assertEquals(1, result.items.size)
        assertEquals("Paredes", result.items.first().documentTitle)
    }
}
