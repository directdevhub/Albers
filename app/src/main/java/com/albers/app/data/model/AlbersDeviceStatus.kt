package com.albers.app.data.model

data class AlbersDeviceStatus(
    val deviceTimestamp: DeviceTimestamp? = null,
    val batteryPercent: Int? = null,
    val batteryType: BatteryType = BatteryType.Unknown,
    val batteryStatus: BatteryStatus = BatteryStatus(),
    val pump1CurrentAmps: Float? = null,
    val pump2CurrentAmps: Float? = null,
    val pumpStatus: PumpStatus = PumpStatus(),
    val pressureHpa: Float? = null,
    val elapsedTimeSeconds: Int? = null,
    val isPumping: Boolean = false,
    val timerStatus: TimerStatus = TimerStatus(),
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
    Scanning,
    Bonding,
    Reconnecting,
    Connecting,
    DiscoveringServices,
    AuthRequired,
    ConnectedReady,
    ConnectionFailed
}
