package com.albers.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albers.app.ble.AlbersBleSession
import com.albers.app.data.repository.AlbersRepository
import com.albers.app.ui.common.SystemStatusBadge
import com.albers.app.ui.common.resolveSystemStatusBadge
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RinseViewModel : ViewModel() {
    private val localState = MutableStateFlow(RinseLocalState())
    private var countdownJob: Job? = null

    val uiState = combine(AlbersRepository.appState, localState) { appState, local ->
        val canStartFromHardware = appState.faultSummary.canRinseSanitize
        val canStart = canStartFromHardware && !local.isRunning
        RinseUiState(
            canStart = canStart,
            canEmergencyStop = local.isRunning,
            countdownText = local.remainingSeconds.toCountdownText(),
            availabilityMessage = when {
                !canStartFromHardware ->
                    "Rinse/Sanitize is disabled because both pumps are unavailable or ALBERS is disconnected."
                local.isRunning ->
                    "Rinse/Sanitize is active. Emergency STOP only has a local fallback until firmware stop is documented."
                else ->
                    "Rinse/Sanitize is available."
            },
            statusBadge = resolveSystemStatusBadge(
                faults = appState.faultSummary.states,
                batteryType = appState.deviceStatus.batteryType,
                batteryPercent = appState.deviceStatus.batteryStatus.percent
            ),
            isRunning = local.isRunning
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RinseUiState())

    fun startRinseCycle() {
        if (!AlbersRepository.appState.value.faultSummary.canRinseSanitize || localState.value.isRunning) {
            return
        }
        AlbersBleSession.startRinseCycle()
        startCountdown()
    }

    fun emergencyStop() {
        resetCountdown()
        AlbersBleSession.stopPumpOrCycle()
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        localState.value = RinseLocalState(remainingSeconds = RINSE_DURATION_SECONDS, isRunning = true)
        countdownJob = viewModelScope.launch {
            var seconds = RINSE_DURATION_SECONDS
            while (isActive && seconds > 0) {
                delay(1_000)
                seconds -= 1
                localState.update { it.copy(remainingSeconds = seconds, isRunning = seconds > 0) }
            }
            resetCountdown()
        }
    }

    private fun resetCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        localState.value = RinseLocalState()
    }

    private fun Int.toCountdownText(): String {
        val totalSeconds = this.coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}

data class RinseUiState(
    val canStart: Boolean = true,
    val canEmergencyStop: Boolean = false,
    val countdownText: String = "01:00",
    val availabilityMessage: String = "Rinse/Sanitize is available.",
    val statusBadge: SystemStatusBadge = SystemStatusBadge.Nominal,
    val isRunning: Boolean = false
)

private data class RinseLocalState(
    val remainingSeconds: Int = RINSE_DURATION_SECONDS,
    val isRunning: Boolean = false
)

private const val RINSE_DURATION_SECONDS = 60
