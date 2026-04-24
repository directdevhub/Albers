package com.albers.app.data.model

enum class AlbersCommand(val payload: Byte, val label: String) {
    RinseSanitize(0x00, "Rinse/Sanitize"),
    AutomaticCycle60(0x01, "60-minute automatic cycle"),
    AutomaticCycle90(0x02, "90-minute automatic cycle"),
    AutomaticCycle120(0x03, "120-minute automatic cycle");

    companion object {
        fun automaticCycleForInterval(minutes: Int): AlbersCommand? {
            return when (minutes) {
                60 -> AutomaticCycle60
                90 -> AutomaticCycle90
                120 -> AutomaticCycle120
                else -> null
            }
        }
    }
}
