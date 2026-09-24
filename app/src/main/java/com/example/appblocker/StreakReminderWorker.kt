package com.example.appblocker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.appblocker.data.AppDatabase
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

class StreakReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val masterEnabled = ThemeManager.getNotifMasterFlow(applicationContext).first()
        val focusEnabled = ThemeManager.getNotifFocusFlow(applicationContext).first()

        if (masterEnabled && focusEnabled) {
            val db = AppDatabase.getDatabase(applicationContext)
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            
            // Calculate current streak
            val allStreaks = db.streakDao().getAllStreaks().first()
                .sortedByDescending { it.date }
            
            var currentStreak = 0
            val calendar = Calendar.getInstance()
            
            for (streak in allStreaks) {
                val dateStr = sdf.format(calendar.time)
                if (streak.date == dateStr) {
                    if (streak.isPerfect) currentStreak++
                    calendar.add(Calendar.DAY_OF_YEAR, -1)
                } else {
                    break
                }
            }

            // Get today's focused minutes
            val todayStr = sdf.format(Date())
            val stats = db.dailyFocusStatsDao().getStatsForDateList(todayStr)
            val focusedMinutes = stats?.protectedMinutes ?: 0

            NotificationHelper.showStreakNotification(
                applicationContext,
                streak = currentStreak,
                focusedMinutes = focusedMinutes
            )
        }

        return Result.success()
    }
}
