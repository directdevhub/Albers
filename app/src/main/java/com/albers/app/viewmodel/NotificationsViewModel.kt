package com.albers.app.viewmodel

import androidx.lifecycle.ViewModel
import com.albers.app.data.repository.AlbersRepository

class NotificationsViewModel : ViewModel() {
    val appState = AlbersRepository.appState
}
