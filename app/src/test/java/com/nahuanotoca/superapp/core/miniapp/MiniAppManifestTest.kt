package com.nahuanotoca.superapp.core.miniapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El manifiesto es el contrato con los bundles: los campos nuevos (display, orientation) deben
 * ser tolerantes para que ningun bundle existente se rompa, y los desconocidos deben caer al
 * valor seguro por defecto.
 */
class MiniAppManifestTest {

    @Test
    fun displayDefaultsToChromeAndParsesKnownValues() {
        assertEquals(MiniAppDisplay.CHROME, MiniAppDisplay.from(null))
        assertEquals(MiniAppDisplay.CHROME, MiniAppDisplay.from(""))
        assertEquals(MiniAppDisplay.CHROME, MiniAppDisplay.from("chrome"))
        assertEquals(MiniAppDisplay.IMMERSIVE, MiniAppDisplay.from("immersive"))
        assertEquals(MiniAppDisplay.IMMERSIVE, MiniAppDisplay.from("IMMERSIVE"))
        assertEquals(MiniAppDisplay.CHROME, MiniAppDisplay.from("fullscreen"))  // desconocido -> seguro
    }

    @Test
    fun orientationIsParsedAndPortraitIsHonored() {
        assertEquals(MiniAppOrientation.AUTO, MiniAppOrientation.from(null))
        assertEquals(MiniAppOrientation.AUTO, MiniAppOrientation.from("auto"))
        assertEquals(MiniAppOrientation.LANDSCAPE, MiniAppOrientation.from("landscape"))
        assertEquals(MiniAppOrientation.LANDSCAPE, MiniAppOrientation.from("Landscape"))
        // El emulador es vertical fijo: un Game Boy lo es y asi el mando cabe en la pantalla.
        assertEquals(MiniAppOrientation.PORTRAIT, MiniAppOrientation.from("portrait"))
        assertEquals(MiniAppOrientation.PORTRAIT, MiniAppOrientation.from("Portrait"))
        assertEquals(MiniAppOrientation.AUTO, MiniAppOrientation.from("diagonal"))
    }

    /**
     * La URL lleva la versión del manifiesto como query: el WebView cachea por URL, y sin ese
     * cambio una actualización del APK podía seguir ejecutando el HTML anterior del bundle.
     */
    @Test
    fun entryUrlIsServedFromTheAssetOriginWithACacheBuster() {
        val manifest = manifest(entry = "miniapps/gba/index.html")
        assertEquals(
            "https://appassets.androidplatform.net/assets/miniapps/gba/index.html?v=1.0.0",
            manifest.entryUrl,
        )
        assertNotEquals(
            "sin cache-buster el WebView puede seguir sirviendo el bundle viejo",
            manifest(entry = "miniapps/gba/index.html", version = "1.1.0").entryUrl,
            manifest.entryUrl,
        )
    }

    @Test
    fun defaultsKeepExistingBundlesCompatible() {
        val manifest = manifest()
        assertEquals(MiniAppDisplay.CHROME, manifest.display)
        assertEquals(MiniAppOrientation.AUTO, manifest.orientation)
        assertTrue(manifest.permissions.isEmpty())
    }

    private fun manifest(
        id: String = "x",
        name: String = "X",
        tagline: String = "",
        version: String = "1.0.0",
        entry: String = "miniapps/x/index.html",
        accent: String = "#0E7C66",
    ) = MiniAppManifest(
        id = id,
        name = name,
        tagline = tagline,
        version = version,
        entry = entry,
        accent = accent,
    )
}
