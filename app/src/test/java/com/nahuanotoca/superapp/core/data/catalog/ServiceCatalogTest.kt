package com.nahuanotoca.superapp.core.data.catalog

import com.nahuanotoca.superapp.core.model.ServiceTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        assertEquals(8, ServiceCatalog.featured.size)
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
