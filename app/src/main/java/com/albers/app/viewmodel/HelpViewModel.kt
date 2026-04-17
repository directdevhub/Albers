package com.albers.app.viewmodel

import androidx.lifecycle.ViewModel
import com.albers.app.data.repository.AlbersRepository

class HelpViewModel : ViewModel() {
    val appState = AlbersRepository.appState
}
