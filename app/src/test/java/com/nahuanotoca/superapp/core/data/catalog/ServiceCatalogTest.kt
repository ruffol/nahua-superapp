package com.nahuanotoca.superapp.core.data.catalog

import com.nahuanotoca.superapp.core.model.ServiceCategory
import com.nahuanotoca.superapp.core.model.ServiceTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ServiceCatalogTest {

    @Test
    fun everyServiceHasUniqueId() {
        val ids = ServiceCatalog.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun featuredServicesExistInCatalog() {
        assertEquals(6, ServiceCatalog.featured.size)
        ServiceCatalog.featured.forEach { service ->
            assertNotNull(ServiceCatalog.find(service.id))
        }
    }

    @Test
    fun everyCategoryHasAtLeastOneService() {
        ServiceCatalog.all.groupBy { it.category }.forEach { (category, services) ->
            assertTrue("Category " + category + " is empty", services.isNotEmpty())
        }
    }

    @Test
    fun removedServicesAreNoLongerOffered() {
        listOf("food", "cinema", "pharmacy", "insurance").forEach { id ->
            assertNull("El servicio " + id + " sigue en el catálogo", ServiceCatalog.find(id))
        }
    }

    @Test
    fun bazarReplacesTheOldTianguisAndKeepsItsBundle() {
        val bazar = ServiceCatalog.find("bazar")
        assertNotNull("Falta el servicio bazar", bazar)
        assertEquals("Bazar/Trueque", bazar!!.name)
        assertEquals(ServiceCategory.MARKET, bazar.category)
        assertNull("El tianguis anterior sigue en el catálogo", ServiceCatalog.find("tianguis"))

        val moduleRoot = File("src/main/assets/miniapps/bazar")
        assertTrue("Falta el bundle del bazar", File(moduleRoot, "index.html").isFile)
        assertTrue("Falta la lógica del bazar", File(moduleRoot, "bazar.js").isFile)
        assertTrue("Falta el estilo del bazar", File(moduleRoot, "bazar.css").isFile)

        val manifest = File("src/main/assets/miniapps/index.json").readText()
        assertTrue("El bazar no declara el aviso de trueques", manifest.contains("\"inbox.notify\""))
        assertTrue("El bazar no declara almacenamiento local", manifest.contains("\"storage.local\""))
    }

    @Test
    fun remainingMiniAppsStayDeclared() {
        val manifest = File("src/main/assets/miniapps/index.json").readText()
        listOf("ride", "bills", "mapa", "tamagochi", "bazar", "gba").forEach { id ->
            assertTrue("Falta la mini-app " + id, manifest.contains("\"" + id + "\""))
        }
    }

    /**
     * Candado del emulador: el bundle debe estar completo (motor + nucleo + idiomas), el nucleo
     * no puede ser una pagina de error del CDN, y ni el repo ni el APK pueden colar una ROM.
     */
    @Test
    fun gbaEmulatorBundleIsCompleteAndShipsNoRoms() {
        val manifest = File("src/main/assets/miniapps/index.json").readText()
        assertTrue("Falta la mini-app gba", manifest.contains("\"gba\""))
        assertTrue("El gba no pide modo inmersivo", manifest.contains("\"display\": \"immersive\""))
        assertTrue("El gba no pide horizontal", manifest.contains("\"orientation\": \"landscape\""))
        assertTrue("El gba no declara lectura de ROMs", manifest.contains("\"rom.read\""))

        val root = File("src/main/assets/miniapps/gba")
        listOf(
            "index.html",
            "gba.js",
            "gba.css",
            "emulatorjs/loader.js",
            "emulatorjs/emulator.min.js",
            "emulatorjs/emulator.min.css",
            "emulatorjs/version.json",
            "emulatorjs/localization/es-ES.json",
            "emulatorjs/cores/mgba-wasm.data",
            "emulatorjs/cores/mgba-legacy-wasm.data",
            "emulatorjs/cores/reports/mgba.json",
            "emulatorjs/compression/extract7z.js",
        ).forEach { relative ->
            assertTrue("Falta " + relative + " en el bundle del emulador", File(root, relative).isFile)
        }

        // Un nucleo truncado (o una pagina de error del CDN) romperia la app en el primer arranque.
        assertTrue(
            "El nucleo mgba-wasm.data pesa muy poco",
            File(root, "emulatorjs/cores/mgba-wasm.data").length() > 500_000,
        )
        assertTrue(
            "El nucleo legacy pesa muy poco",
            File(root, "emulatorjs/cores/mgba-legacy-wasm.data").length() > 500_000,
        )
        // Sin el extractor 7z el nucleo no se descomprime y la pantalla se queda en negro.
        assertTrue(
            "El extractor 7z pesa muy poco",
            File(root, "emulatorjs/compression/extract7z.js").length() > 100_000,
        )

        // Candado anti-ROM: nada de .gba/.agb/.bin/.zip dentro del bundle versionado.
        val forbidden = setOf("gba", "agb", "bin", "zip", "7z", "rar")
        root.walkTopDown().filter { it.isFile }.forEach { file ->
            assertTrue(
                "El bundle incluye un archivo prohibido: " + file,
                file.extension.lowercase() !in forbidden,
            )
        }

        assertNotNull("Falta el servicio del emulador en el catalogo", ServiceCatalog.find("gba"))
    }

    @Test
    fun searchIsCaseInsensitiveAndMatchesTagline() {
        val byName = ServiceCatalog.search("ride")
        assertEquals("ride", byName.first().id)

        val byCategory = ServiceCatalog.search("transporte")
        assertTrue(byCategory.any { it.id == "ride" })
    }

    @Test
    fun searchWithBlankQueryReturnsEverything() {
        assertEquals(ServiceCatalog.all.size, ServiceCatalog.search("   ").size)
    }

    @Test
    fun everyMiniAppTargetHasABundleInAssets() {
        val miniApps = ServiceCatalog.all.mapNotNull { service ->
            (service.target as? ServiceTarget.MiniApp)?.appId
        }
        assertTrue(miniApps.isNotEmpty())

        val moduleRoot = File("src/main/assets/miniapps")
        assertTrue("Missing assets directory at " + moduleRoot.absolutePath, moduleRoot.isDirectory)

        miniApps.forEach { appId ->
            val entry = File(moduleRoot, appId + "/index.html")
            assertTrue("Missing bundle for " + appId + " at " + entry.absolutePath, entry.isFile)
        }

        val index = File(moduleRoot, "index.json").readText()
        miniApps.forEach { appId ->
            assertTrue("Manifest does not declare " + appId, index.contains("\"" + appId + "\""))
        }
    }
}