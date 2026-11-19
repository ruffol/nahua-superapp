package com.nahuanotoca.superapp.core.data.catalog

import com.nahuanotoca.superapp.core.model.ServiceCategory
import com.nahuanotoca.superapp.core.model.ServiceTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        listOf("ride", "bills", "mapa", "yolcatl", "bazar", "gba", "mercado").forEach { id ->
            assertTrue("Falta la mini-app " + id, manifest.contains("\"" + id + "\""))
        }
    }

    /**
     * Candado del Mercado: la portada es un landing con banners de tiendas oficiales, la tienda
     * ordena primero lo que ella fijó y el catálogo viaja por deltas (nunca completo otra vez),
     * conservando las versiones viejas en memoria.
     */
    @Test
    fun mercadoShipsBannersPinnedProductsAndDeltaCatalogue() {
        val mercado = ServiceCatalog.find("mercado")
        assertNotNull("Falta el servicio mercado", mercado)
        assertEquals(ServiceCategory.MARKET, mercado!!.category)
        assertEquals(ServiceTarget.MiniApp("mercado"), mercado.target)

        val root = File("src/main/assets/miniapps/mercado")
        listOf("index.html", "mercado.js", "mercado.css", "catalogo.js").forEach { name ->
            assertTrue("Falta " + name + " en el bundle del Mercado", File(root, name).isFile)
        }

        val js = File(root, "mercado.js").readText()
        assertTrue("La portada perdió el banner de tiendas", js.contains("store-banner"))
        assertTrue("El banner no es tocable", js.contains("data-act=\"store\""))
        assertTrue("La tienda no ordena por fijados", js.contains("pinned.indexOf"))
        assertTrue("La tienda no muestra los últimos agregados", js.contains("Últimos agregados"))
        assertTrue("La portada no lista lo recién llegado", js.contains("Recién llegado"))

        val catalogo = File(root, "catalogo.js").readText()
        assertTrue("El catálogo no expone parches", catalogo.contains("applyDelta"))
        assertTrue("El catálogo no sabe reconstruir versiones", catalogo.contains("function snapshot"))
        assertTrue("El catálogo no guarda historial", catalogo.contains("function history"))
        assertTrue("El catálogo no persiste en memoria", catalogo.contains("nahua.mercado.catalogo")
            || catalogo.contains("settings.key"))

        assertTrue("Falta el contrato de la API", File("../docs/MERCADO_API.md").isFile)
    }

    /**
     * Candado del trueque con match: el chat y el contacto sólo existen con interés mutuo. Si
     * alguien vuelve a la propuesta unilateral («Truquear» sin reciprocidad) este test falla.
     */
    @Test
    fun bazarOnlyOpensTradeChatOnMutualInterest() {
        val root = File("src/main/assets/miniapps/bazar")
        val js = File(root, "bazar.js").readText()

        assertTrue("Falta la marca de interés", js.contains("function toggleInterest"))
        assertTrue("Falta la regla de reciprocidad", js.contains("function isMutual"))
        assertTrue("Falta la creación de matches", js.contains("function reconcileMatches"))
        assertTrue("El candado de la regla desapareció", js.contains("chat-only-on-mutual-interest"))
        assertTrue("Volvió la propuesta unilateral", !js.contains("data-act=\"trade\""))
        assertTrue("El interés recibido debe ser anónimo", js.contains("receivedInterestStats"))
        assertTrue("Falta el teléfono como contacto", js.contains("function formatPhone"))
        assertTrue("El perfil volvió al @usuario", !js.contains("handle:"))
        assertTrue("La versión de datos no subió", js.contains("nahua.bazar.v3"))

        val barter = File("src/main/java/com/nahuanotoca/superapp/core/model/Barter.kt").readText()
        listOf("TEXTILES", "CRAFTS", "FOOD", "PLANTS", "ART", "SCHOOL").forEach { category ->
            assertTrue("Falta la categoría " + category + " para productos locales", barter.contains(category + "("))
        }
    }

    /**
     * Candado de Yōlcatl (antes tamagochi): el bundle nuevo debe traer los tres módulos del
     * rediseño —motor vóxel, cerebro offline y códec del alma— y no puede reintroducir la deuda
     * de la v1 (umbral de decaimiento, guardado redondeado, clave vieja como fuente principal).
     */
    @Test
    fun yolcatlBundleKeepsEngineAiAndMosaicCodec() {
        assertNull("El id tamagochi sigue en el catálogo", ServiceCatalog.find("tamagochi"))
        val manifest = File("src/main/assets/miniapps/index.json").readText()
        assertFalse("El manifiesto todavía anuncia tamagochi", manifest.contains("\"tamagochi\""))
        assertTrue("El manifiesto no declara yolcatl", manifest.contains("\"yolcatl\""))

        val root = File("src/main/assets/miniapps/yolcatl")
        assertTrue("Falta el bundle yolcatl", File(root, "index.html").isFile)
        listOf("engine3d.js", "ai-soul.js", "mosaic-codec.js", "app.js", "pixel.css").forEach { name ->
            assertTrue("Falta " + name + " en el bundle de Yōlcatl", File(root, name).isFile)
        }
        assertFalse("El bundle viejo sigue en assets", File("src/main/assets/miniapps/tamagochi").exists())

        val soul = File(root, "ai-soul.js").readText()
        assertFalse("Volvió el umbral de decaimiento de la v1", soul.contains("hours > 0.02"))
        assertTrue("El decaimiento por tick desapareció", soul.contains("function applyDecay"))
        assertTrue("El alma no se guarda en la clave v2", soul.contains("nahua.yolcatl.v2"))
        assertTrue("Falta la migración del alma v1", soul.contains("nahua.axolote.v1"))

        val mosaic = File(root, "mosaic-codec.js").readText()
        assertTrue("El mosaico perdió los 84 bytes útiles", mosaic.contains("USABLE_BYTES = 84"))
        assertTrue("El mosaico perdió el CRC-16", mosaic.contains("0x1021"))

        val engine = File(root, "engine3d.js").readText()
        assertTrue("El motor perdió el render WebGL", engine.contains("getContext('webgl'"))
        assertTrue("El motor perdió el respaldo 2D", engine.contains("draw2D"))
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
        // El emulador ya no fuerza horizontal: la orientación la elige el usuario en el panel
        // (Vertical incluido), y el manifiesto solo aporta el valor por defecto.
        assertTrue("El gba no declara orientación", manifest.contains("\"orientation\": \"auto\""))
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

    /**
     * Los controles del emulador son divs de EmulatorJS, no botones nativos: si alguien quita la
     * delegacion tactil o el puente, la vibracion desaparece y nada falla. La telemetria tampoco
     * es adorno: es lo unico que permite decidir la Fase 2 (nucleo nativo + AAudio) con datos en
     * lugar de intuicion, y se pierde sin ruido en cualquier refactor del bundle.
     */
    @Test
    fun gbaKeepsNativeHapticsAndFrameTelemetry() {
        val sdk = File("src/main/assets/miniapps/nahua-sdk.js").readText()
        assertTrue("El SDK no expone Nahua.haptic", sdk.contains("haptic: function"))
        assertTrue("El SDK no llama al puente haptic", sdk.contains("NahuaNative.haptic"))

        val bridge = File(
            "src/main/java/com/nahuanotoca/superapp/core/miniapp/MiniAppBridge.kt",
        ).readText()
        assertTrue("El puente perdio el metodo haptic", bridge.contains("fun haptic("))
        assertTrue("El puente perdio el callback onHaptic", bridge.contains("var onHaptic"))

        val gbaJs = File("src/main/assets/miniapps/gba/gba.js").readText()
        assertTrue(
            "gba.js no engancha los controles del gamepad",
            gbaJs.contains(".ejs_virtualGamepad_button"),
        )
        assertTrue("gba.js no aplica el tope de vibracion", gbaJs.contains("HAPTIC_GAP_MS"))
        assertTrue("gba.js no sondea el AudioContext", gbaJs.contains("installAudioProbe"))
        assertTrue("gba.js no mide frame pacing", gbaJs.contains("window.NahuaPerf"))
        assertTrue("gba.js no arranca la ventana de medicion", gbaJs.contains("Perf.start()"))
        // El presupuesto es el del GBA real (16.777216 MHz / 280896 ciclos por frame), no 16.67 ms.
        assertTrue("El presupuesto de frame no es el del GBA", gbaJs.contains("16.746"))
    }

    /**
     * El mando izquierdo es un joystick (no la cruceta por defecto de EmulatorJS) y su tamaño y
     * posicion se configuran desde un panel propio. Si alguien vuelve al gamepad por defecto o
     * quita el panel, el bundle no falla: simplemente el usuario pierde la configuracion.
     */
    @Test
    fun gbaShipsJoystickAndConsoleSettingsPanel() {
        val gbaJs = File("src/main/assets/miniapps/gba/gba.js").readText()
        // "zone" + joystickInput es el mecanismo de EmulatorJS para el stick analogico (N64).
        assertTrue("El mando izquierdo no es un joystick", gbaJs.contains("type: 'zone'"))
        assertTrue("El joystick no declara joystickInput", gbaJs.contains("joystickInput: true"))
        assertTrue("Se perdio la opcion de cruceta", gbaJs.contains("type: 'dpad'"))
        assertTrue("No se declara EJS_VirtualGamepadSettings", gbaJs.contains("EJS_VirtualGamepadSettings"))
        // Tamano y posicion en vivo por CSS (sin reconstruir el emulador).
        assertTrue("No se aplican variables CSS del gamepad", gbaJs.contains("applyControlsCss"))
        assertTrue("No se persiste la configuracion", gbaJs.contains("nahua.gba.controls"))
        assertTrue("No existe el menu de la consola", gbaJs.contains("window.NahuaConsole"))

        val html = File("src/main/assets/miniapps/gba/index.html").readText()
        listOf("consolePanel", "padJoystick", "padDpad", "ctlScale", "ctlBottom", "ctlSide").forEach { id ->
            assertTrue("Falta el control " + id + " en el panel de la consola", html.contains(id))
        }
        // Orientación: Auto / Vertical / Horizontal, aplicada al instante.
        listOf("oriAuto", "oriPortrait", "oriLandscape").forEach { id ->
            assertTrue("Falta el botón de orientación " + id, html.contains(id))
        }
        assertTrue("El panel no persiste la orientación", gbaJs.contains("orientation: 'auto'"))
        assertTrue("No se aplica la orientación", gbaJs.contains("applyOrientation"))

        val sdk = File("src/main/assets/miniapps/nahua-sdk.js").readText()
        assertTrue("El SDK no expone la orientación", sdk.contains("orientation: function"))

        // El boton Menú de la tira nativa abre ese panel, no directamente el menu de EmulatorJS.
        val screen = File(
            "src/main/java/com/nahuanotoca/superapp/feature/miniapp/MiniAppScreen.kt",
        ).readText()
        assertTrue("El boton Menú no abre NahuaConsole", screen.contains("NahuaConsole && window.NahuaConsole.toggle()"))
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