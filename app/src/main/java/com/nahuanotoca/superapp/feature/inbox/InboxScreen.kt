package com.nahuanotoca.superapp.feature.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nahuanotoca.superapp.core.design.PixelAppGlyphs
import com.nahuanotoca.superapp.core.design.PixelBodyText
import com.nahuanotoca.superapp.core.design.PixelBorder
import com.nahuanotoca.superapp.core.design.PixelBorderSoft
import com.nahuanotoca.superapp.core.design.PixelCutShape
import com.nahuanotoca.superapp.core.design.PixelEmptyState
import com.nahuanotoca.superapp.core.design.PixelGlyph
import com.nahuanotoca.superapp.core.design.PixelGreen
import com.nahuanotoca.superapp.core.design.PixelInk
import com.nahuanotoca.superapp.core.design.PixelLabel
import com.nahuanotoca.superapp.core.design.PixelMuted
import com.nahuanotoca.superapp.core.design.PixelPanel
import com.nahuanotoca.superapp.core.design.PixelRed
import com.nahuanotoca.superapp.core.design.PixelScreen
import com.nahuanotoca.superapp.core.design.PixelSun
import com.nahuanotoca.superapp.core.design.PixelCyan
import com.nahuanotoca.superapp.core.model.Notice
import com.nahuanotoca.superapp.core.model.NoticeKind
import com.nahuanotoca.superapp.core.util.formatRelativeTime

/**
 * Bandeja de avisos en pixel art: cada aviso es una tarjeta con su tipo, su cuerpo y la hora.
 */
@Composable
fun InboxScreen(
    viewModel: InboxViewModel,
    onBack: () -> Unit,
) {
    val notices by viewModel.notices.collectAsStateWithLifecycle()
    val unread = notices.count { !it.read }

    PixelScreen(
        title = "Avisos",
        subtitle = if (unread == 0) "Todo al día" else "$unread sin leer",
        onBack = onBack,
        actions = {
            if (unread > 0) {
                Box(
                    modifier = Modifier
                        .clip(PixelCutShape(3.dp))
                        .background(PixelPanel)
                        .border(2.dp, PixelBorderSoft, PixelCutShape(3.dp))
                        .clickable(onClick = viewModel::markAllRead)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    PixelLabel(text = "Leer todo", size = 5.4.sp, color = PixelInk)
                }
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (notices.isEmpty()) {
                item {
                    PixelEmptyState(
                        title = "Sin avisos",
                        message = "Cuando haya pagos, promociones o avisos de seguridad aparecerán aquí.",
                    )
                }
            }
            items(notices, key = { it.id }) { notice ->
                NoticeCard(notice = notice, onClick = { viewModel.markRead(notice.id) })
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun NoticeCard(notice: Notice, onClick: () -> Unit) {
    val (glyph, accent) = noticeVisuals(notice.kind)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PixelCutShape(6.dp))
            .background(PixelPanel)
            .border(2.dp, if (notice.read) PixelBorderSoft else accent.copy(alpha = 0.8f), PixelCutShape(6.dp))
            .clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(PixelCutShape(3.dp))
                    .background(accent.copy(alpha = 0.18f))
                    .border(2.dp, accent.copy(alpha = 0.5f), PixelCutShape(3.dp)),
                contentAlignment = Alignment.Center,
            ) {
                PixelGlyph(glyph, color = lerp(accent, Color.White, 0.6f), size = 19.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelBodyText(
                        text = notice.title,
                        size = 19.sp,
                        color = PixelInk,
                        maxLines = 2,
                        modifier = Modifier.weight(1f),
                    )
                    if (!notice.read) {
                        Spacer(Modifier.width(6.dp))
                        Box(modifier = Modifier.size(7.dp).background(PixelGreen))
                    }
                }
                Spacer(Modifier.height(3.dp))
                PixelBodyText(
                    text = notice.body,
                    size = 15.sp,
                    color = PixelMuted,
                    maxLines = 3,
                )
                Spacer(Modifier.height(6.dp))
                PixelLabel(
                    text = notice.kind.label.uppercase() + " · " + formatRelativeTime(notice.timestamp),
                    size = 5.2.sp,
                    color = accent,
                )
            }
        }
    }
}

private fun noticeVisuals(kind: NoticeKind): Pair<List<String>, Color> = when (kind) {
    NoticeKind.WALLET -> PixelAppGlyphs.wallet to PixelGreen
    NoticeKind.PROMO -> PixelAppGlyphs.store to PixelSun
    NoticeKind.SECURITY -> PixelAppGlyphs.shield to PixelRed
    NoticeKind.SERVICE -> PixelAppGlyphs.receipt to PixelCyan
}
