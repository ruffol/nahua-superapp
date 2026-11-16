package com.nahuanotoca.superapp.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Jade,
    onPrimary = Color.White,
    primaryContainer = JadeSoft,
    onPrimaryContainer = JadeDeep,
    secondary = Coral,
    onSecondary = Color.White,
    secondaryContainer = CoralSoft,
    onSecondaryContainer = Color(0xFF5C1606),
    tertiary = Sky,
    onTertiary = Color.White,
    background = Sand,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0F2F7),
    onSurfaceVariant = InkSoft,
    outline = Line,
    outlineVariant = Line,
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF58D6B8),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF0B4D40),
    onPrimaryContainer = Color(0xFFB6F2E2),
    secondary = Color(0xFFFFB4A0),
    onSecondary = Color(0xFF5E1606),
    secondaryContainer = Color(0xFF7A2A18),
    onSecondaryContainer = Color(0xFFFFDAD1),
    tertiary = Color(0xFFA9C2FF),
    onTertiary = Color(0xFF002B75),
    background = DarkBackground,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkInkSoft,
    outline = Color(0xFF33404D),
    outlineVariant = Color(0xFF2A3541),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val NahuaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun NahuaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = NahuaTypography,
        shapes = NahuaShapes,
        content = content,
    )
}
