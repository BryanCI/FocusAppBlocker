package com.example.appblocker

import android.content.Context
import androidx.work.*
import com.example.appblocker.data.AppDatabase
import com.example.appblocker.data.DailyFocusStats
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class DailyResetWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.dailyFocusStatsDao()
        
        val today = getCurrentDateString()
        val yesterday = getYesterdayDateString()
        
        // Ensure today's entry exists
        val existingStats = dao.getStatsForDateList(today)
        if (existingStats == null) {
            val yesterdayStats = dao.getStatsForDateList(yesterday)
            val yesterdayScore = yesterdayStats?.focusScore ?: 100
            
            val newStats = DailyFocusStats(
                date = today,
                lastYesterdayScore = yesterdayScore
            )
            dao.insertStats(newStats)
        }
        
        return Result.success()
    }

    private fun getCurrentDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun getYesterdayDateString(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(cal.time)
    }

    companion object {
        private const val WORK_NAME = "DailyResetWorker"

        fun schedule(context: Context) {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 1)
            calendar.set(Calendar.SECOND, 0)
            
            val initialDelay = (calendar.timeInMillis - System.currentTimeMillis()).coerceAtLeast(0)

            val request = PeriodicWorkRequestBuilder<DailyResetWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
