package com.nahuanotoca.superapp.core.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class ServiceCategory(val label: String) {
    PAYMENTS("Pagos"),
    TRANSPORT("Transporte"),
    LIFESTYLE("Vida"),
    GOVERNMENT("Gobierno"),
    ENTERTAINMENT("Ocio"),
    MARKET("Bazar"),
}

/** Where a service sends the user when tapped. */
sealed interface ServiceTarget {
    data class MiniApp(val appId: String) : ServiceTarget
    data class Native(val route: String) : ServiceTarget
    data object ComingSoon : ServiceTarget
}

data class Service(
    val id: String,
    val name: String,
    val tagline: String,
    val category: ServiceCategory,
    val icon: ImageVector,
    val accent: Color,
    val target: ServiceTarget,
    val badge: String? = null,
)