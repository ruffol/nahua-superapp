package com.nahuanotoca.superapp.core.data

import com.nahuanotoca.superapp.core.model.NoticeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxRepositoryTest {

    @Test
    fun pushAddsAnUnreadNoticeOnTop() {
        val inbox = InboxRepository()
        val before = inbox.notices.value
        val unreadBefore = before.count { !it.read }

        val notice = inbox.push(
            title = "Ana quiere truequear tu bici",
            body = "Te ofrece una mochila de senderismo.",
            kind = NoticeKind.MARKET,
        )

        val after = inbox.notices.value
        assertEquals(before.size + 1, after.size)
        assertEquals(unreadBefore + 1, after.count { !it.read })
        assertEquals(notice.id, after.first().id)
        assertEquals(NoticeKind.MARKET, after.first().kind)
        assertTrue(after.first().body.contains("mochila"))
    }

    @Test
    fun pushedNoticesGetDistinctIds() {
        val inbox = InboxRepository()
        val first = inbox.push("Aviso uno", "Cuerpo uno", NoticeKind.MARKET)
        val second = inbox.push("Aviso dos", "Cuerpo dos", NoticeKind.MARKET)

        assertTrue(first.id != second.id)
        assertEquals(2, inbox.notices.value.take(2).map { it.id }.distinct().size)
    }

    @Test
    fun marketNoticeKindIsLabelledForTheBazar() {
        assertEquals("Bazar", NoticeKind.MARKET.label)
    }
}
