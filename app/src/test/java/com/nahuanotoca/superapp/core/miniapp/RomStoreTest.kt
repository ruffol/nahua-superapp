package com.nahuanotoca.superapp.core.miniapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Funciones puras de [RomStore]: slugs, validacion de extensiones, tope de tamano y nombres
 * seguros. La copia con ContentResolver no cabe aqui (necesita Android); lo que si se puede
 * romper en produccion es el nombre de archivo, y eso es exactamente lo que se prueba.
 */
class RomStoreTest {

    @Test
    fun romFileNameIsDeterministicAndSurvivesAccents() {
        val name = RomStore.romFileName("Pokémon - Edición Esmerald.GBA", "A1B2C3D4E5F60718")
        assertEquals("pokemon-edicion-esmerald-a1b2c3d4.gba", name)
    }

    @Test
    fun romFileNameIgnoresCaseAndParentheses() {
        // "(U)" se colapsa al slug "u": mejor un nombre feo pero predecible que perder la ROM.
        assertEquals(
            "golden-sun-u-a1b2c3d4.gba",
            RomStore.romFileName("Golden Sun (U).gba", "a1b2c3d4e5f60718"),
        )
    }

    @Test
    fun sanitizeCollapsesSeparatorsAndKeepsDigits() {
        // sanitizeName no conoce extensiones: lo que entre, se eslugifica tal cual.
        assertEquals("zelda-minish-cap-1-gba", RomStore.sanitizeName("Zelda - Minish Cap 1.gba"))
        assertEquals("zelda-minish-cap-1", RomStore.sanitizeName("Zelda - Minish Cap 1"))
    }

    @Test
    fun sanitizeBlocksPathTraversal() {
        // La ruta nunca sobrevive: solo importa el ultimo segmento.
        assertEquals("secret", RomStore.sanitizeName("../../secret"))
        assertEquals("otra", RomStore.sanitizeName("..\\..\\otra"))
    }

    @Test
    fun degenerateNamesFallBackToRom() {
        assertEquals("rom", RomStore.sanitizeName("..."))
        assertEquals("rom", RomStore.sanitizeName("   "))
        assertEquals("rom", RomStore.sanitizeName(""))
    }

    @Test
    fun extensionDetectionIsCaseInsensitive() {
        assertEquals("gba", RomStore.extensionOf("Juego.GBA"))
        assertEquals("gba", RomStore.extensionOf("juego.gba"))
        assertEquals("", RomStore.extensionOf("juego"))
        assertEquals("", RomStore.extensionOf("carpeta/juego"))
    }

    @Test
    fun onlyGbaFamilyExtensionsAreAllowed() {
        assertTrue(RomStore.isAllowedExtension("gba"))
        assertTrue(RomStore.isAllowedExtension("agb"))
        assertTrue(RomStore.isAllowedExtension("bin"))
        assertFalse(RomStore.isAllowedExtension("zip"))
        assertFalse(RomStore.isAllowedExtension("7z"))
        assertFalse(RomStore.isAllowedExtension(""))
        assertFalse(RomStore.isAllowedExtension("gba "))  // espacios no
    }

    @Test
    fun sizeCapCoversRealCartridgesAndRejectsAbsurdSizes() {
        // El cartucho GBA mas grande ronda los 32 MB; el tope de 64 MiB deja holgura.
        assertTrue(RomStore.isAllowedSize(1))
        assertTrue(RomStore.isAllowedSize(32L * 1024 * 1024))
        assertTrue(RomStore.isAllowedSize(RomStore.MAX_ROM_BYTES))
        assertFalse(RomStore.isAllowedSize(0))
        assertFalse(RomStore.isAllowedSize(-1))
        assertFalse(RomStore.isAllowedSize(RomStore.MAX_ROM_BYTES + 1))
    }

    @Test
    fun safeNamesRejectTraversalAndForeignExtensions() {
        assertTrue(RomStore.isSafeName("pokemon-a1b2c3d4.gba"))
        assertFalse(RomStore.isSafeName("../pokemon.gba"))
        assertFalse(RomStore.isSafeName("carpeta/pokemon.gba"))
        assertFalse(RomStore.isSafeName("..\\pokemon.gba"))
        assertFalse(RomStore.isSafeName("pokemon.zip"))
        assertFalse(RomStore.isSafeName(""))
        assertFalse(RomStore.isSafeName("a.b.c.gba.exe"))
    }

    @Test
    fun hashSuffixTakesEightHexCharsLowercased() {
        assertEquals("a1b2c3d4", RomStore.hashSuffix("A1B2C3D4E5F60718"))
        assertEquals("deadbeef", RomStore.hashSuffix("DEADBEEF0000"))
    }

    @Test
    fun romUrlIsServedFromTheAssetOrigin() {
        assertEquals(
            "https://appassets.androidplatform.net/rom/pokemon-a1b2c3d4.gba",
            RomStore.url("pokemon-a1b2c3d4.gba"),
        )
    }
}
