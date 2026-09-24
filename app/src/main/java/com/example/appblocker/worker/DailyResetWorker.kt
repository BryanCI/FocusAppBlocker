package com.example.appblocker.worker

import android.content.Context
import androidx.work.*
import com.example.appblocker.data.AppDatabase
import java.util.*
import java.util.concurrent.TimeUnit

class DailyResetWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        database.dailyFocusStatsDao().clearOldBlockEvents(thirtyDaysAgo)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 1)
            calendar.set(Calendar.SECOND, 0)
            
            if (calendar.before(Calendar.getInstance())) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            val delay = calendar.timeInMillis - System.currentTimeMillis()

            val request = PeriodicWorkRequestBuilder<DailyResetWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "DailyResetWorker",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
