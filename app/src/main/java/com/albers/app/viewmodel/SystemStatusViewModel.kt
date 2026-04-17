package com.albers.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albers.app.data.model.BatteryType
import com.albers.app.data.model.FaultSeverity
import com.albers.app.data.model.FaultState
import com.albers.app.data.repository.AlbersRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SystemStatusViewModel : ViewModel() {
    val uiState = AlbersRepository.appState
        .map { appState ->
            val status = appState.deviceStatus
            val faults = appState.faultSummary.states
            SystemStatusUiState(
                summary = appState.faultSummary.primaryMessage,
                isNominal = appState.faultSummary.highestSeverity == FaultSeverity.Nominal,
                pump1 = if (FaultState.OnePumpFailed in faults || FaultState.BothPumpsFailed in faults) {
                    "Check current: ${status.pump1CurrentAmps?.formatAmps() ?: "--"}"
                } else {
                    "Ready (${status.pump1CurrentAmps?.formatAmps() ?: "--"})"
                },
                pump2 = if (FaultState.OnePumpFailed in faults || FaultState.BothPumpsFailed in faults) {
                    "Check current: ${status.pump2CurrentAmps?.formatAmps() ?: "--"}"
                } else {
                    "Ready (${status.pump2CurrentAmps?.formatAmps() ?: "--"})"
                },
                battery = "${status.batteryPercent?.toString() ?: "--"}% charge",
                batteryMode = when (status.batteryType) {
                    BatteryType.Main -> "Main battery"
                    BatteryType.Emergency -> "Emergency battery active"
                    BatteryType.Failed -> "Battery failure"
                    BatteryType.Unknown -> "Battery type unavailable"
                },
                pressure = status.pressureHpa?.let { "%.1f hPa".format(it) } ?: "Pressure unavailable",
                message = appState.lastErrorMessage ?: appState.faultSummary.primaryMessage
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SystemStatusUiState())

    private fun Float.formatAmps(): String = "%.2f A".format(this)
}

data class SystemStatusUiState(
    val summary: String = "Hardware data unavailable",
    val isNominal: Boolean = false,
    val pump1: String = "Unavailable",
    val pump2: String = "Unavailable",
    val battery: String = "--% charge",
    val batteryMode: String = "Battery type unavailable",
    val pressure: String = "Pressure unavailable",
    val message: String = "Waiting for ALBERS data"
)
