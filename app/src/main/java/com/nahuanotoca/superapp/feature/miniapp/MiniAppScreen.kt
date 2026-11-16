package com.nahuanotoca.superapp.feature.miniapp

import android.Manifest
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.nahuanotoca.superapp.core.miniapp.MiniAppBridge
import com.nahuanotoca.superapp.core.miniapp.PayRequest
import com.nahuanotoca.superapp.core.model.Money
import com.nahuanotoca.superapp.core.model.PaymentResult
import kotlinx.coroutines.launch

/**
 * Contenedor de mini-apps con el marco en pixel art. El contenido sigue siendo HTML dentro del
 * WebView aislado; aquí sólo se rediseña la cabecera, la barra de saldo y el diálogo de pago.
 */
@Composable
fun MiniAppScreen(
    viewModel: MiniAppViewModel,
    onClose: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val manifest = viewModel.manifest
    if (manifest == null) {
        MiniAppNotFound(onClose = onClose, appId = viewModel.appId)
        return
    }

    BackHandler(onBack = onClose)

    var pendingPayment by remember { mutableStateOf<PayRequest?>(null) }
    var reloadSignal by remember { mutableIntStateOf(0) }
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

    var pendingLocationId by remember { mutableStateOf<String?>(null) }

    val locationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val requestId = pendingLocationId
        pendingLocationId = null
        if (requestId != null) {
            if (granted) {
                scope.launch {
                    val fix = viewModel.currentLocation()
                    bridge.resolveLocation(requestId, fix, if (fix == null) "unavailable" else null)
                }
            } else {
                bridge.resolveLocation(requestId, null, "permission_denied")
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
    }

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
                onLoadingChanged = { loading = it },
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
                            onMessage("Monto inválido")
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

@Composable
private fun PaymentDialog(
    request: PayRequest,
    balanceCents: Long,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val hasFunds = request.amountCents <= balanceCents

    Dialog(onDismissRequest = onCancel) {
        com.nahuanotoca.superapp.core.design.PixelPanel(
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
                        "Se descontará de tu saldo Nahua"
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
