package com.albers.app.data.model

data class SavedDiagnosticEntry(
    val index: Int,
    val status: AlbersDeviceStatus
) {
    val occurredAtMillis: Long?
        get() = status.deviceTimestamp?.toEpochMillisOrNull()

    val stableKey: String
        get() = buildString {
            append(occurredAtMillis ?: "no-time")
            append(':')
            append(status.batteryPercent ?: "no-battery")
            append(':')
            append(status.pump1CurrentAmps ?: "no-p1")
            append(':')
            append(status.pump2CurrentAmps ?: "no-p2")
            append(':')
            append(status.pressureHpa ?: "no-pressure")
        }
}
