package com.nahuanotoca.superapp.core.miniapp

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import org.json.JSONObject

data class PayRequest(
    val requestId: String,
    val amountCents: Long,
    val concept: String,
    val merchant: String,
)

/**
 * The only bridge exposed to mini-app JavaScript (window.NahuaNative). Every call is marshalled to
 * the main thread; payments require an explicit user confirmation in the host UI.
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
        val script = "window.Nahua && window.Nahua._resolve(" +
            JSONObject.quote(requestId) + ", " + JSONObject.quote(result.toString()) + ")"
        mainHandler.post { sendToWeb?.invoke(script) }
    }

    companion object {
        private const val TAG = "NahuaMiniApp"
    }
}