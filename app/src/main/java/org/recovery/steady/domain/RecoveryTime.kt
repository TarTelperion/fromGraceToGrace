package org.recovery.steady.domain

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class RecoveryDuration(
    val completedDays: Long,
    val hoursAfterLastDay: Long,
)

fun calculateRecoveryDuration(
    startedAtMillis: Long,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): RecoveryDuration {
    val start = Instant.ofEpochMilli(startedAtMillis)
    val now = Instant.ofEpochMilli(nowMillis.coerceAtLeast(startedAtMillis))
    val startDate = start.atZone(zoneId).toLocalDate()
    val nowDate = now.atZone(zoneId).toLocalDate()
    val calendarDays = ChronoUnit.DAYS.between(startDate, nowDate).coerceAtLeast(0)
    val totalHours = ChronoUnit.HOURS.between(start, now).coerceAtLeast(0)
    return RecoveryDuration(
        completedDays = calendarDays,
        hoursAfterLastDay = (totalHours - calendarDays * 24).coerceAtLeast(0),
    )
}

