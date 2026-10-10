package org.recovery.steady.data

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
            motivationalQuotes = decodeMotivationalQuotes(prefs[MOTIVATIONAL_QUOTES_CACHE]),
            initialSetupComplete = prefs[INITIAL_SETUP_COMPLETE] ?: false,
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

    suspend fun completeInitialSetup(startedAtMillis: Long) = withContext(Dispatchers.IO) {
        database.updateCurrentStart(startedAtMillis)
        context.settingsDataStore.edit { it[INITIAL_SETUP_COMPLETE] = true }
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

    suspend fun deleteResource(id: Long) = withContext(Dispatchers.IO) {
        database.deleteResource(id)
        refreshSignal.value += 1
    }

    suspend fun exportBackup(): String = withContext(Dispatchers.IO) {
        val preferences = appPreferences.first()
        BackupCodec.encode(
            AppBackup(
                recovery = database.currentState(),
                lapses = database.lapses(),
                resources = database.resources(),
                reminder = reminderSettings.first(),
                themeColorId = preferences.themeColorId,
                goals = preferences.goals,
            ),
        )
    }

    suspend fun importBackup(value: String): AppBackup = withContext(Dispatchers.IO) {
        val backup = BackupCodec.decode(value)
        database.replaceAll(backup)
        context.settingsDataStore.edit { prefs ->
            prefs[REMINDER_ENABLED] = backup.reminder.enabled
            prefs[REMINDER_HOUR] = backup.reminder.hour
            prefs[REMINDER_MINUTE] = backup.reminder.minute
            prefs[THEME_COLOR] = backup.themeColorId
            prefs[GOAL_IDS] = backup.goals.enabledGoalIds
            prefs[GOAL_NOTIFICATIONS] = backup.goals.notificationsEnabled
            prefs[LAST_GOAL_START] = backup.goals.lastNotifiedStartMillis
            prefs[LAST_GOAL_ID] = backup.goals.lastNotifiedGoalId
            prefs[INITIAL_SETUP_COMPLETE] = true
        }
        refreshSignal.value += 1
        backup
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

    suspend fun reminderSnapshot(): ReminderSettings = reminderSettings.first()

    suspend fun markGoalNotified(startedAtMillis: Long, goalId: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[LAST_GOAL_START] = startedAtMillis
            prefs[LAST_GOAL_ID] = goalId
        }
    }

    suspend fun refreshDailyScriptureIfNeeded() = withContext(Dispatchers.IO) {
        val today = LocalDate.now().toString()
        val current = appPreferences.first().dailyScripture
        if ((current?.fetchedDate == today) && current.text.isNotBlank()) return@withContext

        val selection = DAILY_SCRIPTURES[Math.floorMod(LocalDate.now().toEpochDay(), DAILY_SCRIPTURES.size.toLong()).toInt()]
        runCatching {
            val connection = URL("$SCRIPTURE_API_BASE/${selection.book}/${selection.chapter}/${selection.verse}")
                .openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "FromGraceToGrace-Android/0.3")
            try {
                if ((connection.responseCode !in 200..299)) error("Scripture API returned ${connection.responseCode}")
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

    suspend fun refreshMotivationalQuotesIfNeeded() = withContext(Dispatchers.IO) {
        val today = LocalDate.now().toEpochDay()
        val prefs = context.settingsDataStore.data.first()
        val lastSuccess = prefs[MOTIVATIONAL_QUOTES_LAST_SUCCESS_DAY]
        if (lastSuccess != null && today - lastSuccess < QUOTE_REFRESH_DAYS) {
            return@withContext
        }

        val result = runCatching {
            val connection = URL(MOTIVATIONAL_QUOTES_API).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "FromGraceToGrace-Android/0.3")
            try {
                if (connection.responseCode !in 200..299) {
                    error("Quote API returned ${connection.responseCode}")
                }
                val response = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
                buildList {
                    for (index in 0 until response.length()) {
                        val item = response.optJSONObject(index) ?: continue
                        val text = item.optString("q").trim()
                        val author = item.optString("a").trim()
                        if (
                            text.isNotBlank() &&
                            author.isNotBlank() &&
                            author.lowercase() != "zenquotes.io" &&
                            isSuitableForRecoveryFeed(text) &&
                            text.length <= MAX_QUOTE_LENGTH
                        ) {
                            add(MotivationalQuote(text, author))
                        }
                    }
                }.distinctBy { it.text }
            } finally {
                connection.disconnect()
            }
        }

        val quotes = result.getOrElse { error ->
            Log.w(QUOTE_LOG_TAG, "Motivational quote refresh failed", error)
            return@withContext
        }
        if (quotes.isEmpty()) {
            Log.w(QUOTE_LOG_TAG, "Quote service returned no suitable quotes.")
            return@withContext
        }

        context.settingsDataStore.edit { settings ->
            settings[MOTIVATIONAL_QUOTES_CACHE] = encodeMotivationalQuotes(quotes)
            settings[MOTIVATIONAL_QUOTES_LAST_SUCCESS_DAY] = today
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
        private val MOTIVATIONAL_QUOTES_CACHE = stringPreferencesKey("motivational_quotes_cache")
        private val MOTIVATIONAL_QUOTES_LAST_SUCCESS_DAY =
            longPreferencesKey("motivational_quotes_last_success_day")
        private val INITIAL_SETUP_COMPLETE = booleanPreferencesKey("initial_setup_complete")

        private const val SCRIPTURE_API_BASE =
            "https://openscriptureapi.org/api/scriptures/v1/lds/en/book"
        private const val MOTIVATIONAL_QUOTES_API =
            "https://zenquotes.io/api/quotes"
        private const val QUOTE_REFRESH_DAYS = 7L
        private const val MAX_QUOTE_LENGTH = 180
        private const val QUOTE_LOG_TAG = "QuoteRefresh"

        private val RECOVERY_SENSITIVE_TERMS = listOf(
            "alcohol",
            "dead",
            "death",
            "die",
            "drug",
            "enemy",
            "enemies",
            "kill",
            "suicide",
        )

        private fun isSuitableForRecoveryFeed(text: String): Boolean {
            val words = text.lowercase()
                .replace(Regex("[^a-z]+"), " ")
                .trim()
                .split(" ")
                .toSet()
            return RECOVERY_SENSITIVE_TERMS.none(words::contains)
        }

        private fun encodeMotivationalQuotes(quotes: List<MotivationalQuote>): String =
            JSONArray().apply {
                quotes.forEach { quote ->
                    put(JSONObject().put("text", quote.text).put("author", quote.author))
                }
            }.toString()

        private fun decodeMotivationalQuotes(value: String?): List<MotivationalQuote> {
            if (value.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(value)
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val text = item.optString("text").trim()
                        val author = item.optString("author").trim()
                        if (text.isNotBlank() && author.isNotBlank()) {
                            add(MotivationalQuote(text, author))
                        }
                    }
                }
            }.getOrDefault(emptyList())
        }

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
