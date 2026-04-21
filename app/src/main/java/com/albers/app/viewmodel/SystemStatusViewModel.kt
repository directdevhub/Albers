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
            val pump1Failed = status.pump1CurrentAmps?.let { it < PUMP_FAILURE_CURRENT_AMPS } == true ||
                FaultState.BothPumpsFailed in faults
            val pump2Failed = status.pump2CurrentAmps?.let { it < PUMP_FAILURE_CURRENT_AMPS } == true ||
                FaultState.BothPumpsFailed in faults
            val batteryPercent = status.batteryPercent
            SystemStatusUiState(
                summary = appState.faultSummary.primaryMessage,
                isNominal = appState.faultSummary.highestSeverity == FaultSeverity.Nominal,
                pump1 = if (pump1Failed) {
                    "Check current: ${status.pump1CurrentAmps?.formatAmps() ?: "--"}"
                } else {
                    "Ready (${status.pump1CurrentAmps?.formatAmps() ?: "--"})"
                },
                pump2 = if (pump2Failed) {
                    "Check current: ${status.pump2CurrentAmps?.formatAmps() ?: "--"}"
                } else {
                    "Ready (${status.pump2CurrentAmps?.formatAmps() ?: "--"})"
                },
                pump1Failed = pump1Failed,
                pump2Failed = pump2Failed,
                batteryPercent = batteryPercent,
                battery = "${status.batteryPercent?.toString() ?: "--"}% charge",
                batteryMode = when (status.batteryType) {
                    BatteryType.Main -> "Main battery"
                    BatteryType.Emergency -> "Emergency battery active"
                    BatteryType.Failed -> "Battery failure"
                    BatteryType.Unknown -> "Battery type unavailable"
                },
                pressure = status.pressureHpa?.let { "%.1f hPa".format(it) } ?: "Pressure unavailable",
                message = appState.lastErrorMessage ?: appState.faultSummary.primaryMessage,
                statusIcon = when {
                    pump1Failed || pump2Failed || FaultState.BatteryFailure in faults -> SystemStatusIcon.PumpError
                    status.batteryType == BatteryType.Emergency -> SystemStatusIcon.EmergencyBattery
                    batteryPercent != null && batteryPercent <= LOW_BATTERY_PERCENT -> SystemStatusIcon.LowBattery
                    else -> SystemStatusIcon.Nominal
                },
                isBatteryLow = batteryPercent != null && batteryPercent <= LOW_BATTERY_PERCENT
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SystemStatusUiState())

    private fun Float.formatAmps(): String = "%.2f A".format(this)

    private companion object {
        private const val PUMP_FAILURE_CURRENT_AMPS = 0.4f
        private const val LOW_BATTERY_PERCENT = 10
    }
}

data class SystemStatusUiState(
    val summary: String = "Hardware data unavailable",
    val isNominal: Boolean = false,
    val pump1: String = "Unavailable",
    val pump2: String = "Unavailable",
    val pump1Failed: Boolean = false,
    val pump2Failed: Boolean = false,
    val batteryPercent: Int? = null,
    val battery: String = "--% charge",
    val batteryMode: String = "Battery type unavailable",
    val pressure: String = "Pressure unavailable",
    val message: String = "Waiting for ALBERS data",
    val statusIcon: SystemStatusIcon = SystemStatusIcon.Nominal,
    val isBatteryLow: Boolean = false
)

enum class SystemStatusIcon {
    Nominal,
    LowBattery,
    EmergencyBattery,
    PumpError
}
