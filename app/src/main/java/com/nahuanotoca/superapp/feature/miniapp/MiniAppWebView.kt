package com.nahuanotoca.superapp.feature.miniapp

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.nahuanotoca.superapp.core.miniapp.MiniAppBridge
import com.nahuanotoca.superapp.core.miniapp.MiniAppManifest

/**
 * Sandboxed WebView host. Assets are served from https://appassets.androidplatform.net so the
 * mini-app runs on a secure origin (no file:// access, no cleartext traffic).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MiniAppWebView(
    manifest: MiniAppManifest,
    bridge: MiniAppBridge,
    reloadSignal: Int,
    onLoadingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val webViewState = remember { mutableStateOf<WebView?>(null) }

    val assetLoader = remember(context) {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }

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
                // Las teselas y el HTML son assets versionados con el APK: la caché de WebView
                // acelera el rezoomy y el reentrar a la mini app sin arriesgar datos viejos.
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.textZoom = 100
                settings.mediaPlaybackRequiresUserGesture = true
                settings.offscreenPreRaster = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)

                webViewClient = object : WebViewClientCompat() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

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
                        if (url.host == "appassets.androidplatform.net") return false
                        runCatching {
                            webViewContext.startActivity(Intent(Intent.ACTION_VIEW, url))
                        }
                        return true
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        Log.d("NahuaMiniApp", consoleMessage.message())
                        return true
                    }
                }

                addJavascriptInterface(bridge, "NahuaNative")
                bridge.sendToWeb = { script -> post { evaluateJavascript(script, null) } }

                loadUrl(manifest.entryUrl)
                webViewState.value = this
            }
        },
        update = {},
    )

    LaunchedEffect(reloadSignal) {
        if (reloadSignal > 0) webViewState.value?.reload()
    }
}