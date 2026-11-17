package com.nahuanotoca.superapp.core.model

enum class NoticeKind(val label: String) {
    WALLET("Cartera"),
    PROMO("Promoción"),
    SECURITY("Seguridad"),
    SERVICE("Servicio"),
    MARKET("Bazar"),
}

data class Notice(
    val id: String,
    val title: String,
    val body: String,
    val kind: NoticeKind,
    val timestamp: Long,
    val read: Boolean = false,
)
