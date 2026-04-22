package com.albers.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albers.app.ble.AlbersBleSession
import com.albers.app.data.model.BatteryType
import com.albers.app.data.model.FaultState
import com.albers.app.data.repository.AlbersRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class RinseViewModel : ViewModel() {
    val uiState = AlbersRepository.appState
        .map { appState ->
            val faults = appState.faultSummary.states
            RinseUiState(
                canStart = appState.faultSummary.canRinseSanitize,
                availabilityMessage = if (appState.faultSummary.canRinseSanitize) {
                    "Rinse/Sanitize is available."
                } else {
                    "Rinse/Sanitize is disabled because both pumps are unavailable."
                },
                statusIcon = when {
                    FaultState.OnePumpFailed in faults ||
                        FaultState.BothPumpsFailed in faults ||
                        FaultState.BatteryFailure in faults -> RinseStatusIcon.PumpError
                    appState.deviceStatus.batteryType == BatteryType.Emergency -> RinseStatusIcon.EmergencyBattery
                    FaultState.LowBattery in faults || FaultState.CriticalBattery in faults -> RinseStatusIcon.LowBattery
                    else -> RinseStatusIcon.Nominal
                }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RinseUiState())

    fun startRinseCycle() {
        AlbersBleSession.startRinseCycle()
    }

    fun emergencyStop() {
        AlbersBleSession.stopPumpOrCycle()
    }
}

data class RinseUiState(
    val canStart: Boolean = true,
    val availabilityMessage: String = "Rinse/Sanitize is available.",
    val statusIcon: RinseStatusIcon = RinseStatusIcon.Nominal
)

enum class RinseStatusIcon {
    Nominal,
    LowBattery,
    EmergencyBattery,
    PumpError
}
