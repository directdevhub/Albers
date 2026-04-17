package com.albers.app.data.model

data class AlbersAppState(
    val deviceStatus: AlbersDeviceStatus = AlbersDeviceStatus(),
    val faultSummary: FaultSummary = FaultSummary(),
    val notifications: List<NotificationItem> = emptyList(),
    val isLoading: Boolean = false,
    val lastErrorMessage: String? = null
)
