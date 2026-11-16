package com.nahuanotoca.superapp.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletLedgerTest {

    @Test
    fun debitReducesBalanceAndPrependsMovement() {
        val ledger = WalletLedger(initialBalanceCents = 20_000L)

        val transaction = ledger.debit(8_500L, "Café", "Café Origen", now = 1_000L, id = "tx_1")

        assertNotNull(transaction)
        assertEquals(11_500L, ledger.balanceCents)
        assertEquals(1, ledger.transactions.size)
        assertEquals(-8_500L, ledger.transactions.first().amountCents)
        assertTrue(ledger.transactions.first().isDebit)
    }

    @Test
    fun debitRejectsAmountsAboveBalance() {
        val ledger = WalletLedger(initialBalanceCents = 5_000L)

        val transaction = ledger.debit(8_500L, "Café", "Café Origen")

        assertNull(transaction)
        assertEquals(5_000L, ledger.balanceCents)
        assertTrue(ledger.transactions.isEmpty())
    }

    @Test
    fun debitRejectsNonPositiveAmounts() {
        val ledger = WalletLedger(initialBalanceCents = 5_000L)

        assertNull(ledger.debit(0L, "Café", "Café Origen"))
        assertNull(ledger.debit(-100L, "Café", "Café Origen"))
        assertEquals(5_000L, ledger.balanceCents)
    }

    @Test
    fun creditIncreasesBalance() {
        val ledger = WalletLedger(initialBalanceCents = 0L)

        ledger.credit(50_000L, "Bienvenida", now = 2_000L, id = "tx_2")

        assertEquals(50_000L, ledger.balanceCents)
        assertEquals(50_000L, ledger.transactions.first().amountCents)
        assertTrue(!ledger.transactions.first().isDebit)
    }

    @Test
    fun creditIgnoresNonPositiveAmounts() {
        val ledger = WalletLedger(initialBalanceCents = 1_000L)

        assertNull(ledger.credit(0L, "Nada"))
        assertNull(ledger.credit(-1L, "Nada"))
        assertEquals(1_000L, ledger.balanceCents)
    }

    @Test
    fun historyIsBounded() {
        val ledger = WalletLedger(initialBalanceCents = 1_000_000L)

        repeat(WalletLedger.MAX_ENTRIES + 25) { index ->
            ledger.debit(100L, "Compra " + index, "Comercio", now = index.toLong(), id = "tx_$index")
        }

        assertEquals(WalletLedger.MAX_ENTRIES, ledger.transactions.size)
        assertEquals("Compra " + (WalletLedger.MAX_ENTRIES + 24), ledger.transactions.first().concept)
    }
}
