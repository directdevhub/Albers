package com.albers.app.data.model

import java.util.Calendar
import java.util.Locale

data class DeviceTimestamp(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int
) {
    fun toEpochMillisOrNull(): Long? {
        if (year !in 2000..2100) return null
        if (month !in 1..12) return null
        if (day !in 1..31) return null
        if (hour !in 0..23) return null
        if (minute !in 0..59) return null
        if (second !in 0..59) return null

        return Calendar.getInstance().run {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, 0)
            timeInMillis
        }
    }

    fun toDisplayText(): String {
        return String.format(
            Locale.US,
            "%04d-%02d-%02d %02d:%02d:%02d",
            year,
            month,
            day,
            hour,
            minute,
            second
        )
    }
}
