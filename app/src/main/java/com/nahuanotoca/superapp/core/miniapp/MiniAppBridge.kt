package com.nahuanotoca.superapp.core.miniapp

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import com.nahuanotoca.superapp.core.location.LocationFix
import com.nahuanotoca.superapp.core.model.BarterValuator
import org.json.JSONObject

data class PayRequest(
    val requestId: String,
    val amountCents: Long,
    val concept: String,
    val merchant: String,
)

data class DirectionsRequest(
    val latitude: Double,
    val longitude: Double,
    val label: String,
)

/** Aviso de trueque que la mini app manda a la bandeja del anfitrión (Avisos). */
data class BarterNotice(
    val title: String,
    val body: String,
)

/**
 * The only bridge exposed to mini-app JavaScript (window.NahuaNative). Every call is marshalled to
 * the main thread; payments require an explicit user confirmation in the host UI.
 *
 * Location is exposed in two modes: [requestLocation] for a one-shot fix and
 * [startLocationUpdates]/[stopLocationUpdates] for the live GPS watcher used by the map. The host
 * pushes fixes with [emitLocation] using the same event channel as [emitStatus].
 */
class MiniAppBridge(
    private val sessionJson: () -> String,
    private val onPayRequest: (PayRequest) -> Unit,
    private val onClose: () -> Unit,
    private val onToast: (String) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Set by the WebView owner; receives JavaScript to evaluate on the UI thread. */
    @Volatile
    var sendToWeb: ((String) -> Unit)? = null

    /** Set by the host screen; the id is echoed back through [resolveLocation]. */
    var onLocationRequest: (String) -> Unit = {}

    /** Live GPS watcher: start with an interval in milliseconds, stop when the mini app pauses. */
    var onLocationWatchStart: (Long) -> Unit = {}

    var onLocationWatchStop: () -> Unit = {}

    /** "Ir a..." hands the destination to the platform Maps app. */
    var onDirectionsRequest: (DirectionsRequest) -> Unit = {}

    var onCopyToClipboard: (String) -> Unit = {}

    /** "A <usuario> le interesa truequear tu <objeto>": termina en la bandeja nativa de Avisos. */
    var onBarterNotice: (BarterNotice) -> Unit = {}

    @JavascriptInterface
    fun getSession(): String = sessionJson()

    @JavascriptInterface
    fun pay(payloadJson: String, requestId: String) {
        val payload = runCatching { JSONObject(payloadJson) }.getOrNull()
        val amountCents = payload?.optLong("amountCents", 0L) ?: 0L
        if (amountCents <= 0L) {
            resolvePay(requestId, success = false, error = "invalid_amount")
            return
        }
        val request = PayRequest(
            requestId = requestId,
            amountCents = amountCents,
            concept = payload?.optString("concept", "Pago en mini app") ?: "Pago en mini app",
            merchant = payload?.optString("merchant", "Nahua") ?: "Nahua",
        )
        mainHandler.post { onPayRequest(request) }
    }

    @JavascriptInterface
    fun requestLocation(payloadJson: String, requestId: String) {
        mainHandler.post { onLocationRequest(requestId) }
    }

    @JavascriptInterface
    fun startLocationUpdates(intervalMs: Int) {
        val interval = intervalMs.coerceIn(500, 15_000).toLong()
        mainHandler.post { onLocationWatchStart(interval) }
    }

    @JavascriptInterface
    fun stopLocationUpdates() {
        mainHandler.post { onLocationWatchStop() }
    }

    @JavascriptInterface
    fun openDirections(payloadJson: String) {
        val payload = runCatching { JSONObject(payloadJson) }.getOrNull() ?: return
        val lat = payload.optDouble("lat", Double.NaN)
        val lng = payload.optDouble("lng", Double.NaN)
        if (lat.isNaN() || lng.isNaN() || lat !in -90.0..90.0 || lng !in -180.0..180.0) return
        val label = payload.optString("label", "Destino").take(80)
        mainHandler.post { onDirectionsRequest(DirectionsRequest(lat, lng, label)) }
    }

    @JavascriptInterface
    fun copy(text: String) {
        mainHandler.post { onCopyToClipboard(text.take(500)) }
    }

    /**
     * Valora un objeto del bazar. Es síncrona a propósito: el formulario de publicación recalcula
     * el valor en cada tecla. La aritmética vive en Kotlin ([BarterValuator]), no en la página.
     */
    @JavascriptInterface
    fun valuate(payloadJson: String): String {
        val payload = runCatching { JSONObject(payloadJson) }.getOrNull()
            ?: return BarterCodec.errorJson("invalid_payload").toString()
        return BarterCodec.valuationJson(
            BarterValuator.valuate(BarterCodec.input(payload)),
        ).toString()
    }

    /** ¿Me conviene el trueque? Compara mi objeto contra el que me ofrecen. */
    @JavascriptInterface
    fun compareBarter(payloadJson: String): String {
        val payload = runCatching { JSONObject(payloadJson) }.getOrNull()
            ?: return BarterCodec.errorJson("invalid_payload").toString()
        val mine = BarterValuator.valuate(BarterCodec.input(payload.optJSONObject("mine")))
        val theirs = BarterValuator.valuate(BarterCodec.input(payload.optJSONObject("theirs")))
        return BarterCodec.balanceJson(BarterValuator.compare(mine, theirs)).toString()
    }

    /** Deja un aviso en Avisos: la notificación de que a alguien le interesa un trueque. */
    @JavascriptInterface
    fun announceBarter(payloadJson: String, requestId: String) {
        val payload = runCatching { JSONObject(payloadJson) }.getOrNull()
        val title = payload?.optString("title").orEmpty().trim().take(90)
        val body = payload?.optString("body").orEmpty().trim().take(240)
        if (payload == null || title.isEmpty() || body.isEmpty()) {
            deliver(requestId, BarterCodec.errorJson("invalid_payload"))
            return
        }
        mainHandler.post { onBarterNotice(BarterNotice(title = title, body = body)) }
        deliver(requestId, JSONObject().put("status", "ok"))
    }

    @JavascriptInterface
    fun toast(message: String) {
        mainHandler.post { onToast(message) }
    }

    @JavascriptInterface
    fun close() {
        mainHandler.post(onClose)
    }

    @JavascriptInterface
    fun log(message: String) {
        Log.d(TAG, message)
    }

    fun resolvePay(
        requestId: String,
        success: Boolean,
        error: String? = null,
        transactionId: String? = null,
        balanceCents: Long? = null,
    ) {
        val result = JSONObject().put("status", if (success) "ok" else "error")
        if (error != null) result.put("error", error)
        if (transactionId != null) result.put("transactionId", transactionId)
        if (balanceCents != null) result.put("balanceCents", balanceCents)
        deliver(requestId, result)
    }

    fun resolveLocation(requestId: String, fix: LocationFix?, error: String? = null) {
        val result = if (fix != null) {
            fixJson(fix)
        } else {
            JSONObject().put("status", "error").put("error", error ?: "unavailable")
        }
        deliver(requestId, result)
    }

    /** Pushes a live fix to the page; the SDK dispatches it to watchLocation listeners. */
    fun emitLocation(fix: LocationFix) {
        emit("location", fixJson(fix))
    }

    /** Notifies the page that the watcher could not start (permission, GPS off, no provider). */
    fun emitLocationError(error: String) {
        emit("locationError", JSONObject().put("status", "error").put("error", error))
    }

    private fun fixJson(fix: LocationFix): JSONObject {
        val result = JSONObject()
            .put("status", "ok")
            .put("lat", fix.latitude)
            .put("lng", fix.longitude)
            .put("accuracy", fix.accuracyMeters.toDouble())
            .put("timestamp", fix.timestampMillis)
        fix.speedMps?.let { result.put("speed", it.toDouble()) }
        fix.bearingDegrees?.let { result.put("bearing", it.toDouble()) }
        fix.altitudeMeters?.let { result.put("altitude", it) }
        fix.provider?.let { result.put("provider", it) }
        return result
    }

    private fun emit(event: String, payload: JSONObject) {
        val script = "window.Nahua && window.Nahua._emit(" +
            JSONObject.quote(event) + ", " + JSONObject.quote(payload.toString()) + ")"
        mainHandler.post { sendToWeb?.invoke(script) }
    }

    private fun deliver(requestId: String, result: JSONObject) {
        val script = "window.Nahua && window.Nahua._resolve(" +
            JSONObject.quote(requestId) + ", " + JSONObject.quote(result.toString()) + ")"
        mainHandler.post { sendToWeb?.invoke(script) }
    }

    companion object {
        private const val TAG = "NahuaMiniApp"
    }
}
