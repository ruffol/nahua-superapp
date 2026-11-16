package com.nahuanotoca.superapp.core.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChunkerTest {

    private fun words(count: Int, prefix: String = "palabra"): String =
        (1..count).joinToString(" ") { prefix + it }

    @Test
    fun shortPageProducesSingleChunk() {
        val chunks = TextChunker.chunk(listOf("Hola mundo, esto es una prueba."))

        assertEquals(1, chunks.size)
        assertEquals(1, chunks.first().page)
        assertEquals(6, chunks.first().words)
    }

    @Test
    fun emptyPagesAreSkipped() {
        val chunks = TextChunker.chunk(listOf("", "   ", "Contenido real"))

        assertEquals(1, chunks.size)
        assertEquals(3, chunks.first().page)
    }

    @Test
    fun longPageIsSplitInBlocksOfMaxWords() {
        val chunks = TextChunker.chunk(listOf(words(1_200)), maxWords = 450, overlapWords = 50)

        assertTrue(chunks.size >= 3)
        chunks.forEach { chunk ->
            assertTrue("bloque demasiado grande: " + chunk.words, chunk.words <= 450)
            assertEquals(1, chunk.page)
        }
    }

    @Test
    fun consecutiveChunksShareTheOverlap() {
        val chunks = TextChunker.chunk(listOf(words(1_200)), maxWords = 450, overlapWords = 50)

        val first = chunks[0].text.split(" ")
        val second = chunks[1].text.split(" ")

        assertEquals(first.takeLast(50), second.take(50))
    }

    @Test
    fun chunksKeepTheirPageNumber() {
        val chunks = TextChunker.chunk(
            listOf("primera", words(900, "pagina"), "tercera"),
            maxWords = 400,
            overlapWords = 40,
        )

        assertTrue(chunks.any { it.page == 1 })
        assertTrue(chunks.any { it.page == 2 })
        assertTrue(chunks.any { it.page == 3 })
        assertTrue(chunks.count { it.page == 2 } > 1)
    }

    @Test
    fun rejectsInvalidConfiguration() {
        var threw = false
        try {
            TextChunker.chunk(listOf("texto"), maxWords = 450, overlapWords = 450)
        } catch (error: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }
}
