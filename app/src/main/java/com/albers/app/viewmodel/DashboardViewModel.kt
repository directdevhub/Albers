package com.albers.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albers.app.data.model.DeviceConnectionState
import com.albers.app.data.model.FaultSeverity
import com.albers.app.data.model.FaultState
import com.albers.app.data.repository.AlbersRepository
import com.albers.app.ble.AlbersBleSession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel : ViewModel() {
    val uiState = AlbersRepository.appState
        .map { appState ->
            val status = appState.deviceStatus
            val faultSummary = appState.faultSummary
            DashboardUiState(
                deviceState = when (status.connectionState) {
                    DeviceConnectionState.Connected -> "ON"
                    DeviceConnectionState.Connecting -> "CONNECTING"
                    DeviceConnectionState.Reconnecting -> "RECONNECTING"
                    DeviceConnectionState.Disconnected -> "OFF"
                },
                countdownText = status.elapsedTimeSeconds?.toCountdownText()
                    ?: if (status.isPumping) "00:00:00" else appState.automaticPumpIntervalMinutes.toIntervalText(),
                pumpStatus = if (status.isPumping) "PUMPING" else "Pump status: Idle",
                warningText = faultSummary.primaryMessage,
                showWarning = faultSummary.highestSeverity != FaultSeverity.Nominal,
                showPumping = status.isPumping,
                isCritical = faultSummary.highestSeverity == FaultSeverity.Critical,
                canPumpNow = faultSummary.canAutoPump && status.connectionState == DeviceConnectionState.Connected,
                canRinseSanitize = faultSummary.canRinseSanitize,
                indicator = when {
                    FaultState.CriticalBattery in faultSummary.states -> DashboardIndicator.CriticalBattery
                    FaultState.LowBattery in faultSummary.states -> DashboardIndicator.LowBattery
                    FaultState.EmergencyBatteryActive in faultSummary.states -> DashboardIndicator.EmergencyBattery
                    FaultState.OnePumpFailed in faultSummary.states ||
                        FaultState.BothPumpsFailed in faultSummary.states ||
                        FaultState.PressureSensorFault in faultSummary.states -> DashboardIndicator.Hazard
                    faultSummary.highestSeverity == FaultSeverity.Nominal -> DashboardIndicator.Nominal
                    else -> DashboardIndicator.Hazard
                }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun onPumpNowClicked() {
        AlbersBleSession.pumpNow()
    }

    fun onStopClicked() {
        AlbersBleSession.stopPumpOrCycle()
    }

    private fun Int.toCountdownText(): String {
        val hours = this / 3600
        val minutes = (this % 3600) / 60
        val seconds = this % 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    private fun Int.toIntervalText(): String {
        val hours = this / 60
        val minutes = this % 60
        return "%02d:%02d:%02d".format(hours, minutes, 0)
    }
}

data class DashboardUiState(
    val deviceState: String = "OFF",
    val countdownText: String = "01:30:00",
    val pumpStatus: String = "Pump status: Idle",
    val warningText: String = "All system parameters are nominal",
    val showWarning: Boolean = false,
    val showPumping: Boolean = false,
    val isCritical: Boolean = false,
    val canPumpNow: Boolean = false,
    val canRinseSanitize: Boolean = true,
    val indicator: DashboardIndicator = DashboardIndicator.Nominal
)

enum class DashboardIndicator {
    Nominal,
    Hazard,
    LowBattery,
    CriticalBattery,
    EmergencyBattery
}
