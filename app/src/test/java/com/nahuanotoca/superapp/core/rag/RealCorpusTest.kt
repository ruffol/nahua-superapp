package com.nahuanotoca.superapp.core.rag

import com.nahuanotoca.superapp.core.model.RagChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Casos del corpus real (docs/eval/cases.json) ejecutados sobre una página extraída de cada libro.
 * No sustituyen a la prueba en la app, pero fijan el comportamiento de la recuperación con prosa
 * real en lugar de texto sintético.
 */
class RealCorpusTest {

    private fun page(resource: String): String =
        javaClass.getResourceAsStream("/eval/" + resource)?.bufferedReader()?.use { it.readText() }
            ?: error("Falta el recurso " + resource)

    private fun chunks(document: String, pageNumber: Int, text: String): List<RagChunk> =
        TextChunker.chunk(listOf(text), maxWords = 450, overlapWords = 50).map { chunk ->
            RagChunk(
                id = document + "-" + pageNumber + "-" + chunk.words,
                documentId = document,
                documentName = document,
                page = pageNumber,
                text = chunk.text,
            )
        }

    @Test
    fun c1CabnalTerritorioCuerpoTierra() {
        val context = ContextBuilder.build(
            query = "¿Qué significa el territorio cuerpo-tierra?",
            chunks = chunks("Cabnal 2010", 7, page("cabnal-p7.txt")),
            maxBlocks = 2,
            blockChars = 900,
        )

        assertFalse(context.weak)
        assertEquals(7, context.items.first().page)
        assertTrue(context.blocks.first().lowercase().contains("territorio"))
        assertTrue(context.items.first().score > 0.0)
        assertEquals(ContextBuilder.MODE_SEARCH, context.items.first().strategy)
    }

    @Test
    fun c2SegatoCrueldadComoPedagogia() {
        val context = ContextBuilder.build(
            query = "¿Por qué la crueldad funciona como pedagogía del poder?",
            chunks = chunks("Segato, La guerra contra las mujeres", 23, page("segato-p23.txt")),
            maxBlocks = 2,
            blockChars = 900,
        )

        assertFalse(context.weak)
        assertTrue(context.blocks.first().lowercase().contains("crueldad"))
        assertEquals(23, context.items.first().page)
    }

    @Test
    fun c3FedericiHuelgaYSalario() {
        val context = ContextBuilder.build(
            query = "¿Por qué la huelga es central en la lucha?",
            chunks = chunks("Federici, Revolución en punto cero", 62, page("federici-p62.txt")),
            maxBlocks = 2,
            blockChars = 900,
        )

        assertFalse(context.weak)
        assertTrue(context.blocks.any { it.lowercase().contains("huelga") })
        assertEquals(62, context.items.first().page)
    }

    @Test
    fun c6SinEvidenciaMarcaContextoDebil() {
        val context = ContextBuilder.build(
            query = "fotosíntesis clorofila",
            chunks = chunks("Cabnal 2010", 7, page("cabnal-p7.txt")),
            maxBlocks = 2,
        )

        assertTrue(context.weak)
        assertTrue(context.blocks.isNotEmpty())
    }
}