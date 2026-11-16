package com.nahuanotoca.superapp.feature.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nahuanotoca.superapp.core.AppContainer
import com.nahuanotoca.superapp.core.model.Money
import com.nahuanotoca.superapp.core.model.WalletState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WalletViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<WalletState> = container.wallet.state
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WalletState(),
        )

    fun topUp(amountCents: Long, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val updated = container.wallet.topUp(amountCents)
            onResult(
                if (updated != null) {
                    "Recarga aplicada por " + Money.format(amountCents)
                } else {
                    "No pudimos procesar la recarga"
                },
            )
        }
    }
}
