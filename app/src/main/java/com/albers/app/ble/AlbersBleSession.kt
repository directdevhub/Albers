package com.albers.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.albers.app.data.model.DeviceConnectionState
import com.albers.app.data.parser.AlbersBleParser
import com.albers.app.data.repository.AlbersRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object AlbersBleSession : AlbersBleManager.Callback {
    private val _state = MutableStateFlow(BleSessionUiState())
    val state: StateFlow<BleSessionUiState> = _state.asStateFlow()

    private var bleManager: AlbersBleManager? = null
    private var selectedDevice: BluetoothDevice? = null
    private var hasReceivedInitialDiagnostic = false

    fun initialize(context: Context) {
        if (bleManager == null) {
            bleManager = AlbersBleManager(context.applicationContext, this)
        }
    }

    fun startConnectionFlow(context: Context) {
        initialize(context)
        selectedDevice = null
        hasReceivedInitialDiagnostic = false
        AlbersRepository.setLoading(true)
        _state.value = BleSessionUiState(
            phase = BleSessionPhase.Scanning,
            statusMessage = "Scanning for ALBERS...",
            selectedDeviceLabel = "Selected device: none",
            canScan = false
        )
        bleManager?.startScan()
    }

    @SuppressLint("MissingPermission")
    fun connectSelectedDevice() {
        val device = selectedDevice
        if (device == null) {
            _state.value = _state.value.copy(
                phase = BleSessionPhase.Failed,
                statusMessage = "Scan and select an ALBERS device first.",
                canScan = true,
                canConnect = false
            )
            return
        }

        AlbersRepository.updateConnectionState(DeviceConnectionState.Connecting)
        _state.value = _state.value.copy(
            phase = BleSessionPhase.Connecting,
            statusMessage = "Connecting to ${device.name ?: device.address}...",
            canScan = false,
            canConnect = false
        )
        bleManager?.connect(device)
    }

    fun disconnectDevice() {
        bleManager?.disconnect()
    }

    fun release() {
        bleManager?.release()
        bleManager = null
        selectedDevice = null
        hasReceivedInitialDiagnostic = false
        _state.value = BleSessionUiState()
    }

    fun pumpNow() {
        writeCommand(COMMAND_PUMP_CASE_1)
        AlbersRepository.setLoading(true)
    }

    fun startRinseCycle() {
        writeCommand(COMMAND_CLEANING_CYCLE)
        AlbersRepository.setLoading(true)
    }

    fun stopPumpOrCycle() {
        AlbersRepository.setError(
            "Stop command is not confirmed by firmware yet. Use Emergency STOP guidance on the device if pumping must stop immediately."
        )
    }

    private fun writeCommand(command: Byte) {
        val manager = bleManager
        if (manager == null) {
            AlbersRepository.setError("BLE session is not ready. Connect to ALBERS first.")
            return
        }
        manager.writeCharacteristic(AlbersGattProfile.COMMAND_SHORT_UUID, byteArrayOf(command))
    }

    override fun onScanStateChanged(isScanning: Boolean) {
        if (isScanning) {
            if (selectedDevice != null) return
            _state.value = _state.value.copy(
                phase = BleSessionPhase.Scanning,
                statusMessage = "Scanning for ALBERS...",
                canScan = false
            )
        } else if (selectedDevice == null) {
            _state.value = _state.value.copy(
                phase = BleSessionPhase.Failed,
                statusMessage = "No ALBERS device found. Confirm battery power, pairing mode, and phone Bluetooth/Location, then retry.",
                canScan = true,
                canConnect = false
            )
            AlbersRepository.setLoading(false)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onDeviceFound(device: BluetoothDevice) {
        selectedDevice = device
        val deviceLabel = device.name ?: device.address
        _state.value = _state.value.copy(
            phase = BleSessionPhase.Idle,
            statusMessage = "ALBERS device found. Tap Connect.",
            selectedDeviceLabel = "Selected device: $deviceLabel",
            canScan = true,
            canConnect = true
        )
        AlbersRepository.setLoading(false)
    }

    override fun onConnectionStateChanged(state: BleConnectionState) {
        when (state) {
            BleConnectionState.Pairing -> {
                AlbersRepository.updateConnectionState(DeviceConnectionState.Connecting)
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
                AlbersRepository.updateConnectionState(DeviceConnectionState.Connected)
                _state.value = _state.value.copy(
                    phase = BleSessionPhase.DiscoveringServices,
                    statusMessage = "Connected. Discovering services...",
                    canScan = false,
                    canConnect = false
                )
            }

            BleConnectionState.Reconnecting -> {
                AlbersRepository.updateConnectionState(DeviceConnectionState.Reconnecting)
                _state.value = _state.value.copy(
                    phase = BleSessionPhase.Reconnecting,
                    statusMessage = "Connection lost. Reconnecting...",
                    canScan = false,
                    canConnect = false
                )
            }

            BleConnectionState.Disconnecting -> {
                AlbersRepository.updateConnectionState(DeviceConnectionState.Reconnecting)
                _state.value = _state.value.copy(
                    phase = BleSessionPhase.Disconnecting,
                    statusMessage = "Disconnecting...",
                    canScan = false,
                    canConnect = false
                )
            }

            BleConnectionState.Disconnected -> {
                AlbersRepository.updateConnectionState(DeviceConnectionState.Disconnected)
                _state.value = _state.value.copy(
                    phase = BleSessionPhase.Idle,
                    statusMessage = "Disconnected.",
                    canScan = true,
                    canConnect = selectedDevice != null,
                    shouldOpenDashboard = false
                )
            }
        }
    }

    override fun onServicesDiscovered(serviceUuids: List<UUID>) {
        _state.value = _state.value.copy(
            phase = BleSessionPhase.ReadingDiagnostics,
            statusMessage = "Connected. Reading ALBERS diagnostics...",
            canScan = false,
            canConnect = false
        )
    }

    override fun onCharacteristicRead(characteristicUuid: UUID, value: ByteArray) {
        val shortUuid = with(AlbersGattProfile) { characteristicUuid.shortUuidOrNull() }
        when (shortUuid) {
            AlbersGattProfile.ADC_SYS_SHORT_UUID -> AlbersRepository.updateFromAdcSys(value)
            AlbersGattProfile.TIMER_SYS_SHORT_UUID -> AlbersRepository.updateFromTimerSys(value)
            AlbersGattProfile.SAVED_SHORT_UUID -> AlbersRepository.updateFromSavedDiagnostics(value)
            else -> parseByPayloadSize(characteristicUuid, value)
        }

        if (!hasReceivedInitialDiagnostic) {
            hasReceivedInitialDiagnostic = true
            _state.value = _state.value.copy(
                phase = BleSessionPhase.Ready,
                statusMessage = "Connected and ready.",
                canScan = false,
                canConnect = false,
                shouldOpenDashboard = true
            )
        }
    }

    override fun onCharacteristicWrite(characteristicUuid: UUID, success: Boolean) {
        if (!success) {
            AlbersRepository.setError("Unable to write ALBERS command to $characteristicUuid")
            return
        }
        AlbersRepository.setLoading(false)
    }

    override fun onError(message: String, cause: Throwable?) {
        AlbersRepository.setError(message)
        _state.value = _state.value.copy(
            phase = if (message.contains("auth", ignoreCase = true) ||
                message.contains("pair", ignoreCase = true) ||
                message.contains("PIN", ignoreCase = true)
            ) {
                BleSessionPhase.AuthRequired
            } else {
                BleSessionPhase.Failed
            },
            statusMessage = message,
            canScan = true,
            canConnect = selectedDevice != null,
            shouldOpenDashboard = false
        )
    }

    fun consumeDashboardNavigation() {
        _state.value = _state.value.copy(shouldOpenDashboard = false)
    }

    private fun parseByPayloadSize(characteristicUuid: UUID, value: ByteArray) {
        when {
            value.size == 4 -> AlbersRepository.updateFromTimerSys(value)
            value.size == AlbersBleParser.ADC_SYS_ENTRY_SIZE_BYTES -> AlbersRepository.updateFromAdcSys(value)
            value.size > AlbersBleParser.ADC_SYS_ENTRY_SIZE_BYTES -> AlbersRepository.updateFromSavedDiagnostics(value)
            else -> AlbersRepository.setError("Unsupported BLE payload from $characteristicUuid (${value.size} bytes)")
        }
    }

    private const val COMMAND_CLEANING_CYCLE: Byte = 0x00
    private const val COMMAND_PUMP_CASE_1: Byte = 0x01
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
    Ready,
    Reconnecting,
    Disconnecting,
    Failed
}
