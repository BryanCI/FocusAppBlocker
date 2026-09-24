package com.example.appblocker

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.flow.first
import java.util.*
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    fun scheduleFocusReminder(context: Context, timeStr: String) {
        try {
            val appContext = context.applicationContext
            val (hour, minute) = timeStr.split(":").map { it.toInt() }
            val now = Calendar.getInstance()
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            if (calendar.before(now)) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            
            val delay = calendar.timeInMillis - now.timeInMillis

            val request = PeriodicWorkRequestBuilder<StreakReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag("focus_reminder")
                .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                "focus_reminder",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } catch (e: Exception) {
            android.util.Log.e("NotificationScheduler", "Failed to schedule focus reminder", e)
        }
    }

    fun cancelFocusReminder(context: Context) {
        try {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork("focus_reminder")
        } catch (e: Exception) {
            android.util.Log.e("NotificationScheduler", "Failed to cancel focus reminder", e)
        }
    }

    fun scheduleMorningSummary(context: Context, timeStr: String) {
        try {
            val appContext = context.applicationContext
            val (hour, minute) = timeStr.split(":").map { it.toInt() }
            val now = Calendar.getInstance()
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            if (calendar.before(now)) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            
            val delay = calendar.timeInMillis - now.timeInMillis

            val request = PeriodicWorkRequestBuilder<MorningSummaryWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag("morning_summary")
                .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                "morning_summary",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } catch (e: Exception) {
            android.util.Log.e("NotificationScheduler", "Failed to schedule morning summary", e)
        }
    }

    fun cancelMorningSummary(context: Context) {
        try {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork("morning_summary")
        } catch (e: Exception) {
            android.util.Log.e("NotificationScheduler", "Failed to cancel morning summary", e)
        }
    }
}
