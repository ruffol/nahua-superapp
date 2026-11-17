package com.nahuanotoca.superapp.core.data

import com.nahuanotoca.superapp.core.model.Notice
import com.nahuanotoca.superapp.core.model.NoticeKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class InboxRepository {

    private val _notices = MutableStateFlow(seed())
    val notices: StateFlow<List<Notice>> = _notices.asStateFlow()

    /**
     * Agrega un aviso al inicio de la bandeja. Lo usan las funciones que no son de la cartera: hoy el
     * bazar, cuando alguien quiere truequear un objeto publicado.
     */
    fun push(title: String, body: String, kind: NoticeKind): Notice {
        val notice = Notice(
            id = "notice_" + clock.incrementAndGet(),
            title = title,
            body = body,
            kind = kind,
            timestamp = System.currentTimeMillis(),
        )
        _notices.update { listOf(notice) + it }
        return notice
    }

    fun markAllRead() {
        _notices.update { list -> list.map { it.copy(read = true) } }
    }

    fun markRead(id: String) {
        _notices.update { list -> list.map { if (it.id == id) it.copy(read = true) else it } }
    }

    private val clock = java.util.concurrent.atomic.AtomicLong(0L)

    private fun seed(): List<Notice> {
        val now = System.currentTimeMillis()
        val minute = 60_000L
        return listOf(
            Notice(
                id = "notice_move",
                title = "Tu transferencia llegó",
                body = "Recibiste $450.00 de Carlos M. Ya está disponible en tu cartera.",
                kind = NoticeKind.WALLET,
                timestamp = now - 12 * minute,
            ),
            Notice(
                id = "notice_promo",
                title = "2x1 en café de productores de Comala",
                body = "Café de Comala en el Bazar/Trueque de la app: paga con tu cartera o truequea tu objeto.",
                kind = NoticeKind.PROMO,
                timestamp = now - 95 * minute,
            ),
            Notice(
                id = "notice_security",
                title = "Nuevo inicio de sesión",
                body = "Detectamos un acceso desde un dispositivo nuevo. Si fuiste tú, ignora este aviso.",
                kind = NoticeKind.SECURITY,
                timestamp = now - 26 * 60 * minute,
                read = true,
            ),
            Notice(
                id = "notice_bill",
                title = "Tu recibo de luz está listo",
                body = "El periodo de noviembre ya se puede pagar desde Servicios > Pagos.",
                kind = NoticeKind.SERVICE,
                timestamp = now - 3 * 24 * 60 * minute,
                read = true,
            ),
        )
    }
}
