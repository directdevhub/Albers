package com.albers.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Context
import com.albers.app.data.model.AlbersCommand
import com.albers.app.data.model.DeviceConnectionState
import com.albers.app.data.parser.AlbersBleParser
import com.albers.app.data.repository.AlbersRepository
import java.util.Calendar
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object AlbersBleSession : AlbersBleManager.Callback {
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(BleSessionUiState())
    val state: StateFlow<BleSessionUiState> = _state.asStateFlow()

    private var bleManager: AlbersBleManager? = null
    private var selectedDevice: BluetoothDevice? = null
    private var diagnosticsPollJob: Job? = null
    private var awaitingClockSyncWrite = false
    private var hasReachedConnectedReady = false
    private var requiredReadyReads = emptySet<Int>()
    private val receivedReadyReads = mutableSetOf<Int>()

    fun initialize(context: Context) {
        if (bleManager == null) {
            bleManager = AlbersBleManager(context.applicationContext, this)
        }
    }

    fun startConnectionFlow(context: Context) {
        initialize(context)
        selectedDevice = null
        stopDiagnosticsPolling()
        resetInitialReadTracking()
        hasReachedConnectedReady = false
        AlbersRepository.setLoading(true)
        AlbersRepository.updateConnectionState(DeviceConnectionState.Scanning)
        _state.value = BleSessionUiState(
            phase = BleSessionPhase.Scanning,
            statusMessage = "Scanning for ALBERS...",
            selectedDeviceLabel = "Selected device: none",
            canScan = false,
            canConnect = false
        )
        bleManager?.startScan()
    }

    @SuppressLint("MissingPermission")
    fun connectSelectedDevice() {
        val device = selectedDevice
        if (device == null) {
            markConnectionFailure("No ALBERS device is selected. Retry scan and connect again.")
            return
        }

        AlbersRepository.updateConnectionState(DeviceConnectionState.Connecting)
        _state.value = _state.value.copy(
            phase = BleSessionPhase.Connecting,
            statusMessage = "Connecting to ${device.name ?: device.address}...",
            selectedDeviceLabel = "Selected device: ${device.name ?: device.address}",
            canScan = false,
            canConnect = false
        )
        bleManager?.connect(device)
    }

    fun disconnectDevice() {
        stopDiagnosticsPolling()
        bleManager?.disconnect()
    }

    fun release() {
        stopDiagnosticsPolling()
        bleManager?.release()
        bleManager = null
        selectedDevice = null
        resetInitialReadTracking()
        hasReachedConnectedReady = false
        awaitingClockSyncWrite = false
        _state.value = BleSessionUiState()
    }

    fun startAutomaticCycle() {
        val command = currentAutomaticCycleCommand() ?: run {
            AlbersRepository.setError("Unsupported automatic pump interval is selected.")
            return
        }
        writeCommand(command)
    }

    fun pumpNow() {
        val command = currentAutomaticCycleCommand() ?: run {
            AlbersRepository.setError("Unsupported automatic pump interval is selected.")
            return
        }
        writeCommand(command)
    }

    fun updateAutomaticPumpInterval(minutes: Int) {
        AlbersRepository.updateAutomaticPumpInterval(minutes)
        if (AlbersRepository.appState.value.deviceStatus.connectionState == DeviceConnectionState.ConnectedReady) {
            val command = AlbersCommand.automaticCycleForInterval(minutes) ?: return
            writeCommand(command)
        }
    }

    fun startRinseCycle() {
        writeCommand(AlbersCommand.RinseSanitize)
    }

    fun stopPumpOrCycle() {
        // TODO: Firmware documentation does not expose a stop-command characteristic or value yet.
        // Keep the UI honest and tell the user to use device-side emergency stop guidance instead.
        AlbersRepository.setError(
            "A confirmed BLE stop command is not documented for this hardware revision. Use the device-side emergency stop guidance immediately if pumping must stop."
        )
    }

    override fun onScanStateChanged(isScanning: Boolean) {
        if (isScanning) {
            if (selectedDevice != null) return
            _state.value = _state.value.copy(
                phase = BleSessionPhase.Scanning,
                statusMessage = "Scanning for ALBERS...",
                canScan = false,
                canConnect = false
            )
            return
        }

        if (selectedDevice == null) {
            markConnectionFailure(
                "No ALBERS device was found. Confirm battery power, BLE is enabled, ALBERS is selected, authentication is ready, and then retry."
            )
        }
    }

    @SuppressLint("MissingPermission")
    override fun onDeviceFound(device: BluetoothDevice) {
        selectedDevice = device
        val deviceLabel = device.name ?: device.address
        _state.value = _state.value.copy(
            phase = BleSessionPhase.Connecting,
            statusMessage = "ALBERS found. Connecting to $deviceLabel...",
            selectedDeviceLabel = "Selected device: $deviceLabel",
            canScan = false,
            canConnect = false
        )
        connectSelectedDevice()
    }

    override fun onConnectionStateChanged(state: BleConnectionState) {
        when (state) {
            BleConnectionState.Pairing -> {
                AlbersRepository.updateConnectionState(DeviceConnectionState.Bonding)
                _state.value = _state.value.copy(
                    phase = BleSessionPhase.Bonding,
                    statusMessage = "Pairing with ALBERS. Use PIN 333333 if Android asks.",
                    canScan = false,
                    canConnect = false
                )
            }

            BleConnectionState.Connecting -> {
                AlbersRepository.updateConnectionState(DeviceConnectionState.Connecting)
                _state.value = _state.value.copy(
                    phase = BleSessionPhase.Connecting,
                    statusMessage = "Connecting...",
                    canScan = false,
                    canConnect = false
                )
            }

            BleConnectionState.Connected -> {
                AlbersRepository.updateConnectionState(DeviceConnectionState.DiscoveringServices)
                _state.value = _state.value.copy(
                    phase = BleSessionPhase.DiscoveringServices,
                    statusMessage = "Connected. Discovering services...",
                    canScan = false,
                    canConnect = false
                )
            }

            BleConnectionState.Reconnecting -> {
                stopDiagnosticsPolling()
                resetInitialReadTracking()
                hasReachedConnectedReady = false
                AlbersRepository.updateConnectionState(DeviceConnectionState.Reconnecting)
                _state.value = _state.value.copy(
                    phase = BleSessionPhase.Reconnecting,
                    statusMessage = "Connection lost. Reconnecting to ALBERS...",
                    canScan = false,
                    canConnect = false,
                    shouldOpenDashboard = false
                )
            }

            BleConnectionState.Disconnecting -> {
                stopDiagnosticsPolling()
                _state.value = _state.value.copy(
                    phase = BleSessionPhase.Disconnecting,
                    statusMessage = "Disconnecting...",
                    canScan = false,
                    canConnect = false,
                    shouldOpenDashboard = false
                )
            }

            BleConnectionState.Disconnected -> {
                stopDiagnosticsPolling()
                awaitingClockSyncWrite = false
                resetInitialReadTracking()
                hasReachedConnectedReady = false
                AlbersRepository.updateConnectionState(DeviceConnectionState.Disconnected)
                _state.value = _state.value.copy(
                    phase = BleSessionPhase.Disconnected,
                    statusMessage = "Disconnected.",
                    canScan = true,
                    canConnect = selectedDevice != null,
                    shouldOpenDashboard = false
                )
            }
        }
    }

    override fun onServicesDiscovered(serviceUuids: List<UUID>) {
        AlbersRepository.updateConnectionState(DeviceConnectionState.DiscoveringServices)
        _state.value = _state.value.copy(
            phase = BleSessionPhase.DiscoveringServices,
            statusMessage = "Connected. Preparing secure diagnostics...",
            canScan = false,
            canConnect = false
        )
        beginInitialDiagnosticsSequence()
    }

    override fun onCharacteristicRead(characteristicUuid: UUID, value: ByteArray) {
        val shortUuid = with(AlbersGattProfile) { characteristicUuid.shortUuidOrNull() }
        when (shortUuid) {
            AlbersGattProfile.ADC_SYS_SHORT_UUID -> AlbersRepository.updateFromAdcSys(value)
            AlbersGattProfile.TIMER_SYS_SHORT_UUID -> AlbersRepository.updateFromTimerSys(value)
            AlbersGattProfile.SAVED_SHORT_UUID -> AlbersRepository.updateFromSavedDiagnostics(value)
            else -> parseByPayloadSize(characteristicUuid, value)
        }

        if (shortUuid != null && shortUuid in requiredReadyReads) {
            receivedReadyReads += shortUuid
        }
        if (!hasReachedConnectedReady && requiredReadyReads.all { it in receivedReadyReads }) {
            markConnectedReady()
        }
    }

    override fun onCharacteristicWrite(characteristicUuid: UUID, success: Boolean, status: Int) {
        val shortUuid = with(AlbersGattProfile) { characteristicUuid.shortUuidOrNull() }
        if (!success) {
            if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) {
                // TODO: The hardware docs mention a test password (333333) but do not document
                // a dedicated password characteristic UUID. For now we rely on bond/passkey flow
                // and surface AuthRequired when Android/GATT reports insufficient authentication.
                markAuthRequired(
                    "ALBERS authentication is required. Pair with PIN 333333, then reconnect and retry the protected reads."
                )
                return
            }

            if (shortUuid == AlbersGattProfile.FECHA_SYS_SHORT_UUID) {
                awaitingClockSyncWrite = false
                beginQueuedDiagnosticsReads()
            }

            AlbersRepository.setError("Unable to write ALBERS characteristic $characteristicUuid (status $status)")
            return
        }

        when (shortUuid) {
            AlbersGattProfile.FECHA_SYS_SHORT_UUID -> {
                awaitingClockSyncWrite = false
                beginQueuedDiagnosticsReads()
            }

            AlbersGattProfile.COMMAND_SHORT_UUID -> {
                AlbersRepository.setLoading(false)
                refreshDiagnosticsSoon()
            }
        }
    }

    override fun onError(message: String, cause: Throwable?) {
        when {
            message.contains("status 5", ignoreCase = true) ||
                message.contains("auth", ignoreCase = true) ||
                message.contains("pair", ignoreCase = true) ||
                message.contains("PIN", ignoreCase = true) -> {
                    markAuthRequired(message)
                }

            else -> markConnectionFailure(message)
        }
    }

    fun consumeDashboardNavigation() {
        _state.value = _state.value.copy(shouldOpenDashboard = false)
    }

    private fun beginInitialDiagnosticsSequence() {
        val manager = bleManager ?: return
        resetInitialReadTracking()
        requiredReadyReads = buildSet {
            if (manager.hasCharacteristic(AlbersGattProfile.ADC_SYS_SHORT_UUID)) {
                add(AlbersGattProfile.ADC_SYS_SHORT_UUID)
            }
            if (manager.hasCharacteristic(AlbersGattProfile.TIMER_SYS_SHORT_UUID)) {
                add(AlbersGattProfile.TIMER_SYS_SHORT_UUID)
            }
        }

        if (requiredReadyReads.isEmpty()) {
            markConnectionFailure("ALBERS diagnostics characteristics are missing from the discovered GATT services.")
            return
        }

        if (manager.hasCharacteristic(AlbersGattProfile.FECHA_SYS_SHORT_UUID)) {
            awaitingClockSyncWrite = true
            _state.value = _state.value.copy(
                phase = BleSessionPhase.DiscoveringServices,
                statusMessage = "Connected. Syncing ALBERS date and time before diagnostics..."
            )
            manager.writeCharacteristic(AlbersGattProfile.FECHA_SYS_SHORT_UUID, buildFechaPayload())
        } else {
            beginQueuedDiagnosticsReads()
        }
    }

    private fun beginQueuedDiagnosticsReads() {
        _state.value = _state.value.copy(
            phase = BleSessionPhase.ReadingDiagnostics,
            statusMessage = "Connected. Reading ALBERS diagnostics...",
            canScan = false,
            canConnect = false
        )
        bleManager?.queueInitialReads()
    }

    private fun markConnectedReady() {
        hasReachedConnectedReady = true
        AlbersRepository.updateConnectionState(DeviceConnectionState.ConnectedReady)
        AlbersRepository.setLoading(false)
        _state.value = _state.value.copy(
            phase = BleSessionPhase.ConnectedReady,
            statusMessage = "Connected and ready.",
            canScan = false,
            canConnect = false,
            shouldOpenDashboard = true
        )
        startDiagnosticsPolling()
    }

    private fun markAuthRequired(message: String) {
        stopDiagnosticsPolling()
        awaitingClockSyncWrite = false
        AlbersRepository.updateConnectionState(DeviceConnectionState.AuthRequired)
        AlbersRepository.setError(message)
        _state.value = _state.value.copy(
            phase = BleSessionPhase.AuthRequired,
            statusMessage = message,
            canScan = true,
            canConnect = selectedDevice != null,
            shouldOpenDashboard = false
        )
    }

    private fun markConnectionFailure(message: String) {
        stopDiagnosticsPolling()
        awaitingClockSyncWrite = false
        AlbersRepository.updateConnectionState(DeviceConnectionState.ConnectionFailed)
        AlbersRepository.setError(message)
        _state.value = _state.value.copy(
            phase = BleSessionPhase.ConnectionFailed,
            statusMessage = message,
            canScan = true,
            canConnect = selectedDevice != null,
            shouldOpenDashboard = false
        )
    }

    private fun startDiagnosticsPolling() {
        stopDiagnosticsPolling()
        diagnosticsPollJob = sessionScope.launch {
            while (isActive) {
                delay(DIAGNOSTIC_POLL_INTERVAL_MS)
                if (AlbersRepository.appState.value.deviceStatus.connectionState != DeviceConnectionState.ConnectedReady) {
                    continue
                }
                bleManager?.queueCharacteristicReads(
                    listOf(
                        AlbersGattProfile.ADC_SYS_SHORT_UUID,
                        AlbersGattProfile.TIMER_SYS_SHORT_UUID
                    )
                )
            }
        }
    }

    private fun stopDiagnosticsPolling() {
        diagnosticsPollJob?.cancel()
        diagnosticsPollJob = null
    }

    private fun refreshDiagnosticsSoon() {
        sessionScope.launch {
            delay(REFRESH_AFTER_COMMAND_DELAY_MS)
            bleManager?.queueCharacteristicReads(
                listOf(
                    AlbersGattProfile.ADC_SYS_SHORT_UUID,
                    AlbersGattProfile.TIMER_SYS_SHORT_UUID
                )
            )
        }
    }

    private fun resetInitialReadTracking() {
        requiredReadyReads = emptySet()
        receivedReadyReads.clear()
    }

    private fun currentAutomaticCycleCommand(): AlbersCommand? {
        return AlbersCommand.automaticCycleForInterval(
            AlbersRepository.appState.value.automaticPumpIntervalMinutes
        )
    }

    private fun writeCommand(command: AlbersCommand) {
        val manager = bleManager
        if (manager == null) {
            AlbersRepository.setError("BLE session is not ready. Connect to ALBERS first.")
            return
        }
        if (AlbersRepository.appState.value.deviceStatus.connectionState != DeviceConnectionState.ConnectedReady) {
            AlbersRepository.setError("ALBERS is not connected and ready for commands yet.")
            return
        }

        AlbersRepository.setLoading(true)
        manager.writeCharacteristic(AlbersGattProfile.COMMAND_SHORT_UUID, byteArrayOf(command.payload))
    }

    private fun parseByPayloadSize(characteristicUuid: UUID, value: ByteArray) {
        when {
            value.size == 4 -> AlbersRepository.updateFromTimerSys(value)
            value.size == AlbersBleParser.ADC_SYS_ENTRY_SIZE_BYTES -> AlbersRepository.updateFromAdcSys(value)
            value.size > AlbersBleParser.ADC_SYS_ENTRY_SIZE_BYTES -> AlbersRepository.updateFromSavedDiagnostics(value)
            else -> AlbersRepository.setError("Unsupported BLE payload from $characteristicUuid (${value.size} bytes)")
        }
    }

    private fun buildFechaPayload(): ByteArray {
        val now = Calendar.getInstance()
        return byteArrayOf(
            (now.get(Calendar.YEAR) and 0xFF).toByte(),
            ((now.get(Calendar.YEAR) shr 8) and 0xFF).toByte(),
            ((now.get(Calendar.MONTH) + 1) and 0xFF).toByte(),
            (((now.get(Calendar.MONTH) + 1) shr 8) and 0xFF).toByte(),
            (now.get(Calendar.DAY_OF_MONTH) and 0xFF).toByte(),
            ((now.get(Calendar.DAY_OF_MONTH) shr 8) and 0xFF).toByte(),
            (now.get(Calendar.HOUR_OF_DAY) and 0xFF).toByte(),
            ((now.get(Calendar.HOUR_OF_DAY) shr 8) and 0xFF).toByte(),
            (now.get(Calendar.MINUTE) and 0xFF).toByte(),
            ((now.get(Calendar.MINUTE) shr 8) and 0xFF).toByte(),
            (now.get(Calendar.SECOND) and 0xFF).toByte(),
            ((now.get(Calendar.SECOND) shr 8) and 0xFF).toByte()
        )
    }

    private const val DIAGNOSTIC_POLL_INTERVAL_MS = 5_000L
    private const val REFRESH_AFTER_COMMAND_DELAY_MS = 1_500L
}

data class BleSessionUiState(
    val phase: BleSessionPhase = BleSessionPhase.Idle,
    val statusMessage: String = "Turn on Albers_BLE_BAL3, pair with PIN 333333 if needed, then find the device here.",
    val selectedDeviceLabel: String = "Selected device: none",
    val canScan: Boolean = true,
    val canConnect: Boolean = false,
    val shouldOpenDashboard: Boolean = false
)

enum class BleSessionPhase {
    Idle,
    Scanning,
    Connecting,
    Bonding,
    DiscoveringServices,
    ReadingDiagnostics,
    AuthRequired,
    ConnectedReady,
    Reconnecting,
    Disconnecting,
    ConnectionFailed,
    Disconnected
}
