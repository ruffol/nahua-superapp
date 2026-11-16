package com.nahuanotoca.superapp.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Una lectura de GPS. `timestampMillis` es la hora del fix (no la de recepción) para que la UI
 * pueda mostrar su antigüedad y no confundir una posición cacheada con una en vivo.
 */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMps: Float? = null,
    val bearingDegrees: Float? = null,
    val altitudeMeters: Double? = null,
    val timestampMillis: Long = 0L,
    val provider: String? = null,
)

/**
 * Envoltorio de [LocationManager] sin Google Play Services.
 *
 * - [currentFix] devuelve null si no hay permiso o no hay ninguna lectura reciente; nunca inventa
 *   una posición (la versión anterior devolvía Colima Centro y el mapa afirmaba que estabas ahí).
 * - [locationUpdates] emite un flujo continuo para el modo "GPS en vivo" del mapa, con velocidad,
 *   rumbo y altitud cuando el proveedor los reporta.
 */
class LocationProvider(private val context: Context) {

    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Primera posición disponible. Prefiere una lectura cacheada reciente y, si no hay, espera unos
     * segundos a GPS/red. Devuelve null cuando no hay forma de saber dónde está el usuario.
     */
    suspend fun currentFix(maxCacheAgeMillis: Long = 30 * 60_000L): LocationFix? =
        withContext(Dispatchers.Main.immediate) {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (!hasPermission() || manager == null) return@withContext null

            val cached = lastKnown(manager)
            if (cached != null && System.currentTimeMillis() - cached.time < 45_000L) {
                return@withContext cached.toFix()
            }

            val gps = awaitFix(manager, LocationManager.GPS_PROVIDER, 3_000L)
            val network = if (gps == null) {
                awaitFix(manager, LocationManager.NETWORK_PROVIDER, 2_000L)
            } else {
                null
            }
            val best = listOfNotNull(gps, network).maxByOrNull { it.time } ?: cached
            if (best == null) return@withContext null
            if (System.currentTimeMillis() - best.time > maxCacheAgeMillis) return@withContext null
            best.toFix()
        }

    /**
     * Flujo de posiciones para el seguimiento en vivo. El llamador debe cancelar la colección
     * (por ejemplo al cerrar la mini app o pausar la pantalla) para liberar el GPS.
     */
    @SuppressLint("MissingPermission")
    fun locationUpdates(
        intervalMillis: Long = 1_500L,
        minDistanceMeters: Float = 0f,
    ): Flow<LocationFix> = callbackFlow {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (!hasPermission() || manager == null) {
            close()
            return@callbackFlow
        }

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) {
            close()
            return@callbackFlow
        }

        val cached = lastKnown(manager)
        if (cached != null && System.currentTimeMillis() - cached.time < 30_000L) {
            trySend(cached.toFix())
        }

        var lastSentAt = 0L
        var lastLocation: Location? = null

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (location.accuracy > 5_000f) return
                val now = SystemClock.elapsedRealtime()
                val previous = lastLocation
                val moved = previous == null || location.distanceTo(previous) >= 0.4f
                if (now - lastSentAt < 450L && !moved) return
                if (previous != null && now - lastSentAt < 900L && !moved) return
                lastLocation = location
                lastSentAt = now
                trySend(location.toFix())
            }

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }

        val looper = Looper.getMainLooper()
        providers.forEach { provider ->
            runCatching {
                manager.requestLocationUpdates(provider, intervalMillis, minDistanceMeters, listener, looper)
            }
        }

        awaitClose {
            runCatching { manager.removeUpdates(listener) }
        }
    }

    private fun lastKnown(manager: LocationManager): Location? {
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        return providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    private suspend fun awaitFix(
        manager: LocationManager,
        provider: String,
        timeoutMillis: Long,
    ): Location? {
        val enabled = runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        if (!enabled) return null

        return withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        runCatching { manager.removeUpdates(this) }
                        if (continuation.isActive) continuation.resume(location)
                    }

                    override fun onProviderEnabled(provider: String) = Unit

                    override fun onProviderDisabled(provider: String) = Unit

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                }

                continuation.invokeOnCancellation {
                    runCatching { manager.removeUpdates(listener) }
                }

                try {
                    manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                } catch (security: SecurityException) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    }

    private fun Location.toFix(): LocationFix = LocationFix(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy,
        speedMps = if (hasSpeed()) speed else null,
        bearingDegrees = if (hasBearing()) bearing else null,
        altitudeMeters = if (hasAltitude()) altitude else null,
        timestampMillis = time,
        provider = provider,
    )
}
