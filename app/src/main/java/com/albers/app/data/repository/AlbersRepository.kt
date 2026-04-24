package com.albers.app.data.repository

import android.util.Log
import com.albers.app.data.model.AlbersAppState
import com.albers.app.data.model.AlbersDeviceStatus
import com.albers.app.data.model.BatteryStatus
import com.albers.app.data.model.BatteryType
import com.albers.app.data.model.DeviceConnectionState
import com.albers.app.data.model.FaultSeverity
import com.albers.app.data.model.FaultState
import com.albers.app.data.model.FaultSummary
import com.albers.app.data.model.NotificationItem
import com.albers.app.data.model.NotificationType
import com.albers.app.data.model.PumpCondition
import com.albers.app.data.model.PumpOperability
import com.albers.app.data.model.PumpReading
import com.albers.app.data.model.PumpStatus
import com.albers.app.data.model.SavedDiagnosticEntry
import com.albers.app.data.model.TimerStatus
import com.albers.app.data.parser.AlbersBleParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AlbersRepository {
    private const val TAG = "AlbersRepository"
    private const val LOW_BATTERY_THRESHOLD = 10
    private const val CRITICAL_BATTERY_THRESHOLD = 5
    private const val NO_FLOW_CURRENT_THRESHOLD_AMPS = 0.05f
    private const val DRY_RUN_CURRENT_THRESHOLD_AMPS = 0.4f
    private const val PRIMING_CURRENT_THRESHOLD_AMPS = 0.5f
    private const val NOMINAL_PUMPING_CURRENT_MIN_AMPS = 0.8f
    private const val NOMINAL_PUMPING_CURRENT_MAX_AMPS = 1.0f
    private const val HIGH_LOAD_CURRENT_THRESHOLD_AMPS = 1.1f
    private const val PRESSURE_FAULT_MIN_HPA = 0f
    private const val PRESSURE_FAULT_MAX_HPA = 1500f

    private val _appState = MutableStateFlow(AlbersAppState())
    val appState: StateFlow<AlbersAppState> = _appState.asStateFlow()

    private var notificationIdSeed = System.currentTimeMillis()
    private var lastFaultKeys = emptySet<String>()
    private var handledSavedDiagnosticKeys = mutableSetOf<String>()

    fun setLoading(isLoading: Boolean) {
        _appState.value = _appState.value.copy(isLoading = isLoading)
    }

    fun setError(message: String) {
        Log.w(TAG, message)
        _appState.value = _appState.value.copy(lastErrorMessage = message, isLoading = false)
    }

    fun updateConnectionState(connectionState: DeviceConnectionState) {
        val previousState = _appState.value.deviceStatus.connectionState
        val updatedStatus = _appState.value.deviceStatus.copy(
            connectionState = connectionState,
            lastUpdatedAtMillis = System.currentTimeMillis()
        )
        applyStatus(updatedStatus)

        when {
            connectionState == DeviceConnectionState.ConnectedReady &&
                previousState in reconnectableStates -> {
                addNotification(
                    NotificationType.ReconnectSuccess,
                    "Connection ready",
                    "ALBERS is connected and diagnostics are available."
                )
            }

            connectionState == DeviceConnectionState.Disconnected &&
                previousState != DeviceConnectionState.Disconnected -> {
                addNotification(
                    NotificationType.ConnectionLost,
                    "Connection lost",
                    "ALBERS disconnected. Return to Connect and retry the BLE session."
                )
            }
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

        val previousPumpActive = _appState.value.deviceStatus.timerStatus.pumpActive
        val merged = _appState.value.deviceStatus.merge(
            AlbersDeviceStatus(
                elapsedTimeSeconds = parsed.waitTimeSeconds,
                isPumping = parsed.pumpActive,
                timerStatus = parsed,
                lastUpdatedAtMillis = parsed.lastReportedAtMillis
            )
        )
        applyStatus(merged)

        if (previousPumpActive && !parsed.pumpActive) {
            addNotification(
                NotificationType.PumpCycleCompleted,
                "Pump cycle completed",
                "The active ALBERS pump cycle completed and the timer has reset."
            )
        }
    }

    fun updateFromSavedDiagnostics(payload: ByteArray) {
        val diagnostics = AlbersBleParser.parseSavedDiagnostics(payload)
        diagnostics.forEach { entry ->
            if (!handledSavedDiagnosticKeys.add(entry.stableKey)) return@forEach

            val summary = interpretFaults(normalizeStatus(entry.status))
            val createdAtMillis = entry.occurredAtMillis ?: System.currentTimeMillis()
            addNotification(
                NotificationItem(
                    id = nextNotificationId(),
                    type = NotificationType.OfflineDiagnostic,
                    title = offlineDiagnosticTitle(summary),
                    message = offlineDiagnosticMessage(entry, summary),
                    createdAtMillis = createdAtMillis
                )
            )
        }
    }

    fun recordPumpCycleCompleted() {
        addNotification(
            NotificationType.PumpCycleCompleted,
            "Pump cycle completed",
            "The latest ALBERS pump cycle completed."
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
        val normalized = normalizeStatus(status)
        val summary = interpretFaults(normalized)
        Log.d(TAG, "Status update: connection=${normalized.connectionState} faults=${summary.states}")
        _appState.value = _appState.value.copy(
            deviceStatus = normalized,
            faultSummary = summary,
            isLoading = false,
            lastErrorMessage = null
        )
        emitFaultNotifications(summary)
    }

    private fun normalizeStatus(status: AlbersDeviceStatus): AlbersDeviceStatus {
        val timerStatus = status.timerStatus.withFallback(
            waitTimeSeconds = status.elapsedTimeSeconds,
            pumpActive = status.isPumping,
            lastReportedAtMillis = status.lastUpdatedAtMillis
        )
        val batteryStatus = BatteryStatus(
            percent = status.batteryPercent,
            type = status.batteryType,
            isLow = status.batteryPercent?.let { it <= LOW_BATTERY_THRESHOLD } == true,
            isCritical = status.batteryPercent?.let { it <= CRITICAL_BATTERY_THRESHOLD } == true
        )
        val pumpStatus = classifyPumps(
            pump1CurrentAmps = status.pump1CurrentAmps,
            pump2CurrentAmps = status.pump2CurrentAmps,
            isPumpActive = timerStatus.pumpActive
        )

        return status.copy(
            batteryStatus = batteryStatus,
            pumpStatus = pumpStatus,
            timerStatus = timerStatus,
            elapsedTimeSeconds = timerStatus.waitTimeSeconds,
            isPumping = timerStatus.pumpActive
        )
    }

    private fun interpretFaults(status: AlbersDeviceStatus): FaultSummary {
        val states = linkedSetOf<FaultState>()
        val batteryStatus = status.batteryStatus
        val pressure = status.pressureHpa

        when {
            status.pumpStatus.bothPumpsInoperable -> states += FaultState.BothPumpsFailed
            status.pumpStatus.pump1.operability == PumpOperability.Inoperable ||
                status.pumpStatus.pump2.operability == PumpOperability.Inoperable -> {
                states += FaultState.OnePumpFailed
            }
        }

        when {
            batteryStatus.isCritical -> states += FaultState.CriticalBattery
            batteryStatus.isLow -> states += FaultState.LowBattery
        }

        when (batteryStatus.type) {
            BatteryType.Emergency -> states += FaultState.EmergencyBatteryActive
            BatteryType.Failed -> states += FaultState.BatteryFailure
            else -> Unit
        }

        if (pressure != null && (pressure < PRESSURE_FAULT_MIN_HPA || pressure > PRESSURE_FAULT_MAX_HPA)) {
            states += FaultState.PressureSensorFault
        }

        when (status.connectionState) {
            DeviceConnectionState.Disconnected -> states += FaultState.ConnectionLost
            DeviceConnectionState.Reconnecting -> states += FaultState.Reconnecting
            DeviceConnectionState.AuthRequired -> states += FaultState.AuthenticationRequired
            DeviceConnectionState.ConnectionFailed -> states += FaultState.ConnectionFailure
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
            primaryMessage = states.toReadableMessage(status),
            canAutoPump = status.connectionState == DeviceConnectionState.ConnectedReady &&
                !status.pumpStatus.bothPumpsInoperable &&
                FaultState.BatteryFailure !in states,
            canRinseSanitize = status.connectionState == DeviceConnectionState.ConnectedReady &&
                !status.pumpStatus.bothPumpsInoperable,
            shouldAlert = severity == FaultSeverity.Critical
        )
    }

    private fun classifyPumps(
        pump1CurrentAmps: Float?,
        pump2CurrentAmps: Float?,
        isPumpActive: Boolean
    ): PumpStatus {
        return PumpStatus(
            pump1 = classifyPump(channel = 1, currentAmps = pump1CurrentAmps, isPumpActive = isPumpActive),
            pump2 = classifyPump(channel = 2, currentAmps = pump2CurrentAmps, isPumpActive = isPumpActive)
        )
    }

    private fun classifyPump(channel: Int, currentAmps: Float?, isPumpActive: Boolean): PumpReading {
        if (currentAmps == null) {
            return PumpReading(
                channel = channel,
                currentAmps = null,
                condition = PumpCondition.Unknown,
                operability = PumpOperability.Unknown,
                detail = "Pump $channel current unavailable"
            )
        }

        if (!isPumpActive) {
            return PumpReading(
                channel = channel,
                currentAmps = currentAmps,
                condition = PumpCondition.Idle,
                operability = PumpOperability.Unknown,
                detail = "Pump $channel is idle and waiting for the next cycle"
            )
        }

        return when {
            currentAmps < NO_FLOW_CURRENT_THRESHOLD_AMPS -> PumpReading(
                channel = channel,
                currentAmps = currentAmps,
                condition = PumpCondition.NoFlow,
                operability = PumpOperability.Inoperable,
                detail = "Pump $channel current is in the low/no-flow range"
            )

            currentAmps < DRY_RUN_CURRENT_THRESHOLD_AMPS -> PumpReading(
                channel = channel,
                currentAmps = currentAmps,
                condition = PumpCondition.DryRun,
                operability = PumpOperability.Inoperable,
                detail = "Pump $channel is below the dry-run threshold"
            )

            currentAmps < PRIMING_CURRENT_THRESHOLD_AMPS -> PumpReading(
                channel = channel,
                currentAmps = currentAmps,
                condition = PumpCondition.Priming,
                operability = PumpOperability.Operable,
                detail = "Pump $channel is in the priming / air-only range"
            )

            currentAmps in NOMINAL_PUMPING_CURRENT_MIN_AMPS..NOMINAL_PUMPING_CURRENT_MAX_AMPS -> PumpReading(
                channel = channel,
                currentAmps = currentAmps,
                condition = PumpCondition.Nominal,
                operability = PumpOperability.Operable,
                detail = "Pump $channel current is in the nominal pumping range"
            )

            currentAmps >= HIGH_LOAD_CURRENT_THRESHOLD_AMPS -> PumpReading(
                channel = channel,
                currentAmps = currentAmps,
                condition = PumpCondition.HighLoad,
                operability = PumpOperability.Operable,
                detail = "Pump $channel current is above the nominal range"
            )

            else -> PumpReading(
                channel = channel,
                currentAmps = currentAmps,
                condition = PumpCondition.Nominal,
                operability = PumpOperability.Operable,
                detail = "Pump $channel is active"
            )
        }
    }

    private fun emitFaultNotifications(summary: FaultSummary) {
        val keys = summary.states.map { it::class.simpleName.orEmpty() }.toSet()
        val newKeys = keys - lastFaultKeys
        lastFaultKeys = keys

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
                    "Battery is at or below $CRITICAL_BATTERY_THRESHOLD%. Connect external power now."
                )

                FaultState.OnePumpFailed,
                FaultState.BothPumpsFailed -> addNotification(
                    NotificationType.PumpError,
                    "Pump error detected",
                    summary.primaryMessage
                )

                FaultState.BatteryFailure -> addNotification(
                    NotificationType.BatteryFailure,
                    "Battery failure",
                    "Inspect the battery path and switch to emergency battery guidance."
                )

                FaultState.ConnectionFailure -> addNotification(
                    NotificationType.UnknownFault,
                    "Connection failed",
                    "ALBERS BLE connection failed. Power-cycle the board and reconnect."
                )

                else -> Unit
            }
        }
    }

    private fun offlineDiagnosticTitle(summary: FaultSummary): String {
        return when {
            FaultState.CriticalBattery in summary.states -> "Offline diagnostic: critical battery"
            FaultState.LowBattery in summary.states -> "Offline diagnostic: battery low"
            FaultState.OnePumpFailed in summary.states || FaultState.BothPumpsFailed in summary.states ->
                "Offline diagnostic: pump fault"
            FaultState.BatteryFailure in summary.states -> "Offline diagnostic: battery failure"
            else -> "Offline diagnostic restored"
        }
    }

    private fun offlineDiagnosticMessage(entry: SavedDiagnosticEntry, summary: FaultSummary): String {
        val timestampText = entry.status.deviceTimestamp?.toDisplayText() ?: "Saved while disconnected"
        val batteryText = entry.status.batteryPercent?.let { "$it%" } ?: "unknown battery"
        val pressureText = entry.status.pressureHpa?.let { "%.1f hPa".format(it) } ?: "pressure unavailable"
        return "$timestampText. ${summary.primaryMessage}. Battery $batteryText. $pressureText."
    }

    private fun addNotification(type: NotificationType, title: String, message: String) {
        addNotification(NotificationItem(nextNotificationId(), type, title, message))
    }

    private fun addNotification(item: NotificationItem) {
        NotificationStore.save(item)
        val notifications = (_appState.value.notifications + item)
            .sortedByDescending { it.createdAtMillis }
            .take(100)
        _appState.value = _appState.value.copy(notifications = notifications)
    }

    private fun nextNotificationId(): Long {
        notificationIdSeed = maxOf(notificationIdSeed + 1L, System.currentTimeMillis())
        return notificationIdSeed
    }

    private fun AlbersDeviceStatus.merge(other: AlbersDeviceStatus): AlbersDeviceStatus {
        val timerStatus = if (
            other.timerStatus.lastReportedAtMillis != null ||
            other.elapsedTimeSeconds != null
        ) {
            other.timerStatus
        } else {
            timerStatus
        }

        return copy(
            deviceTimestamp = other.deviceTimestamp ?: deviceTimestamp,
            batteryPercent = other.batteryPercent ?: batteryPercent,
            batteryType = if (other.batteryType != BatteryType.Unknown) other.batteryType else batteryType,
            batteryStatus = if (other.batteryPercent != null || other.batteryType != BatteryType.Unknown) {
                other.batteryStatus
            } else {
                batteryStatus
            },
            pump1CurrentAmps = other.pump1CurrentAmps ?: pump1CurrentAmps,
            pump2CurrentAmps = other.pump2CurrentAmps ?: pump2CurrentAmps,
            pumpStatus = if (
                other.pump1CurrentAmps != null ||
                other.pump2CurrentAmps != null
            ) {
                other.pumpStatus
            } else {
                pumpStatus
            },
            pressureHpa = other.pressureHpa ?: pressureHpa,
            elapsedTimeSeconds = other.elapsedTimeSeconds ?: elapsedTimeSeconds,
            isPumping = if (other.elapsedTimeSeconds != null || other.timerStatus.lastReportedAtMillis != null) {
                other.isPumping
            } else {
                isPumping
            },
            timerStatus = timerStatus,
            connectionState = other.connectionState.takeUnless { it == DeviceConnectionState.Disconnected } ?: connectionState,
            lastUpdatedAtMillis = other.lastUpdatedAtMillis ?: lastUpdatedAtMillis
        )
    }

    private fun Set<FaultState>.toReadableMessage(status: AlbersDeviceStatus): String {
        return when {
            isEmpty() -> "All system parameters are nominal"
            FaultState.AuthenticationRequired in this ->
                "Authentication is required. Pair with PIN 333333 and reconnect."
            FaultState.ConnectionFailure in this ->
                "ALBERS connection failed. Retry scan/connect and verify BLE authentication."
            FaultState.BatteryFailure in this ->
                "Battery failure detected. Connect external power and inspect the battery path."
            FaultState.CriticalBattery in this ->
                "Critical battery. Connect external power or replace the battery immediately."
            FaultState.BothPumpsFailed in this ->
                "Both pumps appear unavailable during the active cycle."
            FaultState.OnePumpFailed in this ->
                "One pump appears unavailable during the active cycle."
            FaultState.PressureSensorFault in this ->
                "Pressure sensor value is outside the expected range."
            FaultState.EmergencyBatteryActive in this ->
                "Emergency battery is active."
            FaultState.LowBattery in this ->
                "Battery low. Prepare charging or external power."
            FaultState.Reconnecting in this ->
                "Connection dropped. Reconnecting to ALBERS."
            FaultState.ConnectionLost in this ->
                "ALBERS is disconnected."
            else -> "Unknown ALBERS fault detected."
        }
    }

    private fun TimerStatus.withFallback(
        waitTimeSeconds: Int?,
        pumpActive: Boolean,
        lastReportedAtMillis: Long?
    ): TimerStatus {
        return copy(
            waitTimeSeconds = this.waitTimeSeconds ?: waitTimeSeconds,
            pumpActive = this.rawPumpStatus?.let { it == 1 } ?: pumpActive,
            lastReportedAtMillis = this.lastReportedAtMillis ?: lastReportedAtMillis
        )
    }

    private val criticalFaults = setOf(
        FaultState.BothPumpsFailed,
        FaultState.CriticalBattery,
        FaultState.BatteryFailure,
        FaultState.ConnectionFailure
    )

    private val reconnectableStates = setOf(
        DeviceConnectionState.Disconnected,
        DeviceConnectionState.Reconnecting,
        DeviceConnectionState.AuthRequired,
        DeviceConnectionState.ConnectionFailed,
        DeviceConnectionState.Connecting,
        DeviceConnectionState.DiscoveringServices,
        DeviceConnectionState.Bonding,
        DeviceConnectionState.Scanning
    )

    private val SUPPORTED_PUMP_INTERVAL_MINUTES = setOf(60, 90, 120)
}
