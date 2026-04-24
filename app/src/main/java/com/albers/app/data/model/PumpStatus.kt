package com.albers.app.data.model

data class PumpStatus(
    val pump1: PumpReading = PumpReading(channel = 1),
    val pump2: PumpReading = PumpReading(channel = 2)
) {
    val atLeastOnePumpOperable: Boolean
        get() = listOf(pump1, pump2).any { it.operability != PumpOperability.Inoperable }

    val bothPumpsInoperable: Boolean
        get() = listOf(pump1, pump2).all { it.operability == PumpOperability.Inoperable }
}

data class PumpReading(
    val channel: Int,
    val currentAmps: Float? = null,
    val condition: PumpCondition = PumpCondition.Unknown,
    val operability: PumpOperability = PumpOperability.Unknown,
    val detail: String = "Waiting for ALBERS data"
)

enum class PumpCondition {
    Unknown,
    Idle,
    Nominal,
    Priming,
    DryRun,
    NoFlow,
    HighLoad,
    Fault
}

enum class PumpOperability {
    Unknown,
    Operable,
    Inoperable
}
