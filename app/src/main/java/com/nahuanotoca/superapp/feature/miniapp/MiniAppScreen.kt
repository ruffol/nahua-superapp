package com.nahuanotoca.superapp.feature.miniapp

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.nahuanotoca.superapp.core.design.PixelAppGlyphs
import com.nahuanotoca.superapp.core.design.PixelBg
import com.nahuanotoca.superapp.core.design.PixelBodyText
import com.nahuanotoca.superapp.core.design.PixelBorder
import com.nahuanotoca.superapp.core.design.PixelBorderSoft
import com.nahuanotoca.superapp.core.design.PixelButton
import com.nahuanotoca.superapp.core.design.PixelCutShape
import com.nahuanotoca.superapp.core.design.PixelGlyph
import com.nahuanotoca.superapp.core.design.PixelGreen
import com.nahuanotoca.superapp.core.design.PixelIconButton
import com.nahuanotoca.superapp.core.design.PixelIcons
import com.nahuanotoca.superapp.core.design.PixelInk
import com.nahuanotoca.superapp.core.design.PixelMuted
import com.nahuanotoca.superapp.core.design.PixelPanel
import com.nahuanotoca.superapp.core.design.PixelPanelAlt
import com.nahuanotoca.superapp.core.design.PixelPanelDeep
import com.nahuanotoca.superapp.core.design.PixelRed
import com.nahuanotoca.superapp.core.design.PixelTitle
import com.nahuanotoca.superapp.core.design.PixelViolet
import com.nahuanotoca.superapp.core.miniapp.BarterNotice
import com.nahuanotoca.superapp.core.miniapp.DirectionsRequest
import com.nahuanotoca.superapp.core.miniapp.MiniAppBridge
import com.nahuanotoca.superapp.core.miniapp.MiniAppDisplay
import com.nahuanotoca.superapp.core.miniapp.MiniAppManifest
import com.nahuanotoca.superapp.core.miniapp.MiniAppOrientation
import com.nahuanotoca.superapp.core.miniapp.PayRequest
import com.nahuanotoca.superapp.core.miniapp.RomImportException
import com.nahuanotoca.superapp.core.model.Money
import com.nahuanotoca.superapp.core.model.PaymentResult
import com.nahuanotoca.superapp.ui.findActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Contenedor de mini-apps con el marco en pixel art. El contenido sigue siendo HTML dentro del
 * WebView aislado; aqui se rediseña la cabecera, la barra de saldo y el dialogo de pago.
 *
 * Un manifiesto puede pedir dos variantes:
 *  - "chrome": la mini-app vive bajo el marco de Nahua (cabecera, pie de saldo, pagos, GPS).
 *  - "immersive": la mini-app es toda la pantalla (emulador GBA): el host rota a horizontal,
 *    oculta las barras del sistema y deja solo un boton ✕ flotante para salir.
 */
@Composable
fun MiniAppScreen(
    viewModel: MiniAppViewModel,
    onClose: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val manifest = viewModel.manifest
    if (manifest == null) {
        MiniAppNotFound(onClose = onClose, appId = viewModel.appId)
        return
    }

    BackHandler(onBack = onClose)

    var pendingPayment by remember { mutableStateOf<PayRequest?>(null) }
    var reloadSignal by remember { mutableIntStateOf(0) }
    var recreateSignal by remember { mutableIntStateOf(0) }
    var rendererGone by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    val wallet by viewModel.wallet.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val bridge = remember {
        MiniAppBridge(
            sessionJson = { viewModel.sessionJson },
            onPayRequest = { request -> pendingPayment = request },
            onClose = onClose,
            onToast = onMessage,
        )
    }

    // Orientación: el manifiesto propone (landscape en el emulador), pero la página manda si el
    // usuario eligió vertical u horizontal en "Configuración de la consola". Al salir se restaura
    // la orientación que tenía la app.
    val activity = remember(context) { context.findActivity() }
    val previousOrientation = remember(activity) {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    fun applyOrientation(mode: String) {
        val target = activity ?: return
        target.requestedOrientation = when (mode) {
            // Vertical fijo (no "sensor portrait"): girar el telefono no debe reacomodar la
            // partida ni el mando a media jugada.
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    DisposableEffect(activity) {
        if (manifest.display == MiniAppDisplay.IMMERSIVE) {
            // El emulador es vertical (un Game Boy lo es); si el manifiesto pidiera horizontal,
            // se respeta esa intencion.
            applyOrientation(if (manifest.orientation == MiniAppOrientation.LANDSCAPE) "landscape" else "portrait")
        }
        onDispose {
            if (manifest.display == MiniAppDisplay.IMMERSIVE) {
                activity?.requestedOrientation = previousOrientation
            }
        }
    }

    var pendingLocationId by remember { mutableStateOf<String?>(null) }
    var pendingWatchInterval by remember { mutableStateOf<Long?>(null) }
    val locationJob = remember { mutableStateOf<Job?>(null) }

    val locationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val requestId = pendingLocationId
        val watchInterval = pendingWatchInterval
        pendingLocationId = null
        pendingWatchInterval = null

        // En Android 12+ el usuario puede conceder solo ubicacion aproximada: el permiso
        // "fine" llega como denegado pero el coarse ya esta activo.
        if (!granted && !viewModel.hasLocationPermission()) {
            if (requestId != null) {
                bridge.resolveLocation(requestId, null, "permission_denied")
            }
            if (watchInterval != null) {
                bridge.emitLocationError("permission_denied")
            }
            return@rememberLauncherForActivityResult
        }

        if (requestId != null) {
            scope.launch {
                val fix = viewModel.currentLocation()
                bridge.resolveLocation(requestId, fix, if (fix == null) "unavailable" else null)
            }
        }
        if (watchInterval != null) {
            locationJob.value?.cancel()
            locationJob.value = scope.launch {
                viewModel.locationUpdates(watchInterval).collect { fix -> bridge.emitLocation(fix) }
            }
        }
    }

    fun startLocationWatch(intervalMillis: Long) {
        locationJob.value?.cancel()
        locationJob.value = null
        if (!viewModel.hasLocationPermission()) {
            pendingWatchInterval = intervalMillis
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        locationJob.value = scope.launch {
            viewModel.locationUpdates(intervalMillis).collect { fix -> bridge.emitLocation(fix) }
        }
    }

    fun stopLocationWatch() {
        locationJob.value?.cancel()
        locationJob.value = null
    }

    // Brújula: la página pide el rumbo y el flujo se corta al salir de la pantalla o al pausar
    // la app (el sensor de rotación gasta batería, no debe seguir girando en segundo plano).
    var headingWanted by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

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

    // ROMs del emulador: el picker es del sistema (SAF). Muchos providers etiquetan los .gba como
    // application/octet-stream, asi que se pide */* y la validacion real de extension/tamano
    // vive en RomStore al momento de copiar.
    var pendingRomRequestId by remember { mutableStateOf<String?>(null) }
    val romPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val requestId = pendingRomRequestId
        pendingRomRequestId = null
        if (requestId == null) return@rememberLauncherForActivityResult
        if (uri == null) {
            bridge.resolveRomError(requestId, RomImportException.CANCELLED)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            try {
                val rom = viewModel.importRom(uri)
                bridge.resolveRom(requestId, rom)
                onMessage("ROM lista: " + rom.displayName)
            } catch (importFailure: RomImportException) {
                bridge.resolveRomError(requestId, importFailure.code)
                onMessage(romImportMessage(importFailure.code))
            } catch (unexpected: Exception) {
                bridge.resolveRomError(requestId, RomImportException.READ_FAILED)
                onMessage(romImportMessage(RomImportException.READ_FAILED))
            }
        }
    }

    SideEffect {
        bridge.onLocationRequest = { requestId ->
            if (viewModel.hasLocationPermission()) {
                scope.launch {
                    val fix = viewModel.currentLocation()
                    bridge.resolveLocation(requestId, fix, if (fix == null) "unavailable" else null)
                }
            } else {
                pendingLocationId = requestId
                locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        bridge.onLocationWatchStart = { interval -> startLocationWatch(interval) }
        bridge.onLocationWatchStop = { stopLocationWatch() }
        bridge.onHeadingWatchStart = { headingWanted = true }
        bridge.onHeadingWatchStop = { headingWanted = false }
        bridge.onDirectionsRequest = { request -> openDirections(context, request, onMessage) }
        bridge.onCopyToClipboard = { text -> copyToClipboard(context, text, onMessage) }
        bridge.onBarterNotice = { notice ->
            viewModel.announceBarter(notice)
            onMessage(notice.title)
        }
        bridge.onRomPickRequest = { requestId ->
            pendingRomRequestId = requestId
            runCatching { romPicker.launch(arrayOf("*/*")) }
                .onFailure {
                    pendingRomRequestId = null
                    bridge.resolveRomError(requestId, "unavailable")
                }
        }
        bridge.onRomListRequest = { requestId ->
            scope.launch { bridge.resolveRomList(requestId, viewModel.listRoms()) }
        }
        bridge.onRomDeleteRequest = { requestId, name ->
            scope.launch { bridge.resolveDeleted(requestId, viewModel.deleteRom(name)) }
        }
        bridge.onOrientationRequest = { mode -> applyOrientation(mode) }
    }

    DisposableEffect(bridge) {
        onDispose {
            locationJob.value?.cancel()
            locationJob.value = null
            bridge.onLocationWatchStart = {}
            bridge.onLocationWatchStop = {}
            bridge.onHeadingWatchStart = {}
            bridge.onHeadingWatchStop = {}
            bridge.onDirectionsRequest = {}
            bridge.onCopyToClipboard = {}
            bridge.onBarterNotice = {}
            bridge.onRomPickRequest = {}
            bridge.onRomListRequest = {}
            bridge.onRomDeleteRequest = { _, _ -> }
            bridge.onOrientationRequest = {}
        }
    }

    if (manifest.display == MiniAppDisplay.IMMERSIVE) {
        ImmersiveMiniApp(
            manifest = manifest,
            loading = loading,
            rendererGone = rendererGone,
            onRetry = {
                rendererGone = false
                recreateSignal += 1
            },
            onClose = onClose,
            onHome = {
                // Vuelve a la portada (biblioteca de ROMs) sin salir de la mini-app: recargar
                // deja el juego en la tarjeta "Seguir jugando", con su partida en IndexedDB.
                reloadSignal += 1
            },
            onMenu = {
                // Menú = configuración de la consola (joystick, tamaño y posición del gamepad).
                // Desde ese panel se llega a los ajustes propios de EmulatorJS, que si no
                // existen (portada, sin partida) dejan el panel abierto igual.
                bridge.sendToWeb?.invoke(
                    "(window.NahuaConsole && window.NahuaConsole.toggle()) || " +
                        "(window.EJS_emulator && window.EJS_emulator.menu && window.EJS_emulator.menu.toggle())",
                )
            },
            content = {
                MiniAppWebView(
                    manifest = manifest,
                    bridge = bridge,
                    reloadSignal = reloadSignal,
                    recreateSignal = recreateSignal,
                    immersive = true,
                    onLoadingChanged = { loading = it },
                    onRendererGone = {
                        rendererGone = true
                        onMessage("El motor de video se reinicio")
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            },
        )
    } else {
        // Modo chrome: la mini-app bajo el marco de Nahua. Si el render muere, se avisa y el
        // WebView se recrea en silencio: las mini-apps de contenido (mapa, bazar) no pierden nada.
        run {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PixelBg)
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PixelIconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                        PixelGlyph(PixelIcons.close, color = PixelInk, size = 14.dp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        PixelTitle(text = manifest.name, size = 9.sp)
                        Spacer(Modifier.height(3.dp))
                        PixelBodyText(
                            text = "Mini app · v" + manifest.version,
                            size = 14.sp,
                            color = PixelMuted,
                            maxLines = 1,
                        )
                    }
                    PixelIconButton(
                        onClick = { reloadSignal += 1 },
                        modifier = Modifier.size(36.dp),
                    ) {
                        PixelGlyph(PixelAppGlyphs.refresh, color = PixelInk, size = 16.dp)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(if (loading) PixelViolet else PixelBorderSoft.copy(alpha = 0.4f)),
                )

                Box(modifier = Modifier.weight(1f)) {
                    MiniAppWebView(
                        manifest = manifest,
                        bridge = bridge,
                        reloadSignal = reloadSignal,
                        recreateSignal = recreateSignal,
                        immersive = false,
                        onLoadingChanged = { loading = it },
                        onRendererGone = {
                            onMessage("La mini app se reinicio")
                            recreateSignal += 1
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PixelPanelDeep)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PixelGlyph(PixelAppGlyphs.shield, color = PixelGreen, size = 13.dp)
                    Spacer(Modifier.width(8.dp))
                    PixelBodyText(
                        text = "Pagos protegidos por Nahua · saldo " + Money.format(wallet.balanceCents),
                        size = 14.sp,
                        color = PixelMuted,
                        maxLines = 1,
                    )
                }
            }
        }
    }

    val request = pendingPayment
    if (request != null) {
        PaymentDialog(
            request = request,
            balanceCents = wallet.balanceCents,
            onConfirm = {
                scope.launch {
                    val result = viewModel.pay(request)
                    pendingPayment = null
                    when (result) {
                        is PaymentResult.Success -> {
                            bridge.resolvePay(
                                requestId = request.requestId,
                                success = true,
                                transactionId = result.transaction.id,
                                balanceCents = result.balanceCents,
                            )
                            onMessage("Pago aprobado · " + Money.format(request.amountCents))
                        }

                        PaymentResult.InsufficientFunds -> {
                            bridge.resolvePay(request.requestId, success = false, error = "insufficient_funds")
                            onMessage("Saldo insuficiente")
                        }

                        PaymentResult.InvalidAmount -> {
                            bridge.resolvePay(request.requestId, success = false, error = "invalid_amount")
                            onMessage("Monto invalido")
                        }
                    }
                }
            },
            onCancel = {
                bridge.resolvePay(request.requestId, success = false, error = "cancelled")
                pendingPayment = null
            },
        )
    }
}

/** Traduce el codigo estable de [RomImportException] a algo que el usuario entienda. */
private fun romImportMessage(code: String): String = when (code) {
    RomImportException.UNSUPPORTED -> "Ese archivo no es una ROM de GBA, GBC ni GB (.gba, .agb, .bin, .gbc o .gb)"
    RomImportException.TOO_LARGE -> "La ROM supera los 64 MB"
    RomImportException.READ_FAILED -> "No se pudo leer el archivo"
    RomImportException.WRITE_FAILED -> "No se pudo guardar la ROM"
    else -> "No se pudo importar la ROM"
}

/**
 * Variante inmersiva: rota a horizontal (si el manifiesto lo pide), oculta las barras del
 * sistema y deja un boton ✕ flotante. EmulatorJS coloca su propio menu en la esquina superior
 * derecha, asi que el cierre va a la izquierda.
 */
@Composable
private fun ImmersiveMiniApp(
    manifest: MiniAppManifest,
    loading: Boolean,
    rendererGone: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onHome: () -> Unit,
    onMenu: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }

    // La orientación la gobierna MiniAppScreen (manifiesto + preferencia de la página).
    DisposableEffect(activity, view) {
        // La Fullscreen API no existe en el WebView de Android: la inmersion la maneja el host.
        val controller = activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).also { insets ->
                insets.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insets.hide(WindowInsetsCompat.Type.systemBars())
            }
        }

        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PixelBg),
    ) {
        if (rendererGone) {
            RendererGonePanel(onRetry = onRetry, onClose = onClose)
        } else {
            content()
        }

        if (loading && !rendererGone) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(PixelViolet),
            )
        }

        // Tira de control nativa: Salir, Menu de EmulatorJS y Recargar. Va arriba a la
        // izquierda (EmulatorJS deja esa esquina libre) y respeta el recorte de la camara:
        // sin los insets de displayCutout, en horizontal el notch tapaba el boton y solo se
        // veia una franja inutilizable.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PixelIconButton(
                onClick = onClose,
                modifier = Modifier.size(46.dp),
                fill = PixelPanelDeep,
                border = PixelBorder,
            ) {
                PixelGlyph(PixelIcons.close, color = PixelInk, size = 15.dp)
            }
            PixelIconButton(
                onClick = onHome,
                modifier = Modifier.size(46.dp),
                fill = PixelPanelDeep,
                border = PixelBorder,
            ) {
                PixelGlyph(PixelAppGlyphs.house, color = PixelInk, size = 16.dp)
            }
            PixelIconButton(
                onClick = onMenu,
                modifier = Modifier.size(46.dp),
                fill = PixelPanelDeep,
                border = PixelBorder,
            ) {
                PixelGlyph(PixelIcons.gear, color = PixelInk, size = 16.dp)
            }
        }
    }
}

@Composable
private fun RendererGonePanel(onRetry: () -> Unit, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PixelBg),
        contentAlignment = Alignment.Center,
    ) {
        PixelPanel(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            fill = PixelPanelDeep,
            border = PixelBorder,
            borderWidth = 3.dp,
            cut = 8.dp,
            contentPadding = PaddingValues(16.dp),
        ) {
            PixelTitle(text = "El juego se cerro", size = 9.sp)
            Spacer(Modifier.height(10.dp))
            PixelBodyText(
                text = "El motor de video se quedo sin memoria. Tu partida guardada esta a salvo: " +
                    "vuelve a entrar y continua desde donde la dejaste.",
                size = 15.sp,
                color = PixelMuted,
            )
            Spacer(Modifier.height(12.dp))
            PixelButton(
                text = "Reintentar",
                onClick = onRetry,
                expand = true,
                color = PixelGreen,
                textColor = Color(0xFF04240F),
                fontSize = 6.sp,
                contentPadding = PaddingValues(vertical = 11.dp),
            )
            Spacer(Modifier.height(6.dp))
            PixelButton(
                text = "Salir",
                onClick = onClose,
                expand = true,
                color = PixelPanel,
                borderColor = PixelBorderSoft,
                fontSize = 5.6.sp,
                contentPadding = PaddingValues(vertical = 9.dp),
            )
        }
    }
}

private fun openDirections(
    context: Context,
    request: DirectionsRequest,
    onMessage: (String) -> Unit,
) {
    val label = Uri.encode(request.label.ifBlank { "Destino" })
    val uri = Uri.parse(
        "geo:" + request.latitude + "," + request.longitude +
            "?q=" + request.latitude + "," + request.longitude + "(" + label + ")",
    )
    val intent = Intent(Intent.ACTION_VIEW, uri)
    runCatching { context.startActivity(intent) }
        .onFailure { onMessage("No hay una app de mapas instalada") }
}

private fun copyToClipboard(context: Context, text: String, onMessage: (String) -> Unit) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    manager?.setPrimaryClip(ClipData.newPlainText("Coordenadas", text))
    onMessage("Coordenadas copiadas")
}

@Composable
private fun PaymentDialog(
    request: PayRequest,
    balanceCents: Long,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val hasFunds = request.amountCents <= balanceCents

    Dialog(onDismissRequest = onCancel) {
        PixelPanel(
            modifier = Modifier.fillMaxWidth(),
            fill = PixelPanelDeep,
            border = PixelBorder,
            borderWidth = 3.dp,
            cut = 8.dp,
            contentPadding = PaddingValues(16.dp),
        ) {
            PixelTitle(text = "Confirmar pago", size = 10.sp)
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PixelCutShape(4.dp))
                    .background(PixelPanelAlt)
                    .border(2.dp, PixelBorderSoft, PixelCutShape(4.dp))
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        PixelBodyText(text = request.merchant, size = 19.sp, maxLines = 1)
                        Spacer(Modifier.height(2.dp))
                        PixelBodyText(
                            text = request.concept,
                            size = 14.sp,
                            color = PixelMuted,
                            maxLines = 2,
                        )
                    }
                    PixelBodyText(
                        text = Money.format(request.amountCents),
                        size = 26.sp,
                        color = PixelInk,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PixelCutShape(3.dp))
                    .background(if (hasFunds) PixelGreen.copy(alpha = 0.16f) else PixelRed.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                PixelBodyText(
                    text = if (hasFunds) {
                        "Se descontara de tu saldo Nahua"
                    } else {
                        "Saldo insuficiente para completar el pago"
                    },
                    size = 15.sp,
                    color = if (hasFunds) PixelGreen else PixelRed,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.height(12.dp))
            PixelButton(
                text = "Pagar " + Money.format(request.amountCents),
                onClick = onConfirm,
                enabled = hasFunds,
                expand = true,
                color = PixelGreen,
                textColor = Color(0xFF04240F),
                fontSize = 6.sp,
                contentPadding = PaddingValues(vertical = 11.dp),
            )
            Spacer(Modifier.height(6.dp))
            PixelButton(
                text = "Cancelar",
                onClick = onCancel,
                expand = true,
                color = PixelPanel,
                borderColor = PixelBorderSoft,
                fontSize = 5.6.sp,
                contentPadding = PaddingValues(vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun MiniAppNotFound(appId: String, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PixelBg)
            .statusBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PixelTitle(text = "Mini app no encontrada", size = 9.sp)
            Spacer(Modifier.height(10.dp))
            PixelBodyText(
                text = "No existe un manifiesto con id \"" + appId + "\" en assets/miniapps/index.json.",
                size = 16.sp,
                color = PixelMuted,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            PixelButton(
                text = "Volver",
                onClick = onClose,
                color = PixelViolet,
                fontSize = 6.sp,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
