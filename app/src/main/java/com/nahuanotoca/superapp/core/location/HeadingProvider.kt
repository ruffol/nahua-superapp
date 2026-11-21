package com.nahuanotoca.superapp.core.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Rumbo del dispositivo: hacia dónde mira el teléfono cuando se sostiene en la mano.
 *
 * La referencia es la **pantalla**: acostado manda el borde superior (como un mapa) y en alto manda
 * el eje de la cámara trasera (como una foto); [LookDirection] mezcla los dos ejes según la postura
 * real, así que el cono no salta al pasar de una a otra. Se corrige por la rotación de pantalla
 * porque al girar el teléfono el «arriba» de la pantalla es otro eje físico.
 *
 * Se usa el sensor de rotación (`ROTATION_VECTOR`, con norte magnético) o, si el equipo no lo
 * tiene, `GAME_ROTATION_VECTOR` (sin norte real: el cono sigue sirviendo para girar). Las
 * lecturas se filtran como vector unitario para que el cono del mapa no tiemble.
 */
class HeadingProvider(private val context: Context) {

    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    /** true si el equipo puede orientar el cono; false se avisa a la página como "unsupported". */
    fun isAvailable(): Boolean = rotationSensor() != null

    /**
     * Rumbo en grados (0 = norte, 90 = este). El flujo se cierra al cancelar la colección, que
     * es lo que libera el sensor cuando la página deja de pedirlo.
     */
    fun headings(): Flow<Float> = callbackFlow {
        val manager = sensorManager
        val sensor = rotationSensor()
        if (manager == null || sensor == null) {
            close()
            return@callbackFlow
        }

        val rotationMatrix = FloatArray(9)
        var smoothX = 0f
        var smoothY = 0f
        var hasSmooth = false
        var lastEmitAt = 0L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

                val direction = LookDirection.horizontal(rotationMatrix, displayRotation()) ?: return
                val unitX = direction[0]
                val unitY = direction[1]
                if (!hasSmooth) {
                    smoothX = unitX
                    smoothY = unitY
                    hasSmooth = true
                } else {
                    // Suavizado exponencial sobre el vector (no sobre el ángulo) para no girar
                    // al revés al cruzar el norte.
                    smoothX += (unitX - smoothX) * SMOOTHING
                    smoothY += (unitY - smoothY) * SMOOTHING
                }

                val now = SystemClock.elapsedRealtime()
                if (now - lastEmitAt < EMIT_INTERVAL_MS) return
                lastEmitAt = now

                trySend(LookDirection.degreesOf(smoothX, smoothY))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { runCatching { manager.unregisterListener(listener) } }
    }

    private fun rotationSensor(): Sensor? {
        val manager = sensorManager ?: return null
        return manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    }

    /**
     * Rotación de pantalla en las constantes de `Surface.ROTATION_*`. Al girar el teléfono cambia
     * qué eje físico es «el arriba» de la pantalla.
     */
    @Suppress("DEPRECATION")
    private fun displayRotation(): Int {
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return Surface.ROTATION_0
        return runCatching { manager.defaultDisplay.rotation }.getOrDefault(Surface.ROTATION_0)
    }

    private companion object {
        const val SMOOTHING = 0.22f

        /** ~12 Hz: suficiente para que el cono siga el giro sin saturar el puente. */
        const val EMIT_INTERVAL_MS = 80L
    }
}
