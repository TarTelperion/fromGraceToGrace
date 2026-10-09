package org.recovery.steady.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class RecoveryRepository(private val context: Context) {
    private val database = RecoveryDatabase(context)
    private val refreshSignal = MutableStateFlow(0)

    val recoveryState: Flow<RecoveryState> = refreshSignal.map {
        withContext(Dispatchers.IO) { database.currentState() }
    }

    val lapses: Flow<List<LapseRecord>> = refreshSignal.map {
        withContext(Dispatchers.IO) { database.lapses() }
    }

    val savedResources: Flow<List<SavedResource>> = refreshSignal.map {
        withContext(Dispatchers.IO) { database.resources() }
    }

    val reminderSettings: Flow<ReminderSettings> = context.settingsDataStore.data.map { prefs ->
        ReminderSettings(
            enabled = prefs[REMINDER_ENABLED] ?: false,
            hour = prefs[REMINDER_HOUR] ?: 20,
            minute = prefs[REMINDER_MINUTE] ?: 0,
        )
    }

    val appPreferences: Flow<AppPreferences> = context.settingsDataStore.data.map { prefs ->
        AppPreferences(
            themeColorId = prefs[THEME_COLOR] ?: "sage",
            goals = GoalSettings(
                enabledGoalIds = prefs[GOAL_IDS] ?: GoalSettings.DEFAULT_GOAL_IDS,
                notificationsEnabled = prefs[GOAL_NOTIFICATIONS] ?: false,
                lastNotifiedStartMillis = prefs[LAST_GOAL_START] ?: -1L,
                lastNotifiedGoalId = prefs[LAST_GOAL_ID].orEmpty(),
            ),
            dailyScripture = prefs[DAILY_SCRIPTURE_TEXT]?.takeIf { it.isNotBlank() }?.let { text ->
                DailyScripture(
                    text = text,
                    reference = prefs[DAILY_SCRIPTURE_REFERENCE].orEmpty(),
                    fetchedDate = prefs[DAILY_SCRIPTURE_DATE].orEmpty(),
                    sourceUrl = prefs[DAILY_SCRIPTURE_SOURCE].orEmpty(),
                )
            },
        )
    }

    suspend fun recordLapse(note: String) = withContext(Dispatchers.IO) {
        database.recordLapse(note)
        refreshSignal.value += 1
    }

    suspend fun updateCurrentStart(startedAtMillis: Long) = withContext(Dispatchers.IO) {
        database.updateCurrentStart(startedAtMillis)
        refreshSignal.value += 1
    }

    suspend fun deleteLapse(id: Long) = withContext(Dispatchers.IO) {
        database.deleteLapse(id)
        refreshSignal.value += 1
    }

    suspend fun saveSharedResource(text: String) = withContext(Dispatchers.IO) {
        database.saveSharedResource(text)
        refreshSignal.value += 1
    }

    suspend fun updateReminder(settings: ReminderSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[REMINDER_ENABLED] = settings.enabled
            prefs[REMINDER_HOUR] = settings.hour
            prefs[REMINDER_MINUTE] = settings.minute
        }
    }

    suspend fun updateThemeColor(themeColorId: String) {
        context.settingsDataStore.edit { it[THEME_COLOR] = themeColorId }
    }

    suspend fun updateGoals(settings: GoalSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[GOAL_IDS] = settings.enabledGoalIds
            prefs[GOAL_NOTIFICATIONS] = settings.notificationsEnabled
        }
    }

    suspend fun recoverySnapshot(): RecoveryState = withContext(Dispatchers.IO) {
        database.currentState()
    }

    suspend fun preferencesSnapshot(): AppPreferences = appPreferences.first()

    suspend fun markGoalNotified(startedAtMillis: Long, goalId: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[LAST_GOAL_START] = startedAtMillis
            prefs[LAST_GOAL_ID] = goalId
        }
    }

    suspend fun refreshDailyScriptureIfNeeded() = withContext(Dispatchers.IO) {
        val today = LocalDate.now().toString()
        val current = appPreferences.first().dailyScripture
        if (current?.fetchedDate == today && current.text.isNotBlank()) return@withContext

        val selection = DAILY_SCRIPTURES[Math.floorMod(LocalDate.now().toEpochDay(), DAILY_SCRIPTURES.size.toLong()).toInt()]
        runCatching {
            val connection = URL("$SCRIPTURE_API_BASE/${selection.book}/${selection.chapter}/${selection.verse}")
                .openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Steady-Android/0.3")
            try {
                if (connection.responseCode !in 200..299) error("Scripture API returned ${connection.responseCode}")
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                DailyScripture(
                    text = json.getString("text").trim(),
                    reference = json.getString("reference").trim(),
                    fetchedDate = today,
                    sourceUrl = selection.churchUrl,
                )
            } finally {
                connection.disconnect()
            }
        }.onSuccess { scripture ->
            context.settingsDataStore.edit { prefs ->
                prefs[DAILY_SCRIPTURE_TEXT] = scripture.text
                prefs[DAILY_SCRIPTURE_REFERENCE] = scripture.reference
                prefs[DAILY_SCRIPTURE_DATE] = scripture.fetchedDate
                prefs[DAILY_SCRIPTURE_SOURCE] = scripture.sourceUrl
            }
        }
    }

    companion object {
        private val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        private val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        private val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        private val THEME_COLOR = stringPreferencesKey("theme_color")
        private val GOAL_IDS = stringSetPreferencesKey("goal_ids")
        private val GOAL_NOTIFICATIONS = booleanPreferencesKey("goal_notifications")
        private val LAST_GOAL_START = longPreferencesKey("last_goal_start")
        private val LAST_GOAL_ID = stringPreferencesKey("last_goal_id")
        private val DAILY_SCRIPTURE_TEXT = stringPreferencesKey("daily_scripture_text")
        private val DAILY_SCRIPTURE_REFERENCE = stringPreferencesKey("daily_scripture_reference")
        private val DAILY_SCRIPTURE_DATE = stringPreferencesKey("daily_scripture_date")
        private val DAILY_SCRIPTURE_SOURCE = stringPreferencesKey("daily_scripture_source")

        private const val SCRIPTURE_API_BASE =
            "https://openscriptureapi.org/api/scriptures/v1/lds/en/book"

        private data class ScriptureSelection(
            val book: String,
            val chapter: Int,
            val verse: Int,
            val churchUrl: String,
        )

        private val DAILY_SCRIPTURES = listOf(
            ScriptureSelection("alma", 32, 21, "https://www.churchofjesuschrist.org/study/scriptures/bofm/alma/32?lang=eng&id=p21#p21"),
            ScriptureSelection("ether", 12, 27, "https://www.churchofjesuschrist.org/study/scriptures/bofm/ether/12?lang=eng&id=p27#p27"),
            ScriptureSelection("mosiah", 24, 14, "https://www.churchofjesuschrist.org/study/scriptures/bofm/mosiah/24?lang=eng&id=p14#p14"),
            ScriptureSelection("2nephi", 4, 34, "https://www.churchofjesuschrist.org/study/scriptures/bofm/2-ne/4?lang=eng&id=p34#p34"),
            ScriptureSelection("helaman", 5, 12, "https://www.churchofjesuschrist.org/study/scriptures/bofm/hel/5?lang=eng&id=p12#p12"),
            ScriptureSelection("doctrineandcovenants", 6, 36, "https://www.churchofjesuschrist.org/study/scriptures/dc-testament/dc/6?lang=eng&id=p36#p36"),
            ScriptureSelection("psalms", 46, 10, "https://www.churchofjesuschrist.org/study/scriptures/ot/ps/46?lang=eng&id=p10#p10"),
            ScriptureSelection("matthew", 11, 28, "https://www.churchofjesuschrist.org/study/scriptures/nt/matt/11?lang=eng&id=p28#p28"),
            ScriptureSelection("moroni", 10, 32, "https://www.churchofjesuschrist.org/study/scriptures/bofm/moro/10?lang=eng&id=p32#p32"),
            ScriptureSelection("1corinthians", 10, 13, "https://www.churchofjesuschrist.org/study/scriptures/nt/1-cor/10?lang=eng&id=p13#p13"),
        )
    }
}
