package com.nahuanotoca.superapp.feature.home

import com.nahuanotoca.superapp.core.data.catalog.ServiceCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contrato del inicio: el mapa vive en un pergamino que se desenrolla al tocar y la pantalla ya no
 * ofrece saludo con el Tlalchichi ni lector de QR.
 *
 * Las ausencias se comprueban a propósito. Un saludo o un escáner que vuelven al código no fallan
 * solos: quedan compilando y la app los vuelve a mostrar sin que nadie lo note.
 *
 * Las rutas son relativas al módulo (`app/`): los assets y las fuentes se leen como
 * `src/main/...`, no como `app/src/main/...`.
 */
class HomeContractTest {

    private val homeDir = "src/main/java/com/nahuanotoca/superapp/feature/home"
    private val hero = File(homeDir, "HomeMapHero.kt")
    private val screen = File(homeDir, "HomeScreen.kt")
    private val routes = File("src/main/java/com/nahuanotoca/superapp/core/navigation/Routes.kt")
    private val host = File("src/main/java/com/nahuanotoca/superapp/ui/NahuaApp.kt")
    private val catalog = File("src/main/java/com/nahuanotoca/superapp/core/data/catalog/ServiceCatalog.kt")
    private val wallet = File("src/main/java/com/nahuanotoca/superapp/feature/wallet/WalletScreen.kt")

    private fun read(file: File): String {
        assertTrue("falta el archivo " + file.path, file.isFile)
        return file.readText()
    }

    @Test
    fun mapHeroIsAParchmentThatUnrollsOnTap() {
        val source = read(hero)

        assertTrue("el pergamino perdió la hoja de papel", source.contains("ParchmentSheet"))
        assertTrue("el rollo perdió la varilla de madera", source.contains("ScrollRoller"))
        assertTrue("desapareció el alto del rollo cerrado", source.contains("ScrollClosedHeight"))
        assertTrue("el estado abierto/cerrado no sobrevive a la rotación", source.contains("rememberSaveable"))
        assertTrue("no queda animación de desenrollado", source.contains("animateFloatAsState"))
        assertTrue("la hoja que tapa el mapa desapareció", source.contains("PaperVeil"))
        assertTrue("el ojo que abre/cierra se perdió", source.contains("ScrollHeader"))

        // Leaflet sigue guardando el tamaño: sin el aviso, los toques caen donde no es.
        assertTrue("el pergamino dejó de avisar el tamaño a Leaflet", source.contains("RESIZE_SCRIPT"))
        assertTrue("volvió un invalidateSize por fotograma", source.contains("RESIZE_GAP_MS"))

        val home = read(screen)
        assertTrue("el inicio dejó de pasar el alto desplegado", home.contains("expandedHeight = mapHeight"))
        assertFalse("el inicio volvió a fijar el alto del mapa a mano", home.contains(".height(mapHeight)"))
    }

    @Test
    fun welcomeGreetingAndQrReaderAreGone() {
        val home = read(screen)
        assertFalse("volvió el saludo de bienvenida", home.contains("HomeGreeting"))
        assertFalse("el Tlalchichi volvió a la portada", home.contains("Tlalchichi"))
        assertFalse("volvió el botón del lector de QR", home.contains("onScan"))

        assertNull("el servicio de QR sigue en el catálogo", ServiceCatalog.find("scan"))
        assertTrue(
            "el QR volvió a la lista de destacados",
            ServiceCatalog.featured.none { it.id == "scan" },
        )
        assertTrue(
            "el bundle del escáner sigue en el código",
            !File("src/main/java/com/nahuanotoca/superapp/feature/scan").exists(),
        )
        assertFalse("la ruta del escáner sigue declarada", read(routes).contains("SCAN"))
        assertFalse("el host volvió a montar el escáner", read(host).contains("Scan"))
        assertFalse("el catálogo sigue anunciando el QR", read(catalog).contains("QrCodeScanner"))
        assertFalse("la cartera manda a un escáner que ya no existe", read(wallet).contains("Escanear"))
    }

    @Test
    fun appsAreTwoHexagonRowsAndTheStatCardsAreGone() {
        val home = read(screen)
        assertTrue("desapareció el panal de hexágonos", home.contains("HexagonShape"))
        assertTrue("las celdas del panal ya no se dibujan", home.contains("ServiceHex"))
        assertTrue("el panal dejó de partirse en dos filas", home.contains("homeApps.take(4)") && home.contains("homeApps.drop(4)"))
        assertTrue("el panal dejó de medir su hueco real", home.contains("BoxWithConstraints"))
        assertTrue("el emulador de Game Boy Advance no está en el panal", home.contains("serviceId = \"gba\""))
        assertTrue(
            "falta el sprite del emulador",
            File("src/main/res/drawable-nodpi/px_icon_gba.png").isFile,
        )
        assertTrue("faltan los dos huecos para futuras apps", home.contains("EmptyHexSlot") && home.contains("Espacio libre para una futura app"))
        assertTrue("los huecos no se reservan al final de la fila", home.contains("trailingSlots"))
        assertFalse("volvió el carrusel horizontal de tarjetas", home.contains("LazyRow"))
        assertFalse("volvieron las tarjetas rectangulares", home.contains("HomeAppCard"))

        assertFalse("volvieron los tres recuadros de cifras", home.contains("HomeStats"))
        assertFalse("volvieron los paneles de cifras", home.contains("StatPanel"))
        assertFalse("el resumen del día sigue en la portada", home.contains("Ver detalle"))
        assertFalse("la portada vuelve a leer el saldo", home.contains("balanceCents"))
    }

    @Test
    fun featuredRailKeepsFiveServices() {
        assertEquals(5, ServiceCatalog.featured.size)
        assertEquals(
            listOf("ride", "bills", "wallet", "inbox", "profile"),
            ServiceCatalog.featured.map { it.id },
        )
    }
}
