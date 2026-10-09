package org.recovery.steady.domain

import kotlin.math.roundToInt

enum class SobrietyGoal(
    val id: String,
    val label: String,
    val durationMillis: Long,
) {
    ONE_HOUR("one_hour", "1 hour", 60L * 60 * 1_000),
    ONE_DAY("one_day", "1 day", 24L * 60 * 60 * 1_000),
    THREE_DAYS("three_days", "3 days", 3L * 24 * 60 * 60 * 1_000),
    ONE_WEEK("one_week", "1 week", 7L * 24 * 60 * 60 * 1_000),
    ONE_MONTH("one_month", "1 month", 30L * 24 * 60 * 60 * 1_000),
    THREE_MONTHS("three_months", "3 months", 90L * 24 * 60 * 60 * 1_000),
    SIX_MONTHS("six_months", "6 months", 180L * 24 * 60 * 60 * 1_000),
    ONE_YEAR("one_year", "1 year", 365L * 24 * 60 * 60 * 1_000),
}

data class GoalProgress(
    val previousGoal: SobrietyGoal?,
    val currentGoal: SobrietyGoal?,
    val fraction: Float,
    val percent: Int,
    val completedGoals: List<SobrietyGoal>,
)

fun calculateGoalProgress(
    startedAtMillis: Long,
    nowMillis: Long,
    enabledGoalIds: Set<String>,
): GoalProgress {
    val goals = SobrietyGoal.entries.filter { it.id in enabledGoalIds }
    if (goals.isEmpty()) return GoalProgress(null, null, 0f, 0, emptyList())

    val elapsed = (nowMillis - startedAtMillis).coerceAtLeast(0L)
    val completed = goals.filter { elapsed >= it.durationMillis }
    val current = goals.firstOrNull { elapsed < it.durationMillis }
    val previous = completed.lastOrNull()
    if (current == null) return GoalProgress(previous, null, 1f, 100, completed)

    val previousMillis = previous?.durationMillis ?: 0L
    val span = (current.durationMillis - previousMillis).coerceAtLeast(1L)
    val fraction = ((elapsed - previousMillis).toDouble() / span)
        .coerceIn(0.0, 1.0)
        .toFloat()
    return GoalProgress(
        previousGoal = previous,
        currentGoal = current,
        fraction = fraction,
        percent = (fraction * 100).roundToInt(),
        completedGoals = completed,
    )
}
