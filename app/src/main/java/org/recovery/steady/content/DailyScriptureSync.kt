package org.recovery.steady.content

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.recovery.steady.data.RecoveryRepository
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object DailyScriptureSync {
    private const val WORK_NAME = "daily_scripture_refresh"

    fun schedule(context: Context) {
        val now = ZonedDateTime.now()
        val nextRefresh = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusMinutes(2)
        val initialDelay = Duration.between(now, nextRefresh).toMinutes().coerceAtLeast(1)
        val request = PeriodicWorkRequestBuilder<DailyScriptureWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}

class DailyScriptureWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        RecoveryRepository(applicationContext).refreshDailyScriptureIfNeeded()
        return Result.success()
    }
}
