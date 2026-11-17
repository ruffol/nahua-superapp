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
        listOf("ride", "bills", "mapa", "tamagochi", "bazar").forEach { id ->
            assertTrue("Falta la mini-app " + id, manifest.contains("\"" + id + "\""))
        }
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