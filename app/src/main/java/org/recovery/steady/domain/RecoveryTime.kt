package org.recovery.steady.domain

data class RecoveryDuration(
    val completedDays: Long,
    val hoursAfterLastDay: Long,
)

fun calculateRecoveryDuration(
    startedAtMillis: Long,
    nowMillis: Long,
): RecoveryDuration {
    val elapsedMillis = (nowMillis - startedAtMillis).coerceAtLeast(0L)
    val totalHours = elapsedMillis / MILLIS_PER_HOUR
    val completedDays = totalHours / HOURS_PER_DAY
    return RecoveryDuration(
        completedDays = completedDays,
        hoursAfterLastDay = totalHours % HOURS_PER_DAY,
    )
}

private const val HOURS_PER_DAY = 24L
private const val MILLIS_PER_HOUR = 60L * 60L * 1_000L
