package com.example.appblocker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.appblocker.data.AppDatabase
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

class MorningSummaryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val masterEnabled = ThemeManager.getNotifMasterFlow(applicationContext).first()
        val summaryEnabled = ThemeManager.getNotifSummaryFlow(applicationContext).first()

        if (masterEnabled && summaryEnabled) {
            val db = AppDatabase.getDatabase(applicationContext)
            
            // Get yesterday's date string
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val yesterdayStr = sdf.format(calendar.time)
            
            val stats = db.dailyFocusStatsDao().getStatsForDateList(yesterdayStr)
            val blockCount = db.dailyFocusStatsDao().getBlockCountForDateSync(yesterdayStr)
            val streak = db.streakDao().getCurrentStreak() ?: 0
            
            val minutes = stats?.protectedMinutes ?: 0
            val score = stats?.focusScore ?: 75

            NotificationHelper.showMorningSummary(
                applicationContext,
                minutes,
                blockCount,
                score,
                streak
            )
        }

        return Result.success()
    }
}
