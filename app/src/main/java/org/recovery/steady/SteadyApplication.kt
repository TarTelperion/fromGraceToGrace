package org.recovery.steady

import android.app.Application
import org.recovery.steady.notifications.ReminderNotifications
import org.recovery.steady.notifications.GoalNotifications
import org.recovery.steady.content.DailyScriptureSync

class SteadyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.createChannel(this)
        GoalNotifications.createChannel(this)
        DailyScriptureSync.schedule(this)
    }
}
