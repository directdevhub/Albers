package com.albers.app.data.parser

import android.util.Log
import com.albers.app.data.model.AlbersDeviceStatus
import com.albers.app.data.model.BatteryType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

object AlbersBleParser {
    private const val TAG = "AlbersBleParser"
    const val ADC_SYS_ENTRY_SIZE_BYTES = 44
    private const val ADC_SYS_FLOAT_COUNT = 11

    fun parseAdcSys(payload: ByteArray): AlbersDeviceStatus? {
        if (payload.size < ADC_SYS_ENTRY_SIZE_BYTES) {
            Log.w(TAG, "ADC_SYS payload too short: ${payload.size}")
            return null
        }

        return runCatching {
            val values = FloatArray(ADC_SYS_FLOAT_COUNT) { index ->
                parseSwappedFloat(payload, index * 4)
            }
            if (values.any { !it.isFinite() }) {
                Log.w(TAG, "ADC_SYS payload contained non-finite values")
                return null
            }

            AlbersDeviceStatus(
                batteryPercent = values[6].roundToInt().coerceIn(0, 100),
                batteryType = mapBatteryType(values[7]),
                pump1CurrentAmps = values[8],
                pump2CurrentAmps = values[9],
                pressureHpa = values[10],
                lastUpdatedAtMillis = System.currentTimeMillis()
            )
        }.onFailure { error ->
            Log.e(TAG, "Failed to parse ADC_SYS payload", error)
        }.getOrNull()
    }

    fun parseTimerSys(payload: ByteArray): Pair<Int, Boolean>? {
        if (payload.size < 4) {
            Log.w(TAG, "Timer_SYS payload too short: ${payload.size}")
            return null
        }

        return runCatching {
            val elapsed = (payload[0].toInt() and 0xFF) or ((payload[1].toInt() and 0xFF) shl 8)
            val pumpStatus = (payload[2].toInt() and 0xFF) or ((payload[3].toInt() and 0xFF) shl 8)
            elapsed to (pumpStatus == 1)
        }.onFailure { error ->
            Log.e(TAG, "Failed to parse Timer_SYS payload", error)
        }.getOrNull()
    }

    fun parseSavedDiagnostics(payload: ByteArray): List<AlbersDeviceStatus> {
        if (payload.isEmpty()) return emptyList()

        val entries = payload.size / ADC_SYS_ENTRY_SIZE_BYTES
        if (entries == 0) {
            Log.w(TAG, "Saved diagnostics payload has no complete 44-byte entries: ${payload.size}")
            return emptyList()
        }

        return (0 until entries).mapNotNull { index ->
            val start = index * ADC_SYS_ENTRY_SIZE_BYTES
            val entry = payload.copyOfRange(start, start + ADC_SYS_ENTRY_SIZE_BYTES)
            parseAdcSys(entry)
        }
    }

    private fun parseSwappedFloat(payload: ByteArray, offset: Int): Float {
        val bytes = byteArrayOf(
            payload[offset + 3],
            payload[offset + 2],
            payload[offset + 1],
            payload[offset]
        )
        return ByteBuffer.wrap(bytes)
            .order(ByteOrder.BIG_ENDIAN)
            .float
    }

    private fun mapBatteryType(rawValue: Float): BatteryType {
        return when (rawValue.roundToInt()) {
            0 -> BatteryType.Main
            1 -> BatteryType.Emergency
            2 -> BatteryType.Failed
            else -> BatteryType.Unknown
        }
    }
}
