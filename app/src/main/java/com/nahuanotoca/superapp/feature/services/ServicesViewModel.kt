package com.nahuanotoca.superapp.feature.services

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nahuanotoca.superapp.core.AppContainer
import com.nahuanotoca.superapp.core.data.catalog.ServiceCatalog
import com.nahuanotoca.superapp.core.model.Service
import com.nahuanotoca.superapp.core.model.ServiceCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ServiceGroup(
    val category: ServiceCategory,
    val services: List<Service>,
)

data class ServicesUiState(
    val query: String = "",
    val category: ServiceCategory? = null,
    val groups: List<ServiceGroup> = emptyList(),
) {
    val isEmpty: Boolean get() = groups.isEmpty()
}

class ServicesViewModel(container: AppContainer) : ViewModel() {

    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<ServiceCategory?>(null)

    val state: StateFlow<ServicesUiState> = combine(query, category) { currentQuery, currentCategory ->
        val matches = ServiceCatalog.search(currentQuery)
            .filter { service -> currentCategory == null || service.category == currentCategory }

        ServicesUiState(
            query = currentQuery,
            category = currentCategory,
            groups = ServiceCategory.entries.mapNotNull { entry ->
                val services = matches.filter { it.category == entry }
                if (services.isEmpty()) null else ServiceGroup(entry, services)
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ServicesUiState(
            groups = ServiceCategory.entries.map { ServiceGroup(it, ServiceCatalog.byCategory(it)) },
        ),
    )

    fun setQuery(value: String) {
        query.value = value
    }

    fun setCategory(value: ServiceCategory?) {
        category.value = value
    }
}
