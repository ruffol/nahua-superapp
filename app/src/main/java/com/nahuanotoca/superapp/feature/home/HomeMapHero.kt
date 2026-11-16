package com.nahuanotoca.superapp.feature.home

import android.Manifest
import android.annotation.SuppressLint
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.nahuanotoca.superapp.core.design.Ochre
import com.nahuanotoca.superapp.core.design.PixelBodyText
import com.nahuanotoca.superapp.core.design.PixelBorder
import com.nahuanotoca.superapp.core.design.PixelCutShape
import com.nahuanotoca.superapp.core.design.PixelFrame
import com.nahuanotoca.superapp.core.design.PixelGlyph
import com.nahuanotoca.superapp.core.design.PixelPanelDeep
import com.nahuanotoca.superapp.core.design.PixelUiGlyphs
import com.nahuanotoca.superapp.core.miniapp.MiniAppBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Mapa offline real en el inicio: el mismo bundle de teselas de la mini app, en modo compacto,
 * dentro de un marco de barro. Deja a la vista los municipios y el botón de ubicación del propio
 * mapa, y añade el acceso al mapa a pantalla completa.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HomeMapHero(
    viewModel: HomeViewModel,
    onOpenMap: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationFix by viewModel.currentLocation.collectAsStateWithLifecycle()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pendingLocationRequest by remember { mutableStateOf<String?>(null) }

    val bridge = remember {
        MiniAppBridge(
            sessionJson = { "{}" },
            onPayRequest = {},
            onClose = onOpenMap,
            onToast = onMessage,
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val requestId = pendingLocationRequest
        pendingLocationRequest = null
        if (requestId == null) return@rememberLauncherForActivityResult
        if (granted) {
            viewModel.refreshLocation()
            scope.launch {
                delay(700)
                bridge.resolveLocation(requestId, viewModel.currentLocation.value, null)
                webViewRef?.evaluateJavascript(
                    "if (typeof locateUser === 'function') locateUser();",
                    null,
                )
            }
        } else {
            bridge.resolveLocation(requestId, null, "permission_denied")
            onMessage("Centrado en Colima Centro (modo local)")
        }
    }

    bridge.onLocationRequest = { requestId ->
        if (viewModel.hasLocationPermission()) {
            bridge.resolveLocation(requestId, locationFix, null)
        } else {
            pendingLocationRequest = requestId
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val assetLoader = remember(context) {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }

    PixelFrame(
        modifier = modifier,
        fill = PixelPanelDeep,
        border = PixelBorder,
        borderWidth = 3.dp,
        cut = 10.dp,
        shadow = 4.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(PixelCutShape(9.dp)),
                factory = { webViewContext ->
                    WebView(webViewContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowContentAccess = false
                        settings.allowFileAccess = false
                        // El mapa y sus teselas son assets: sin caché HTTP para que un update del APK
                        // no deje servida la versión anterior del bundle.
                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                        setBackgroundColor(android.graphics.Color.parseColor("#EFE3CC"))

                        webViewClient = object : WebViewClientCompat() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest,
                            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                            override fun onPageFinished(view: WebView, url: String) {
                                evaluateJavascript(
                                    "if (typeof triggerResize === 'function') triggerResize();",
                                    null,
                                )
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = true
                        }

                        addJavascriptInterface(bridge, "NahuaNative")
                        bridge.sendToWeb = { script -> post { evaluateJavascript(script, null) } }

                        loadUrl("https://appassets.androidplatform.net/assets/miniapps/mapa/index.html?mode=compact")
                        webViewRef = this
                    }
                },
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(PixelCutShape(4.dp))
                    .background(Ochre)
                    .clickable(onClick = onOpenMap)
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelGlyph(PixelUiGlyphs.expand, color = PixelPanelDeep, size = 14.dp)
                Spacer(Modifier.width(6.dp))
                PixelBodyText("Abrir mapa", size = 14.sp, color = PixelPanelDeep, maxLines = 1)
            }
        }
    }
}
