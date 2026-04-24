package com.albers.app.data.parser

import android.util.Log
import com.albers.app.data.model.AlbersDeviceStatus
import com.albers.app.data.model.BatteryStatus
import com.albers.app.data.model.BatteryType
import com.albers.app.data.model.DeviceTimestamp
import com.albers.app.data.model.SavedDiagnosticEntry
import com.albers.app.data.model.TimerStatus
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

object AlbersBleParser {
    private const val TAG = "AlbersBleParser"
    const val ADC_SYS_ENTRY_SIZE_BYTES = 44
    private const val ADC_SYS_FLOAT_COUNT = 11
    private const val LOW_BATTERY_THRESHOLD = 10
    private const val CRITICAL_BATTERY_THRESHOLD = 5

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

            val batteryPercent = values[6].roundToInt().coerceIn(0, 100)
            val batteryType = mapBatteryType(values[7])
            AlbersDeviceStatus(
                deviceTimestamp = parseDeviceTimestamp(values),
                batteryPercent = batteryPercent,
                batteryType = batteryType,
                batteryStatus = BatteryStatus(
                    percent = batteryPercent,
                    type = batteryType,
                    isLow = batteryPercent <= LOW_BATTERY_THRESHOLD,
                    isCritical = batteryPercent <= CRITICAL_BATTERY_THRESHOLD
                ),
                pump1CurrentAmps = values[8].takeIf { it.isFinite() },
                pump2CurrentAmps = values[9].takeIf { it.isFinite() },
                pressureHpa = values[10].takeIf { it.isFinite() },
                lastUpdatedAtMillis = System.currentTimeMillis()
            )
        }.onFailure { error ->
            Log.e(TAG, "Failed to parse ADC_SYS payload", error)
        }.getOrNull()
    }

    fun parseTimerSys(payload: ByteArray): TimerStatus? {
        if (payload.size < 4) {
            Log.w(TAG, "Timer_SYS payload too short: ${payload.size}")
            return null
        }

        return runCatching {
            val waitTimeSeconds = (payload[0].toInt() and 0xFF) or ((payload[1].toInt() and 0xFF) shl 8)
            val pumpStatus = (payload[2].toInt() and 0xFF) or ((payload[3].toInt() and 0xFF) shl 8)
            TimerStatus(
                waitTimeSeconds = waitTimeSeconds,
                pumpActive = pumpStatus == 1,
                rawPumpStatus = pumpStatus,
                lastReportedAtMillis = System.currentTimeMillis()
            )
        }.onFailure { error ->
            Log.e(TAG, "Failed to parse Timer_SYS payload", error)
        }.getOrNull()
    }

    fun parseSavedDiagnostics(payload: ByteArray): List<SavedDiagnosticEntry> {
        if (payload.isEmpty()) return emptyList()

        val entries = payload.size / ADC_SYS_ENTRY_SIZE_BYTES
        if (entries == 0) {
            Log.w(TAG, "Saved diagnostics payload has no complete 44-byte entries: ${payload.size}")
            return emptyList()
        }

        return (0 until entries).mapNotNull { index ->
            val start = index * ADC_SYS_ENTRY_SIZE_BYTES
            val entry = payload.copyOfRange(start, start + ADC_SYS_ENTRY_SIZE_BYTES)
            parseAdcSys(entry)?.let { status ->
                SavedDiagnosticEntry(index = index, status = status)
            }
        }
    }

    private fun parseSwappedFloat(payload: ByteArray, offset: Int): Float {
        // ALBERS sends each float with byte-pair swapping. Reverse the pair swap,
        // then decode the corrected little-endian bytes into a Float.
        val bytes = byteArrayOf(
            payload[offset + 1],
            payload[offset],
            payload[offset + 3],
            payload[offset + 2]
        )
        return ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .float
    }

    private fun parseDeviceTimestamp(values: FloatArray): DeviceTimestamp? {
        val year = values[0].roundToInt()
        val month = values[1].roundToInt()
        val day = values[2].roundToInt()
        val hour = values[3].roundToInt()
        val minute = values[4].roundToInt()
        val second = values[5].roundToInt()
        val timestamp = DeviceTimestamp(
            year = year,
            month = month,
            day = day,
            hour = hour,
            minute = minute,
            second = second
        )
        return timestamp.takeIf { it.toEpochMillisOrNull() != null }
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
