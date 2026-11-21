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
        listOf("ride", "bills", "mapa", "yolcatl", "bazar", "gba").forEach { id ->
            assertTrue("Falta la mini-app " + id, manifest.contains("\"" + id + "\""))
        }
        assertFalse("El mercado suelto sigue en el manifiesto", manifest.contains("\"mercado\""))
    }

    /**
     * Candado de la integración: las tiendas oficiales viven DENTRO del bazar, con banner en la
     * portada, stock, catálogo versionado, aviso de frescura, actividad para auditar y reportes.
     * También fija la retirada de la valoración automática: el precio lo pone quien publica.
     */
    @Test
    fun bazarIntegratesOfficialStoresAndHasNoAutomaticValuation() {
        val bazar = ServiceCatalog.find("bazar")
        assertNotNull("Falta el servicio bazar", bazar)
        assertEquals(ServiceCategory.MARKET, bazar!!.category)
        assertNull("El mercado sigue como mini-app aparte", ServiceCatalog.find("mercado"))
        assertFalse("El bundle del mercado sigue en assets", File("src/main/assets/miniapps/mercado").exists())

        val root = File("src/main/assets/miniapps/bazar")
        listOf("index.html", "bazar.js", "bazar.css", "catalogo.js").forEach { name ->
            assertTrue("Falta " + name + " en el bundle del bazar", File(root, name).isFile)
        }

        val js = File(root, "bazar.js").readText()
        assertTrue("La portada perdió los banners de tiendas", js.contains("class=\"banner-rail\""))
        assertTrue("Los banners no abren la tienda", js.contains("data-act=\"store\""))
        assertTrue("La tienda no ordena por fijados", js.contains("pinned.indexOf"))
        assertTrue("Falta el estado del catálogo", js.contains("function catalogStatus"))
        assertTrue("El catálogo no expone actividad", js.contains("catalogo.audit()"))
        assertTrue("Falta el stock", js.contains("function localStock"))
        assertTrue("Faltan los motivos de reporte", js.contains("REPORT_REASONS"))
        assertTrue("Falta el botón de reportar artículo", js.contains("data-act=\"report-open\""))
        assertTrue("Falta el botón de reportar usuario", js.contains("data-act=\"report-user\""))

        assertFalse("Volvió la valoración automática", js.contains("Nahua.valuate"))
        assertFalse("Volvió el valor estimado", js.contains("valor estimado"))
        assertFalse("Volvió el panel de valoración", js.contains("valuationPanel"))
        assertFalse("Volvió el mínimo de trueque", js.contains("barterMinCents"))

        val catalogo = File(root, "catalogo.js").readText()
        assertTrue("El catálogo no aplica parches", catalogo.contains("applyDelta"))
        assertTrue("El catálogo no reconstruye versiones", catalogo.contains("function snapshot"))
        assertTrue("El catálogo no registra actividad", catalogo.contains("function audit"))
        assertTrue("El catálogo no guarda la fecha de sincronización", catalogo.contains("function syncedAt"))
    }

    /**
     * Candado del trueque con match: el chat y el contacto sólo existen con interés mutuo.
     */
    @Test
    fun bazarOnlyOpensTradeChatOnMutualInterest() {
        val js = File("src/main/assets/miniapps/bazar/bazar.js").readText()

        assertTrue("Falta la marca de interés", js.contains("function toggleInterest"))
        assertTrue("Falta la regla de reciprocidad", js.contains("function isMutual"))
        assertTrue("Falta la creación de matches", js.contains("function reconcileMatches"))
        assertTrue("El candado de la regla desapareció", js.contains("chat-only-on-mutual-interest"))
        assertTrue("Volvió la propuesta unilateral", !js.contains("data-act=\"trade\""))
        assertTrue("El interés recibido debe ser anónimo", js.contains("receivedInterestStats"))
        assertTrue("Falta el teléfono como contacto", js.contains("function formatPhone"))
        assertTrue("El perfil volvió al @usuario", !js.contains("handle:"))
        assertTrue("La versión de datos no subió", js.contains("nahua.bazar.v4"))
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
     * Candado del emulador: el bundle debe estar completo (motor + los dos nucleos + idiomas), los
     * nucleos no pueden ser una pagina de error del CDN, y el APK solo puede llevar la ROM de
     * demostracion que se decidio incluir (nada de .gba sueltos, ni zip/7z con ROMs dentro).
     */
    @Test
    fun gbaEmulatorBundleIsCompleteAndShipsOnlyTheDemoRom() {
        val manifest = File("src/main/assets/miniapps/index.json").readText()
        assertTrue("Falta la mini-app gba", manifest.contains("\"gba\""))
        assertTrue("El gba no pide modo inmersivo", manifest.contains("\"display\": \"immersive\""))
        // Vertical fijo: un Game Boy es vertical, y girar cambiaba la geometria del mando a
        // media partida.
        assertTrue("El gba no declara orientación vertical", manifest.contains("\"orientation\": \"portrait\""))
        assertTrue("El gba no declara lectura de ROMs", manifest.contains("\"rom.read\""))

        val root = File("src/main/assets/miniapps/gba")
        listOf(
            "index.html",
            "gba.js",
            "gba.css",
            "fonts/press-start-2p.ttf",
            "emulatorjs/loader.js",
            "emulatorjs/emulator.min.js",
            "emulatorjs/emulator.min.css",
            "emulatorjs/version.json",
            "emulatorjs/localization/es-ES.json",
            "emulatorjs/cores/mgba-wasm.data",
            "emulatorjs/cores/mgba-legacy-wasm.data",
            "emulatorjs/cores/reports/mgba.json",
            // Game Boy / Color: sin gambatte no hay .gbc ni .gb.
            "emulatorjs/cores/gambatte-wasm.data",
            "emulatorjs/cores/gambatte-legacy-wasm.data",
            "emulatorjs/cores/reports/gambatte.json",
            "emulatorjs/compression/extract7z.js",
            "games/smbdx.gbc",
        ).forEach { relative ->
            assertTrue("Falta " + relative + " en el bundle del emulador", File(root, relative).isFile)
        }

        // Un nucleo truncado (o una pagina de error del CDN) romperia la app en el primer arranque.
        listOf("mgba", "mgba-legacy", "gambatte", "gambatte-legacy").forEach { core ->
            assertTrue(
                "El nucleo " + core + "-wasm.data pesa muy poco",
                File(root, "emulatorjs/cores/" + core + "-wasm.data").length() > 500_000,
            )
        }
        // Sin el extractor 7z el nucleo no se descomprime y la pantalla se queda en negro.
        assertTrue(
            "El extractor 7z pesa muy poco",
            File(root, "emulatorjs/compression/extract7z.js").length() > 100_000,
        )
        // La demo incluida es un cartucho GBC completo de 1 MiB, no un archivo vacio ni truncado.
        assertEquals(1_048_576L, File(root, "games/smbdx.gbc").length())

        // Candado anti-ROM: el unico cartucho permitido es la demo; nada de .gba/.agb/.bin ni
        // archivos comprimidos (que es como se cuela una ROM sin que se vea en el diff).
        val forbidden = setOf("gba", "agb", "bin", "zip", "7z", "rar", "gbc", "gb")
        root.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = file.relativeTo(root).invariantSeparatorsPath
            if (relative == "games/smbdx.gbc") return@forEach
            assertTrue(
                "El bundle incluye un archivo prohibido: " + relative,
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
     * El mando direccional es propio (NahuaPad) y envia los botones del pad de RetroArch
     * directamente. Si alguien vuelve al joystick de EmulatorJS, el eje se desfasa con el
     * transform:scale del layout y "arriba" cae donde el dedo espera "derecha": el sintoma exacto
     * que se reporto en Super Mario Bros. Deluxe. Estas aserciones son el candado de la
     * orientacion: UP tiene que salir con dy negativa y RIGHT con dx positiva.
     */
    @Test
    fun gbaShipsJoystickAndConsoleSettingsPanel() {
        val gbaJs = File("src/main/assets/miniapps/gba/gba.js").readText()
        assertTrue("No existe el pad propio", gbaJs.contains("var NahuaPad"))
        assertTrue("El pad no envia botones al motor", gbaJs.contains("simulateInput(0, id"))
        assertTrue("UP no se dispara con dy negativa", gbaJs.contains("if (dy <= -limit) ids.push(UP)"))
        assertTrue("DOWN no se dispara con dy positiva", gbaJs.contains("if (dy >= limit) ids.push(DOWN)"))
        assertTrue("LEFT no se dispara con dx negativa", gbaJs.contains("if (dx <= -limit) ids.push(LEFT)"))
        assertTrue("RIGHT no se dispara con dx positiva", gbaJs.contains("if (dx >= limit) ids.push(RIGHT)"))
        assertTrue("Se perdio la cruceta propia", gbaJs.contains("nahua-dpad"))
        assertTrue("No se centra con getBoundingClientRect", gbaJs.contains("getBoundingClientRect()"))
        // El gamepad de EmulatorJS ya no dibuja direccion: solo botones.
        assertFalse("EmulatorJS sigue dibujando el joystick", gbaJs.contains("type: 'zone'"))
        assertTrue("No se declara EJS_VirtualGamepadSettings", gbaJs.contains("EJS_VirtualGamepadSettings"))
        // Tamano y posicion en vivo por CSS, recalculados al girar (no px fijos de EmulatorJS).
        assertTrue("No se aplica el layout del gamepad", gbaJs.contains("applyPadLayout"))
        assertTrue("El mando no se recalcula al girar", gbaJs.contains("orientationchange"))
        assertTrue("No se persiste la configuracion", gbaJs.contains("nahua.arcade.pad"))
        assertTrue("No existe el menu de la consola", gbaJs.contains("window.NahuaConsole"))

        val html = File("src/main/assets/miniapps/gba/index.html").readText()
        listOf("consolePanel", "padJoystick", "padDpad", "ctlScale", "ctlBottom", "ctlSide").forEach { id ->
            assertTrue("Falta el control " + id + " en el panel de la consola", html.contains(id))
        }
        // Accesos en la franja libre de arriba (esquinas redondeadas de los telefonos).
        listOf("gameBand", "gameBar", "gameSettings", "gamePadBtn").forEach { id ->
            assertTrue("Falta el acceso " + id + " en la franja del juego", html.contains(id))
        }
        // La pantalla conserva la proporcion del cartucho y se coloca debajo de la franja.
        assertTrue("No hay proporcion por consola", gbaJs.contains("SCREEN_RATIO"))
        assertTrue("No se ajusta la pantalla al hueco", gbaJs.contains("layoutScreen"))
        assertTrue("La proporcion de GB no es 160x144", gbaJs.contains("160 / 144"))
        assertTrue("La proporcion de GBA no es 240x160", gbaJs.contains("240 / 160"))
        // La posicion vertical de la pantalla es suya: mover los controles no debe mover el juego.
        assertTrue("No hay ajuste de posicion de pantalla", gbaJs.contains("nahua.arcade.screen"))
        assertTrue("La pantalla sigue dependiendo del mando", gbaJs.contains("height - bandHeight - 12"))
        assertTrue("Falta el control de posicion de pantalla", html.contains("ctlScreenPos"))
        // El mando vive fuera del rectangulo del juego y hay gesto de correr + saltar.
        assertTrue("La capa del mando no se saca de #display", gbaJs.contains("liftLayer"))
        assertTrue("No hay gesto de correr + saltar", gbaJs.contains("nahua-sprint"))
        assertTrue("El gesto no usa A ni B", gbaJs.contains("BUTTON_A") && gbaJs.contains("BUTTON_B"))
        assertTrue("Falta el interruptor del boton de correr", html.contains("data-sprint"))
        // Vertical fijo: el panel ya no ofrece girar la pantalla.
        assertTrue("El panel no dice que la orientación es fija", html.contains("Vertical · fijo"))
        assertTrue("El panel no pide la orientación vertical", gbaJs.contains("Nahua.orientation('portrait')"))

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