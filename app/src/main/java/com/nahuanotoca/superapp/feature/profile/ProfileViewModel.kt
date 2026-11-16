package com.nahuanotoca.superapp.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nahuanotoca.superapp.core.AppContainer
import com.nahuanotoca.superapp.core.data.AppPreferences
import com.nahuanotoca.superapp.core.data.ThemeMode
import com.nahuanotoca.superapp.core.model.UserProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(private val container: AppContainer) : ViewModel() {

    val profile: UserProfile = container.session.profile

    val preferences: StateFlow<AppPreferences> = container.preferences.preferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppPreferences(),
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.preferences.setThemeMode(mode) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { container.preferences.setNotificationsEnabled(enabled) }
    }

    fun setBiometricsEnabled(enabled: Boolean) {
        viewModelScope.launch { container.preferences.setBiometricsEnabled(enabled) }
    }
}
