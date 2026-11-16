package com.nahuanotoca.superapp.core.rag

import com.nahuanotoca.superapp.core.model.RagChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reproduce el caso que reportó el usuario: un PDF de 8 páginas con texto, y una pregunta que el
 * motor anterior rechazaba con "No encontré pasajes relevantes".
 */
class StudyScenarioTest {

    private val pages = listOf(
        "Portada. Historia económica de México. Apuntes del bloque 3 para la exposición.",
        "El porfiriato se sostuvo en la inversión extranjera, el ferrocarril y la concentración de la tierra.",
        "Las haciendas concentraban la propiedad rural; los peones vivían en condiciones de endeudamiento.",
        "La crisis de 1907 redujo los salarios y aumentó el desempleo en las ciudades mineras.",
        "El Plan de San Luis convocó a levantarse el 20 de noviembre de 1910 contra el régimen porfirista.",
        "Madero llegó a la presidencia en 1911, pero no resolvió el reparto agrario y perdió apoyo popular.",
        "La Constitución de 1917 incorporó el artículo 27 sobre la propiedad de la tierra y el subsuelo.",
        "El cardenismo repartió tierras ejidales y nacionalizó la industria petrolera en 1938.",
    )

    private val chunks = TextChunker.chunk(pages).map { chunk ->
        RagChunk(
            documentId = "doc_historia",
            documentName = "Historia económica de México",
            page = chunk.page,
            text = chunk.text,
        )
    }

    @Test
    fun resumenRequestOnEightPagePdfProducesContext() {
        assertTrue("el PDF debe producir fragmentos", chunks.size >= 8)

        val context = ContextBuilder.build(
            query = "Dame un resumen",
            chunks = chunks,
            maxBlocks = 8,
            broad = true,
        )

        assertFalse("antes esto devolvía vacío y la UI decía 'sin pasajes relevantes'", context.isEmpty)
        assertEquals(ContextBuilder.MODE_COVERAGE, context.mode)
        assertFalse(context.weak)
        assertEquals("deben aparecer las 8 páginas", 8, context.sources.map { it.page }.distinct().size)
        assertTrue(context.blocks.first().startsWith("[Historia económica de México, p. 1]"))
    }

    @Test
    fun questionWithoutKeywordsStillGetsContextAndIsFlaggedWeak() {
        val context = ContextBuilder.build(
            query = "¿de qué trata y qué fechas importan?",
            chunks = chunks,
            maxBlocks = 6,
        )

        // "trata" y "fechas" no están en el texto: la búsqueda no encuentra nada y entra el respaldo.
        assertFalse(context.isEmpty)
        assertTrue(context.weak)
        assertEquals(ContextBuilder.MODE_COVERAGE, context.mode)
    }

    @Test
    fun keywordQuestionCitesTheRightPage() {
        val context = ContextBuilder.build(
            query = "¿cuándo se nacionalizó el petróleo?",
            chunks = chunks,
            maxBlocks = 3,
        )

        assertEquals(ContextBuilder.MODE_SEARCH, context.mode)
        assertFalse(context.weak)
        assertTrue(context.sources.any { it.page == 8 })
    }

    @Test
    fun correlationCoversBothDocuments() {
        val otra = listOf(
            RagChunk("doc_articulo", "Artículo: reforma agraria", 1, "La reforma agraria de 1934 creó el ejido colectivo."),
            RagChunk("doc_articulo", "Artículo: reforma agraria", 2, "El reparto cardenista entregó 18 millones de hectáreas."),
        )
        val context = ContextBuilder.build(
            query = "correlaciona",
            chunks = chunks + otra,
            maxBlocks = 8,
            broad = true,
        )

        assertEquals(2, context.documentCount)
        assertTrue(context.blocks.any { it.contains("Historia económica de México") })
        assertTrue(context.blocks.any { it.contains("Artículo: reforma agraria") })
    }
}
