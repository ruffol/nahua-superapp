package com.nahuanotoca.superapp.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nahuanotoca.superapp.core.AppContainer
import com.nahuanotoca.superapp.core.data.catalog.ServiceCatalog
import com.nahuanotoca.superapp.core.location.LocationFix
import com.nahuanotoca.superapp.core.model.Service
import com.nahuanotoca.superapp.core.model.ServiceTarget
import com.nahuanotoca.superapp.core.model.UserProfile
import com.nahuanotoca.superapp.core.model.WalletState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    val profile: UserProfile = container.session.profile

    val featuredServices: List<Service> = ServiceCatalog.featured
    val allActiveServices: List<Service> = ServiceCatalog.all.filter { it.target !is ServiceTarget.ComingSoon }

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

    // null = todavía no hay una lectura real; no inventamos Colima Centro (el mapa lo avisa).
    private val _currentLocation = MutableStateFlow<LocationFix?>(null)
    val currentLocation: StateFlow<LocationFix?> = _currentLocation.asStateFlow()

    init {
        refreshLocation()
    }

    fun hasLocationPermission(): Boolean = container.locationProvider.hasPermission()

    fun refreshLocation() {
        viewModelScope.launch {
            val fix = container.locationProvider.currentFix()
            if (fix != null) {
                _currentLocation.value = fix
            }
        }
    }

    /** Espera una lectura real (caché reciente o GPS) para responder a "Mi ubicación". */
    suspend fun locationNow(): LocationFix? {
        val fix = container.locationProvider.currentFix()
        if (fix != null) {
            _currentLocation.value = fix
        }
        return fix
    }
}
