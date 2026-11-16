package com.nahuanotoca.superapp.feature.inbox

import androidx.lifecycle.ViewModel
import com.nahuanotoca.superapp.core.AppContainer
import com.nahuanotoca.superapp.core.model.Notice
import kotlinx.coroutines.flow.StateFlow

class InboxViewModel(private val container: AppContainer) : ViewModel() {

    val notices: StateFlow<List<Notice>> = container.inbox.notices

    fun markAllRead() = container.inbox.markAllRead()

    fun markRead(id: String) = container.inbox.markRead(id)
}
