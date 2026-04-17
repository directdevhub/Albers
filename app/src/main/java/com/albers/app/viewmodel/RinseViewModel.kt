package com.albers.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albers.app.data.repository.AlbersRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class RinseViewModel : ViewModel() {
    val uiState = AlbersRepository.appState
        .map { appState ->
            RinseUiState(
                canStart = appState.faultSummary.canRinseSanitize,
                availabilityMessage = if (appState.faultSummary.canRinseSanitize) {
                    "Rinse/Sanitize is available."
                } else {
                    "Rinse/Sanitize is disabled because both pumps are unavailable."
                }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RinseUiState())
}

data class RinseUiState(
    val canStart: Boolean = true,
    val availabilityMessage: String = "Rinse/Sanitize is available."
)
