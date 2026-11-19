package com.nahuanotoca.superapp.core.miniapp

import org.junit.Assert.assertEquals
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
    fun orientationDefaultsToAutoAndOnlyLandscapeIsHonored() {
        assertEquals(MiniAppOrientation.AUTO, MiniAppOrientation.from(null))
        assertEquals(MiniAppOrientation.AUTO, MiniAppOrientation.from("auto"))
        assertEquals(MiniAppOrientation.LANDSCAPE, MiniAppOrientation.from("landscape"))
        assertEquals(MiniAppOrientation.LANDSCAPE, MiniAppOrientation.from("Landscape"))
        // "portrait" no esta pedido a proposito: girar a vertical lo decide el usuario.
        assertEquals(MiniAppOrientation.AUTO, MiniAppOrientation.from("portrait"))
        assertEquals(MiniAppOrientation.AUTO, MiniAppOrientation.from("diagonal"))
    }

    @Test
    fun entryUrlIsServedFromTheAssetOrigin() {
        val manifest = manifest(entry = "miniapps/gba/index.html")
        assertEquals(
            "https://appassets.androidplatform.net/assets/miniapps/gba/index.html",
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
