package com.nahuanotoca.superapp.core.location

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Hacia dónde mira el teléfono, tomando la pantalla como referencia.
 *
 * El aparato se sostiene de dos maneras y cada una tiene su eje de mirada:
 *  - **acostado**, como un mapa: manda el **borde superior de la pantalla** (el eje -Y, corregido
 *    por la rotación de pantalla, porque al girar el teléfono el «arriba» de la pantalla es otro
 *    eje físico);
 *  - **en alto**, como una cámara: manda el **eje de la cámara trasera** (el -Z, que sale por
 *    detrás de la pantalla).
 *
 * La mezcla pesa cada eje según cuánto apunta hacia arriba el borde superior: 0 acostado, 1 en
 * alto. Así el cono sigue la mirada en las dos posturas y en el camino entre ellas, sin el salto
 * que producía un vector fijo a 45°.
 *
 * Es matemática pura sobre la matriz de rotación del sensor: se puede probar sin Android.
 */
internal object LookDirection {

    /**
     * Vector unitario `[este, norte]` de la mirada, o `null` si el aparato apunta al cenit (ahí no
     * hay rumbo defendible). El vector, y no el ángulo, es lo que se suaviza después para no girar
     * al revés al cruzar el norte.
     */
    fun horizontal(rotationMatrix: FloatArray, displayRotation: Int): FloatArray? {
        // Eje del borde superior de la pantalla, ya en el sistema del aparato.
        val topX: Float
        val topY: Float
        when (displayRotation) {
            1 -> { topX = -1f; topY = 0f } // Surface.ROTATION_90
            2 -> { topX = 0f; topY = 1f }  // Surface.ROTATION_180
            3 -> { topX = 1f; topY = 0f }  // Surface.ROTATION_270
            else -> { topX = 0f; topY = -1f } // Surface.ROTATION_0
        }

        // La matriz lleva ejes del aparato a (este, norte, arriba) por filas.
        val topEast = rotationMatrix[0] * topX + rotationMatrix[1] * topY
        val topNorth = rotationMatrix[3] * topX + rotationMatrix[4] * topY
        val topUp = rotationMatrix[6] * topX + rotationMatrix[7] * topY

        // Cámara trasera: -Z del aparato.
        val cameraEast = -rotationMatrix[2]
        val cameraNorth = -rotationMatrix[5]

        val tilt = abs(topUp).coerceIn(0f, 1f)
        var east = topEast * (1f - tilt) + cameraEast * tilt
        var north = topNorth * (1f - tilt) + cameraNorth * tilt

        val length = hypot(east, north)
        if (length < MIN_HORIZONTAL) return null
        east /= length
        north /= length
        return floatArrayOf(east, north)
    }

    /** Postura del aparato: 0 acostado con la pantalla arriba, 1 en alto. */
    fun tilt(rotationMatrix: FloatArray, displayRotation: Int): Float {
        val topX: Float
        val topY: Float
        when (displayRotation) {
            1 -> { topX = -1f; topY = 0f }
            2 -> { topX = 0f; topY = 1f }
            3 -> { topX = 1f; topY = 0f }
            else -> { topX = 0f; topY = -1f }
        }
        val topUp = rotationMatrix[6] * topX + rotationMatrix[7] * topY
        return abs(topUp).coerceIn(0f, 1f)
    }

    /** Rumbo en grados (0 = norte, 90 = este) de un vector unitario este/norte. */
    fun degreesOf(east: Float, north: Float): Float {
        val degrees = Math.toDegrees(atan2(east.toDouble(), north.toDouble()))
        return ((degrees + 360.0) % 360.0).toFloat()
    }

    /** A menos de esto la mirada es vertical y ningún rumbo se sostiene. */
    private const val MIN_HORIZONTAL = 0.08f
}
