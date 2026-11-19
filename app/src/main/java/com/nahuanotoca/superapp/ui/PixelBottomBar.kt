package com.nahuanotoca.superapp.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nahuanotoca.superapp.R
import com.nahuanotoca.superapp.core.design.Clay
import com.nahuanotoca.superapp.core.design.PixelBgDeep
import com.nahuanotoca.superapp.core.design.PixelBorder
import com.nahuanotoca.superapp.core.design.PixelCream
import com.nahuanotoca.superapp.core.design.PixelCutShape
import com.nahuanotoca.superapp.core.design.PixelInk
import com.nahuanotoca.superapp.core.design.PixelLabel
import com.nahuanotoca.superapp.core.design.PixelMuted
import com.nahuanotoca.superapp.core.design.PixelSpriteImage
import com.nahuanotoca.superapp.core.design.PixelViolet
import com.nahuanotoca.superapp.core.navigation.Routes

/**
 * Barra inferior en pixel art: cinco accesos, con el Tlalchichi en el centro como botón de la
 * mascota. Reemplaza la barra Material del rediseño anterior.
 */

private data class PixelNavItem(
    val route: String,
    val label: String,
    @param:DrawableRes val icon: Int,
)

private val pixelNavItems = listOf(
    PixelNavItem(Routes.HOME, "Inicio", R.drawable.px_nav_home),
    PixelNavItem(Routes.SERVICES, "Aplicaciones", R.drawable.px_nav_grid),
    PixelNavItem(Routes.INBOX, "Actividad", R.drawable.px_nav_bell),
    PixelNavItem(Routes.PROFILE, "Perfil", R.drawable.px_nav_person),
)

@Composable
fun PixelBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onOpenPet: () -> Unit,
) {
    val petSelected = currentRoute == Routes.miniApp(PET_APP_ID)

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PixelBgDeep),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(PixelBorder.copy(alpha = 0.75f)),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(74.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavItemView(pixelNavItems[0], currentRoute == Routes.HOME, onNavigate, Modifier.weight(1f))
                NavItemView(pixelNavItems[1], currentRoute == Routes.SERVICES, onNavigate, Modifier.weight(1f))
                PetButton(petSelected, onOpenPet, Modifier.weight(1f))
                NavItemView(pixelNavItems[2], currentRoute == Routes.INBOX, onNavigate, Modifier.weight(1f))
                NavItemView(pixelNavItems[3], currentRoute == Routes.PROFILE, onNavigate, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NavItemView(
    item: PixelNavItem,
    selected: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = PixelCutShape(5.dp)
    var itemModifier = modifier
        .padding(horizontal = 2.dp)
        .height(56.dp)
        .clip(shape)
        .background(if (selected) PixelViolet else Color.Transparent)
    if (selected) {
        itemModifier = itemModifier.border(2.dp, PixelCream.copy(alpha = 0.6f), shape)
    }
    Box(
        modifier = itemModifier.clickable { onNavigate(item.route) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            PixelSpriteImage(
                res = item.icon,
                modifier = Modifier.size(26.dp),
                contentDescription = item.label,
            )
            PixelLabel(text = item.label, size = 5.sp, color = PixelInk)
        }
    }
}

@Composable
private fun PetButton(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .offset(y = (-14).dp)
                .size(66.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Clay, Color(0xFF3A2117)),
                    ),
                )
                .border(3.dp, if (selected) PixelCream else PixelBorder, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            PixelSpriteImage(
                res = R.drawable.px_dog,
                modifier = Modifier.fillMaxSize(),
                contentDescription = "Yōlcatl",
            )
        }
    }
}

private const val PET_APP_ID = "yolcatl"
