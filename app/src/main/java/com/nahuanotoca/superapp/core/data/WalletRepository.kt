package com.nahuanotoca.superapp.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nahuanotoca.superapp.core.model.PaymentResult
import com.nahuanotoca.superapp.core.model.WalletLedger
import com.nahuanotoca.superapp.core.model.WalletState
import com.nahuanotoca.superapp.core.model.WalletTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the wallet in Preferences DataStore. A real deployment would move this to Room and a
 * server-side ledger; the public API ([state], [pay], [topUp]) is the seam that will not change.
 */
class WalletRepository(private val context: Context) {

    private val store = context.walletStore

    val state: Flow<WalletState> = store.data.map { prefs -> prefs.toWalletState() }

    suspend fun pay(
        amountCents: Long,
        concept: String,
        merchant: String,
    ): PaymentResult {
        if (amountCents <= 0L) return PaymentResult.InvalidAmount

        var result: PaymentResult = PaymentResult.InsufficientFunds
        store.edit { prefs ->
            val ledger = prefs.toLedger()
            val transaction = ledger.debit(amountCents, concept, merchant)
            if (transaction == null) {
                result = PaymentResult.InsufficientFunds
            } else {
                prefs.writeLedger(ledger)
                result = PaymentResult.Success(transaction, ledger.balanceCents)
            }
        }
        return result
    }

    suspend fun topUp(
        amountCents: Long,
        concept: String = "Recarga de saldo",
    ): WalletState? {
        if (amountCents <= 0L) return null

        var updated: WalletState? = null
        store.edit { prefs ->
            val ledger = prefs.toLedger()
            ledger.credit(amountCents, concept)
            prefs.writeLedger(ledger)
            updated = prefs.toWalletState()
        }
        return updated
    }

    private fun androidx.datastore.preferences.core.Preferences.toLedger(): WalletLedger {
        val balance = this[BALANCE_KEY] ?: return seedLedger()
        val transactions = WalletJson.decode(this[TRANSACTIONS_KEY])
        return WalletLedger(balance, transactions)
    }

    private fun androidx.datastore.preferences.core.Preferences.toWalletState(): WalletState {
        val balance = this[BALANCE_KEY]
        if (balance == null) {
            val ledger = seedLedger()
            return WalletState(ledger.balanceCents, ledger.transactions)
        }
        return WalletState(balance, WalletJson.decode(this[TRANSACTIONS_KEY]))
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.writeLedger(ledger: WalletLedger) {
        this[BALANCE_KEY] = ledger.balanceCents
        this[TRANSACTIONS_KEY] = WalletJson.encode(ledger.transactions)
    }

    private fun seedLedger(): WalletLedger {
        val now = System.currentTimeMillis()
        val day = 24L * 60L * 60L * 1000L
        return WalletLedger(
            initialBalanceCents = SEED_BALANCE_CENTS,
            transactions = listOf(
                WalletTransaction(
                    id = "seed_welcome",
                    concept = "Saldo de bienvenida",
                    merchant = "Nahua",
                    amountCents = 50_000L,
                    timestamp = now - 2 * day,
                ),
                WalletTransaction(
                    id = "seed_coffee",
                    concept = "Café de especialidad",
                    merchant = "Café Origen",
                    amountCents = -8_500L,
                    timestamp = now - day,
                ),
            ),
        )
    }

    private companion object {
        val BALANCE_KEY = longPreferencesKey("balance_cents")
        val TRANSACTIONS_KEY = stringPreferencesKey("transactions_json")
        const val SEED_BALANCE_CENTS = 250_000L
    }
}

/** JSON (de)serialisation for the wallet history. */
internal object WalletJson {

    fun encode(transactions: List<WalletTransaction>): String {
        val array = JSONArray()
        transactions.forEach { transaction ->
            array.put(
                JSONObject()
                    .put("id", transaction.id)
                    .put("concept", transaction.concept)
                    .put("merchant", transaction.merchant)
                    .put("amountCents", transaction.amountCents)
                    .put("timestamp", transaction.timestamp),
            )
        }
        return array.toString()
    }

    fun decode(raw: String?): List<WalletTransaction> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        WalletTransaction(
                            id = item.optString("id"),
                            concept = item.optString("concept"),
                            merchant = item.optString("merchant"),
                            amountCents = item.optLong("amountCents"),
                            timestamp = item.optLong("timestamp"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}