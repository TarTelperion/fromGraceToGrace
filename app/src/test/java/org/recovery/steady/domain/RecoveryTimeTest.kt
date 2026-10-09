package org.recovery.steady.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class RecoveryTimeTest {
    private val zone = ZoneId.of("America/Denver")

    @Test
    fun sameDayReturnsZeroCompletedDays() {
        val start = Instant.parse("2026-10-08T14:00:00Z").toEpochMilli()
        val now = Instant.parse("2026-10-08T20:00:00Z").toEpochMilli()

        val result = calculateRecoveryDuration(start, now, zone)

        assertEquals(0, result.completedDays)
        assertEquals(6, result.hoursAfterLastDay)
    }

    @Test
    fun crossingLocalMidnightCountsOneCalendarDay() {
        val start = Instant.parse("2026-10-08T05:50:00Z").toEpochMilli()
        val now = Instant.parse("2026-10-08T06:10:00Z").toEpochMilli()

        val result = calculateRecoveryDuration(start, now, zone)

        assertEquals(1, result.completedDays)
    }

    @Test
    fun clockBeforeStartNeverProducesNegativeValues() {
        val start = Instant.parse("2026-10-08T20:00:00Z").toEpochMilli()
        val now = Instant.parse("2026-10-08T19:00:00Z").toEpochMilli()

        val result = calculateRecoveryDuration(start, now, zone)

        assertEquals(0, result.completedDays)
        assertEquals(0, result.hoursAfterLastDay)
    }
}

