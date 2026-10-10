package org.recovery.steady.data

data class RecoveryState(
    val startedAtMillis: Long,
    val originalZoneId: String,
)

data class LapseRecord(
    val id: Long,
    val priorStartMillis: Long,
    val occurredAtMillis: Long,
    val note: String,
)

data class SavedResource(
    val id: Long,
    val title: String,
    val body: String,
    val sourceUrl: String?,
    val createdAtMillis: Long,
)

data class ReminderSettings(
    val enabled: Boolean = false,
    val hour: Int = 20,
    val minute: Int = 0,
)

data class GoalSettings(
    val enabledGoalIds: Set<String> = DEFAULT_GOAL_IDS,
    val notificationsEnabled: Boolean = false,
    val lastNotifiedStartMillis: Long = -1L,
    val lastNotifiedGoalId: String = "",
) {
    companion object {
        val DEFAULT_GOAL_IDS = setOf(
            "one_hour",
            "one_day",
            "three_days",
            "one_week",
            "one_month",
            "three_months",
            "six_months",
            "one_year",
        )
    }
}

data class AppPreferences(
    val themeColorId: String = "sage",
    val goals: GoalSettings = GoalSettings(),
    val dailyScripture: DailyScripture? = null,
    val motivationalQuotes: List<MotivationalQuote> = emptyList(),
    val initialSetupComplete: Boolean = false,
)

data class AppBackup(
    val recovery: RecoveryState,
    val lapses: List<LapseRecord>,
    val resources: List<SavedResource>,
    val reminder: ReminderSettings,
    val themeColorId: String,
    val goals: GoalSettings,
)

data class DailyScripture(
    val text: String,
    val reference: String,
    val fetchedDate: String,
    val sourceUrl: String,
)

data class MotivationalQuote(
    val text: String,
    val author: String,
)
