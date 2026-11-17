package com.nahuanotoca.superapp.feature.miniapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nahuanotoca.superapp.core.AppContainer
import com.nahuanotoca.superapp.core.location.LocationFix
import com.nahuanotoca.superapp.core.miniapp.BarterNotice
import com.nahuanotoca.superapp.core.miniapp.MiniAppManifest
import com.nahuanotoca.superapp.core.miniapp.PayRequest
import com.nahuanotoca.superapp.core.model.NoticeKind
import com.nahuanotoca.superapp.core.model.PaymentResult
import com.nahuanotoca.superapp.core.model.WalletState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MiniAppViewModel(
    private val container: AppContainer,
    val appId: String,
) : ViewModel() {

    val manifest: MiniAppManifest? = container.miniApps.find(appId)

    val sessionJson: String = container.session.asJson()

    val wallet: StateFlow<WalletState> = container.wallet.state
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WalletState(),
        )

    fun hasLocationPermission(): Boolean = container.locationProvider.hasPermission()

    suspend fun currentLocation(): LocationFix? = container.locationProvider.currentFix()

    fun locationUpdates(intervalMillis: Long): Flow<LocationFix> =
        container.locationProvider.locationUpdates(intervalMillis)

    /** Un aviso de trueque del bazar entra a la bandeja nativa de Avisos. */
    fun announceBarter(notice: BarterNotice) {
        container.inbox.push(
            title = notice.title,
            body = notice.body,
            kind = NoticeKind.MARKET,
        )
    }

    suspend fun pay(request: PayRequest): PaymentResult = container.wallet.pay(
        amountCents = request.amountCents,
        concept = request.concept,
        merchant = request.merchant,
    )
}