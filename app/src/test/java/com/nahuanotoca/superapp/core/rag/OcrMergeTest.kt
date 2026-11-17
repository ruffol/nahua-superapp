package com.nahuanotoca.superapp.core.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrMergeTest {

    private val scanned = listOf("", "  ", "12", "")
    private val digital = listOf("Texto real de la página uno con suficiente contenido.", "Otra página con texto.")

    @Test
    fun detectsScannedDocuments() {
        assertTrue(OcrMerge.needsOcr(scanned))
        assertFalse(OcrMerge.needsOcr(digital))
        assertTrue(OcrMerge.needsOcr(emptyList()))
    }

    @Test
    fun keepsDigitalTextAndFillsOnlyEmptyPages() {
        val pages = listOf(
            "Texto seleccionable de la página uno, con suficiente contenido para conservarse.",
            "",
            "12",
        )
        val ocr = listOf("texto OCR uno", "texto OCR dos", "texto OCR tres")

        val merged = OcrMerge.merge(pages, ocr)

        assertEquals(pages[0], merged[0])
        assertEquals("texto OCR dos", merged[1])
        assertEquals("texto OCR tres", merged[2])
    }

    @Test
    fun ignoresEmptyOcrResults() {
        val pages = listOf("", "uno")
        val merged = OcrMerge.merge(pages, listOf("", ""))

        assertEquals(pages, merged)
    }

    @Test
    fun handlesMissingOcrPages() {
        val pages = listOf("", "", "")

        val merged = OcrMerge.merge(pages, listOf("sólo la primera"))

        assertEquals("sólo la primera", merged[0])
        assertEquals("", merged[1])
    }
}
