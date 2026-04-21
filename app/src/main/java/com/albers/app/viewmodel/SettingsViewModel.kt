package com.albers.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albers.app.data.repository.AlbersRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel : ViewModel() {
    val uiState = AlbersRepository.appState
        .map { appState ->
            SettingsUiState(selectedIntervalMinutes = appState.automaticPumpIntervalMinutes)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun selectInterval(minutes: Int) {
        AlbersRepository.updateAutomaticPumpInterval(minutes)
    }
}

data class SettingsUiState(
    val selectedIntervalMinutes: Int = 90
)
