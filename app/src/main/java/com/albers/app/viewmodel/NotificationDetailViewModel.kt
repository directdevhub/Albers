package com.albers.app.viewmodel

import androidx.lifecycle.ViewModel
import com.albers.app.data.repository.NotificationStore

class NotificationDetailViewModel : ViewModel() {
    fun observeNotification(id: Long) = NotificationStore.observeNotification(id)
}
