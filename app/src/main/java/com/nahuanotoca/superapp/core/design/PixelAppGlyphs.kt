package com.nahuanotoca.superapp.core.design

import com.nahuanotoca.superapp.core.model.Service
import com.nahuanotoca.superapp.core.model.ServiceCategory

/**
 * Glifos 9×9 para servicios y ajustes. Se dibujan con [PixelGlyph], así que no dependen de la
 * densidad y combinan con el resto de la iconografía del rediseño.
 */
object PixelAppGlyphs {

    val car = rows(
        ".........",
        "..XXXXX..",
        ".XXXXXXX.",
        "XXXXXXXXX",
        "XXXXXXXXX",
        ".XX...XX.",
        ".XX...XX.",
        ".........",
        ".........",
    )

    val bus = rows(
        ".........",
        "XXXXXXXXX",
        "XXXXXXXXX",
        "X.X.X.X.X",
        "XXXXXXXXX",
        "X.......X",
        "X.......X",
        ".XX...XX.",
        ".........",
    )

    val receipt = rows(
        "..XXXXX..",
        "..X...X..",
        "..X.X.X..",
        "..X...X..",
        "..X.X.X..",
        "..X...X..",
        "..X.X.X..",
        "..XXXXX..",
        ".........",
    )

    val wallet = rows(
        ".........",
        "XXXXXXXXX",
        "X.......X",
        "X.......X",
        "X.XXXXX.X",
        "X.XX.XX.X",
        "X.XXXXX.X",
        "XXXXXXXXX",
        ".........",
    )

    val qr = rows(
        "XXX...XXX",
        "X.X...X.X",
        "XXX...XXX",
        ".........",
        "..X.X.X..",
        ".........",
        "XXX...XXX",
        "X.X...X.X",
        "XXX...XXX",
    )

    val bell = rows(
        "....X....",
        "...XXX...",
        "..XXXXX..",
        "..XXXXX..",
        "..XXXXX..",
        ".XXXXXXX.",
        "XXXXXXXXX",
        "....X....",
        "...XXX...",
    )

    val person = rows(
        "...XXX...",
        "...XXX...",
        "....X....",
        "..XXXXX..",
        ".XXXXXXX.",
        "XXXXXXXXX",
        "XXXXXXXXX",
        "XX.....XX",
        ".........",
    )

    val map = rows(
        "...XXX...",
        "..XXXXX..",
        ".XX.X.XX.",
        "XXX.X.XXX",
        "XXXX.XXXX",
        "XXX.X.XXX",
        ".XX.X.XX.",
        "..XXXXX..",
        "...XXX...",
    )

    val store = rows(
        ".........",
        "XXXXXXXXX",
        "X.X.X.X.X",
        "XXXXXXXXX",
        "..XXXXX..",
        "..X...X..",
        "..X...X..",
        "..XXXXX..",
        ".........",
    )

    /** Mando clastico: cruceta a la izquierda, botones a la derecha. */
    val gamepad = rows(
        ".........",
        ".XXXXXXX.",
        "XXXXXXXXX",
        "XX.XXX.XX",
        "XX.XXX.XX",
        "XXXXXXXXX",
        ".XXXXXXX.",
        ".........",
        ".........",
    )

    val paw = rows(
        "XX...XX..",
        "XXX.XXX..",
        "XXX.XXX..",
        "..XXX....",
        ".XXXXX...",
        "XXXXXXX..",
        "XXXXXXX..",
        ".XXXXX...",
        ".........",
    )

    val phone = rows(
        "..XXXXX..",
        "..X...X..",
        "..X...X..",
        "..X...X..",
        "..X...X..",
        "..X...X..",
        "..XXXXX..",
        "...XXX...",
        ".........",
    )

    val bank = rows(
        "...XXX...",
        "..XXXXX..",
        ".XXXXXXX.",
        "XXXXXXXXX",
        "X.X.X.X.X",
        "X.X.X.X.X",
        "XXXXXXXXX",
        ".........",
        ".........",
    )

    val house = rows(
        "...XX....",
        "..XXXX...",
        ".XXXXXX..",
        "XXXXXXXXX",
        "XXX.X.XXX",
        "XXX.X.XXX",
        "XXX.X.XXX",
        "XXXXXXXXX",
        ".........",
    )

    val grid = rows(
        "XXX...XXX",
        "XXX...XXX",
        "XXX...XXX",
        ".........",
        ".........",
        "XXX...XXX",
        "XXX...XXX",
        "XXX...XXX",
        ".........",
    )

    val shield = rows(
        ".XXXXXXX.",
        "XXXXXXXXX",
        "XXXXXXXXX",
        "XXXXXXXXX",
        "XXXXXXXXX",
        ".XXXXXXX.",
        "..XXXXX..",
        "...XXX...",
        "....X....",
    )

    val logout = rows(
        "XXXXX....",
        "X...X....",
        "X...X....",
        "X...XXXXX",
        "X...XXXXX",
        "X...XXXXX",
        "X...X....",
        "XXXXX....",
        ".........",
    )

    val help = rows(
        "..XXXXX..",
        ".XX...XX.",
        "XX.....XX",
        "....XXX.X",
        "...XX.XXX",
        "....X..XX",
        ".........",
        "....XX...",
        ".........",
    )

    val info = rows(
        "XXXXXXXXX",
        "X..XXX..X",
        "X...X...X",
        "X...X...X",
        "X...X...X",
        "X...X...X",
        "X..XXX..X",
        "XXXXXXXXX",
        ".........",
    )

    val clock = rows(
        "..XXXXX..",
        ".X.....X.",
        "X...X...X",
        "X...X...X",
        "X...XXX.X",
        "X.......X",
        ".X.....X.",
        "..XXXXX..",
        ".........",
    )

    val refresh = rows(
        "..XXXXX..",
        ".X.....X.",
        "X.......X",
        "X.....XXX",
        "X.....X.X",
        "X.......X",
        ".X.....X.",
        "..XXXXX..",
        ".........",
    )

    val card = rows(
        ".........",
        "XXXXXXXXX",
        "XXXXXXXXX",
        "X.......X",
        "XXXXXXXXX",
        "X.X.X.X.X",
        "XXXXXXXXX",
        ".........",
        ".........",
    )

    val shieldCheck = rows(
        ".XXXXXXX.",
        "XXXXXXXXX",
        "XXX...XXX",
        "XX.....XX",
        "XXX...XXX",
        ".XX.X.XX.",
        "..XXXXX..",
        "...XXX...",
        "....X....",
    )

    /** Glifo del servicio: primero por id, luego por categoría. */
    fun forService(service: Service): List<String> = byId[service.id]
        ?: category(service.category)

    fun category(category: ServiceCategory): List<String> = when (category) {
        ServiceCategory.PAYMENTS -> wallet
        ServiceCategory.TRANSPORT -> bus
        ServiceCategory.LIFESTYLE -> person
        ServiceCategory.GOVERNMENT -> bank
        ServiceCategory.ENTERTAINMENT -> sparkleGlyph
        ServiceCategory.MARKET -> store
    }

    private val sparkleGlyph = PixelIcons.sparkle

    private val byId: Map<String, List<String>> = mapOf(
        "ride" to car,
        "bills" to receipt,
        "wallet" to wallet,
        "scan" to qr,
        "inbox" to bell,
        "profile" to person,
        "mapas" to map,
        "bazar" to store,
        "tamagochi" to paw,
        "gba" to gamepad,
        "studio" to sparkleGlyph,
        "topup" to phone,
        "transit" to bus,
        "gov" to bank,
        "predial" to house,
    )
}
