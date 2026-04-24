package com.albers.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albers.app.ble.AlbersBleSession
import com.albers.app.data.repository.AlbersRepository
import com.albers.app.ui.common.SystemStatusBadge
import com.albers.app.ui.common.resolveSystemStatusBadge
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel : ViewModel() {
    val uiState = AlbersRepository.appState
        .map { appState ->
            SettingsUiState(
                selectedIntervalMinutes = appState.automaticPumpIntervalMinutes,
                statusBadge = resolveSystemStatusBadge(
                    faults = appState.faultSummary.states,
                    batteryType = appState.deviceStatus.batteryType,
                    batteryPercent = appState.deviceStatus.batteryStatus.percent
                )
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun selectInterval(minutes: Int) {
        AlbersBleSession.updateAutomaticPumpInterval(minutes)
    }
}

data class SettingsUiState(
    val selectedIntervalMinutes: Int = 90,
    val statusBadge: SystemStatusBadge = SystemStatusBadge.Nominal
)
