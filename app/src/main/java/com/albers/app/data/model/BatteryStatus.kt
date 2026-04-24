package com.albers.app.data.model

data class BatteryStatus(
    val percent: Int? = null,
    val type: BatteryType = BatteryType.Unknown,
    val isLow: Boolean = false,
    val isCritical: Boolean = false
)
