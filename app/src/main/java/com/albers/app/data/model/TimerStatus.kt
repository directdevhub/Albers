package com.albers.app.data.model

data class TimerStatus(
    val waitTimeSeconds: Int? = null,
    val pumpActive: Boolean = false,
    val rawPumpStatus: Int? = null,
    val lastReportedAtMillis: Long? = null
)
