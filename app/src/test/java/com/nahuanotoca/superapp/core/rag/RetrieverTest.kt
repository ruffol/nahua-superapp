package com.nahuanotoca.superapp.core.rag

import com.nahuanotoca.superapp.core.model.RagChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetrieverTest {

    private val chunks = listOf(
        RagChunk(
            documentId = "doc_1",
            documentName = "Contrato de renta",
            page = 2,
            text = "El inquilino deberá pagar la renta mensual los primeros cinco días de cada mes.",
        ),
        RagChunk(
            documentId = "doc_1",
            documentName = "Contrato de renta",
            page = 7,
            text = "El depósito en garantía se devuelve al término del contrato si no hay daños.",
        ),
        RagChunk(
            documentId = "doc_2",
            documentName = "Recetario",
            page = 1,
            text = "Para el mole poblano se tuestan chiles, almendras y ajonjolí.",
        ),
    )

    @Test
    fun ranksTheChunkThatAnswersTheQuestion() {
        val ranked = Retriever.select("¿Cuándo se paga la renta?", chunks, limit = 2)

        assertTrue(ranked.isNotEmpty())
        assertEquals(2, ranked.first().chunk.page)
        assertEquals("Contrato de renta", ranked.first().chunk.documentName)
    }

    @Test
    fun ignoresStopwordsOnlyQueries() {
        val ranked = Retriever.select("de la que en el", chunks)

        assertTrue(ranked.isEmpty())
    }

    @Test
    fun respectsTheLimit() {
        val ranked = Retriever.select("contrato renta depósito daños", chunks, limit = 1)

        assertEquals(1, ranked.size)
    }

    @Test
    fun isAccentInsensitive() {
        val ranked = Retriever.select("deposito garantia", chunks)

        assertTrue(ranked.isNotEmpty())
        assertEquals(7, ranked.first().chunk.page)
    }

    @Test
    fun unrelatedQueryReturnsNothing() {
        val ranked = Retriever.select("astronomía cuántica", chunks)

        assertTrue(ranked.isEmpty())
    }
}
