package com.nahuanotoca.superapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nahuanotoca.superapp.core.AppContainer
import com.nahuanotoca.superapp.core.appContainer

/**
 * Creates a ViewModel backed by the app [AppContainer] without adding a DI framework.
 * Swap the body for Hilt's hiltViewModel() when the project grows.
 */
@Composable
inline fun <reified VM : ViewModel> rememberAppViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalContext.current.applicationContext.appContainer
    val factory = viewModelFactory {
        initializer { create(container) }
    }
    return if (key == null) {
        viewModel(factory = factory)
    } else {
        viewModel(key = key, factory = factory)
    }
}
