package com.nahuanotoca.superapp.feature.wallet

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nahuanotoca.superapp.R
import com.nahuanotoca.superapp.core.design.Clay
import com.nahuanotoca.superapp.core.design.PixelAppGlyphs
import com.nahuanotoca.superapp.core.design.PixelBodyText
import com.nahuanotoca.superapp.core.design.PixelBorder
import com.nahuanotoca.superapp.core.design.PixelBorderSoft
import com.nahuanotoca.superapp.core.design.PixelButton
import com.nahuanotoca.superapp.core.design.PixelChip
import com.nahuanotoca.superapp.core.design.PixelCutShape
import com.nahuanotoca.superapp.core.design.PixelDivider
import com.nahuanotoca.superapp.core.design.PixelEmptyState
import com.nahuanotoca.superapp.core.design.PixelFrame
import com.nahuanotoca.superapp.core.design.PixelGlyph
import com.nahuanotoca.superapp.core.design.PixelIconButton
import com.nahuanotoca.superapp.core.design.PixelInk
import com.nahuanotoca.superapp.core.design.PixelLabel
import com.nahuanotoca.superapp.core.design.PixelMuted
import com.nahuanotoca.superapp.core.design.PixelPanel
import com.nahuanotoca.superapp.core.design.PixelPanelDeep
import com.nahuanotoca.superapp.core.design.PixelRed
import com.nahuanotoca.superapp.core.design.PixelScreen
import com.nahuanotoca.superapp.core.design.PixelSectionHeader
import com.nahuanotoca.superapp.core.design.PixelSpriteImage
import com.nahuanotoca.superapp.core.design.PixelSurface
import com.nahuanotoca.superapp.core.design.PixelTitle
import com.nahuanotoca.superapp.core.design.PixelUiGlyphs
import com.nahuanotoca.superapp.core.design.PixelViolet
import com.nahuanotoca.superapp.core.design.PixelVioletDeep
import com.nahuanotoca.superapp.core.design.PixelGreen
import com.nahuanotoca.superapp.core.model.Money
import com.nahuanotoca.superapp.core.model.WalletTransaction
import com.nahuanotoca.superapp.core.util.formatRelativeTime

private enum class MovementFilter(val label: String) {
    ALL("Todos"),
    INCOME("Ingresos"),
    EXPENSES("Gastos"),
}

/**
 * Cartera en pixel art: saldo, acciones rápidas y libro de movimientos con filtros.
 */
@Composable
fun WalletScreen(
    viewModel: WalletViewModel,
    onMessage: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(MovementFilter.ALL) }
    var showTopUp by remember { mutableStateOf(false) }

    val visibleTransactions: List<WalletTransaction> = remember(state.transactions, filter) {
        when (filter) {
            MovementFilter.ALL -> state.transactions
            MovementFilter.INCOME -> state.transactions.filter { !it.isDebit }
            MovementFilter.EXPENSES -> state.transactions.filter { it.isDebit }
        }
    }

    PixelScreen(
        title = "Cartera",
        subtitle = "Tu dinero, en un solo lugar",
        actions = {
            PixelIconButton(
                onClick = { onMessage("Detalles de tarjeta próximamente") },
                modifier = Modifier.size(38.dp),
            ) {
                PixelGlyph(PixelAppGlyphs.card, color = PixelInk, size = 16.dp)
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                BalancePanel(
                    balanceCents = state.balanceCents,
                    onTopUp = { showTopUp = true },
                    onSend = { onMessage("Transferencias llegan en la próxima versión") },
                    onCollect = { onMessage("Los cobros llegan en la próxima versión") },
                )
            }

            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MovementFilter.entries.forEach { option ->
                        PixelChip(
                            text = option.label,
                            selected = filter == option,
                            onClick = { filter = option },
                        )
                    }
                }
            }

            item { PixelSectionHeader(title = "Movimientos", count = visibleTransactions.size) }

            item {
                PixelFrame(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    fill = PixelPanel,
                    border = PixelBorder,
                    borderWidth = 2.dp,
                    cut = 8.dp,
                    shadow = 3.dp,
                ) {
                    Column {
                        if (visibleTransactions.isEmpty()) {
                            Box(modifier = Modifier.padding(vertical = 10.dp)) {
                                PixelEmptyState(
                                    title = "Sin movimientos",
                                    message = "Cuando pagues o recibas dinero verás aquí cada movimiento.",
                                )
                            }
                        } else {
                            visibleTransactions.forEachIndexed { index, transaction ->
                                TransactionPixelRow(transaction)
                                if (index != visibleTransactions.lastIndex) {
                                    PixelDivider()
                                }
                            }
                        }
                    }
                }
            }

            item {
                PixelBodyText(
                    text = "Nahua · tarjeta terminación 4821",
                    size = 15.sp,
                    color = PixelMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
        }
    }

    if (showTopUp) {
        TopUpDialog(
            onDismiss = { showTopUp = false },
            onTopUp = { amount ->
                viewModel.topUp(amount) { message ->
                    onMessage(message)
                    showTopUp = false
                }
            },
        )
    }
}

@Composable
private fun BalancePanel(
    balanceCents: Long,
    onTopUp: () -> Unit,
    onSend: () -> Unit,
    onCollect: () -> Unit,
) {
    PixelFrame(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(172.dp),
        fill = PixelVioletDeep,
        border = PixelBorder,
        borderWidth = 3.dp,
        cut = 10.dp,
        shadow = 4.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(PixelCutShape(9.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Clay, Color(0xFF3A2117)),
                    ),
                ),
        ) {
            PixelSpriteImage(
                res = R.drawable.px_icon_coins,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
                    .size(56.dp),
            )
            Column(modifier = Modifier.padding(14.dp)) {
                PixelBodyText(
                    text = "Saldo disponible",
                    size = 16.sp,
                    color = PixelMuted,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                PixelBodyText(
                    text = Money.format(balanceCents),
                    size = 42.sp,
                    color = PixelInk,
                    maxLines = 1,
                )
                PixelBodyText(
                    text = "Nahua Balance · •••• 4821",
                    size = 14.sp,
                    color = PixelMuted,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BalanceAction(
                        label = "Recargar",
                        fill = PixelGreen,
                        labelColor = Color(0xFF042B12),
                        onClick = onTopUp,
                        modifier = Modifier.weight(1f),
                    )
                    BalanceAction(
                        label = "Enviar",
                        fill = PixelPanelDeep,
                        labelColor = PixelInk,
                        onClick = onSend,
                        modifier = Modifier.weight(1f),
                    )
                    BalanceAction(
                        label = "Cobrar",
                        fill = PixelPanelDeep,
                        labelColor = PixelInk,
                        onClick = onCollect,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BalanceAction(
    label: String,
    fill: Color,
    labelColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PixelSurface(
        modifier = modifier.height(36.dp),
        fill = fill,
        border = PixelBorderSoft,
        borderWidth = 2.dp,
        cut = 4.dp,
        onClick = onClick,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PixelLabel(text = label, size = 5.4.sp, color = labelColor)
        }
    }
}

@Composable
private fun TransactionPixelRow(transaction: WalletTransaction) {
    val credit = !transaction.isDebit
    val accent = if (credit) PixelGreen else PixelRed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(PixelCutShape(3.dp))
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            PixelGlyph(
                rows = if (transaction.isDebit) PixelUiGlyphs.arrowUp else PixelUiGlyphs.arrowDown,
                color = accent,
                size = 16.dp,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            PixelBodyText(text = transaction.concept, size = 19.sp, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            PixelBodyText(
                text = transaction.merchant + " · " + formatRelativeTime(transaction.timestamp),
                size = 14.sp,
                color = PixelMuted,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        PixelBodyText(
            text = Money.formatSigned(transaction.amountCents),
            size = 20.sp,
            color = if (credit) PixelGreen else PixelInk,
            maxLines = 1,
        )
    }
}

@Composable
private fun TopUpDialog(
    onDismiss: () -> Unit,
    onTopUp: (Long) -> Unit,
) {
    val amounts = listOf(10_000L, 20_000L, 50_000L, 100_000L)

    Dialog(onDismissRequest = onDismiss) {
        PixelPanel(
            modifier = Modifier.fillMaxWidth(),
            fill = PixelPanelDeep,
            border = PixelBorder,
            borderWidth = 3.dp,
            cut = 8.dp,
            contentPadding = PaddingValues(16.dp),
        ) {
            PixelTitle(text = "Recargar saldo", size = 10.sp)
            Spacer(Modifier.height(6.dp))
            PixelBodyText(
                text = "Elige un monto. En producción esta pantalla abriría la pasarela de pago.",
                size = 16.sp,
                color = PixelMuted,
                maxLines = 3,
            )
            Spacer(Modifier.height(14.dp))
            amounts.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { amount ->
                        PixelButton(
                            text = Money.format(amount),
                            onClick = { onTopUp(amount) },
                            modifier = Modifier.weight(1f),
                            color = PixelViolet,
                            fontSize = 5.6.sp,
                            contentPadding = PaddingValues(vertical = 12.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(4.dp))
            PixelButton(
                text = "Cancelar",
                onClick = onDismiss,
                expand = true,
                color = PixelPanel,
                borderColor = PixelBorderSoft,
                fontSize = 5.6.sp,
                contentPadding = PaddingValues(vertical = 10.dp),
            )
        }
    }
}