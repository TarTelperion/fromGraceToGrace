package org.recovery.steady.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class RecoveryTimeTest {
    @Test
    fun sameDayReturnsZeroCompletedDays() {
        val start = Instant.parse("2026-10-08T14:00:00Z").toEpochMilli()
        val now = Instant.parse("2026-10-08T20:00:00Z").toEpochMilli()

        val result = calculateRecoveryDuration(start, now)

        assertEquals(0, result.completedDays)
        assertEquals(6, result.hoursAfterLastDay)
    }

    @Test
    fun crossingLocalMidnightDoesNotCountUntilTwentyFourHoursPass() {
        val start = Instant.parse("2026-10-08T05:50:00Z").toEpochMilli()
        val now = Instant.parse("2026-10-08T06:10:00Z").toEpochMilli()

        val result = calculateRecoveryDuration(start, now)

        assertEquals(0, result.completedDays)
        assertEquals(0, result.hoursAfterLastDay)
    }

    @Test
    fun twentyFiveElapsedHoursCountsOneDayAndOneHour() {
        val start = Instant.parse("2026-10-08T05:50:00Z").toEpochMilli()
        val now = Instant.parse("2026-10-09T06:50:00Z").toEpochMilli()

        val result = calculateRecoveryDuration(start, now)

        assertEquals(1, result.completedDays)
        assertEquals(1, result.hoursAfterLastDay)
    }

    @Test
    fun clockBeforeStartNeverProducesNegativeValues() {
        val start = Instant.parse("2026-10-08T20:00:00Z").toEpochMilli()
        val now = Instant.parse("2026-10-08T19:00:00Z").toEpochMilli()

        val result = calculateRecoveryDuration(start, now)

        assertEquals(0, result.completedDays)
        assertEquals(0, result.hoursAfterLastDay)
    }
}
