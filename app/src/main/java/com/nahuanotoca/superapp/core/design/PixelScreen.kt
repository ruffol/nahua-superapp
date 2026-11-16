package com.nahuanotoca.superapp.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Piezas compartidas de las pantallas del rediseño: cabecera con botón de regreso, secciones,
 * filas de lista, interruptores y estados vacíos. Todas usan la paleta y las formas de
 * [Pixel.kt] para que cualquier pantalla nueva se sienta parte del mismo juego.
 */

@Composable
fun PixelScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    PixelBackdrop(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PixelTopBar(title = title, subtitle = subtitle, onBack = onBack, actions = actions)
            Box(modifier = Modifier.weight(1f), content = content)
        }
    }
}

@Composable
fun PixelTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                PixelIconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                    PixelGlyph(PixelUiGlyphs.chevronLeft, color = PixelInk, size = 16.dp)
                }
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                PixelTitle(text = title, size = 12.sp)
                if (subtitle != null) {
                    Spacer(Modifier.height(3.dp))
                    PixelBodyText(text = subtitle, size = 16.sp, color = PixelMuted, maxLines = 2)
                }
            }
            if (actions != null) {
                Spacer(Modifier.width(8.dp))
                actions()
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(PixelBorder.copy(alpha = 0.30f)),
        )
    }
}

@Composable
fun PixelSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelTitle(text = title, size = 9.sp, modifier = Modifier.weight(1f))
        if (count != null) {
            PixelLabel(text = count.toString(), size = 6.sp, color = PixelMuted)
            Spacer(Modifier.width(6.dp))
        }
        if (actionLabel != null && onAction != null) {
            Row(
                modifier = Modifier
                    .clip(PixelCutShape(3.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelLabel(text = actionLabel, size = 5.6.sp, color = PixelInk)
                Spacer(Modifier.width(4.dp))
                PixelGlyph(PixelUiGlyphs.chevron, color = PixelInk, size = 8.dp)
            }
        }
    }
}

@Composable
fun PixelRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    glyph: List<String>? = null,
    accent: Color = PixelBorder,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    var rowModifier = modifier.fillMaxWidth()
    if (onClick != null) {
        rowModifier = rowModifier.clickable(onClick = onClick)
    }
    Row(
        modifier = rowModifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(PixelCutShape(3.dp))
                    .background(accent.copy(alpha = 0.32f))
                    .border(2.dp, accent.copy(alpha = 0.8f), PixelCutShape(3.dp)),
                contentAlignment = Alignment.Center,
            ) {
                PixelGlyph(glyph, color = lerp(accent, Color.White, 0.65f), size = 19.dp)
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            PixelBodyText(text = title, size = 19.sp, color = PixelInk, maxLines = 1)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                PixelBodyText(
                    text = subtitle,
                    size = 15.sp,
                    color = PixelMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun PixelDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(2.dp)
            .background(PixelBorderSoft.copy(alpha = 0.45f)),
    )
}

@Composable
fun PixelToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = PixelCutShape(3.dp)
    Box(
        modifier = modifier
            .size(width = 46.dp, height = 24.dp)
            .clip(shape)
            .background(if (checked) PixelGreenDeep else PixelPanelAlt)
            .border(2.dp, if (checked) PixelGreen else PixelBorderSoft, shape)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .size(14.dp)
                .background(if (checked) PixelInk else PixelMuted),
        )
    }
}

@Composable
fun PixelBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = PixelViolet,
) {
    Box(
        modifier = modifier
            .clip(PixelCutShape(2.dp))
            .background(color)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        PixelLabel(text = text, size = 5.2.sp, color = PixelInk)
    }
}

@Composable
fun PixelEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixelGlyph(PixelUiGlyphs.sparkle, color = PixelMuted, size = 26.dp)
        Spacer(Modifier.height(12.dp))
        PixelTitle(text = title, size = 9.sp, align = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        PixelBodyText(
            text = message,
            size = 17.sp,
            color = PixelMuted,
            align = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )
    }
}

@Composable
fun PixelInfoCard(
    modifier: Modifier = Modifier,
    fill: Color = PixelPanel,
    border: Color = PixelBorder,
    content: @Composable () -> Unit,
) {
    PixelPanel(
        modifier = modifier,
        fill = fill,
        border = border,
        borderWidth = 2.dp,
        shadow = 3.dp,
        contentPadding = PaddingValues(12.dp),
    ) {
        content()
    }
}

@Composable
fun PixelSnackbar(text: String) {
    PixelSurface(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        fill = PixelPanelDeep,
        border = PixelBorder,
        borderWidth = 2.dp,
        cut = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelGlyph(PixelUiGlyphs.sparkle, color = PixelSun, size = 12.dp)
            Spacer(Modifier.width(8.dp))
            PixelBodyText(text = text, size = 17.sp, color = PixelInk, maxLines = 3)
        }
    }
}

@Composable
fun PixelStatBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = PixelInk,
) {
    Column(modifier = modifier) {
        PixelLabel(text = label, size = 5.4.sp, color = PixelMuted)
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            color = accent,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = PixelBody,
                fontSize = 22.sp,
                lineHeight = 24.sp,
            ),
            maxLines = 1,
        )
    }
}
