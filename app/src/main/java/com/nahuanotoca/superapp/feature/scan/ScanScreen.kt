package com.nahuanotoca.superapp.feature.scan

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.nahuanotoca.superapp.core.design.PixelInk
import com.nahuanotoca.superapp.core.design.PixelMuted
import com.nahuanotoca.superapp.core.design.PixelPanel
import com.nahuanotoca.superapp.core.design.PixelPanelDeep
import com.nahuanotoca.superapp.core.design.PixelSurface
import com.nahuanotoca.superapp.core.design.PixelTitle
import com.nahuanotoca.superapp.core.design.PixelUiGlyphs
import com.nahuanotoca.superapp.core.model.Money

/**
 * Escáner QR en pixel art: visor con línea animada y confirmación del cobro.
 */
@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    onOpenWallet: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val merchant by viewModel.merchant.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PixelBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelIconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                PixelGlyph(PixelUiGlyphs.chevronLeft, color = PixelInk, size = 16.dp)
            }
            Spacer(Modifier.width(10.dp))
            PixelTitle(text = "Pagar con QR", size = 10.sp, modifier = Modifier.weight(1f))
            PixelIconButton(
                onClick = { onMessage("La linterna se activa con la cámara real") },
                modifier = Modifier.size(38.dp),
            ) {
                PixelGlyph(PixelUiGlyphs.sparkle, color = PixelSunColor, size = 16.dp)
            }
        }

        Spacer(Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            contentAlignment = Alignment.Center,
        ) {
            Viewfinder()
        }

        Spacer(Modifier.height(16.dp))

        PixelBodyText(
            text = if (merchant == null) {
                "Apunta al código QR del comercio o pide que te muestren su código Nahua."
            } else {
                "Código detectado"
            },
            size = 17.sp,
            color = PixelMuted,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        val current = merchant
        if (current == null) {
            PixelButton(
                text = "Simular escaneo",
                onClick = viewModel::simulateScan,
                expand = true,
                color = PixelGreen,
                textColor = Color(0xFF04240F),
                fontSize = 7.sp,
                contentPadding = PaddingValues(vertical = 12.dp),
            )
            Spacer(Modifier.height(10.dp))
            PixelBodyText(
                text = "Demo: sustituye esta acción por CameraX + ML Kit Barcode Scanning.",
                size = 14.sp,
                color = PixelMuted.copy(alpha = 0.75f),
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            PixelSurface(
                modifier = Modifier.fillMaxWidth(),
                fill = PixelPanelDeep,
                border = PixelBorder,
                borderWidth = 2.dp,
                cut = 8.dp,
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            PixelTitle(text = current.name, size = 9.sp)
                            Spacer(Modifier.height(4.dp))
                            PixelBodyText(
                                text = current.code,
                                size = 15.sp,
                                color = PixelMuted,
                                maxLines = 1,
                            )
                        }
                        PixelBodyText(
                            text = Money.format(current.amountCents),
                            size = 30.sp,
                            color = PixelInk,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    PixelButton(
                        text = if (busy) "Procesando…" else "Pagar con cartera",
                        onClick = {
                            viewModel.pay { message ->
                                onMessage(message)
                                onOpenWallet()
                            }
                        },
                        enabled = !busy,
                        expand = true,
                        color = PixelGreen,
                        textColor = Color(0xFF04240F),
                        fontSize = 6.4.sp,
                        contentPadding = PaddingValues(vertical = 11.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(PixelCutShape(3.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = viewModel::reset,
                            )
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        PixelBodyText(
                            text = "Cancelar escaneo",
                            size = 16.sp,
                            color = PixelMuted,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        PixelBodyText(
            text = "Tus pagos se protegen con confirmación en la app",
            size = 13.sp,
            color = PixelMuted.copy(alpha = 0.65f),
            align = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun Viewfinder() {
    val transition = rememberInfiniteTransition(label = "scan")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scanLine",
    )

    Box(
        modifier = Modifier
            .size(264.dp)
            .clip(PixelCutShape(8.dp))
            .background(PixelPanelDeep.copy(alpha = 0.55f))
            .border(2.dp, PixelBorderSoft, PixelCutShape(8.dp)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val length = 34.dp.toPx()
            val stroke = 5.dp.toPx()
            val w = size.width
            val h = size.height

            fun segment(start: Offset, end: Offset) {
                drawLine(PixelGreen, start, end, strokeWidth = stroke)
            }

            // Esquinas cuadradas, como un visor de 8 bits
            segment(Offset(0f, length), Offset(0f, 0f))
            segment(Offset(0f, 0f), Offset(length, 0f))
            segment(Offset(w - length, 0f), Offset(w, 0f))
            segment(Offset(w, 0f), Offset(w, length))
            segment(Offset(0f, h - length), Offset(0f, h))
            segment(Offset(0f, h), Offset(length, h))
            segment(Offset(w - length, h), Offset(w, h))
            segment(Offset(w, h), Offset(w, h - length))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .offset(y = (22 + progress * 216).dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, PixelGreen, Color.Transparent),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(72.dp),
            contentAlignment = Alignment.Center,
        ) {
            PixelGlyph(PixelAppGlyphs.qr, color = PixelInk.copy(alpha = 0.22f), size = 64.dp)
        }
    }
}

private val PixelSunColor = Color(0xFFFFE066)
