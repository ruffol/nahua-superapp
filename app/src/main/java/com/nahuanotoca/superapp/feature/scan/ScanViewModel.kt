package com.nahuanotoca.superapp.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nahuanotoca.superapp.core.AppContainer
import com.nahuanotoca.superapp.core.model.Money
import com.nahuanotoca.superapp.core.model.PaymentResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScannedMerchant(
    val code: String,
    val name: String,
    val amountCents: Long,
)

class ScanViewModel(private val container: AppContainer) : ViewModel() {

    private val _merchant = MutableStateFlow<ScannedMerchant?>(null)
    val merchant: StateFlow<ScannedMerchant?> = _merchant.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Demo scan. Replace with CameraX + ML Kit barcode scanning. */
    fun simulateScan() {
        _merchant.value = ScannedMerchant(
            code = "NAHUA-CAFE-01",
            name = "Café Origen",
            amountCents = 8_500L,
        )
    }

    fun reset() {
        _merchant.value = null
    }

    fun pay(onResult: (String) -> Unit) {
        val current = _merchant.value ?: return
        _busy.value = true
        viewModelScope.launch {
            val result = container.wallet.pay(
                amountCents = current.amountCents,
                concept = "Pago QR",
                merchant = current.name,
            )
            _busy.value = false
            when (result) {
                is PaymentResult.Success -> {
                    _merchant.value = null
                    onResult("Pago aprobado · " + Money.format(current.amountCents))
                }

                PaymentResult.InsufficientFunds -> onResult("Saldo insuficiente para este pago")
                PaymentResult.InvalidAmount -> onResult("Monto inválido")
            }
        }
    }
}
