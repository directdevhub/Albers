package com.albers.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albers.app.ble.AlbersBleSession
import com.albers.app.data.model.DeviceConnectionState
import com.albers.app.data.model.FaultSeverity
import com.albers.app.data.model.PumpOperability
import com.albers.app.data.repository.AlbersRepository
import com.albers.app.ui.common.SystemStatusBadge
import com.albers.app.ui.common.resolveSystemStatusBadge
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

class DashboardViewModel : ViewModel() {
    private val clockTickMillis = MutableStateFlow(System.currentTimeMillis())

    val uiState = combine(AlbersRepository.appState, clockTickMillis) { appState, nowMillis ->
        val status = appState.deviceStatus
        val faultSummary = appState.faultSummary
        val isConnectedReady = status.connectionState == DeviceConnectionState.ConnectedReady
        val isPumping = status.timerStatus.pumpActive
        val countdownSeconds = status.countdownSeconds(nowMillis)
            ?: appState.automaticPumpIntervalMinutes * 60
        val pumpingTextVisible = !isPumping || ((nowMillis / PUMP_BLINK_INTERVAL_MS) % 2L == 0L)

        DashboardUiState(
            deviceState = when (status.connectionState) {
                DeviceConnectionState.ConnectedReady -> "ON"
                DeviceConnectionState.Scanning -> "SCAN"
                DeviceConnectionState.Bonding -> "PAIR"
                DeviceConnectionState.Connecting,
                DeviceConnectionState.DiscoveringServices -> "SYNC"
                DeviceConnectionState.Reconnecting -> "RETRY"
                DeviceConnectionState.AuthRequired -> "AUTH"
                DeviceConnectionState.ConnectionFailed,
                DeviceConnectionState.Disconnected -> "OFF"
            },
            countdownText = countdownSeconds.toCountdownText(),
            pumpStatus = if (isPumping) "PUMPING" else "Pump idle",
            warningText = appState.lastErrorMessage ?: faultSummary.primaryMessage,
            showWarning = faultSummary.highestSeverity != FaultSeverity.Nominal || appState.lastErrorMessage != null,
            showPumping = isPumping,
            isCritical = faultSummary.highestSeverity == FaultSeverity.Critical,
            canPumpNow = faultSummary.canAutoPump && isConnectedReady && !isPumping,
            canRinseSanitize = faultSummary.canRinseSanitize,
            isDeviceButtonEnabled = isConnectedReady && !isPumping,
            isDeviceIlluminated = isConnectedReady && !isPumping,
            isStopIlluminated = isPumping,
            isStopEnabled = isPumping,
            stopLabel = if (isPumping) "STOP" else "OFF",
            pumpNowLabel = when {
                isPumping && pumpingTextVisible -> "PUMPING"
                isPumping -> ""
                else -> "PUMP NOW"
            },
            shouldShowTimerOverride = !isPumping,
            statusBadge = resolveSystemStatusBadge(
                faults = faultSummary.states,
                batteryType = status.batteryType,
                batteryPercent = status.batteryStatus.percent
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    init {
        viewModelScope.launch {
            while (isActive) {
                clockTickMillis.value = System.currentTimeMillis()
                delay(CLOCK_TICK_INTERVAL_MS)
            }
        }
    }

    fun onDeviceStateClicked() {
        val status = AlbersRepository.appState.value.deviceStatus
        if (status.connectionState != DeviceConnectionState.ConnectedReady || status.timerStatus.pumpActive) {
            return
        }
        AlbersBleSession.startAutomaticCycle()
    }

    fun onPumpNowClicked() {
        if (!AlbersRepository.appState.value.faultSummary.canAutoPump) return
        AlbersBleSession.pumpNow()
    }

    fun onStopClicked() {
        if (!AlbersRepository.appState.value.deviceStatus.timerStatus.pumpActive) return
        AlbersBleSession.stopPumpOrCycle()
    }

    fun deviceStateClickMessage(): String {
        val appState = AlbersRepository.appState.value
        val status = appState.deviceStatus
        return when {
            status.timerStatus.pumpActive ->
                "ALBERS is already pumping."
            status.connectionState == DeviceConnectionState.ConnectedReady ->
                "Automatic pump cycle started. The countdown will restart from the selected interval."
            status.connectionState == DeviceConnectionState.Reconnecting ->
                "ALBERS is reconnecting. Wait for the connection to recover first."
            status.connectionState == DeviceConnectionState.AuthRequired ->
                "Authentication is required. Pair with PIN 333333 and reconnect."
            status.connectionState == DeviceConnectionState.ConnectionFailed ||
                status.connectionState == DeviceConnectionState.Disconnected ->
                "ALBERS is not connected. Return to Connect and start the BLE connection flow."
            else ->
                "ALBERS is still preparing. Please wait a moment."
        }
    }

    fun pumpNowClickMessage(): String {
        val appState = AlbersRepository.appState.value
        val status = appState.deviceStatus
        val faults = appState.faultSummary
        return when {
            status.timerStatus.pumpActive ->
                "ALBERS is already pumping."
            status.connectionState != DeviceConnectionState.ConnectedReady ->
                "ALBERS is not connected and ready yet."
            status.pumpStatus.bothPumpsInoperable ->
                "PUMP NOW is unavailable because both pumps report an error."
            status.pumpStatus.pump1.operability != PumpOperability.Operable &&
                status.pumpStatus.pump2.operability != PumpOperability.Operable ->
                "PUMP NOW is unavailable because both pumps are not active."
            !faults.canAutoPump ->
                faults.primaryMessage
            else ->
                "PUMP NOW activated. ALBERS should begin pumping immediately."
        }
    }

    fun stopClickMessage(): String {
        val status = AlbersRepository.appState.value.deviceStatus
        return if (status.timerStatus.pumpActive) {
            "Stop requested. Use device-side emergency stop guidance if pumping must stop immediately."
        } else {
            "Stop is only available while ALBERS is pumping."
        }
    }

    private fun Int.toCountdownText(): String {
        val hours = this / 3600
        val minutes = (this % 3600) / 60
        val seconds = this % 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    private fun com.albers.app.data.model.AlbersDeviceStatus.countdownSeconds(nowMillis: Long): Int? {
        val waitTimeSeconds = timerStatus.waitTimeSeconds ?: elapsedTimeSeconds ?: return null
        if (timerStatus.pumpActive) return 0

        val reportedAtMillis = timerStatus.lastReportedAtMillis ?: lastUpdatedAtMillis ?: return waitTimeSeconds
        val elapsedSecondsSinceReport = ((nowMillis - reportedAtMillis) / 1000L).toInt().coerceAtLeast(0)
        return max(0, waitTimeSeconds - elapsedSecondsSinceReport)
    }
}

data class DashboardUiState(
    val deviceState: String = "OFF",
    val countdownText: String = "01:30:00",
    val pumpStatus: String = "Pump idle",
    val warningText: String = "All system parameters are nominal",
    val showWarning: Boolean = false,
    val showPumping: Boolean = false,
    val isCritical: Boolean = false,
    val canPumpNow: Boolean = false,
    val canRinseSanitize: Boolean = true,
    val isDeviceButtonEnabled: Boolean = false,
    val isDeviceIlluminated: Boolean = false,
    val isStopIlluminated: Boolean = false,
    val isStopEnabled: Boolean = false,
    val stopLabel: String = "OFF",
    val pumpNowLabel: String = "PUMP NOW",
    val shouldShowTimerOverride: Boolean = true,
    val statusBadge: SystemStatusBadge = SystemStatusBadge.Nominal
)

private const val CLOCK_TICK_INTERVAL_MS = 500L
private const val PUMP_BLINK_INTERVAL_MS = 500L
