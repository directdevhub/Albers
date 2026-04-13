package com.albers.app.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DashboardViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun onPumpNowClicked() {
        _uiState.value = _uiState.value.copy(
            pumpStatus = "PUMPING",
            countdownText = "Manual pump command pending BLE integration"
        )
    }

    fun onStopClicked() {
        _uiState.value = _uiState.value.copy(
            pumpStatus = "Stopped",
            countdownText = "Automatic pump cycle not started"
        )
    }
}

data class DashboardUiState(
    val deviceState: String = "Device state: Not connected",
    val countdownText: String = "Automatic pump cycle not started",
    val pumpStatus: String = "Pump status: Idle"
)
