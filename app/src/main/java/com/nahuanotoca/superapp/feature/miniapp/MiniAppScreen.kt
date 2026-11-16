package com.nahuanotoca.superapp.feature.miniapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nahuanotoca.superapp.core.design.CoralSoft
import com.nahuanotoca.superapp.core.design.Jade
import com.nahuanotoca.superapp.core.design.JadeSoft
import com.nahuanotoca.superapp.core.design.NahuaCard
import com.nahuanotoca.superapp.core.miniapp.MiniAppBridge
import com.nahuanotoca.superapp.core.miniapp.PayRequest
import com.nahuanotoca.superapp.core.model.Money
import com.nahuanotoca.superapp.core.model.PaymentResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Cerrar mini app")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = manifest.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Mini app · v" + manifest.version,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { reloadSignal += 1 }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Recargar")
            }
        }

        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Box(modifier = Modifier.weight(1f)) {
            MiniAppWebView(
                manifest = manifest,
                bridge = bridge,
                reloadSignal = reloadSignal,
                onLoadingChanged = { loading = it },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = Jade,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Pagos protegidos por Nahua · saldo " + Money.format(wallet.balanceCents),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    val request = pendingPayment
    if (request != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                bridge.resolvePay(request.requestId, success = false, error = "cancelled")
                pendingPayment = null
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            PaymentSheetContent(
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
}

@Composable
private fun PaymentSheetContent(
    request: PayRequest,
    balanceCents: Long,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val hasFunds = request.amountCents <= balanceCents
    Column(
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
    ) {
        Text(
            text = "Confirmar pago",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        NahuaCard(contentPadding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.merchant,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = request.concept,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = Money.format(request.amountCents),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (hasFunds) JadeSoft else CoralSoft)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (hasFunds) {
                    "Se descontará de tu saldo Nahua"
                } else {
                    "Saldo insuficiente para completar el pago"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (hasFunds) Jade else Color(0xFFC2410C),
            )
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onConfirm,
            enabled = hasFunds,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Jade),
        ) {
            Text("Pagar " + Money.format(request.amountCents), style = MaterialTheme.typography.labelLarge)
        }
        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Cancelar")
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun MiniAppNotFound(appId: String, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Mini app no encontrada",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "No existe un manifiesto con id \"" + appId + "\" en assets/miniapps/index.json.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onClose) { Text("Volver") }
    }
}
