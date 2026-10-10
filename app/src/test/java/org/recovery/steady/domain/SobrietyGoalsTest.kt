package org.recovery.steady.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SobrietyGoalsTest {
    @Test
    fun progressBeginsAtZeroTowardFirstEnabledGoal() {
        val progress = calculateGoalProgress(
            startedAtMillis = 1_000L,
            nowMillis = 1_000L,
            enabledGoalIds = setOf(SobrietyGoal.ONE_HOUR.id),
        )

        assertEquals(SobrietyGoal.ONE_HOUR, progress.currentGoal)
        assertEquals(0, progress.percent)
    }

    @Test
    fun progressMovesFromLastCompletedGoalToNextGoal() {
        val hour = SobrietyGoal.ONE_HOUR.durationMillis
        val day = SobrietyGoal.ONE_DAY.durationMillis
        val halfway = hour + ((day - hour) / 2)
        val progress = calculateGoalProgress(
            startedAtMillis = 0L,
            nowMillis = halfway,
            enabledGoalIds = setOf(SobrietyGoal.ONE_HOUR.id, SobrietyGoal.ONE_DAY.id),
        )

        assertEquals(SobrietyGoal.ONE_HOUR, progress.previousGoal)
        assertEquals(SobrietyGoal.ONE_DAY, progress.currentGoal)
        assertEquals(50, progress.percent)
    }

    @Test
    fun finalGoalRemainsFullAfterCompletion() {
        val progress = calculateGoalProgress(
            startedAtMillis = 0L,
            nowMillis = SobrietyGoal.ONE_DAY.durationMillis + 1L,
            enabledGoalIds = setOf(SobrietyGoal.ONE_DAY.id),
        )

        assertNull(progress.currentGoal)
        assertEquals(100, progress.percent)
    }
}
