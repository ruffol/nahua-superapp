package com.nahuanotoca.superapp.core.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextCleanerTest {

    @Test
    fun removesRepeatedHeaderAndFooter() {
        val pages = (1..6).map { page ->
            "HISTORIA DE MÉXICO\n" +
                "Contenido distinto de la página " + page + " con una idea propia.\n" +
                "Editorial Colima"
        }

        val cleaned = TextCleaner.clean(pages)

        cleaned.forEach { text ->
            assertFalse(text.contains("HISTORIA DE MÉXICO"))
            assertFalse(text.contains("Editorial Colima"))
        }
        assertTrue(cleaned[0].contains("Contenido distinto"))
    }

    @Test
    fun removesStandalonePageNumbers() {
        val pages = listOf("Primera página con texto suficiente.", "12", "Segunda página con más texto.")

        val cleaned = TextCleaner.clean(pages)

        assertEquals("", cleaned[1])
        assertTrue(cleaned[0].contains("Primera página"))
    }

    @Test
    fun joinsWordsBrokenByHyphen() {
        val pages = listOf("La pala-\nbra se corta al final de la línea.")

        val cleaned = TextCleaner.clean(pages)

        assertTrue(cleaned[0].contains("palabra"))
        assertFalse(cleaned[0].contains("pala-"))
    }

    @Test
    fun keepsParagraphsButCollapsesLineBreaks() {
        val pages = listOf("Primera línea\nsegunda línea\n\nPárrafo nuevo.")

        val cleaned = TextCleaner.clean(pages)

        assertTrue(cleaned[0].contains("Primera línea segunda línea"))
        assertTrue(cleaned[0].contains("\n\n"))
    }

    @Test
    fun doesNotDropShortBooks() {
        val pages = listOf("Capítulo corto\n\ncon texto.")

        val cleaned = TextCleaner.clean(pages)

        assertEquals(1, cleaned.size)
        assertTrue(cleaned[0].isNotBlank())
    }
}
