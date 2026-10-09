package org.recovery.steady.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.recovery.steady.data.LapseRecord
import org.recovery.steady.data.RecoveryRepository
import org.recovery.steady.data.RecoveryState
import org.recovery.steady.data.ReminderSettings
import org.recovery.steady.data.SavedResource
import org.recovery.steady.data.AppPreferences
import org.recovery.steady.data.GoalSettings
import org.recovery.steady.notifications.GoalNotifications
import org.recovery.steady.notifications.ReminderNotifications

data class AppState(
    val recovery: RecoveryState? = null,
    val lapses: List<LapseRecord> = emptyList(),
    val resources: List<SavedResource> = emptyList(),
    val reminder: ReminderSettings = ReminderSettings(),
    val preferences: AppPreferences = AppPreferences(),
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RecoveryRepository(application)

    val state: StateFlow<AppState> = combine(
        repository.recoveryState,
        repository.lapses,
        repository.savedResources,
        repository.reminderSettings,
        repository.appPreferences,
    ) { recovery, lapses, resources, reminder, preferences ->
        AppState(recovery, lapses, resources, reminder, preferences)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState())

    init {
        viewModelScope.launch { repository.refreshDailyScriptureIfNeeded() }
        viewModelScope.launch {
            combine(repository.recoveryState, repository.appPreferences) { recovery, preferences ->
                recovery to preferences.goals
            }.collectLatest { (recovery, goals) ->
                GoalNotifications.schedule(getApplication(), recovery, goals)
            }
        }
    }

    fun recordLapse(note: String) {
        viewModelScope.launch { repository.recordLapse(note) }
    }

    fun updateCurrentStart(startedAtMillis: Long) {
        viewModelScope.launch { repository.updateCurrentStart(startedAtMillis) }
    }

    fun deleteLapse(id: Long) {
        viewModelScope.launch { repository.deleteLapse(id) }
    }

    fun importSharedText(text: String) {
        viewModelScope.launch { repository.saveSharedResource(text) }
    }

    fun updateReminder(settings: ReminderSettings) {
        viewModelScope.launch {
            repository.updateReminder(settings)
            ReminderNotifications.schedule(getApplication(), settings)
        }
    }

    fun updateThemeColor(themeColorId: String) {
        viewModelScope.launch { repository.updateThemeColor(themeColorId) }
    }

    fun updateGoals(settings: GoalSettings) {
        viewModelScope.launch { repository.updateGoals(settings) }
    }
}
