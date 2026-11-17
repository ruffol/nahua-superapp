package com.nahuanotoca.superapp.core.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyLibraryTest {

    private val meta = """
        {
          "id": "doc_abc",
          "mesa": "mesa_1",
          "name": "Guia_Colima.pdf",
          "pages": 4,
          "chunks": 4,
          "characters": 1200,
          "addedAt": 1763000000000,
          "sha256": "aabbcc",
          "version": 2,
          "figures": [ { "file": "fig_doc_abc_0.jpg", "page": 3 } ]
        }
    """.trimIndent()

    private val chunks = """
        [
          { "doc": "Guia_Colima.pdf", "page": 1, "text": "El estado de Colima se ubica en la costa." },
          { "doc": "Guia_Colima.pdf", "page": 2, "text": "El Volcan de Colima mide 3,860 msnm." }
        ]
    """.trimIndent()

    @Test
    fun parsesDocumentMetadata() {
        val legacy = LegacyLibrary.parseMeta(meta)

        assertEquals("doc_abc", legacy?.id)
        assertEquals(4, legacy?.pages)
        assertEquals("aabbcc", legacy?.sha256)
        assertEquals(2, legacy?.version)
        assertEquals(1, legacy?.figures?.size)
        assertEquals(3, legacy?.figures?.first()?.page)
    }

    @Test
    fun computesChunkIdsWhenMissing() {
        val parsed = LegacyLibrary.parseChunks(chunks, "doc_abc")

        assertEquals(2, parsed.size)
        assertTrue(parsed[0].id.startsWith("doc_abc:1:"))
        assertTrue(parsed[1].id.startsWith("doc_abc:2:"))
        assertEquals(1, parsed[0].page)
    }

    @Test
    fun keepsExistingChunkIds() {
        val withId = """[ { "id": "doc_abc:1:zzz", "page": 1, "text": "texto" } ]"""

        val parsed = LegacyLibrary.parseChunks(withId, "doc_abc")

        assertEquals("doc_abc:1:zzz", parsed.first().id)
    }

    @Test
    fun returnsNullForBrokenMeta() {
        assertNull(LegacyLibrary.parseMeta("{ no es json"))
        assertTrue(LegacyLibrary.parseChunks("{ roto", "doc").isEmpty())
    }
}
