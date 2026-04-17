package com.albers.app.ble

import java.util.UUID

object AlbersGattProfile {
    const val SERVICE_SHORT_UUID = 0x000F
    const val ADC_SYS_SHORT_UUID = 0x001F
    const val TIMER_SYS_SHORT_UUID = 0x002F
    const val FECHA_SYS_SHORT_UUID = 0x003F
    const val COMMAND_SHORT_UUID = 0x004F
    const val SAVED_SHORT_UUID = 0x005F

    val readableShortUuids = listOf(
        ADC_SYS_SHORT_UUID,
        TIMER_SYS_SHORT_UUID,
        SAVED_SHORT_UUID
    )

    fun UUID.matchesShortUuid(shortUuid: Int): Boolean {
        val expectedPrefix = "0000${shortUuid.toString(16).padStart(4, '0')}-"
        return toString().startsWith(expectedPrefix, ignoreCase = true)
    }

    fun UUID.shortUuidOrNull(): Int? {
        val uuidText = toString()
        if (!uuidText.startsWith("0000", ignoreCase = true)) return null
        if (!uuidText.endsWith("-0000-1000-8000-00805f9b34fb", ignoreCase = true)) return null
        return uuidText.substring(startIndex = 4, endIndex = 8).toIntOrNull(radix = 16)
    }
}
