package com.nahuanotoca.superapp.feature.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
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
import com.nahuanotoca.superapp.core.design.PixelCutShape
import com.nahuanotoca.superapp.core.design.PixelFrame
import com.nahuanotoca.superapp.core.design.PixelGlyph
import com.nahuanotoca.superapp.core.design.PixelLabel
import com.nahuanotoca.superapp.core.design.PixelPanelDeep
import com.nahuanotoca.superapp.core.design.PixelTitle
import com.nahuanotoca.superapp.core.design.PixelUiGlyphs
import com.nahuanotoca.superapp.core.miniapp.MiniAppBridge
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Mapa offline del inicio dentro de un pergamino de papel.
 *
 * Cerrado es un rollo con su varilla de madera: una franja de papel con el título y la pista de
 * «toca para desenrollar». Al tocarlo el papel se desenrolla hasta el alto del mapa y la varilla de
 * abajo baja con él; al tocar el encabezado o la varilla, se vuelve a enrollar. El estado sobrevive
 * a la rotación (`rememberSaveable`) y, como el WebView no se destruye al cerrar, el rollo tapa el
 * mapa con una hoja de papel mientras anima el alto en vez de recargar teselas en cada toque.
 *
 * Dos cosas se aprendieron a golpes y aquí quedan blindadas:
 *  1. `pauseTimers()` es global al proceso (lo usa el WebView del mapa grande al pausarse), así
 *     que al volver al inicio los timers de este WebView podían quedar congelados y el mapa
 *     "no respondía" al arrastrarlo. En ON_RESUME se hace `resumeTimers()`.
 *  2. Leaflet guarda el tamaño del contenedor: si el marco cambia de alto (o llega el primer
 *     layout después del alta del WebView), hay que avisar con `triggerResize()` o los toques
 *     caen donde no es. Se avisa en cada cambio real de tamaño —con un freno para no disparar un
 *     `invalidateSize` por fotograma durante el desenrollado— y al terminar la animación.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HomeMapHero(
    viewModel: HomeViewModel,
    expandedHeight: Dp,
    onOpenMap: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val webViewState = remember { mutableStateOf<WebView?>(null) }
    val lastSize = remember { intArrayOf(0, 0) }
    val lastResizeAt = remember { longArrayOf(0L) }
    val locationJob = remember { mutableStateOf<Job?>(null) }

    // El pergamino arranca cerrado: el inicio se lee de un vistazo y el mapa se desenrolla al tocar.
    var open by rememberSaveable { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing),
        label = "pergamino",
    )
    val toggle: () -> Unit = { open = !open }
    val height = ScrollClosedHeight * (1f - progress) + expandedHeight * progress

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

    // El último tamaño lo decide la animación, no cada fotograma: al terminar se avisa a Leaflet.
    LaunchedEffect(open) {
        if (!open) return@LaunchedEffect
        delay(520)
        webViewState.value?.evaluateJavascript(RESIZE_SCRIPT, null)
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
        modifier = modifier
            .height(height)
            .then(if (open) Modifier else Modifier.clickable(onClick = toggle)),
        fill = ParchmentMid,
        border = ParchmentEdge,
        borderWidth = 2.dp,
        cut = 8.dp,
        shadow = 4.dp,
    ) {
        ParchmentSheet(Modifier.fillMaxSize())

        Column(modifier = Modifier.fillMaxSize()) {
            ScrollHeader(open = open, onToggle = toggle)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 9.dp, end = 9.dp, bottom = 1.dp)
                    .clip(PixelCutShape(4.dp))
                    .background(ParchmentMid)
                    .border(1.dp, ParchmentEdge.copy(alpha = 0.55f), PixelCutShape(4.dp))
                    .onSizeChanged { size ->
                        if (size.width <= 0 || size.height <= 0) return@onSizeChanged
                        val changed = size.width != lastSize[0] || size.height != lastSize[1]
                        if (!changed) return@onSizeChanged
                        lastSize[0] = size.width
                        lastSize[1] = size.height
                        // Desenrollar cambia el alto en cada fotograma: se avisa cada 180 ms en vez
                        // de encolar un invalidateSize por fotograma.
                        val now = SystemClock.uptimeMillis()
                        if (now - lastResizeAt[0] < RESIZE_GAP_MS) return@onSizeChanged
                        lastResizeAt[0] = now
                        webViewState.value?.evaluateJavascript(RESIZE_SCRIPT, null)
                    },
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
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

                            loadUrl("https://appassets.androidplatform.net/assets/miniapps/mapa/index.html?mode=compact&v=1.4.0")
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

                if (progress > 0.92f) {
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

                // La hoja que se retira al desenrollar: tapa mapa y botón mientras el rollo está
                // cerrado y se desvanece con el mismo avance que el alto.
                if (progress < 0.999f) {
                    PaperVeil(modifier = Modifier.fillMaxSize(), alpha = 1f - progress)
                }
            }

            ScrollRoller(modifier = Modifier.clickable(onClick = toggle))
        }
    }
}

private const val RESIZE_SCRIPT = "if (typeof triggerResize === 'function') triggerResize();"
private const val RESIZE_GAP_MS = 180L

// --- Pergamino ------------------------------------------------------------

private val ScrollClosedHeight = 88.dp
private val ScrollHeaderHeight = 42.dp
private val ScrollRollerHeight = 16.dp

private val ParchmentLight = Color(0xFFF8EAC8)
private val ParchmentMid = Color(0xFFEBD3A4)
private val ParchmentAge = Color(0xFFC79A5B)
private val ParchmentEdge = Color(0xFF7B5228)
private val ParchmentInk = Color(0xFF3B2412)
private val ParchmentMuted = Color(0xFF7A5836)
private val WoodDark = Color(0xFF43260F)
private val WoodMid = Color(0xFF7A4324)
private val WoodLight = Color(0xFFA5663A)

/** Manchas de edad en posiciones fijas: el papel no debe titilar mientras se desenrolla. */
private val parchmentStains = listOf(
    floatArrayOf(0.14f, 0.08f, 0.30f, 0.06f),
    floatArrayOf(0.86f, 0.12f, 0.24f, 0.05f),
    floatArrayOf(0.70f, 0.52f, 0.36f, 0.05f),
    floatArrayOf(0.22f, 0.80f, 0.28f, 0.06f),
    floatArrayOf(0.50f, 0.96f, 0.22f, 0.05f),
)

/** Hoja del pergamino: papel viejo con fibras, pliegues, manchas y cantos quemados. */
@Composable
private fun ParchmentSheet(modifier: Modifier) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(ParchmentLight, ParchmentMid, ParchmentLight, ParchmentMid),
            ),
        )

        // Fibras horizontales del papel prensado: un pelo de tono cada 9 dp.
        val fiberStep = 9.dp.toPx()
        val fiberThickness = 1.dp.toPx()
        var fiber = fiberStep
        while (fiber < size.height) {
            drawRect(
                ParchmentAge.copy(alpha = 0.10f),
                Offset(0f, fiber),
                Size(size.width, fiberThickness),
            )
            fiber += fiberStep
        }

        // Pliegues: dos dobleces del rollo, más marcados que las fibras.
        listOf(0.34f, 0.72f).forEach { fold ->
            drawRect(
                ParchmentEdge.copy(alpha = 0.10f),
                Offset(0f, size.height * fold),
                Size(size.width, 2.dp.toPx()),
            )
            drawRect(
                ParchmentLight.copy(alpha = 0.55f),
                Offset(0f, size.height * fold + 2.dp.toPx()),
                Size(size.width, 1.dp.toPx()),
            )
        }

        // Manchas de edad en posiciones fijas: el papel no debe titilar mientras se desenrolla.
        parchmentStains.forEach { stain ->
            drawCircle(
                color = ParchmentAge.copy(alpha = stain[3]),
                radius = size.minDimension * stain[2],
                center = Offset(size.width * stain[0], size.height * stain[1]),
            )
        }

        // Cantos: viñeta de papel tostado en los cuatro bordes.
        val edge = 10.dp.toPx()
        val burnt = ParchmentEdge.copy(alpha = 0.20f)
        drawRect(
            brush = Brush.verticalGradient(
                0f to burnt, 1f to Color.Transparent,
                startY = 0f, endY = edge,
            ),
            size = Size(size.width, edge),
        )
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent, 1f to burnt,
                startY = size.height - edge, endY = size.height,
            ),
            topLeft = Offset(0f, size.height - edge),
            size = Size(size.width, edge),
        )
        drawRect(
            brush = Brush.horizontalGradient(
                0f to burnt, 1f to Color.Transparent,
                startX = 0f, endX = edge,
            ),
            size = Size(edge, size.height),
        )
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent, 1f to burnt,
                startX = size.width - edge, endX = size.width,
            ),
            topLeft = Offset(size.width - edge, 0f),
            size = Size(edge, size.height),
        )

        drawRect(ParchmentEdge.copy(alpha = 0.5f), style = Stroke(width = 2f))
    }
}

/** Cabecera del rollo: título, pista y la flecha que dice si abre o cierra. */
@Composable
private fun ScrollHeader(open: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ScrollHeaderHeight)
            .clickable(onClick = onToggle)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            PixelTitle(text = "Mapa de Colima", size = 8.sp, color = ParchmentInk)
            Spacer(Modifier.height(3.dp))
            PixelLabel(
                text = if (open) "GPS, brújula y neblina · © OSM · INEGI" else "Toca para desenrollar",
                size = 5.5.sp,
                color = ParchmentMuted,
            )
        }
        PixelGlyph(
            rows = if (open) PixelUiGlyphs.arrowUp else PixelUiGlyphs.arrowDown,
            color = ParchmentInk,
            size = 13.dp,
        )
    }
}

/**
 * Hoja que cubre el mapa mientras el rollo está cerrado. No es un rectángulo plano: son las vueltas
 * del papel enrollado (claro arriba, sombra al centro, canto oscuro abajo) para que el estado
 * cerrado se lea como un rollo y no como un hueco vacío.
 */
@Composable
private fun PaperVeil(modifier: Modifier, alpha: Float) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                0.00f to ParchmentMid,
                0.14f to ParchmentLight,
                0.34f to ParchmentMid,
                0.58f to ParchmentLight,
                0.80f to ParchmentAge,
                0.92f to ParchmentMid,
            ),
            alpha = alpha,
        )

        // Vueltas del rollo: una línea fina marca dónde acaba cada capa de papel.
        listOf(0.30f, 0.56f).forEach { layer ->
            drawRect(
                ParchmentEdge.copy(alpha = 0.18f * alpha),
                Offset(0f, size.height * layer),
                Size(size.width, 1.dp.toPx()),
            )
        }

        // Canto inferior: la sombra del pliegue contra la varilla.
        drawRect(
            color = ParchmentEdge.copy(alpha = 0.35f * alpha),
            topLeft = Offset(0f, size.height - 5.dp.toPx()),
            size = Size(size.width, 5.dp.toPx()),
        )
    }
}

/** Varilla de madera del rollo: dos listones, brillo al centro y una perilla en cada extremo. */
@Composable
private fun ScrollRoller(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(ScrollRollerHeight)) {
        val h = size.height
        val knob = size.width * 0.07f
        val inset = 3.dp.toPx()

        fun band(left: Float, topFraction: Float, width: Float, heightFraction: Float, color: Color) {
            drawRect(color, Offset(left, h * topFraction), Size(width, h * heightFraction))
        }

        // El palo: contorno oscuro, madera al centro y un listón de luz arriba.
        val shaftLeft = inset
        val shaftWidth = size.width - inset * 2f
        band(shaftLeft, 0.10f, shaftWidth, 0.16f, WoodDark)
        band(shaftLeft, 0.26f, shaftWidth, 0.30f, WoodMid)
        band(shaftLeft, 0.32f, shaftWidth, 0.08f, WoodLight)
        band(shaftLeft, 0.56f, shaftWidth, 0.18f, WoodMid)
        band(shaftLeft, 0.74f, shaftWidth, 0.16f, WoodDark)

        // Perillas: sobresalen del palo y llevan su propio brillo.
        listOf(0f, size.width - knob).forEach { left ->
            drawRect(WoodDark, Offset(left, 0f), Size(knob, h))
            drawRect(WoodMid, Offset(left + knob * 0.22f, h * 0.12f), Size(knob * 0.56f, h * 0.76f))
            drawRect(WoodLight, Offset(left + knob * 0.32f, h * 0.24f), Size(knob * 0.20f, h * 0.30f))
        }
    }
}

private fun copyToClipboard(context: Context, text: String, onMessage: (String) -> Unit) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Coordenadas", text))
        onMessage("Coordenadas copiadas")
    }
}
