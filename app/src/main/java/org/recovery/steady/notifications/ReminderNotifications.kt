package org.recovery.steady.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.recovery.steady.MainActivity
import org.recovery.steady.R
import org.recovery.steady.data.ReminderSettings
import org.recovery.steady.data.RecoveryRepository
import org.recovery.steady.data.RecoveryState
import org.recovery.steady.data.GoalSettings
import org.recovery.steady.domain.SobrietyGoal
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object ReminderNotifications {
    const val CHANNEL_ID = "gentle_reminders"
    private const val WORK_NAME = "daily_gentle_reminder"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun schedule(context: Context, settings: ReminderSettings) {
        val manager = WorkManager.getInstance(context)
        if (!settings.enabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }

        val now = ZonedDateTime.now()
        var next = now.withHour(settings.hour).withMinute(settings.minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
            .build()
        manager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}

object GoalNotifications {
    const val CHANNEL_ID = "sobriety_milestones"
    private const val WORK_NAME = "next_sobriety_milestone"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sobriety milestones",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Gentle encouragement when a selected goal is reached"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun schedule(context: Context, recovery: RecoveryState, settings: GoalSettings) {
        val manager = WorkManager.getInstance(context)
        if (!settings.notificationsEnabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }

        val enabledGoals = SobrietyGoal.entries.filter { it.id in settings.enabledGoalIds }
        if (enabledGoals.isEmpty()) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }

        val elapsed = (System.currentTimeMillis() - recovery.startedAtMillis).coerceAtLeast(0L)
        val reached = enabledGoals.lastOrNull { elapsed >= it.durationMillis }
        val reachedAlreadyNotified = (reached != null) &&
            (settings.lastNotifiedStartMillis == recovery.startedAtMillis) &&
            (settings.lastNotifiedGoalId == reached.id)
        val delayMillis = if ((reached != null) && !reachedAlreadyNotified) {
            0L
        } else {
            val next = enabledGoals.firstOrNull { elapsed < it.durationMillis }
            if (next == null) {
                manager.cancelUniqueWork(WORK_NAME)
                return
            }
            (next.durationMillis - elapsed).coerceAtLeast(0L)
        }

        val request = OneTimeWorkRequestBuilder<GoalWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        manager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}

class GoalWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repository = RecoveryRepository(applicationContext)
        val recovery = repository.recoverySnapshot()
        val settings = repository.preferencesSnapshot().goals
        if (!settings.notificationsEnabled) return Result.success()
        if (!canPostNotifications(applicationContext)) return Result.success()

        val elapsed = (System.currentTimeMillis() - recovery.startedAtMillis).coerceAtLeast(0L)
        val reached = SobrietyGoal.entries
            .lastOrNull { (it.id in settings.enabledGoalIds) && (elapsed >= it.durationMillis) }
        if (reached == null) {
            GoalNotifications.schedule(applicationContext, recovery, settings)
            return Result.success()
        }

        val alreadyNotified = (settings.lastNotifiedStartMillis == recovery.startedAtMillis) &&
            (settings.lastNotifiedGoalId == reached.id)
        if (!alreadyNotified) {
            val intent = Intent(applicationContext, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                1,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val message = "You reached ${reached.label}. Keep moving forward, grace to grace."
            val notification = NotificationCompat.Builder(applicationContext, GoalNotifications.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle("A milestone reached")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(applicationContext).notify(2_000 + reached.ordinal, notification)
            repository.markGoalNotified(recovery.startedAtMillis, reached.id)
        }

        val latestSettings = repository.preferencesSnapshot().goals
        GoalNotifications.schedule(applicationContext, recovery, latestSettings)
        return Result.success()
    }
}

private fun canPostNotifications(context: Context): Boolean =
    (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) ||
        (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED)

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repository = RecoveryRepository(applicationContext)
        if (
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) &&
            (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED)
        ) {
            ReminderNotifications.schedule(applicationContext, repository.reminderSnapshot())
            return Result.success()
        }

        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val message = ENCOURAGEMENTS.random()
        val notification = NotificationCompat.Builder(applicationContext, ReminderNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle("A quiet moment is ready")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(1001, notification)
        ReminderNotifications.schedule(applicationContext, repository.reminderSnapshot())
        return Result.success()
    }

    companion object {
        private val ENCOURAGEMENTS = listOf(
            "Take one small step. You do not need to solve the whole day at once.",
            "Pause, breathe, and choose what helps you stay connected.",
            "Progress can be quiet. Showing up still matters.",
            "You can begin again without erasing what you have learned.",
        )
    }
}
