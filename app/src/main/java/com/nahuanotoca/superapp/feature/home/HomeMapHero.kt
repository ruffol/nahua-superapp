package com.nahuanotoca.superapp.feature.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Mapa offline real en el inicio: el mismo bundle de teselas de la mini app, en modo compacto,
 * dentro de un marco de barro. Deja a la vista el GPS, las coordenadas y la neblina, y añade el
 * acceso al mapa a pantalla completa.
 *
 * Dos cosas se aprendieron a golpes y aquí quedan blindadas:
 *  1. `pauseTimers()` es global al proceso (lo usa el WebView del mapa grande al pausarse), así
 *     que al volver al inicio los timers de este WebView podían quedar congelados y el mapa
 *     "no respondía" al arrastrarlo. En ON_RESUME se hace `resumeTimers()`.
 *  2. Leaflet guarda el tamaño del contenedor: si el marco cambia de alto (o llega el primer
 *     layout después del alta del WebView), hay que avisar con `triggerResize()` o los toques
 *     caen donde no es. Se avisa en cada cambio real de tamaño y al terminar de cargar.
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val webViewState = remember { mutableStateOf<WebView?>(null) }
    val lastSize = remember { intArrayOf(0, 0) }
    val locationJob = remember { mutableStateOf<Job?>(null) }

    var pendingLocationRequest by remember { mutableStateOf<String?>(null) }
    var pendingWatchInterval by remember { mutableStateOf<Long?>(null) }
    var watchInterval by remember { mutableStateOf<Long?>(null) }
    var headingWanted by remember { mutableStateOf(false) }

    val currentOpenMap by rememberUpdatedState(onOpenMap)
    val currentMessage by rememberUpdatedState(onMessage)

    val bridge = remember {
        MiniAppBridge(
            sessionJson = { "{}" },
            onPayRequest = {},
            onClose = { currentOpenMap() },
            onToast = { currentMessage(it) },
        )
    }

    val assetLoader = remember(context) {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val requestId = pendingLocationRequest
        val watch = pendingWatchInterval
        pendingLocationRequest = null
        pendingWatchInterval = null
        val allowed = granted || viewModel.hasLocationPermission()
        if (!allowed) {
            if (requestId != null) bridge.resolveLocation(requestId, null, "permission_denied")
            if (watch != null) bridge.emitLocationError("permission_denied")
            currentMessage("Permite la ubicación para verte en el mapa")
            return@rememberLauncherForActivityResult
        }
        if (requestId != null) {
            scope.launch {
                val fix = viewModel.locationNow()
                bridge.resolveLocation(requestId, fix, if (fix == null) "unavailable" else null)
            }
        }
        if (watch != null) watchInterval = watch
    }

    // GPS en vivo del inicio: la neblina necesita la posición continua, no un solo punto.
    LaunchedEffect(watchInterval) {
        val interval = watchInterval
        locationJob.value?.cancel()
        locationJob.value = null
        if (interval == null) return@LaunchedEffect
        locationJob.value = launch {
            viewModel.locationUpdates(interval).collect { fix -> bridge.emitLocation(fix) }
        }
    }

    // Brújula para el cono del ojo; se apaga al salir de la pantalla o pausar la app.
    LaunchedEffect(headingWanted, lifecycleOwner) {
        if (!headingWanted) return@LaunchedEffect
        if (!viewModel.hasHeadingSensor()) {
            bridge.emitHeadingError("unsupported")
            return@LaunchedEffect
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.headings().collect { degrees -> bridge.emitHeading(degrees) }
        }
    }

    SideEffect {
        bridge.onLocationRequest = { requestId ->
            if (viewModel.hasLocationPermission()) {
                scope.launch {
                    val fix = viewModel.locationNow()
                    bridge.resolveLocation(requestId, fix, if (fix == null) "unavailable" else null)
                }
            } else {
                pendingLocationRequest = requestId
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        bridge.onLocationWatchStart = { interval ->
            if (viewModel.hasLocationPermission()) {
                watchInterval = interval
            } else {
                pendingWatchInterval = interval
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        bridge.onLocationWatchStop = { watchInterval = null }
        bridge.onHeadingWatchStart = { headingWanted = true }
        bridge.onHeadingWatchStop = { headingWanted = false }
        bridge.onCopyToClipboard = { text -> copyToClipboard(context, text, currentMessage) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val web = webViewState.value ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE -> web.onPause()
                Lifecycle.Event.ON_RESUME -> {
                    // Otra pantalla pudo dejar los timers del proceso en pausa (ver KDoc).
                    web.resumeTimers()
                    web.onResume()
                    web.evaluateJavascript(RESIZE_SCRIPT, null)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            locationJob.value?.cancel()
            locationJob.value = null
        }
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
                    .clip(PixelCutShape(9.dp))
                    .onSizeChanged { size ->
                        if (size.width <= 0 || size.height <= 0) return@onSizeChanged
                        val changed = size.width != lastSize[0] || size.height != lastSize[1]
                        if (!changed) return@onSizeChanged
                        lastSize[0] = size.width
                        lastSize[1] = size.height
                        webViewState.value?.evaluateJavascript(RESIZE_SCRIPT, null)
                    },
                factory = { webViewContext ->
                    WebView(webViewContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowContentAccess = false
                        settings.allowFileAccess = false
                        settings.allowFileAccessFromFileURLs = false
                        settings.allowUniversalAccessFromFileURLs = false
                        settings.setGeolocationEnabled(false)
                        // Los assets van versionados con el APK; la caché de WebView acelera el
                        // rezoomy del mapa sin dejar HTML viejo entre actualizaciones.
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.setSupportZoom(false)
                        settings.builtInZoomControls = false
                        settings.displayZoomControls = false
                        settings.textZoom = 100
                        settings.mediaPlaybackRequiresUserGesture = true
                        settings.offscreenPreRaster = true
                        setBackgroundColor(android.graphics.Color.parseColor("#EFE3CC"))

                        // El WebView del mapa grande pudo pausar los timers de todo el proceso.
                        resumeTimers()

                        webViewClient = object : WebViewClientCompat() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest,
                            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                            override fun onPageFinished(view: WebView, url: String) {
                                evaluateJavascript(RESIZE_SCRIPT, null)
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = true
                        }

                        addJavascriptInterface(bridge, "NahuaNative")
                        bridge.sendToWeb = { script -> post { evaluateJavascript(script, null) } }

                        loadUrl("https://appassets.androidplatform.net/assets/miniapps/mapa/index.html?mode=compact")
                        webViewState.value = this
                    }
                },
                onRelease = { view ->
                    // Sin destroy() cada visita al inicio dejaba un WebView vivo con su GPS y su
                    // proceso de render colgado: el siguiente mapa acababa sin responder.
                    bridge.sendToWeb = null
                    webViewState.value = null
                    locationJob.value?.cancel()
                    locationJob.value = null
                    runCatching { view.destroy() }
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

private const val RESIZE_SCRIPT = "if (typeof triggerResize === 'function') triggerResize();"

private fun copyToClipboard(context: Context, text: String, onMessage: (String) -> Unit) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Coordenadas", text))
        onMessage("Coordenadas copiadas")
    }
}
