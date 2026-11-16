package com.nahuanotoca.superapp.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {

    @Test
    fun parsesTagsWithPrefix() {
        assertEquals(0, Version.compare("v0.3.0", "0.3.0"))
        assertEquals(0, Version.compare("1.2.3", "v1.2.3"))
    }

    @Test
    fun detectsNewerVersions() {
        assertTrue(Version.compare("0.3.1", "0.3.0") > 0)
        assertTrue(Version.compare("0.4.0", "0.3.9") > 0)
        assertTrue(Version.compare("1.0.0", "0.99.99") > 0)
    }

    @Test
    fun detectsOlderVersions() {
        assertTrue(Version.compare("0.2.9", "0.3.0") < 0)
    }

    @Test
    fun toleratesSuffixesAndMissingParts() {
        assertEquals(0, Version.compare("0.3", "0.3.0"))
        assertEquals(0, Version.compare("0.3.0-debug", "0.3.0"))
    }
}
