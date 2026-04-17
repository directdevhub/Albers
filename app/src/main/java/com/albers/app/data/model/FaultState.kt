package com.albers.app.data.model

data class FaultSummary(
    val highestSeverity: FaultSeverity = FaultSeverity.Nominal,
    val states: Set<FaultState> = emptySet(),
    val primaryMessage: String = "All system parameters are nominal",
    val canAutoPump: Boolean = true,
    val canRinseSanitize: Boolean = true,
    val shouldAlert: Boolean = false
)

enum class FaultSeverity {
    Nominal,
    Warning,
    Critical
}

sealed class FaultState {
    data object OnePumpFailed : FaultState()
    data object BothPumpsFailed : FaultState()
    data object PressureSensorFault : FaultState()
    data object LowBattery : FaultState()
    data object CriticalBattery : FaultState()
    data object EmergencyBatteryActive : FaultState()
    data object ConnectionLost : FaultState()
    data object Reconnecting : FaultState()
    data object BatteryFailure : FaultState()
    data object UnknownFault : FaultState()
}
