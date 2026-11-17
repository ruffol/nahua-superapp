package com.nahuanotoca.superapp.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class DigestTest {

    @Test
    fun matchesTheKnownSha256OfAbc() {
        val hash = Digest.sha256(ByteArrayInputStream("abc".toByteArray(Charsets.UTF_8)))

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash)
    }

    @Test
    fun isStableForTheSameBytes() {
        val a = Digest.sha256(ByteArrayInputStream("Hola, Nahua".toByteArray(Charsets.UTF_8)))
        val b = Digest.sha256(ByteArrayInputStream("Hola, Nahua".toByteArray(Charsets.UTF_8)))

        assertEquals(a, b)
        assertEquals(64, a.length)
    }
}
