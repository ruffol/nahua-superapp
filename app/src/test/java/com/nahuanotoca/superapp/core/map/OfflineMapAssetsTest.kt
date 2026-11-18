package com.nahuanotoca.superapp.core.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the offline map contract: the mini-app ships real tiles for Colima so the map still works
 * without connectivity. El paquete se genera con `.tmp/inegi/render_v4.py` (Natural Earth + OSM +
 * cartografía geoestadística del INEGI): z9–13 en PNG para todo el estado y z14–16 en WebP con
 * manzanas, ejes viales y servicios de las localidades amanzanadas.
 */
class OfflineMapAssetsTest {

    private val tiles = File("src/main/assets/miniapps/mapa/tiles")

    private fun pngs(): List<File> =
        tiles.walkTopDown().filter { it.isFile && it.extension == "png" }.toList()

    private fun webps(): List<File> =
        tiles.walkTopDown().filter { it.isFile && it.extension == "webp" }.toList()

    @Test
    fun mapBundleShipsTiles() {
        assertTrue("missing tiles directory at " + tiles.absolutePath, tiles.isDirectory)

        val base = pngs().size
        val detail = webps().size
        assertTrue("expected more than 100 base tiles, found " + base, base > 100)
        assertTrue("expected more than 500 detail tiles, found " + detail, detail > 500)
    }

    @Test
    fun coversStateAndMetroZooms() {
        val zooms = tiles.listFiles { file -> file.isDirectory }
            ?.mapNotNull { it.name.toIntOrNull() }
            ?.toSet()
            .orEmpty()

        assertTrue("zoom 9 (estado) missing in " + zooms, 9 in zooms)
        assertTrue("zoom 13 (estado) missing in " + zooms, 13 in zooms)
        assertTrue("zoom 14 (detalle urbano) missing in " + zooms, 14 in zooms)
        assertTrue("zoom 15 (detalle urbano) missing in " + zooms, 15 in zooms)
        assertTrue("zoom 16 (detalle de barrio) missing in " + zooms, 16 in zooms)
        assertTrue(
            "el paquete no debe pasar de zoom 16 (maxNativeZoom): " + zooms,
            zooms.all { it <= 16 },
        )
        assertTrue("faltan teselas WebP de detalle", webps().any { it.parentFile?.parentFile?.name == "14" })
    }

    @Test
    fun detailTilesCoverColimaCentro() {
        val (x, y) = tileAt(19.2433, -103.7250, 16)
        val detail = File(tiles, "16/$x/$y.webp")

        assertTrue("falta la tesela z16 de Colima Centro: " + detail.path, detail.isFile)
        assertTrue("la tesela z16 no es WebP válida: " + detail.path, webpIsValid(detail))
        assertTrue("la tesela z16 está vacía", detail.length() > 2_000)
    }

    @Test
    fun detailCoversTheWholeState() {
        // Manzanillo y Tecomán tienen manzanas INEGI y deben tener z16 nativo, no z13 estirado.
        listOf(
            19.0512 to -104.3148,
            18.9103 to -103.8728,
            19.3273 to -103.7592,
        ).forEach { (lat, lon) ->
            val (x, y) = tileAt(lat, lon, 16)
            val detail = File(tiles, "16/$x/$y.webp")
            assertTrue("falta detalle z16 en " + lat + ", " + lon + ": " + detail.path, detail.isFile)
            assertTrue("detalle z16 inválido en " + detail.path, webpIsValid(detail))
            assertTrue("detalle z16 vacío en " + detail.path, detail.length() > 2_000)
        }
    }

    private fun tileAt(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
        val scale = 256.0 * (1 shl zoom)
        val x = ((lon + 180.0) / 360.0 * scale / 256).toInt()
        val sin = Math.sin(Math.toRadians(lat))
        val y = ((0.5 - Math.log((1 + sin) / (1 - sin)) / (4 * Math.PI)) * scale / 256).toInt()
        return x to y
    }

    @Test
    fun tilePathsFollowXYZLayout() {
        listOf(pngs().first(), webps().first()).forEach { sample ->
            val relative = sample.relativeTo(tiles).invariantSeparatorsPath
            val segments = relative.split("/")
            assertTrue("unexpected tile path: " + relative, segments.size == 3)
            assertTrue(segments[0].toIntOrNull() != null)
            assertTrue(segments[1].toIntOrNull() != null)
            assertTrue(segments[2].substringBefore('.').toIntOrNull() != null)
        }
    }

    @Test
    fun tilesAreRealMapImages() {
        // El paquete anterior eran 2 151 copias de la imagen «403 Access blocked» de OSM:
        // comprobamos que la base sigue siendo PNG de 256×256 de verdad.
        val sample = pngs().sortedBy { it.path }.take(28)
        sample.forEach { file ->
            val size = pngSize(file)
            assertNotNull("no es un PNG legible: " + file.path, size)
            assertEquals("tamaño inesperado en " + file.path, 256, size!!.first)
            assertEquals("tamaño inesperado en " + file.path, 256, size.second)
        }
    }

    /** Comprueba la firma RIFF/WEBP sin depender de decodificadores externos. */
    private fun webpIsValid(file: File): Boolean {
        val header = file.inputStream().use { it.readNBytes(12) }
        if (header.size < 12) return false
        if (String(header, 0, 4, Charsets.US_ASCII) != "RIFF") return false
        return String(header, 8, 4, Charsets.US_ASCII) == "WEBP"
    }

    /** Lee ancho y alto del IHDR sin depender de `javax.imageio` (no está en el classpath). */
    private fun pngSize(file: File): Pair<Int, Int>? {
        val header = file.inputStream().use { it.readNBytes(24) }
        if (header.size < 24) return null
        val signature = intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        for (index in signature.indices) {
            if ((header[index].toInt() and 0xFF) != signature[index]) return null
        }
        if (String(header, 12, 4, Charsets.US_ASCII) != "IHDR") return null
        fun int(offset: Int): Int =
            ((header[offset].toInt() and 0xFF) shl 24) or
                ((header[offset + 1].toInt() and 0xFF) shl 16) or
                ((header[offset + 2].toInt() and 0xFF) shl 8) or
                (header[offset + 3].toInt() and 0xFF)
        return int(16) to int(20)
    }
}
