package com.nahuanotoca.superapp.core.map

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the offline map contract: the mini-app ships real tiles for Colima so the map still works
 * without connectivity.
 */
class OfflineMapAssetsTest {

    private val tiles = File("src/main/assets/miniapps/mapa/tiles")

    @Test
    fun mapBundleShipsTiles() {
        assertTrue("missing tiles directory at " + tiles.absolutePath, tiles.isDirectory)

        val pngs = tiles.walkTopDown().filter { it.isFile && it.extension == "png" }.toList()
        assertTrue("expected more than 100 tiles, found " + pngs.size, pngs.size > 100)
    }

    @Test
    fun coversStateAndMetroZooms() {
        val zooms = tiles.listFiles { file -> file.isDirectory }?.mapNotNull { it.name.toIntOrNull() }?.toSet().orEmpty()

        assertTrue("zoom 9 (state) missing in " + zooms, 9 in zooms)
        assertTrue("zoom 13 (state detail) missing in " + zooms, 13 in zooms)
        assertTrue("zoom 15 (metro) missing in " + zooms, 15 in zooms)
    }

    @Test
    fun tilePathsFollowXYZLayout() {
        val sample = tiles.walkTopDown()
            .first { it.isFile && it.extension == "png" }

        val relative = sample.relativeTo(tiles).invariantSeparatorsPath
        val segments = relative.split("/")
        assertTrue("unexpected tile path: " + relative, segments.size == 3)
        assertTrue(segments[0].toIntOrNull() != null)
        assertTrue(segments[1].toIntOrNull() != null)
        assertTrue(segments[2].removeSuffix(".png").toIntOrNull() != null)
    }
}
