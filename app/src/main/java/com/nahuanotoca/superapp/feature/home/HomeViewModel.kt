package com.nahuanotoca.superapp.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nahuanotoca.superapp.core.AppContainer
import com.nahuanotoca.superapp.core.data.catalog.ServiceCatalog
import com.nahuanotoca.superapp.core.model.Service
import com.nahuanotoca.superapp.core.model.UserProfile
import com.nahuanotoca.superapp.core.model.WalletState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(container: AppContainer) : ViewModel() {

    val profile: UserProfile = container.session.profile

    val featuredServices: List<Service> = ServiceCatalog.featured

    val wallet: StateFlow<WalletState> = container.wallet.state
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WalletState(),
        )

    val unreadCount: StateFlow<Int> = container.inbox.notices
        .map { notices -> notices.count { !it.read } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )
}
