package com.albers.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
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
        onCharacteristicRead = callback::onCharacteristicRead,
        onCharacteristicWrite = callback::onCharacteristicWrite,
        onError = callback::onError
    )

    private var bluetoothGatt: BluetoothGatt? = null
    private var isScanning = false

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = result.scanRecord?.deviceName ?: device.name
            Log.d(TAG, "Scan result: name=$deviceName address=${device.address}")

            if (!isAlbersDeviceName(deviceName)) {
                return
            }

            if (discoveredDeviceAddresses.add(device.address)) {
                Log.d(TAG, "ALBERS device found: name=$deviceName address=${device.address}")
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
        disconnect()

        Log.d(TAG, "Connecting to device: address=${device.address}")
        callback.onConnectionStateChanged(BleConnectionState.Connecting)
        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context.applicationContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context.applicationContext, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        Log.d(TAG, "Disconnect requested")
        callback.onConnectionStateChanged(BleConnectionState.Disconnecting)
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
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

    fun release() {
        stopScan()
        disconnect()
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
        callback.onConnectionStateChanged(state)
    }

    private fun handleServicesDiscovered(gatt: BluetoothGatt, serviceUuids: List<UUID>) {
        Log.d(TAG, "Services discovered: ${serviceUuids.joinToString()}")
        bluetoothGatt = gatt
        callback.onServicesDiscovered(serviceUuids)
    }

    private val scanTimeoutRunnable = Runnable {
        if (isScanning) {
            Log.d(TAG, "BLE scan timed out")
            stopScan()
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
            "Albers_BLE",
            "Albers_BLE_BAL3"
        )
        private const val SCAN_TIMEOUT_MS = 15_000L
    }
}

sealed class BleConnectionState {
    data object Disconnected : BleConnectionState()
    data object Connecting : BleConnectionState()
    data object Connected : BleConnectionState()
    data object Disconnecting : BleConnectionState()
}

private object BluetoothGattCharacteristicWriteType {
    const val DEFAULT = android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
}
