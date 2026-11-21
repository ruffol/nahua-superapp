package com.nahuanotoca.superapp.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Candado de la mirada «desde la pantalla»: acostado manda el borde superior, en alto manda la
 * cámara trasera, y la mezcla sigue el rumbo en las dos posturas.
 *
 * La matriz de rotación lleva ejes del aparato a (este, norte, arriba) por filas, igual que
 * `SensorManager.getRotationMatrixFromVector`. Con la identidad el teléfono está acostado con la
 * pantalla hacia arriba y el borde superior apuntando al **sur**.
 */
class LookDirectionTest {

    private val flat = floatArrayOf(
        1f, 0f, 0f,
        0f, 1f, 0f,
        0f, 0f, 1f,
    )

    /** En alto, pantalla vertical: el borde superior mira al cenit y la cámara al norte. */
    private val upright = floatArrayOf(
        -1f, 0f, 0f,
        0f, 0f, -1f,
        0f, -1f, 0f,
    )

    @Test
    fun layingFlatTheTopEdgeIsWhereYouLook() {
        val direction = LookDirection.horizontal(flat, ROTATION_0)
        assertNotNull("acostado siempre hay rumbo", direction)
        assertEquals("acostado manda el borde superior, que aquí apunta al sur", 180f, LookDirection.degreesOf(direction!![0], direction[1]), 0.5f)
        assertEquals("la postura acostada no puede pesar como la vertical", 0f, LookDirection.tilt(flat, ROTATION_0), 0.01f)
    }

    @Test
    fun heldUprightTheRearCameraIsWhereYouLook() {
        val direction = LookDirection.horizontal(upright, ROTATION_0)
        assertNotNull(direction)
        assertEquals("en alto manda la cámara trasera, que aquí mira al norte", 0f, LookDirection.degreesOf(direction!![0], direction[1]), 0.5f)
        assertEquals("la pantalla vertical es la postura en alto", 1f, LookDirection.tilt(upright, ROTATION_0), 0.01f)
    }

    @Test
    fun halfTiltBlendsBothAxesWithoutASeam() {
        // Misma postura que `upright`, inclinada 45° hacia atrás sobre el eje este.
        val leaned = rotateAboutEast(upright, Math.toRadians(45.0).toFloat())
        val tilt = LookDirection.tilt(leaned, ROTATION_0)
        assertTrue("a 45° la postura debe quedar entre acostado y en alto: " + tilt, tilt > 0.6f && tilt < 0.8f)

        val direction = LookDirection.horizontal(leaned, ROTATION_0)
        assertNotNull(direction)
        assertEquals("la mezcla no debe torcer el rumbo", 0f, LookDirection.degreesOf(direction!![0], direction[1]), 1f)
    }

    @Test
    fun displayRotationMovesTheScreenTopToAnotherPhysicalEdge() {
        // Mismo aparato, girado 90°: el «arriba» de la pantalla pasa a ser el borde izquierdo.
        val direction = LookDirection.horizontal(flat, ROTATION_90)
        assertNotNull(direction)
        assertEquals(270f, LookDirection.degreesOf(direction!![0], direction[1]), 0.5f)
    }

    @Test
    fun degreesWrapAroundNorth() {
        assertEquals(0f, LookDirection.degreesOf(0f, 1f), 0.01f)
        assertEquals(90f, LookDirection.degreesOf(1f, 0f), 0.01f)
        assertEquals(270f, LookDirection.degreesOf(-1f, 0f), 0.01f)
        assertEquals(359f, LookDirection.degreesOf(-0.017f, 1f), 1.5f)
    }

    /** Rota la postura alrededor del eje este del mundo, como inclinar el teléfono hacia atrás. */
    private fun rotateAboutEast(matrix: FloatArray, angle: Float): FloatArray {
        val c = cos(angle)
        val s = sin(angle)
        val result = FloatArray(9)
        for (column in 0 until 3) {
            val east = matrix[column]
            val north = matrix[3 + column]
            val up = matrix[6 + column]
            result[column] = east
            result[3 + column] = north * c - up * s
            result[6 + column] = north * s + up * c
        }
        return result
    }

    private companion object {
        // Surface.ROTATION_0 y Surface.ROTATION_90: son enteros también fuera de Android.
        const val ROTATION_0 = 0
        const val ROTATION_90 = 1
    }
}
