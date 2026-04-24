package com.albers.app

import com.albers.app.data.model.BatteryType
import com.albers.app.data.parser.AlbersBleParser
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbersBleParserTest {
    @Test
    fun `parseAdcSys decodes byte-pair-swapped floats`() {
        val values = listOf(
            2026f,
            4f,
            25f,
            14f,
            5f,
            30f,
            9f,
            1f,
            0.92f,
            0.41f,
            1013.2f
        )
        val payload = values.flatMap { encodeSwappedFloat(it).toList() }.toByteArray()

        val parsed = AlbersBleParser.parseAdcSys(payload)

        assertNotNull(parsed)
        assertEquals(9, parsed?.batteryPercent)
        assertEquals(BatteryType.Emergency, parsed?.batteryType)
        assertEquals(0.92f, parsed?.pump1CurrentAmps ?: 0f, 0.001f)
        assertEquals(0.41f, parsed?.pump2CurrentAmps ?: 0f, 0.001f)
        assertEquals(1013.2f, parsed?.pressureHpa ?: 0f, 0.01f)
        assertEquals("2026-04-25 14:05:30", parsed?.deviceTimestamp?.toDisplayText())
    }

    @Test
    fun `parseTimerSys decodes little-endian wait time and pump status`() {
        val payload = byteArrayOf(
            0x1E,
            0x00,
            0x01,
            0x00
        )

        val parsed = AlbersBleParser.parseTimerSys(payload)

        assertNotNull(parsed)
        assertEquals(30, parsed?.waitTimeSeconds)
        assertTrue(parsed?.pumpActive == true)
        assertEquals(1, parsed?.rawPumpStatus)
    }

    @Test
    fun `parseSavedDiagnostics decodes multiple 44-byte entries`() {
        val first = listOf(
            2026f, 4f, 25f, 14f, 5f, 30f, 50f, 0f, 0.9f, 0.95f, 1000f
        )
        val second = listOf(
            2026f, 4f, 25f, 15f, 10f, 0f, 4f, 1f, 0.02f, 0.03f, 950f
        )
        val payload = (first + second).flatMap { encodeSwappedFloat(it).toList() }.toByteArray()

        val entries = AlbersBleParser.parseSavedDiagnostics(payload)

        assertEquals(2, entries.size)
        assertEquals(50, entries.first().status.batteryPercent)
        assertEquals(4, entries.last().status.batteryPercent)
        assertEquals("2026-04-25 15:10:00", entries.last().status.deviceTimestamp?.toDisplayText())
    }

    private fun encodeSwappedFloat(value: Float): ByteArray {
        val original = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(value)
            .array()
        return byteArrayOf(
            original[1],
            original[0],
            original[3],
            original[2]
        )
    }
}
