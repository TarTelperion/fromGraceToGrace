package org.recovery.steady.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus: StateFlow<String?> = _backupStatus.asStateFlow()

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
        viewModelScope.launch { repository.refreshMotivationalQuotesIfNeeded() }
        viewModelScope.launch {
            combine(repository.recoveryState, repository.appPreferences) { recovery, preferences ->
                recovery to preferences.goals
            }.collectLatest { (recovery, goals) ->
                GoalNotifications.schedule(getApplication(), recovery, goals)
            }
        }
        viewModelScope.launch {
            repository.reminderSettings.collectLatest { reminder ->
                ReminderNotifications.schedule(getApplication(), reminder)
            }
        }
    }

    fun recordLapse(note: String) {
        viewModelScope.launch { repository.recordLapse(note) }
    }

    fun updateCurrentStart(startedAtMillis: Long) {
        viewModelScope.launch { repository.updateCurrentStart(startedAtMillis) }
    }

    fun completeInitialSetup(startedAtMillis: Long) {
        viewModelScope.launch { repository.completeInitialSetup(startedAtMillis) }
    }

    fun deleteLapse(id: Long) {
        viewModelScope.launch { repository.deleteLapse(id) }
    }

    fun importSharedText(text: String) {
        viewModelScope.launch { repository.saveSharedResource(text) }
    }

    fun deleteResource(id: Long) {
        viewModelScope.launch { repository.deleteResource(id) }
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

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = repository.exportBackup()
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { output ->
                    output.writer().use { it.write(json) }
                } ?: error("The selected file could not be opened.")
            }.onSuccess {
                _backupStatus.value = "Backup saved."
            }.onFailure {
                _backupStatus.value = "Backup could not be saved."
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader().use { it.readText() }
                } ?: error("The selected file could not be opened.")
                val backup = repository.importBackup(json)
                ReminderNotifications.schedule(getApplication(), backup.reminder)
            }.onSuccess {
                _backupStatus.value = "Backup restored."
            }.onFailure {
                _backupStatus.value = it.message?.let { message -> "Backup not restored: $message" }
                    ?: "Backup could not be restored."
            }
        }
    }

    fun clearBackupStatus() {
        _backupStatus.value = null
    }

}
