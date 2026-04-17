package com.albers.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID

class AlbersBleManager(
    private val context: Context,
    private val callback: Callback
) {
    private val bluetoothManager =
        context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner get() = bluetoothAdapter?.bluetoothLeScanner
    private val mainHandler = Handler(Looper.getMainLooper())
    private val discoveredDeviceAddresses = mutableSetOf<String>()
    private val gattCallback = GattCallback(
        onConnectionStateChanged = ::handleConnectionStateChanged,
        onServicesDiscovered = ::handleServicesDiscovered,
        onCharacteristicRead = ::handleCharacteristicRead,
        onCharacteristicWrite = callback::onCharacteristicWrite,
        onError = ::handleGattError
    )

    private var bluetoothGatt: BluetoothGatt? = null
    private var isScanning = false
    private var currentDevice: BluetoothDevice? = null
    private var pendingBondDevice: BluetoothDevice? = null
    private var userRequestedDisconnect = false
    private var reconnectAttempts = 0
    private var suppressNextReconnect = false
    private var bondReceiverRegistered = false
    private val pendingReadShortUuids = ArrayDeque<Int>()
    private val readRetryCounts = mutableMapOf<Int, Int>()
    private var readInProgress = false
    private var currentReadShortUuid: Int? = null

    private val bondStateReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return

            val device = intent.bluetoothDeviceExtra() ?: return
            val pendingDevice = pendingBondDevice ?: return
            if (device.address != pendingDevice.address) return

            val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
            val previousBondState =
                intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
            Log.d(TAG, "Bond state changed: ${device.address} $previousBondState -> $bondState")

            when (bondState) {
                BluetoothDevice.BOND_BONDED -> {
                    unregisterBondReceiver()
                    pendingBondDevice = null
                    callback.onConnectionStateChanged(BleConnectionState.Connecting)
                    bluetoothGatt = openGatt(device)
                }

                BluetoothDevice.BOND_NONE -> {
                    unregisterBondReceiver()
                    pendingBondDevice = null
                    callback.onConnectionStateChanged(BleConnectionState.Disconnected)
                    callback.onError(
                        "Bluetooth pairing failed. Pair Albers_BLE_BAL3 in Android Bluetooth settings using PIN 333333, then retry."
                    )
                }

                BluetoothDevice.BOND_BONDING -> callback.onConnectionStateChanged(BleConnectionState.Pairing)
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = result.scanRecord?.deviceName ?: device.name
            val serviceUuids = result.scanRecord?.serviceUuids.orEmpty()
            val isNameMatch = isAlbersDeviceName(deviceName)
            val isServiceMatch = serviceUuids.any { parcelUuid ->
                parcelUuid.uuid.matchesShortUuid(AlbersGattProfile.SERVICE_SHORT_UUID)
            }
            val isAddressHintMatch = ALBERS_DEVICE_ADDRESS_HINTS.any { hint ->
                device.address.startsWith(hint, ignoreCase = true)
            }

            Log.d(
                TAG,
                "Scan result: name=$deviceName address=${device.address} rssi=${result.rssi} " +
                    "services=${serviceUuids.joinToString { it.uuid.toString() }} " +
                    "nameMatch=$isNameMatch serviceMatch=$isServiceMatch addressHint=$isAddressHintMatch"
            )

            if (!isNameMatch && !isServiceMatch && !isAddressHintMatch) {
                return
            }

            if (discoveredDeviceAddresses.add(device.address)) {
                Log.d(TAG, "ALBERS candidate found: name=$deviceName address=${device.address}")
                callback.onDeviceFound(device)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            Log.e(TAG, "BLE scan failed: $errorCode")
            callback.onScanStateChanged(isScanning = false)
            callback.onError("BLE scan failed with error code $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (isScanning) {
            Log.d(TAG, "Scan already running")
            return
        }

        if (bluetoothAdapter?.isEnabled != true) {
            callback.onError("Bluetooth is unavailable or disabled")
            return
        }

        discoveredDeviceAddresses.clear()
        val bondedMatches = emitBondedAlbersDevices()

        val scanner = bluetoothLeScanner
        if (scanner == null) {
            if (bondedMatches == 0) {
                callback.onError("BLE scanner is unavailable. Confirm Bluetooth and Location are enabled.")
            }
            return
        }

        isScanning = true
        Log.d(TAG, "Starting broad BLE scan for $TARGET_DEVICE_NAME")
        callback.onScanStateChanged(isScanning = true)

        scanner.startScan(null, buildScanSettings(), scanCallback)
        mainHandler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) {
            return
        }

        Log.d(TAG, "Stopping BLE scan")
        bluetoothLeScanner?.stopScan(scanCallback)
        mainHandler.removeCallbacks(scanTimeoutRunnable)
        isScanning = false
        callback.onScanStateChanged(isScanning = false)
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        stopScan()
        closeGatt()

        currentDevice = device
        userRequestedDisconnect = false
        reconnectAttempts = 0
        suppressNextReconnect = false
        Log.d(TAG, "Connecting to device: address=${device.address}")
        connectWhenBonded(device)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        Log.d(TAG, "Disconnect requested")
        userRequestedDisconnect = true
        reconnectAttempts = 0
        pendingBondDevice = null
        unregisterBondReceiver()
        callback.onConnectionStateChanged(BleConnectionState.Disconnecting)
        bluetoothGatt?.disconnect()
        closeGatt()
        callback.onConnectionStateChanged(BleConnectionState.Disconnected)
    }

    @SuppressLint("MissingPermission")
    fun readCharacteristic(serviceUuid: UUID, characteristicUuid: UUID) {
        val characteristic = bluetoothGatt
            ?.getService(serviceUuid)
            ?.getCharacteristic(characteristicUuid)

        if (characteristic == null) {
            callback.onError("Characteristic not found: $characteristicUuid")
            return
        }

        Log.d(TAG, "Reading characteristic: $characteristicUuid")
        val started = bluetoothGatt?.readCharacteristic(characteristic) == true
        if (!started) {
            callback.onError("Unable to start characteristic read: $characteristicUuid")
        }
    }

    @SuppressLint("MissingPermission")
    fun readCharacteristic(shortUuid: Int) {
        val gatt = bluetoothGatt
        val characteristic = gatt?.findCharacteristic(shortUuid)

        if (gatt == null || characteristic == null) {
            callback.onError("Characteristic not found: 0x${shortUuid.toHexShort()}")
            return
        }

        readCharacteristic(gatt, characteristic)
    }

    @SuppressLint("MissingPermission", "ObsoleteSdkInt", "DEPRECATION")
    fun writeCharacteristic(serviceUuid: UUID, characteristicUuid: UUID, payload: ByteArray) {
        val gatt = bluetoothGatt
        val characteristic = gatt
            ?.getService(serviceUuid)
            ?.getCharacteristic(characteristicUuid)

        if (gatt == null || characteristic == null) {
            callback.onError("Characteristic not found: $characteristicUuid")
            return
        }

        Log.d(TAG, "Writing ${payload.size} bytes to characteristic: $characteristicUuid")
        val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                characteristic,
                payload,
                BluetoothGattCharacteristicWriteType.DEFAULT
            )
        } else {
            characteristic.writeType = BluetoothGattCharacteristicWriteType.DEFAULT
            characteristic.value = payload
            if (gatt.writeCharacteristic(characteristic)) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE
        }

        if (status != BluetoothGatt.GATT_SUCCESS) {
            callback.onError("Unable to start characteristic write: $characteristicUuid")
        }
    }

    @SuppressLint("MissingPermission", "ObsoleteSdkInt", "DEPRECATION")
    fun writeCharacteristic(shortUuid: Int, payload: ByteArray) {
        val gatt = bluetoothGatt
        val characteristic = gatt?.findCharacteristic(shortUuid)

        if (gatt == null || characteristic == null) {
            callback.onError("Characteristic not found: 0x${shortUuid.toHexShort()}")
            return
        }

        writeCharacteristic(gatt, characteristic, payload)
    }

    fun release() {
        userRequestedDisconnect = true
        stopScan()
        disconnect()
    }

    @SuppressLint("MissingPermission")
    private fun openGatt(device: BluetoothDevice): BluetoothGatt? {
        callback.onConnectionStateChanged(BleConnectionState.Connecting)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context.applicationContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context.applicationContext, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectWhenBonded(device: BluetoothDevice) {
        when (device.bondState) {
            BluetoothDevice.BOND_BONDED -> {
                Log.d(TAG, "Device already bonded. Opening GATT.")
                bluetoothGatt = openGatt(device)
            }

            BluetoothDevice.BOND_BONDING -> {
                Log.d(TAG, "Device is already bonding. Waiting for bond completion.")
                pendingBondDevice = device
                registerBondReceiver()
                callback.onConnectionStateChanged(BleConnectionState.Pairing)
            }

            else -> {
                Log.d(TAG, "Device is not bonded. Starting bond before GATT.")
                pendingBondDevice = device
                registerBondReceiver()
                callback.onConnectionStateChanged(BleConnectionState.Pairing)
                val started = device.createBond()
                if (!started) {
                    unregisterBondReceiver()
                    pendingBondDevice = null
                    callback.onError(
                        "Unable to start Bluetooth pairing. Pair Albers_BLE_BAL3 in Android Bluetooth settings using PIN 333333, then retry."
                    )
                }
            }
        }
    }

    private fun closeGatt() {
        pendingReadShortUuids.clear()
        readRetryCounts.clear()
        readInProgress = false
        currentReadShortUuid = null
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    private fun registerBondReceiver() {
        if (bondReceiverRegistered) return
        ContextCompat.registerReceiver(
            context.applicationContext,
            bondStateReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        bondReceiverRegistered = true
    }

    private fun unregisterBondReceiver() {
        if (!bondReceiverRegistered) return
        runCatching {
            context.applicationContext.unregisterReceiver(bondStateReceiver)
        }.onFailure { error ->
            Log.w(TAG, "Unable to unregister bond receiver", error)
        }
        bondReceiverRegistered = false
    }

    @SuppressLint("MissingPermission")
    private fun emitBondedAlbersDevices(): Int {
        val bondedDevices = bluetoothAdapter?.bondedDevices.orEmpty()
        Log.d(TAG, "Checking bonded devices: count=${bondedDevices.size}")

        var matchCount = 0
        bondedDevices
            .filter { device -> isAlbersDeviceName(device.name) }
            .forEach { device ->
                if (discoveredDeviceAddresses.add(device.address)) {
                    matchCount++
                    Log.d(TAG, "Bonded ALBERS device found: name=${device.name} address=${device.address}")
                    callback.onDeviceFound(device)
                }
            }
        return matchCount
    }

    private fun isAlbersDeviceName(deviceName: String?): Boolean {
        return deviceName != null && ALBERS_DEVICE_NAME_HINTS.any { hint ->
            deviceName.contains(hint, ignoreCase = true)
        }
    }

    private fun buildScanSettings(): ScanSettings {
        return ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun handleConnectionStateChanged(gatt: BluetoothGatt, state: BleConnectionState) {
        Log.d(TAG, "Connection state changed: $state")
        bluetoothGatt = gatt
        when (state) {
            BleConnectionState.Connected -> {
                reconnectAttempts = 0
                callback.onConnectionStateChanged(state)
            }

            BleConnectionState.Disconnected -> {
                closeGatt()
                if (suppressNextReconnect) {
                    suppressNextReconnect = false
                    callback.onConnectionStateChanged(state)
                } else if (!userRequestedDisconnect && currentDevice != null && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    reconnectAttempts += 1
                    callback.onConnectionStateChanged(BleConnectionState.Reconnecting)
                    mainHandler.postDelayed({
                        val device = currentDevice ?: return@postDelayed
                        Log.d(TAG, "Reconnect attempt $reconnectAttempts to ${device.address}")
                        callback.onConnectionStateChanged(BleConnectionState.Connecting)
                        bluetoothGatt = openGatt(device)
                    }, RECONNECT_DELAY_MS)
                } else {
                    callback.onConnectionStateChanged(state)
                }
            }

            else -> callback.onConnectionStateChanged(state)
        }
    }

    private fun handleGattError(message: String, cause: Throwable?) {
        if (readInProgress && message.contains("Characteristic read failed", ignoreCase = true)) {
            val failedShortUuid = currentReadShortUuid
            readInProgress = false
            currentReadShortUuid = null
            if (failedShortUuid != null && shouldRetryRead(message, failedShortUuid)) {
                scheduleReadRetry(failedShortUuid)
                return
            }
            if (message.contains("status 133", ignoreCase = true)) {
                callback.onError(
                    "ALBERS connected, but secure data read failed. This usually means the Bluetooth bond is stale. Forget/unpair Albers_BLE_BAL3 in Android Bluetooth settings, pair again with PIN 333333, then retry in the app.",
                    cause
                )
                return
            }
        }

        if (message.contains("status 5", ignoreCase = true)) {
            if (message.contains("GATT connection failed", ignoreCase = true)) {
                suppressNextReconnect = true
            }
            callback.onError(
                "Bluetooth authentication failed. Forget/unpair Albers_BLE_BAL3 in Android Bluetooth settings, pair again using PIN 333333, then retry in the app.",
                cause
            )
            return
        }

        callback.onError(message, cause)
    }

    private fun handleServicesDiscovered(gatt: BluetoothGatt, serviceUuids: List<UUID>) {
        Log.d(TAG, "Services discovered: ${serviceUuids.joinToString()}")
        bluetoothGatt = gatt
        logAlbersCharacteristics(gatt)
        callback.onServicesDiscovered(serviceUuids)
        queueInitialReads()
    }

    private fun handleCharacteristicRead(characteristicUuid: UUID, value: ByteArray) {
        readInProgress = false
        currentReadShortUuid = null
        callback.onCharacteristicRead(characteristicUuid, value)
        readNextQueuedCharacteristic()
    }

    private fun queueInitialReads() {
        pendingReadShortUuids.clear()
        readRetryCounts.clear()
        pendingReadShortUuids.addAll(AlbersGattProfile.readableShortUuids)
        readInProgress = false
        currentReadShortUuid = null
        mainHandler.postDelayed(::readNextQueuedCharacteristic, POST_DISCOVERY_READ_DELAY_MS)
    }

    @SuppressLint("MissingPermission")
    private fun readNextQueuedCharacteristic() {
        if (readInProgress) return
        val gatt = bluetoothGatt ?: return

        while (pendingReadShortUuids.isNotEmpty()) {
            val shortUuid = pendingReadShortUuids.removeFirst()
            val characteristic = gatt.findCharacteristic(shortUuid)
            if (characteristic == null) {
                Log.w(TAG, "Initial read skipped. Characteristic not found: 0x${shortUuid.toHexShort()}")
                continue
            }

            readCharacteristic(gatt, characteristic)
            return
        }
    }

    @SuppressLint("MissingPermission")
    private fun readCharacteristic(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        val shortUuid = characteristic.uuid.shortUuidOrNull()
        currentReadShortUuid = shortUuid
        Log.d(
            TAG,
            "Reading characteristic: ${characteristic.uuid} shortUuid=${shortUuid?.toHexShort()} bondState=${gatt.device.bondState}"
        )
        readInProgress = true
        val started = gatt.readCharacteristic(characteristic)
        if (!started) {
            readInProgress = false
            currentReadShortUuid = null
            callback.onError("Unable to start characteristic read: ${characteristic.uuid}")
            readNextQueuedCharacteristic()
        }
    }

    private fun shouldRetryRead(message: String, shortUuid: Int): Boolean {
        val status133 = message.contains("status 133", ignoreCase = true)
        val attempts = readRetryCounts[shortUuid] ?: 0
        return status133 && attempts < MAX_READ_RETRY_ATTEMPTS
    }

    private fun scheduleReadRetry(shortUuid: Int) {
        val nextAttempt = (readRetryCounts[shortUuid] ?: 0) + 1
        readRetryCounts[shortUuid] = nextAttempt
        Log.w(
            TAG,
            "Protected read failed for 0x${shortUuid.toHexShort()} with GATT 133. Retrying $nextAttempt/$MAX_READ_RETRY_ATTEMPTS"
        )
        mainHandler.postDelayed({
            pendingReadShortUuids.addFirst(shortUuid)
            readNextQueuedCharacteristic()
        }, READ_RETRY_DELAY_MS)
    }

    @SuppressLint("MissingPermission", "ObsoleteSdkInt", "DEPRECATION")
    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray
    ) {
        Log.d(TAG, "Writing ${payload.size} bytes to characteristic: ${characteristic.uuid}")
        val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                characteristic,
                payload,
                BluetoothGattCharacteristicWriteType.DEFAULT
            )
        } else {
            characteristic.writeType = BluetoothGattCharacteristicWriteType.DEFAULT
            characteristic.value = payload
            if (gatt.writeCharacteristic(characteristic)) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE
        }

        if (status != BluetoothGatt.GATT_SUCCESS) {
            callback.onError("Unable to start characteristic write: ${characteristic.uuid}")
        }
    }

    private fun BluetoothGatt.findCharacteristic(shortUuid: Int): BluetoothGattCharacteristic? {
        return services
            .asSequence()
            .flatMap { service -> service.characteristics.asSequence() }
            .firstOrNull { characteristic -> characteristic.uuid.matchesShortUuid(shortUuid) }
    }

    private fun logAlbersCharacteristics(gatt: BluetoothGatt) {
        gatt.services.forEach { service ->
            val serviceShortUuid = service.uuid.shortUuidOrNull()
            val serviceLabel = serviceShortUuid?.let { "0x${it.toHexShort()}" } ?: service.uuid.toString()
            Log.d(TAG, "GATT service discovered: $serviceLabel")
            service.characteristics.forEach { characteristic ->
                val characteristicShortUuid = characteristic.uuid.shortUuidOrNull()
                val characteristicLabel =
                    characteristicShortUuid?.let { "0x${it.toHexShort()}" } ?: characteristic.uuid.toString()
                Log.d(TAG, "GATT characteristic discovered: service=$serviceLabel characteristic=$characteristicLabel")
            }
        }
    }

    private val scanTimeoutRunnable = Runnable {
        if (isScanning) {
            Log.d(TAG, "BLE scan timed out")
            stopScan()
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.bluetoothDeviceExtra(): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    interface Callback {
        fun onScanStateChanged(isScanning: Boolean)
        fun onDeviceFound(device: BluetoothDevice)
        fun onConnectionStateChanged(state: BleConnectionState)
        fun onServicesDiscovered(serviceUuids: List<UUID>)
        fun onCharacteristicRead(characteristicUuid: UUID, value: ByteArray)
        fun onCharacteristicWrite(characteristicUuid: UUID, success: Boolean)
        fun onError(message: String, cause: Throwable? = null)
    }

    private companion object {
        private const val TAG = "AlbersBleManager"
        private const val TARGET_DEVICE_NAME = "ALBERS"
        private val ALBERS_DEVICE_NAME_HINTS = listOf(
            TARGET_DEVICE_NAME,
            "Albers_BLE_BAL3",
        )
        private val ALBERS_DEVICE_ADDRESS_HINTS = listOf(
            "00:80:E1"
        )
        private const val SCAN_TIMEOUT_MS = 15_000L
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val RECONNECT_DELAY_MS = 2_000L
        private const val POST_DISCOVERY_READ_DELAY_MS = 900L
        private const val READ_RETRY_DELAY_MS = 1_200L
        private const val MAX_READ_RETRY_ATTEMPTS = 2
    }
}

private fun UUID.matchesShortUuid(shortUuid: Int): Boolean =
    with(AlbersGattProfile) { this@matchesShortUuid.matchesShortUuid(shortUuid) }

private fun UUID.shortUuidOrNull(): Int? =
    with(AlbersGattProfile) { this@shortUuidOrNull.shortUuidOrNull() }

private fun Int.toHexShort(): String = toString(radix = 16).padStart(4, '0').uppercase()

sealed class BleConnectionState {
    data object Disconnected : BleConnectionState()
    data object Pairing : BleConnectionState()
    data object Connecting : BleConnectionState()
    data object Connected : BleConnectionState()
    data object Reconnecting : BleConnectionState()
    data object Disconnecting : BleConnectionState()
}

private object BluetoothGattCharacteristicWriteType {
    const val DEFAULT = android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
}
