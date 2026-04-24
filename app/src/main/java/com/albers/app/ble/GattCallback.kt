package com.albers.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.os.Build
import android.util.Log
import java.util.UUID

class GattCallback(
    private val onConnectionStateChanged: (BluetoothGatt, BleConnectionState) -> Unit,
    private val onServicesDiscovered: (BluetoothGatt, List<UUID>) -> Unit,
    private val onCharacteristicRead: (UUID, ByteArray) -> Unit,
    private val onCharacteristicWrite: (UUID, Boolean, Int) -> Unit,
    private val onError: (String, Throwable?) -> Unit
) : BluetoothGattCallback() {

    @SuppressLint("MissingPermission")
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        val deviceAddress = gatt.device?.address.orEmpty()
        Log.d(TAG, "GATT state changed: address=$deviceAddress status=$status newState=$newState")

        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.e(TAG, "GATT connection error: status=$status address=$deviceAddress")
            onError("GATT connection failed with status $status", null)
            onConnectionStateChanged(gatt, BleConnectionState.Disconnected)
            return
        }

        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                onConnectionStateChanged(gatt, BleConnectionState.Connected)
                Log.d(TAG, "Discovering GATT services for $deviceAddress")
                if (!gatt.discoverServices()) {
                    onError("Unable to start GATT service discovery", null)
                }
            }

            BluetoothProfile.STATE_DISCONNECTED -> {
                Log.d(TAG, "GATT disconnected: address=$deviceAddress")
                onConnectionStateChanged(gatt, BleConnectionState.Disconnected)
            }

            else -> Log.d(TAG, "Unhandled GATT state: $newState")
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.e(TAG, "Service discovery failed: status=$status")
            onError("GATT service discovery failed with status $status", null)
            return
        }

        val serviceUuids = gatt.services.map { it.uuid }
        Log.d(TAG, "GATT services discovered: count=${serviceUuids.size}")
        onServicesDiscovered(gatt, serviceUuids)
    }

    @Deprecated("Used by Android 12 and lower")
    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return
        }
        handleCharacteristicRead(characteristic.uuid, characteristic.value, status)
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        handleCharacteristicRead(characteristic.uuid, value, status)
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        val success = status == BluetoothGatt.GATT_SUCCESS
        Log.d(TAG, "Characteristic write: uuid=${characteristic.uuid} success=$success status=$status")
        onCharacteristicWrite(characteristic.uuid, success, status)
    }

    private fun handleCharacteristicRead(uuid: UUID, value: ByteArray, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.e(TAG, "Characteristic read failed: uuid=$uuid status=$status")
            onError("Characteristic read failed for $uuid with status $status", null)
            return
        }

        Log.d(TAG, "Characteristic read: uuid=$uuid bytes=${value.size}")
        onCharacteristicRead(uuid, value)
    }

    private companion object {
        private const val TAG = "AlbersGattCallback"
    }
}
