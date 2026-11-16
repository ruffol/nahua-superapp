package com.nahuanotoca.superapp.core.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserTest {

    private fun textPut(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun sampleEpub(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            textPut(zip, "mimetype", "application/epub+zip")
            textPut(
                zip,
                "META-INF/container.xml",
                "<container><rootfiles><rootfile full-path=\"OEBPS/content.opf\" " +
                    "media-type=\"application/oebps-package+xml\"/></rootfiles></container>",
            )
            textPut(
                zip,
                "OEBPS/content.opf",
                "<package><manifest>" +
                    "<item id=\"c1\" href=\"cap1.xhtml\" media-type=\"application/xhtml+xml\"/>" +
                    "<item id=\"c2\" href=\"cap2.xhtml\" media-type=\"application/xhtml+xml\"/>" +
                    "</manifest><spine><itemref idref=\"c1\"/><itemref idref=\"c2\"/></spine></package>",
            )
            textPut(
                zip,
                "OEBPS/cap1.xhtml",
                "<html><head><title>Uno</title></head><body><h1>Capítulo uno</h1>" +
                    "<p>Texto del capítulo uno con suficiente contenido para pasar el filtro. " +
                    "Seguimos escribiendo para llegar a los ochenta caracteres.</p></body></html>",
            )
            textPut(
                zip,
                "OEBPS/cap2.xhtml",
                "<html><body><h2>Capítulo dos</h2><p>Otro capítulo con texto suficiente para que el " +
                    "lector lo acepte sin problema alguno y con &amp; entidades.</p></body></html>",
            )
        }
        return out.toByteArray()
    }

    @Test
    fun readsChaptersInSpineOrder() {
        val chapters = EpubParser.parse(ByteArrayInputStream(sampleEpub()))

        assertEquals(2, chapters.size)
        assertEquals("Capítulo uno", chapters[0].title)
        assertEquals("Capítulo dos", chapters[1].title)
        assertTrue(chapters[0].text.contains("Texto del capítulo uno"))
    }

    @Test
    fun stripsHtmlAndDecodesEntities() {
        val chapters = EpubParser.parse(ByteArrayInputStream(sampleEpub()))

        assertTrue(chapters[1].text.contains("&"))
        assertTrue(!chapters[1].text.contains("<p>"))
        assertTrue(!chapters[1].text.contains("</h2>"))
    }

    @Test
    fun htmlToTextKeepsParagraphBreaks() {
        val text = EpubParser.htmlToText("<p>Uno</p><p>Dos</p>")

        assertTrue(text.contains("Uno"))
        assertTrue(text.contains("Dos"))
        assertTrue(text.contains("\n"))
    }
}
