package org.recovery.steady.data

import org.json.JSONArray
import org.json.JSONObject
import org.recovery.steady.domain.SobrietyGoal
import java.time.ZoneId

object BackupCodec {
    private const val FORMAT_VERSION = 1
    private const val MAX_RECORDS = 10_000
    private const val MAX_TEXT_LENGTH = 100_000
    private val allowedThemes = setOf("sage", "ocean", "lavender", "sunset", "slate")

    fun encode(backup: AppBackup): String = JSONObject().apply {
        put("formatVersion", FORMAT_VERSION)
        put("exportedAtMillis", System.currentTimeMillis())
        put("recovery", JSONObject().apply {
            put("startedAtMillis", backup.recovery.startedAtMillis)
            put("originalZoneId", backup.recovery.originalZoneId)
        })
        put("lapses", JSONArray().apply {
            backup.lapses.forEach { lapse ->
                put(JSONObject().apply {
                    put("id", lapse.id)
                    put("priorStartMillis", lapse.priorStartMillis)
                    put("occurredAtMillis", lapse.occurredAtMillis)
                    put("note", lapse.note)
                })
            }
        })
        put("resources", JSONArray().apply {
            backup.resources.forEach { resource ->
                put(JSONObject().apply {
                    put("id", resource.id)
                    put("title", resource.title)
                    put("body", resource.body)
                    put("sourceUrl", resource.sourceUrl ?: JSONObject.NULL)
                    put("createdAtMillis", resource.createdAtMillis)
                })
            }
        })
        put("settings", JSONObject().apply {
            put("themeColorId", backup.themeColorId)
            put("reminderEnabled", backup.reminder.enabled)
            put("reminderHour", backup.reminder.hour)
            put("reminderMinute", backup.reminder.minute)
            put("goalNotificationsEnabled", backup.goals.notificationsEnabled)
            put("enabledGoalIds", JSONArray(backup.goals.enabledGoalIds.toList()))
            put("lastNotifiedStartMillis", backup.goals.lastNotifiedStartMillis)
            put("lastNotifiedGoalId", backup.goals.lastNotifiedGoalId)
        })
    }.toString(2)

    fun decode(value: String): AppBackup {
        require(value.length <= 5_000_000) { "This backup file is too large." }
        val root = JSONObject(value)
        require(root.optInt("formatVersion", -1) == FORMAT_VERSION) {
            "This backup version is not supported."
        }

        val now = System.currentTimeMillis()
        val recoveryJson = root.getJSONObject("recovery")
        val startedAt = recoveryJson.getLong("startedAtMillis")
        require(startedAt in 0..now) { "The recovery start time is not valid." }
        val zone = recoveryJson.optString("originalZoneId", ZoneId.systemDefault().id)
            .takeIf { runCatching { ZoneId.of(it) }.isSuccess }
            ?: ZoneId.systemDefault().id

        val lapseArray = root.optJSONArray("lapses") ?: JSONArray()
        require(lapseArray.length() <= MAX_RECORDS) { "This backup has too many history records." }
        val lapses = buildList {
            for (index in 0 until lapseArray.length()) {
                val item = lapseArray.getJSONObject(index)
                val note = item.optString("note")
                require(note.length <= MAX_TEXT_LENGTH) { "A history note is too large." }
                add(
                    LapseRecord(
                        id = item.getLong("id").coerceAtLeast(1L),
                        priorStartMillis = item.getLong("priorStartMillis").coerceAtLeast(0L),
                        occurredAtMillis = item.getLong("occurredAtMillis").coerceIn(0L, now),
                        note = note,
                    ),
                )
            }
        }.distinctBy { it.id }

        val resourceArray = root.optJSONArray("resources") ?: JSONArray()
        require(resourceArray.length() <= MAX_RECORDS) { "This backup has too many saved passages." }
        val resources = buildList {
            for (index in 0 until resourceArray.length()) {
                val item = resourceArray.getJSONObject(index)
                val title = item.optString("title", "Saved passage")
                val body = item.getString("body")
                require(title.length <= 1_000 && body.length <= MAX_TEXT_LENGTH) {
                    "A saved passage is too large."
                }
                add(
                    SavedResource(
                        id = item.getLong("id").coerceAtLeast(1L),
                        title = title,
                        body = body,
                        sourceUrl = item.optString("sourceUrl").takeIf { it.isNotBlank() },
                        createdAtMillis = item.optLong("createdAtMillis", now).coerceIn(0L, now),
                    ),
                )
            }
        }.distinctBy { it.id }

        val settings = root.optJSONObject("settings") ?: JSONObject()
        val validGoalIds = SobrietyGoal.entries.mapTo(mutableSetOf()) { it.id }
        val goalArray = settings.optJSONArray("enabledGoalIds") ?: JSONArray()
        val enabledGoals = buildSet {
            for (index in 0 until goalArray.length()) {
                goalArray.optString(index).takeIf(validGoalIds::contains)?.let(::add)
            }
        }.ifEmpty { GoalSettings.DEFAULT_GOAL_IDS }
        val theme = settings.optString("themeColorId", "sage").takeIf(allowedThemes::contains) ?: "sage"

        return AppBackup(
            recovery = RecoveryState(startedAt, zone),
            lapses = lapses,
            resources = resources,
            reminder = ReminderSettings(
                enabled = settings.optBoolean("reminderEnabled", false),
                hour = settings.optInt("reminderHour", 20).coerceIn(0, 23),
                minute = settings.optInt("reminderMinute", 0).coerceIn(0, 59),
            ),
            themeColorId = theme,
            goals = GoalSettings(
                enabledGoalIds = enabledGoals,
                notificationsEnabled = settings.optBoolean("goalNotificationsEnabled", false),
                lastNotifiedStartMillis = settings.optLong("lastNotifiedStartMillis", -1L),
                lastNotifiedGoalId = settings.optString("lastNotifiedGoalId"),
            ),
        )
    }
}
