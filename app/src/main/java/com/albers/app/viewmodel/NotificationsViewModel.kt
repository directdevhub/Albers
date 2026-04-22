package com.albers.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albers.app.data.model.NotificationItem
import com.albers.app.data.repository.AlbersRepository
import com.albers.app.data.repository.NotificationStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class NotificationsViewModel : ViewModel() {
    val appState = AlbersRepository.appState
    val notifications: StateFlow<List<NotificationItem>> = NotificationStore.notifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearNotifications() {
        AlbersRepository.clearNotifications()
    }
}
