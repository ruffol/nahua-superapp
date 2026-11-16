package com.nahuanotoca.superapp.core.model

/** A single wallet movement. Amounts are stored in cents to avoid floating point drift. */
data class WalletTransaction(
    val id: String,
    val concept: String,
    val merchant: String,
    val amountCents: Long,
    val timestamp: Long,
) {
    val isDebit: Boolean get() = amountCents < 0L
}

data class WalletState(
    val balanceCents: Long = 0L,
    val transactions: List<WalletTransaction> = emptyList(),
)

sealed interface PaymentResult {
    data class Success(
        val transaction: WalletTransaction,
        val balanceCents: Long,
    ) : PaymentResult

    data object InsufficientFunds : PaymentResult

    data object InvalidAmount : PaymentResult
}

/**
 * Pure balance arithmetic shared by the repository and the unit tests. The ledger keeps the
 * invariants (never negative, newest first, bounded history) in one place.
 */
class WalletLedger(
    initialBalanceCents: Long = 0L,
    transactions: List<WalletTransaction> = emptyList(),
) {
    var balanceCents: Long = initialBalanceCents
        private set

    private val entries = transactions.toMutableList()

    val transactions: List<WalletTransaction> get() = entries.toList()

    fun canPay(amountCents: Long): Boolean = amountCents > 0L && amountCents <= balanceCents

    fun debit(
        amountCents: Long,
        concept: String,
        merchant: String,
        now: Long = System.currentTimeMillis(),
        id: String = newId(now),
    ): WalletTransaction? {
        if (!canPay(amountCents)) return null
        val transaction = WalletTransaction(
            id = id,
            concept = concept,
            merchant = merchant,
            amountCents = -amountCents,
            timestamp = now,
        )
        balanceCents -= amountCents
        entries.add(0, transaction)
        trim()
        return transaction
    }

    fun credit(
        amountCents: Long,
        concept: String,
        merchant: String = "Nahua",
        now: Long = System.currentTimeMillis(),
        id: String = newId(now),
    ): WalletTransaction? {
        if (amountCents <= 0L) return null
        val transaction = WalletTransaction(
            id = id,
            concept = concept,
            merchant = merchant,
            amountCents = amountCents,
            timestamp = now,
        )
        balanceCents += amountCents
        entries.add(0, transaction)
        trim()
        return transaction
    }

    private fun trim() {
        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(entries.lastIndex)
        }
    }

    companion object {
        const val MAX_ENTRIES: Int = 100

        private fun newId(now: Long): String =
            "tx_" + now.toString(36) + "_" + (0..0xFFFF).random().toString(16)
    }
}
