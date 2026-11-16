package com.nahuanotoca.superapp.feature.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nahuanotoca.superapp.core.design.AmberSoft
import com.nahuanotoca.superapp.core.design.CoralSoft
import com.nahuanotoca.superapp.core.design.JadeSoft
import com.nahuanotoca.superapp.core.design.SkySoft
import com.nahuanotoca.superapp.core.design.Amber
import com.nahuanotoca.superapp.core.design.Coral
import com.nahuanotoca.superapp.core.design.Jade
import com.nahuanotoca.superapp.core.design.Sky
import com.nahuanotoca.superapp.core.model.Notice
import com.nahuanotoca.superapp.core.model.NoticeKind
import com.nahuanotoca.superapp.core.util.formatRelativeTime

@Composable
fun InboxScreen(
    viewModel: InboxViewModel,
    onBack: () -> Unit,
) {
    val notices by viewModel.notices.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Regresar")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Avisos",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = notices.count { !it.read }.toString() + " sin leer",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = viewModel::markAllRead) {
                Text("Marcar leído", style = MaterialTheme.typography.labelMedium)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(notices, key = { it.id }) { notice ->
                NoticeCard(notice = notice, onClick = { viewModel.markRead(notice.id) })
            }
            item { Spacer(Modifier.size(24.dp)) }
        }
    }
}

@Composable
private fun NoticeCard(notice: Notice, onClick: () -> Unit) {
    val (icon, accent, background) = noticeVisuals(notice.kind)

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        ),
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(background),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = notice.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (!notice.read) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Jade),
                        )
                    }
                }
                Spacer(Modifier.size(3.dp))
                Text(
                    text = notice.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = notice.kind.label + " · " + formatRelativeTime(notice.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class NoticeVisuals(val icon: ImageVector, val accent: Color, val background: Color)

private fun noticeVisuals(kind: NoticeKind): NoticeVisuals = when (kind) {
    NoticeKind.WALLET -> NoticeVisuals(Icons.Rounded.AccountBalanceWallet, Jade, JadeSoft)
    NoticeKind.PROMO -> NoticeVisuals(Icons.Rounded.LocalOffer, Amber, AmberSoft)
    NoticeKind.SECURITY -> NoticeVisuals(Icons.Rounded.Shield, Coral, CoralSoft)
    NoticeKind.SERVICE -> NoticeVisuals(Icons.AutoMirrored.Rounded.ReceiptLong, Sky, SkySoft)
}