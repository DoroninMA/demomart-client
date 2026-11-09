package com.demomart.client.token

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

internal class MemoryOffsetStore : OffsetStore {
    private var value = 0L
    override suspend fun load(): Long = value
    override suspend fun save(offsetSeconds: Long) { value = offsetSeconds }
}

class ServerClockTest {
    private val instant = Instant.ofEpochSecond(1_800_000_000)
    private val device = Clock.fixed(instant, ZoneOffset.UTC)
    private val store = MemoryOffsetStore()
    private val clock = ServerClock(device, store)

    private fun date(offset: Long): String =
        DateTimeFormatter.RFC_1123_DATE_TIME.format(instant.plusSeconds(offset).atZone(ZoneOffset.UTC))

    @Test fun offsetWhenFarOff() {
        clock.onServerDate(date(240), instant)
        assertEquals(instant.epochSecond + 240, clock.now())
    }

    @Test fun badDateHeaderIgnored() {
        clock.onServerDate(date(240), instant)
        clock.onServerDate("garbage", instant)
        clock.onServerDate(null, instant)
        assertEquals(instant.epochSecond + 240, clock.now())
    }

    @Test fun offsetSurvivesRestart() = runTest {
        clock.onServerDate(date(240), instant)
        val restored = ServerClock(device, store)
        restored.restore()
        assertEquals(instant.epochSecond + 240, restored.now())
    }
}
