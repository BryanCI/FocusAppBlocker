package com.example.appblocker.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InsightsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val statsRepository: StatsRepository
) {

    fun getTodayAppUsage(): List<AppUsageModel> {
        val eventData = statsRepository.getTodayUsageFromEvents()

        return eventData.map { data ->
            AppUsageModel(
                packageName = data.packageName,
                appName = getAppName(data.packageName),
                timeSpentMillis = data.totalTimeInForeground
            )
        }
        .filter { it.timeSpentMillis > 0 }
        .sortedByDescending { it.timeSpentMillis }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}

data class AppUsageModel(
    val packageName: String,
    val appName: String,
    val timeSpentMillis: Long
)
