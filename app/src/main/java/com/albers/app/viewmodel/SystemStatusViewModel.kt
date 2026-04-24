package com.albers.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albers.app.data.model.BatteryType
import com.albers.app.data.model.FaultSeverity
import com.albers.app.data.model.PumpOperability
import com.albers.app.data.model.PumpReading
import com.albers.app.data.repository.AlbersRepository
import com.albers.app.ui.common.SystemStatusBadge
import com.albers.app.ui.common.resolveSystemStatusBadge
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SystemStatusViewModel : ViewModel() {
    val uiState = AlbersRepository.appState
        .map { appState ->
            val status = appState.deviceStatus
            val faults = appState.faultSummary.states
            val batteryPercent = status.batteryStatus.percent
            SystemStatusUiState(
                summary = appState.faultSummary.primaryMessage,
                isNominal = appState.faultSummary.highestSeverity == FaultSeverity.Nominal,
                pump1 = status.pumpStatus.pump1.toShortStatus(),
                pump2 = status.pumpStatus.pump2.toShortStatus(),
                pump1Failed = status.pumpStatus.pump1.operability == PumpOperability.Inoperable,
                pump2Failed = status.pumpStatus.pump2.operability == PumpOperability.Inoperable,
                batteryPercent = batteryPercent,
                battery = when (batteryPercent) {
                    null -> "Battery unavailable"
                    else -> "$batteryPercent% battery"
                },
                batteryMode = when (status.batteryType) {
                    BatteryType.Main -> "Main battery"
                    BatteryType.Emergency -> "Emergency battery active"
                    BatteryType.Failed -> "Battery failure"
                    BatteryType.Unknown -> "Battery type unavailable"
                },
                pressure = status.pressureHpa?.let { "%.1f hPa".format(it) } ?: "Pressure unavailable",
                message = appState.lastErrorMessage ?: status.deviceTimestamp?.toDisplayText() ?: "Waiting for ALBERS data",
                statusBadge = resolveSystemStatusBadge(
                    faults = faults,
                    batteryType = status.batteryType,
                    batteryPercent = batteryPercent
                ),
                isBatteryLow = batteryPercent != null && batteryPercent <= LOW_BATTERY_PERCENT
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SystemStatusUiState())

    private fun PumpReading.toShortStatus(): String {
        return when (operability) {
            PumpOperability.Inoperable -> "ERROR"
            PumpOperability.Operable -> "ACTIVE"
            PumpOperability.Unknown -> "WAITING"
        }
    }

    private companion object {
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
    val battery: String = "Battery unavailable",
    val batteryMode: String = "Battery type unavailable",
    val pressure: String = "Pressure unavailable",
    val message: String = "Waiting for ALBERS data",
    val statusBadge: SystemStatusBadge = SystemStatusBadge.Nominal,
    val isBatteryLow: Boolean = false
)
