package com.albers.app.data.model

data class AlbersDeviceStatus(
    val batteryPercent: Int? = null,
    val batteryType: BatteryType = BatteryType.Unknown,
    val pump1CurrentAmps: Float? = null,
    val pump2CurrentAmps: Float? = null,
    val pressureHpa: Float? = null,
    val elapsedTimeSeconds: Int? = null,
    val isPumping: Boolean = false,
    val connectionState: DeviceConnectionState = DeviceConnectionState.Disconnected,
    val lastUpdatedAtMillis: Long? = null
)

enum class BatteryType {
    Main,
    Emergency,
    Failed,
    Unknown
}

enum class DeviceConnectionState {
    Disconnected,
    Reconnecting,
    Connecting,
    Connected
}
