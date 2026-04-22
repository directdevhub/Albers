package com.albers.app.data.repository

import android.util.Log
import com.albers.app.data.model.AlbersAppState
import com.albers.app.data.model.AlbersDeviceStatus
import com.albers.app.data.model.BatteryType
import com.albers.app.data.model.DeviceConnectionState
import com.albers.app.data.model.FaultSeverity
import com.albers.app.data.model.FaultState
import com.albers.app.data.model.FaultSummary
import com.albers.app.data.model.NotificationItem
import com.albers.app.data.model.NotificationType
import com.albers.app.data.parser.AlbersBleParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AlbersRepository {
    private const val TAG = "AlbersRepository"
    private const val LOW_BATTERY_THRESHOLD = 10
    private const val CRITICAL_BATTERY_THRESHOLD = 5
    private const val DRY_RUN_CURRENT_THRESHOLD_AMPS = 0.4f
    private const val PRESSURE_FAULT_MIN_HPA = 0f
    private const val PRESSURE_FAULT_MAX_HPA = 1500f

    private val _appState = MutableStateFlow(AlbersAppState())
    val appState: StateFlow<AlbersAppState> = _appState.asStateFlow()

    private var notificationId = 0L
    private var lastAlertKeys = emptySet<String>()

    fun setLoading(isLoading: Boolean) {
        _appState.value = _appState.value.copy(isLoading = isLoading)
    }

    fun setError(message: String) {
        Log.w(TAG, message)
        _appState.value = _appState.value.copy(lastErrorMessage = message, isLoading = false)
    }

    fun updateConnectionState(connectionState: DeviceConnectionState) {
        val current = _appState.value.deviceStatus
        val updatedStatus = current.copy(connectionState = connectionState)
        applyStatus(updatedStatus)

        when (connectionState) {
            DeviceConnectionState.Connected -> addNotificationOnce(
                key = "reconnect-success",
                type = NotificationType.ReconnectSuccess,
                title = "Connection restored",
                message = "ALBERS is connected and ready."
            )

            DeviceConnectionState.Disconnected -> addNotificationOnce(
                key = "connection-lost",
                type = NotificationType.ConnectionLost,
                title = "Connection lost",
                message = "ALBERS disconnected. Use the Start screen to reconnect."
            )

            else -> Unit
        }
    }

    fun updateFromAdcSys(payload: ByteArray) {
        val parsed = AlbersBleParser.parseAdcSys(payload)
        if (parsed == null) {
            setError("Malformed ADC_SYS payload received")
            return
        }
        applyStatus(_appState.value.deviceStatus.merge(parsed))
    }

    fun updateFromTimerSys(payload: ByteArray) {
        val parsed = AlbersBleParser.parseTimerSys(payload)
        if (parsed == null) {
            setError("Malformed Timer_SYS payload received")
            return
        }
        val (elapsedSeconds, isPumping) = parsed
        applyStatus(
            _appState.value.deviceStatus.copy(
                elapsedTimeSeconds = elapsedSeconds,
                isPumping = isPumping,
                lastUpdatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    fun updateFromSavedDiagnostics(payload: ByteArray) {
        val diagnostics = AlbersBleParser.parseSavedDiagnostics(payload)
        diagnostics.forEachIndexed { index, status ->
            val summary = interpretFaults(status)
            addNotification(
                NotificationItem(
                    id = nextNotificationId(),
                    type = summary.toNotificationType(),
                    title = "Stored diagnostic ${index + 1}",
                    message = summary.primaryMessage
                )
            )
        }
    }

    fun recordPumpCycleCompleted() {
        addNotificationOnce(
            key = "pump-cycle-completed",
            type = NotificationType.PumpCycleCompleted,
            title = "Pump cycle completed",
            message = "The latest pump cycle completed."
        )
    }

    fun updateAutomaticPumpInterval(minutes: Int) {
        if (minutes !in SUPPORTED_PUMP_INTERVAL_MINUTES) {
            setError("Unsupported automatic pump interval: $minutes minutes")
            return
        }
        _appState.value = _appState.value.copy(automaticPumpIntervalMinutes = minutes)
    }

    fun clearNotifications() {
        NotificationStore.clear()
        _appState.value = _appState.value.copy(notifications = emptyList())
    }

    private fun applyStatus(status: AlbersDeviceStatus) {
        val summary = interpretFaults(status)
        Log.d(TAG, "Fault state changed: severity=${summary.highestSeverity}, states=${summary.states}")
        val nextState = _appState.value.copy(
            deviceStatus = status,
            faultSummary = summary,
            isLoading = false,
            lastErrorMessage = null
        )
        _appState.value = nextState
        emitFaultNotifications(summary)
    }

    private fun interpretFaults(status: AlbersDeviceStatus): FaultSummary {
        val states = mutableSetOf<FaultState>()
        val batteryPercent = status.batteryPercent
        val pump1Failed = status.pump1CurrentAmps?.let { it < DRY_RUN_CURRENT_THRESHOLD_AMPS } == true
        val pump2Failed = status.pump2CurrentAmps?.let { it < DRY_RUN_CURRENT_THRESHOLD_AMPS } == true

        when {
            pump1Failed && pump2Failed -> states += FaultState.BothPumpsFailed
            pump1Failed || pump2Failed -> states += FaultState.OnePumpFailed
        }

        if (batteryPercent != null && batteryPercent <= CRITICAL_BATTERY_THRESHOLD) {
            states += FaultState.CriticalBattery
        } else if (batteryPercent != null && batteryPercent <= LOW_BATTERY_THRESHOLD) {
            states += FaultState.LowBattery
        }

        when (status.batteryType) {
            BatteryType.Emergency -> states += FaultState.EmergencyBatteryActive
            BatteryType.Failed -> states += FaultState.BatteryFailure
            else -> Unit
        }

        val pressure = status.pressureHpa
        if (pressure != null && (pressure < PRESSURE_FAULT_MIN_HPA || pressure > PRESSURE_FAULT_MAX_HPA)) {
            states += FaultState.PressureSensorFault
        }

        when (status.connectionState) {
            DeviceConnectionState.Disconnected -> states += FaultState.ConnectionLost
            DeviceConnectionState.Reconnecting -> states += FaultState.Reconnecting
            else -> Unit
        }

        val severity = when {
            states.any { it in criticalFaults } -> FaultSeverity.Critical
            states.isNotEmpty() -> FaultSeverity.Warning
            else -> FaultSeverity.Nominal
        }

        return FaultSummary(
            highestSeverity = severity,
            states = states,
            primaryMessage = states.toReadableMessage(),
            canAutoPump = FaultState.BothPumpsFailed !in states &&
                FaultState.PressureSensorFault !in states &&
                FaultState.CriticalBattery !in states &&
                FaultState.BatteryFailure !in states &&
                FaultState.ConnectionLost !in states,
            canRinseSanitize = FaultState.BothPumpsFailed !in states,
            shouldAlert = severity == FaultSeverity.Critical
        )
    }

    private fun emitFaultNotifications(summary: FaultSummary) {
        val keys = summary.states.map { it::class.simpleName.orEmpty() }.toSet()
        val newKeys = keys - lastAlertKeys
        lastAlertKeys = keys

        summary.states.forEach { state ->
            val key = state::class.simpleName.orEmpty()
            if (key !in newKeys) return@forEach

            when (state) {
                FaultState.LowBattery -> addNotification(
                    NotificationType.BatteryLow,
                    "Battery low",
                    "Battery is at or below $LOW_BATTERY_THRESHOLD%."
                )

                FaultState.CriticalBattery -> addNotification(
                    NotificationType.BatteryCritical,
                    "Critical battery",
                    "Battery is at or below $CRITICAL_BATTERY_THRESHOLD%. Take action now."
                )

                FaultState.OnePumpFailed, FaultState.BothPumpsFailed -> addNotification(
                    NotificationType.PumpError,
                    "Pump error detected",
                    summary.primaryMessage
                )

                FaultState.BatteryFailure -> addNotification(
                    NotificationType.BatteryFailure,
                    "Battery failure",
                    "Switch to emergency battery guidance and inspect the battery."
                )

                else -> Unit
            }
        }
    }

    private fun addNotificationOnce(key: String, type: NotificationType, title: String, message: String) {
        if (key in lastAlertKeys) return
        lastAlertKeys = lastAlertKeys + key
        addNotification(type, title, message)
    }

    private fun addNotification(type: NotificationType, title: String, message: String) {
        addNotification(NotificationItem(nextNotificationId(), type, title, message))
    }

    private fun addNotification(item: NotificationItem) {
        NotificationStore.save(item)
        val notifications = (_appState.value.notifications + item)
            .sortedByDescending { it.createdAtMillis }
            .take(50)
        _appState.value = _appState.value.copy(notifications = notifications)
    }

    private fun nextNotificationId(): Long {
        notificationId += 1
        return notificationId
    }

    private fun AlbersDeviceStatus.merge(other: AlbersDeviceStatus): AlbersDeviceStatus {
        return copy(
            batteryPercent = other.batteryPercent ?: batteryPercent,
            batteryType = if (other.batteryType != BatteryType.Unknown) other.batteryType else batteryType,
            pump1CurrentAmps = other.pump1CurrentAmps ?: pump1CurrentAmps,
            pump2CurrentAmps = other.pump2CurrentAmps ?: pump2CurrentAmps,
            pressureHpa = other.pressureHpa ?: pressureHpa,
            elapsedTimeSeconds = other.elapsedTimeSeconds ?: elapsedTimeSeconds,
            isPumping = if (other.elapsedTimeSeconds != null) other.isPumping else isPumping,
            lastUpdatedAtMillis = other.lastUpdatedAtMillis ?: lastUpdatedAtMillis
        )
    }

    private fun Set<FaultState>.toReadableMessage(): String {
        return when {
            isEmpty() -> "All system parameters are nominal"
            FaultState.BatteryFailure in this -> "Battery failure detected. Use emergency battery guidance."
            FaultState.CriticalBattery in this -> "Critical battery. Charge or replace the battery immediately."
            FaultState.BothPumpsFailed in this -> "Both pumps are unavailable. Pumping and rinse actions are disabled."
            FaultState.OnePumpFailed in this -> "One pump appears unavailable. Rinse remains available with one operable pump."
            FaultState.PressureSensorFault in this -> "Pressure sensor value is outside the expected range."
            FaultState.EmergencyBatteryActive in this -> "Emergency battery is active."
            FaultState.LowBattery in this -> "Battery low. Prepare charging or replacement."
            FaultState.Reconnecting in this -> "Connection lost. Reconnecting to ALBERS."
            FaultState.ConnectionLost in this -> "ALBERS is disconnected."
            else -> "Unknown ALBERS fault detected."
        }
    }

    private fun FaultSummary.toNotificationType(): NotificationType {
        return when {
            FaultState.BatteryFailure in states -> NotificationType.BatteryFailure
            FaultState.CriticalBattery in states -> NotificationType.BatteryCritical
            FaultState.LowBattery in states -> NotificationType.BatteryLow
            FaultState.OnePumpFailed in states || FaultState.BothPumpsFailed in states -> NotificationType.PumpError
            else -> NotificationType.UnknownFault
        }
    }

    private val criticalFaults = setOf(
        FaultState.BothPumpsFailed,
        FaultState.CriticalBattery,
        FaultState.BatteryFailure
    )

    private val SUPPORTED_PUMP_INTERVAL_MINUTES = setOf(60, 90, 120)
}
