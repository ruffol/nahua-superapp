package com.nahuanotoca.superapp.feature.profile

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nahuanotoca.superapp.core.design.PixelAppGlyphs
import com.nahuanotoca.superapp.core.design.PixelBodyText
import com.nahuanotoca.superapp.core.design.PixelBorder
import com.nahuanotoca.superapp.core.design.PixelBorderSoft
import com.nahuanotoca.superapp.core.design.PixelButton
import com.nahuanotoca.superapp.core.design.PixelChip
import com.nahuanotoca.superapp.core.design.PixelCutShape
import com.nahuanotoca.superapp.core.design.PixelDivider
import com.nahuanotoca.superapp.core.design.PixelFrame
import com.nahuanotoca.superapp.core.design.PixelGlyph
import com.nahuanotoca.superapp.core.design.PixelInk
import com.nahuanotoca.superapp.core.design.PixelIcons
import com.nahuanotoca.superapp.core.design.PixelMuted
import com.nahuanotoca.superapp.core.design.PixelPanel
import com.nahuanotoca.superapp.core.design.PixelPanelDeep
import com.nahuanotoca.superapp.core.design.PixelRed
import com.nahuanotoca.superapp.core.design.PixelRow
import com.nahuanotoca.superapp.core.design.PixelScreen
import com.nahuanotoca.superapp.core.design.PixelSectionHeader
import com.nahuanotoca.superapp.core.design.PixelTitle
import com.nahuanotoca.superapp.core.design.PixelToggle
import com.nahuanotoca.superapp.core.design.PixelUiGlyphs
import com.nahuanotoca.superapp.core.design.PixelViolet
import com.nahuanotoca.superapp.core.design.PixelVioletDeep
import com.nahuanotoca.superapp.feature.update.UpdateSheet
import com.nahuanotoca.superapp.feature.update.UpdateViewModel
import com.nahuanotoca.superapp.ui.rememberAppViewModel

/**
 * Perfil en pixel art: ficha del jugador, ajustes de cuenta, preferencias y actualizaciones.
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onOpenInbox: () -> Unit,
    onOpenWallet: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val profile = viewModel.profile
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showUpdateSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val updateViewModel = rememberAppViewModel { UpdateViewModel(it) }
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()

    PixelScreen(title = "Perfil", subtitle = "Tu cuenta Nahua") {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ProfileHeroCard(
                    name = profile.name,
                    initials = profile.avatarInitials,
                    handle = profile.handle,
                    phone = profile.phone,
                    tier = profile.tier,
                    memberSince = profile.memberSince,
                )
            }

            item { PixelSectionHeader(title = "Cuenta") }
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
                        PixelRow(
                            title = "Mis datos",
                            subtitle = "Nombre, teléfono y correo",
                            glyph = PixelAppGlyphs.person,
                            onClick = { onMessage("Edición de datos disponible con el backend") },
                            trailing = { TrailingChevron() },
                        )
                        PixelDivider()
                        PixelRow(
                            title = "Seguridad",
                            subtitle = "PIN, dispositivos y sesiones",
                            glyph = PixelAppGlyphs.shield,
                            onClick = { onMessage("Seguridad avanzada próximamente") },
                            trailing = { TrailingChevron() },
                        )
                        PixelDivider()
                        PixelRow(
                            title = "Avisos",
                            subtitle = "Promociones, pagos y seguridad",
                            glyph = PixelAppGlyphs.bell,
                            onClick = onOpenInbox,
                            trailing = { TrailingChevron() },
                        )
                        PixelDivider()
                        PixelRow(
                            title = "Cartera",
                            subtitle = "Saldo y movimientos",
                            glyph = PixelAppGlyphs.wallet,
                            onClick = onOpenWallet,
                            trailing = { TrailingChevron() },
                        )
                    }
                }
            }

            item { PixelSectionHeader(title = "Preferencias") }
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
                        PixelRow(
                            title = "Notificaciones",
                            subtitle = "Avisos de pagos y promociones",
                            glyph = PixelAppGlyphs.bell,
                            trailing = {
                                PixelToggle(
                                    checked = preferences.notificationsEnabled,
                                    onCheckedChange = viewModel::setNotificationsEnabled,
                                )
                            },
                        )
                        PixelDivider()
                        PixelRow(
                            title = "Biometría",
                            subtitle = "Confirma pagos con huella",
                            glyph = PixelAppGlyphs.shieldCheck,
                            trailing = {
                                PixelToggle(
                                    checked = preferences.biometricsEnabled,
                                    onCheckedChange = viewModel::setBiometricsEnabled,
                                )
                            },
                        )

                    }
                }
            }

            item { PixelSectionHeader(title = "Acerca de") }
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
                        PixelRow(
                            title = "Versión " + updateState.currentVersion,
                            subtitle = "Buscar actualizaciones",
                            glyph = PixelIcons.download,
                            onClick = { showUpdateSheet = true },
                            trailing = { TrailingChevron() },
                        )
                        PixelDivider()
                        PixelRow(
                            title = "Actualizar app",
                            subtitle = "Descarga la última versión sin pasar un APK",
                            glyph = PixelAppGlyphs.clock,
                            onClick = { showUpdateSheet = true },
                            trailing = { TrailingChevron() },
                        )
                        PixelDivider()
                        PixelRow(
                            title = "Código en GitHub",
                            subtitle = "ruffol/nahua-superapp",
                            glyph = PixelIcons.doc,
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://github.com/ruffol/nahua-superapp"),
                                        ),
                                    )
                                }
                            },
                            trailing = { TrailingChevron() },
                        )
                        PixelDivider()
                        PixelRow(
                            title = "Cerrar sesión",
                            subtitle = "Salir de este dispositivo",
                            glyph = PixelAppGlyphs.logout,
                            accent = PixelRed,
                            onClick = { showLogoutDialog = true },
                            trailing = { TrailingChevron() },
                        )
                    }
                }
            }

            item {
                PixelBodyText(
                    text = "Nahua · hecho con Kotlin y Jetpack Compose",
                    size = 14.sp,
                    color = PixelMuted,
                    align = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        }
    }

    if (showUpdateSheet) {
        UpdateSheet(viewModel = updateViewModel, onDismiss = { showUpdateSheet = false })
    }

    if (showLogoutDialog) {
        LogoutDialog(
            onDismiss = { showLogoutDialog = false },
            onConfirm = {
                showLogoutDialog = false
                onMessage("Sesión cerrada (demo)")
            },
        )
    }
}

@Composable
private fun TrailingChevron() {
    PixelGlyph(PixelUiGlyphs.chevron, color = PixelMuted, size = 9.dp)
}

@Composable
private fun ProfileHeroCard(
    name: String,
    initials: String,
    handle: String,
    phone: String,
    tier: String,
    memberSince: String,
) {
    PixelFrame(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(186.dp),
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
                        listOf(Color(0xFF4B2A9E), Color(0xFF2A1460)),
                    ),
                ),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(PixelCutShape(4.dp))
                            .background(PixelViolet)
                            .padding(1.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        PixelTitle(text = initials, size = 12.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        PixelTitle(text = name, size = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        PixelBodyText(
                            text = handle + " · " + phone,
                            size = 14.sp,
                            color = PixelMuted,
                            maxLines = 1,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(PixelCutShape(3.dp))
                            .background(PixelViolet)
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                    ) {
                        PixelTitle(text = tier, size = 5.4.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(PixelBorderSoft.copy(alpha = 0.6f)),
                )
                Spacer(Modifier.height(10.dp))
                Row {
                    ProfileStat("1,240", "Puntos", Modifier.weight(1f))
                    ProfileStat(memberSince, "Miembro desde", Modifier.weight(1f))
                    ProfileStat("12", "Servicios", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ProfileStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        PixelBodyText(text = value, size = 22.sp, color = PixelInk, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        PixelBodyText(text = label, size = 13.sp, color = PixelMuted, maxLines = 1)
    }
}

@Composable
private fun LogoutDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            color = Color.Transparent,
        ) {
            com.nahuanotoca.superapp.core.design.PixelPanel(
                modifier = Modifier.fillMaxWidth(),
                fill = PixelPanelDeep,
                border = PixelBorder,
                borderWidth = 3.dp,
                cut = 8.dp,
                contentPadding = PaddingValues(16.dp),
            ) {
                PixelTitle(text = "¿Cerrar sesión?", size = 10.sp)
                Spacer(Modifier.height(8.dp))
                PixelBodyText(
                    text = "Tendrás que volver a entrar para usar tu cartera y tus mini apps.",
                    size = 16.sp,
                    color = PixelMuted,
                    maxLines = 3,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButton(
                        text = "Cancelar",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        color = PixelPanel,
                        borderColor = PixelBorderSoft,
                        fontSize = 5.4.sp,
                        contentPadding = PaddingValues(vertical = 10.dp),
                    )
                    PixelButton(
                        text = "Salir",
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        color = PixelRed,
                        fontSize = 5.4.sp,
                        contentPadding = PaddingValues(vertical = 10.dp),
                    )
                }
            }
        }
    }
}
