package com.nahuanotoca.superapp.core.rag

import com.nahuanotoca.superapp.core.model.RagChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBuilderTest {

    private fun chunk(document: String, page: Int, text: String): RagChunk = RagChunk(
        documentId = "id_" + document,
        documentName = document,
        page = page,
        text = text,
    )

    private val algebra = listOf(
        chunk("Algebra", 1, "Una ecuacion lineal tiene la forma ax + b = 0 y se resuelve despejando x."),
        chunk("Algebra", 2, "La pendiente de una recta mide el cambio en y por cada unidad de x."),
        chunk("Algebra", 3, "Los sistemas de dos ecuaciones se resuelven por sustitucion o igualacion."),
        chunk("Algebra", 4, "Una funcion cuadratica forma una parabola con vertice en su punto minimo."),
    )

    private val historia = listOf(
        chunk("Historia", 1, "La Revolucion Mexicana comenzo en 1910 con el levantamiento de Madero."),
        chunk("Historia", 2, "El porfiriato concentro la tierra en pocas manos y provoco descontento rural."),
        chunk("Historia", 3, "La constitucion de 1917 incluyo garantias sociales y derechos laborales."),
        chunk("Historia", 4, "El cardenismo repartio tierras y nacionalizo el petroleo en 1938."),
    )

    @Test
    fun coverageIncludesEveryDocument() {
        val context = ContextBuilder.build(
            query = "resumen general",
            chunks = algebra + historia,
            maxBlocks = 4,
            broad = true,
        )

        assertFalse(context.isEmpty)
        assertEquals(ContextBuilder.MODE_COVERAGE, context.mode)
        assertFalse(context.weak)
        assertEquals(2, context.documentCount)
        assertTrue(context.blocks.any { it.contains("Algebra") })
        assertTrue(context.blocks.any { it.contains("Historia") })
    }

    @Test
    fun focusWithoutMatchesFallsBackToCoverageInsteadOfFailing() {
        val context = ContextBuilder.build(
            query = "fotosintesis clorofila",
            chunks = algebra + historia,
            maxBlocks = 3,
        )

        assertFalse("un PDF con texto nunca debe quedarse sin contexto", context.isEmpty)
        assertEquals(ContextBuilder.MODE_COVERAGE, context.mode)
        assertTrue("debe avisar que la busqueda fue debil", context.weak)
    }

    @Test
    fun focusedQueryUsesRankedSearch() {
        val context = ContextBuilder.build(
            query = "cuando comenzo la revolucion",
            chunks = algebra + historia,
            maxBlocks = 4,
        )

        assertEquals(ContextBuilder.MODE_SEARCH, context.mode)
        assertFalse(context.weak)
        assertTrue(context.blocks.first().contains("Historia"))
        assertTrue(context.blocks.first().contains("p. 1"))
    }

    @Test
    fun respectsDocumentSelectionForCorrelation() {
        val context = ContextBuilder.build(
            query = "correlacion",
            chunks = algebra + historia,
            maxBlocks = 4,
            broad = true,
            documentIds = setOf("id_Historia"),
        )

        assertEquals(1, context.documentCount)
        assertTrue(context.blocks.all { it.contains("Historia") })
        assertTrue(context.sources.all { it.documentName == "Historia" })
    }

    @Test
    fun respectsCharacterBudget() {
        val long = List(20) { index ->
            chunk("Apuntes", index + 1, "texto repetido ".repeat(60) + index)
        }
        val context = ContextBuilder.build(
            query = "cualquier cosa",
            chunks = long,
            maxBlocks = 12,
            maxChars = 2_000,
            broad = true,
        )

        val total = context.blocks.sumOf { it.length }
        assertTrue("presupuesto excedido: " + total, total <= 2_000 + 400)
        assertTrue(context.blocks.isNotEmpty())
    }

    @Test
    fun emptyDocumentSetProducesEmptyContext() {
        val context = ContextBuilder.build("¿de qué trata?", emptyList())

        assertTrue(context.isEmpty)
        assertEquals(0, context.documentCount)
    }
}
