package com.nahuanotoca.superapp.core.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contrato de la mini app del mapa: lo que un refactor puede tirar sin que nada falle.
 *
 * El buscador se retiró, así que la ausencia de su marcado también se comprueba: una prueba que
 * sólo verifica lo nuevo deja volver el mecanismo viejo al lado del nuevo.
 */
class MapMiniAppContractTest {

    private val page = File("src/main/assets/miniapps/mapa/index.html")
    private val places = File("src/main/assets/miniapps/mapa/places.json")
    private val sdk = File("src/main/assets/miniapps/nahua-sdk.js")
    private val manifest = File("src/main/assets/miniapps/index.json")
    private val bridge = File("src/main/java/com/nahuanotoca/superapp/core/miniapp/MiniAppBridge.kt")
    private val heading = File("src/main/java/com/nahuanotoca/superapp/core/location/HeadingProvider.kt")

    private fun read(file: File): String {
        assertTrue("falta el archivo " + file.path, file.isFile)
        return file.readText()
    }

    @Test
    fun mapKeepsGpsAndCoordinatesInsteadOfSearch() {
        val html = read(page)

        assertTrue("la barra de GPS/coordenadas desapareció", html.contains("id=\"liveCoords\""))
        assertTrue("falta el estado del GPS", html.contains("id=\"liveText\""))
        assertTrue("desapareció el cono de orientación", html.contains("look-cone"))
        assertTrue("el marcador dejó de ser el ojo", html.contains("user-eye"))

        // La brújula pequeña es la referencia de la mirada: arriba del dial eres tú.
        assertTrue("la brújula pequeña desapareció", html.contains("id=\"compass\""))
        assertTrue("la rosa de la brújula no gira con el rumbo", html.contains("id=\"rose\""))
        assertTrue("la brújula no se actualiza con el rumbo", html.contains("function updateCompassUi"))
        assertTrue("falta el ojo que marca la mirada en la brújula", html.contains("eyeMark"))

        assertFalse("el buscador volvió al mapa", html.contains("searchrow"))
        assertFalse("el campo de búsqueda volvió", html.contains("id=\"search\""))
        assertFalse("la página vuelve a leer places.json", html.contains("places.json"))
        assertFalse("el dataset de búsqueda sigue empaquetado", places.exists())
    }

    @Test
    fun fogKeepsRadiusAndRejectsDoubtfulFixes() {
        val html = read(page)

        // El radio de despeje es el contrato con el usuario: 12 m, ni más ni menos.
        assertTrue("cambió el radio de la neblina", html.contains("REVEAL_RADIUS_M = 12"))
        assertTrue("se perdió el filtro de precisión", html.contains("REVEAL_MAX_ACCURACY_M = 8"))
        assertTrue("se perdió el filtro de antigüedad", html.contains("REVEAL_MAX_FIX_AGE_MS"))
        assertTrue("se perdió el filtro de saltos", html.contains("REVEAL_MAX_SPEED_MPS"))
        assertTrue("no hay capa de neblina", html.contains("L.GridLayer.extend"))
        assertTrue("el recorrido no se persiste", html.contains("nahua.mapa.neblina.v3"))
        assertTrue("falta el botón de la neblina", html.contains("id=\"fogBtn\""))
    }

    @Test
    fun headingFlowsFromSensorToThePage() {
        val html = read(page)
        val js = read(sdk)

        assertTrue("la página no pide el rumbo", html.contains("Nahua.watchHeading"))
        assertTrue("el SDK no expone watchHeading", js.contains("watchHeading: function"))
        assertTrue("el SDK no expone onHeadingError", js.contains("onHeadingError: function"))
        assertTrue("el SDK perdió la versión con brújula", js.contains("version: '1.7.0'"))

        val bridgeSource = read(bridge)
        assertTrue("el puente no publica el rumbo", bridgeSource.contains("fun emitHeading("))
        assertTrue("falta el arranque del sensor", bridgeSource.contains("fun startHeadingUpdates("))
        assertTrue("falta el apagado del sensor", bridgeSource.contains("fun stopHeadingUpdates("))

        val sensor = read(heading)
        assertTrue("el proveedor no usa el sensor de rotación", sensor.contains("TYPE_ROTATION_VECTOR"))
        assertTrue("sin respaldo de rotación relativa", sensor.contains("TYPE_GAME_ROTATION_VECTOR"))
        // El rumbo se mide desde la pantalla: acostado manda el borde superior, en alto la cámara.
        assertTrue("el proveedor no mezcla las dos posturas", sensor.contains("LookDirection.horizontal"))
        val look = read(File("src/main/java/com/nahuanotoca/superapp/core/location/LookDirection.kt"))
        assertTrue("la mirada dejó de corregirse por rotación de pantalla", look.contains("displayRotation"))
        assertTrue("desapareció la postura acostado/en alto", look.contains("fun tilt"))
    }

    @Test
    fun manifestAdvertisesTheMapVersionItShips() {
        val json = read(manifest)
        val start = json.indexOf("\"id\": \"mapa\"")
        assertTrue("el manifiesto no declara la mini app del mapa", start >= 0)

        val block = json.substring(start, minOf(json.length, start + 400))
        assertTrue("la versión del mapa no se actualizó: " + block, block.contains("\"version\": \"1.4.0\""))
        assertTrue("el manifiesto no anuncia la neblina", block.contains("neblina"))
    }
}
