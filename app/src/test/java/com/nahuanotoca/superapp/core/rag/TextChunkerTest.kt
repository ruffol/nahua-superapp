package com.nahuanotoca.superapp.core.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChunkerTest {

    @Test
    fun shortPageProducesSingleChunk() {
        val chunks = TextChunker.chunk(listOf("Hola mundo. Esto es una prueba."))

        assertEquals(1, chunks.size)
        assertEquals(1, chunks.first().page)
        assertEquals("Hola mundo. Esto es una prueba.", chunks.first().text)
    }

    @Test
    fun emptyPagesAreSkipped() {
        val chunks = TextChunker.chunk(listOf("", "   ", "Contenido real"))

        assertEquals(1, chunks.size)
        assertEquals(3, chunks.first().page)
    }

    @Test
    fun longPageIsSplitRespectingMaxChars() {
        val sentence = "El contrato establece obligaciones para ambas partes. "
        val page = sentence.repeat(80)

        val chunks = TextChunker.chunk(listOf(page), maxChars = 600, overlapChars = 100)

        assertTrue(chunks.size > 1)
        chunks.forEach { chunk ->
            assertTrue("chunk too long: " + chunk.text.length, chunk.text.length <= 600)
            assertEquals(1, chunk.page)
        }
    }

    @Test
    fun chunksKeepTheirPageNumber() {
        val longPage = "palabra ".repeat(300)
        val chunks = TextChunker.chunk(listOf("primera", longPage, "tercera"), maxChars = 500, overlapChars = 80)

        assertTrue(chunks.any { it.page == 1 })
        assertTrue(chunks.any { it.page == 2 })
        assertTrue(chunks.any { it.page == 3 })
        assertTrue(chunks.count { it.page == 2 } > 1)
    }

    @Test
    fun overlappingChunksShareText() {
        val page = "abcdefghij ".repeat(120)
        val chunks = TextChunker.chunk(listOf(page), maxChars = 500, overlapChars = 120)

        assertTrue(chunks.size >= 2)
        val firstTail = chunks[0].text.takeLast(40)
        val secondHead = chunks[1].text.take(160)
        assertTrue("expected overlap between chunks", secondHead.contains(firstTail.take(20)))
    }
}
