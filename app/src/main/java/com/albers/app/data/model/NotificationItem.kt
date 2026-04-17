package com.albers.app.data.model

data class NotificationItem(
    val id: Long,
    val type: NotificationType,
    val title: String,
    val message: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

enum class NotificationType {
    PumpCycleCompleted,
    BatteryLow,
    BatteryCritical,
    ReconnectSuccess,
    PumpError,
    BatteryFailure,
    ConnectionLost,
    UnknownFault
}
