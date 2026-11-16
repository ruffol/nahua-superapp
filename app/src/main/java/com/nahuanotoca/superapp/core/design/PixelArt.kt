package com.nahuanotoca.superapp.core.design

import androidx.compose.ui.graphics.Color

/**
 * Sprites del estudio. Cada fila es una línea de píxeles y cada carácter su color.
 * Se dibujan con [PixelSprite], así no hace falta empaquetar imágenes.
 */

// Axolote asistente: gillas rosas, ojos oscuros y sonrisa.
val pixelMascot: List<String> = listOf(
    "...B....B...",
    "..B.B..B.B..",
    "..BB.AA.BB..",
    "...AAAAAA...",
    "..AAAAAAAA..",
    ".AAAAAAAAAA.",
    ".AACAAAACAA.",
    ".AAAAAAAAAA.",
    "..AAAAAAAA..",
    "...AA..AA...",
    "...AAAAAA...",
    "............",
)

val pixelMascotPalette: Map<Char, Color> = mapOf(
    'A' to Color(0xFFFF9BB8),
    'B' to Color(0xFFFF6F9C),
    'C' to Color(0xFF2A1030),
)

// Estudiante: pelo castaño, playera verde.
val pixelUser: List<String> = listOf(
    "............",
    "...HHHHHH...",
    "..HHHHHHHH..",
    "..HHHHHHHH..",
    "..SSSSSSSS..",
    "..SESSSSES..",
    "..SSSSSSSS..",
    "...SSMMSS...",
    "....SSSS....",
    "..CCCCCCCC..",
    ".CCCCCCCCCC.",
    "............",
)

val pixelUserPalette: Map<Char, Color> = mapOf(
    'H' to Color(0xFF4A2E1F),
    'S' to Color(0xFFE8B48A),
    'E' to Color(0xFF1A1020),
    'M' to Color(0xFF8A4A3A),
    'C' to Color(0xFF5BE49B),
)
