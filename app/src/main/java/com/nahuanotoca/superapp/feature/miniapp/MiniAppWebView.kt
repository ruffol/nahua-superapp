package com.nahuanotoca.superapp.feature.miniapp

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebViewClientCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nahuanotoca.superapp.core.miniapp.MiniAppBridge
import com.nahuanotoca.superapp.core.miniapp.MiniAppManifest
import java.io.ByteArrayInputStream

/**
 * Sandboxed WebView host. Assets are served from https://appassets.androidplatform.net so the
 * mini-app runs on a secure origin (no file:// access, no cleartext traffic).
 *
 * Tres cosas que se aprendieron integrando el emulador:
 *  1. Todo request http(s) a otro host se responde con un 404 vacio, no se deja pasar. EmulatorJS
 *     trae un failsafe que cae a cdn.emulatorjs.org cuando falta un archivo del nucleo: sin este
 *     bloqueo habria trafico real con datos moviles y un timeout largo antes del error.
 *  2. El ciclo de vida pausa el WebView en ON_PAUSE (onPause + pauseTimers): sin eso, el
 *     AudioContext del emulador sigue sonando en segundo plano tras el boton Home.
 *  3. Si el proceso de render muere (OOM de GPU suele ser), se avisa al anfitrion en vez de matar
 *     la app: [recreateSignal] reemplaza el WebView por uno nuevo.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MiniAppWebView(
    manifest: MiniAppManifest,
    bridge: MiniAppBridge,
    reloadSignal: Int,
    recreateSignal: Int,
    immersive: Boolean,
    onLoadingChanged: (Boolean) -> Unit,
    onRendererGone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val webViewState = remember { mutableStateOf<WebView?>(null) }

    val assetLoader = remember(context) { MiniAppAssetLoader.build(context) }

    // key(recreateSignal): al cambiar, el AndroidView viejo se libera (onRelease -> destroy) y el
    // factory crea un WebView limpio. Es la unica forma sana de recuperarse de un renderer muerto.
    key(recreateSignal) {
        AndroidView(
            modifier = modifier,
            factory = { webViewContext ->
                WebView(webViewContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowContentAccess = false
                    settings.allowFileAccess = false
                    settings.allowFileAccessFromFileURLs = false
                    settings.allowUniversalAccessFromFileURLs = false
                    settings.setGeolocationEnabled(false)
                    // Las teselas y el HTML son assets versionados con el APK: la cache de WebView
                    // acelera el rezoom y el reentrar a la mini app sin arriesgar datos viejos.
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.setSupportZoom(false)
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    settings.textZoom = 100
                    // Inmersivo: el emulador arranca su audio desde el primer frame; en modo
                    // chrome se mantiene el gesto para que un video suelto no suene solo.
                    settings.mediaPlaybackRequiresUserGesture = !immersive
                    settings.offscreenPreRaster = true
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)

                    webViewClient = object : WebViewClientCompat() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? {
                            val url = request.url
                            val scheme = url.scheme ?: return assetLoader.shouldInterceptRequest(url)
                            val isWeb = scheme.equals("http", ignoreCase = true) ||
                                scheme.equals("https", ignoreCase = true)
                            // El bundle es offline: cualquier fuga hacia otro origen se corta con
                            // un 404 vacio (0 ms, sin red). data: y blob: pasan, son locales.
                            if (isWeb && !url.host.equals(MiniAppAssetLoader.HOST, ignoreCase = true)) {
                                Log.w(TAG, "Request externo bloqueado: " + url)
                                return emptyResponse()
                            }
                            return assetLoader.shouldInterceptRequest(url)
                        }

                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            onLoadingChanged(true)
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            onLoadingChanged(false)
                            view.evaluateJavascript(
                                "if (typeof triggerResize === 'function') triggerResize();",
                                null,
                            )
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            val url = request.url
                            if (url.host == MiniAppAssetLoader.HOST) return false
                            runCatching {
                                webViewContext.startActivity(Intent(Intent.ACTION_VIEW, url))
                            }
                            return true
                        }

                        override fun onRenderProcessGone(
                            view: WebView,
                            detail: RenderProcessGoneDetail,
                        ): Boolean {
                            Log.w(TAG, "Render process gone (crash=" + detail.didCrash() + ")")
                            onRendererGone()
                            // true: la app absorbe la muerte y decide (recrear o cerrar).
                            return true
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                            Log.d(TAG, consoleMessage.message())
                            return true
                        }
                    }

                    addJavascriptInterface(bridge, "NahuaNative")
                    bridge.sendToWeb = { script -> post { evaluateJavascript(script, null) } }
                    // La háptica la ejecuta el WebView: es el View que está bajo el dedo, no la
                    // Activity, y así no hace falta permiso VIBRATE ni un Vibrator propio.
                    bridge.onHaptic = { style -> performHapticFeedback(hapticConstant(style)) }

                    loadUrl(manifest.entryUrl)
                    webViewState.value = this
                }
            },
            update = {},
            onRelease = { view ->
                // El proceso de render pudo morir: destruir es lo unico seguro antes de crear otro.
                bridge.onHaptic = {}
                runCatching {
                    view.destroy()
                }
            },
        )
    }

    // Pausa/reanuda el WebView con la Activity. onPause() detiene el layout y el AudioContext del
    // emulador; pauseTimers() congela los timers de JS (peticion global del proceso, y esta
    // pantalla es el unico WebView vivo de la app).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val web = webViewState.value ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    web.onPause()
                    web.pauseTimers()
                }
                Lifecycle.Event.ON_RESUME -> {
                    web.resumeTimers()
                    web.onResume()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webViewState.value?.onPause()
        }
    }

    LaunchedEffect(reloadSignal) {
        if (reloadSignal > 0) webViewState.value?.reload()
    }
}

/**
 * Traduce el estilo que pide la página a una constante de háptica. Las constantes modernas
 * (CONFIRM/REJECT, API 30) caen a las clásicas en minSdk 26; un valor desconocido no vibra
 * raro: cae en VIRTUAL_KEY, que es el "clic de tecla" del sistema.
 */
private fun hapticConstant(style: String): Int = when (style.trim().lowercase()) {
    "tap" -> HapticFeedbackConstants.KEYBOARD_TAP
    "long", "longpress" -> HapticFeedbackConstants.LONG_PRESS
    "confirm", "ok" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.VIRTUAL_KEY
    }
    "reject", "error" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.REJECT
    } else {
        HapticFeedbackConstants.LONG_PRESS
    }
    else -> HapticFeedbackConstants.VIRTUAL_KEY
}

/** 404 vacio e inmediato: el WebView no toca la red y la pagina ve el fallo al instante. */
private fun emptyResponse(): WebResourceResponse = WebResourceResponse(
    "text/plain",
    "utf-8",
    404,
    "Blocked",
    emptyMap(),
    ByteArrayInputStream(ByteArray(0)),
)

private const val TAG = "NahuaMiniApp"
