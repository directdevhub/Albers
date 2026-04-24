package com.albers.app.ui.common

import androidx.annotation.DrawableRes
import com.albers.app.R
import com.albers.app.data.model.BatteryType
import com.albers.app.data.model.FaultState

enum class SystemStatusBadge {
    Nominal,
    LowBattery,
    EmergencyBattery,
    PumpError
}

fun resolveSystemStatusBadge(
    faults: Set<FaultState>,
    batteryType: BatteryType,
    batteryPercent: Int?
): SystemStatusBadge {
    return when {
        FaultState.BatteryFailure in faults ||
            FaultState.OnePumpFailed in faults ||
            FaultState.BothPumpsFailed in faults ||
            FaultState.PressureSensorFault in faults ||
            FaultState.ConnectionFailure in faults -> SystemStatusBadge.PumpError

        batteryType == BatteryType.Emergency ||
            FaultState.EmergencyBatteryActive in faults -> SystemStatusBadge.EmergencyBattery

        FaultState.LowBattery in faults ||
            FaultState.CriticalBattery in faults ||
            (batteryPercent != null && batteryPercent <= 10) -> SystemStatusBadge.LowBattery

        else -> SystemStatusBadge.Nominal
    }
}

@DrawableRes
fun SystemStatusBadge.toDrawableRes(): Int {
    return when (this) {
        SystemStatusBadge.Nominal -> R.drawable.ic_system_icon
        SystemStatusBadge.LowBattery -> R.drawable.ic_low_battery
        SystemStatusBadge.EmergencyBattery -> R.drawable.ic_emgency_battery
        SystemStatusBadge.PumpError -> R.drawable.ic_pump_error
    }
}
