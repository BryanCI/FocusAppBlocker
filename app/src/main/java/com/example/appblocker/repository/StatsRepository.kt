package com.example.appblocker.repository

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.example.appblocker.data.DailyFocusStats
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

enum class TimeRange { DAY, WEEK, MONTH, YEAR }

data class AppUsageEventData(
    val packageName: String,
    val totalTimeInForeground: Long,
    val appName: String = "",
    val category: AppCategory = AppCategory.NEUTRAL
)

enum class AppCategory { DISTRACTING, PRODUCTIVE, NEUTRAL }

data class AnalysisResult(
    val suggestions: List<String>,
    val focusScore: Int,
    val impacts: List<com.example.appblocker.ScoreImpact>
)

@Singleton
class StatsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val focusStatsDao: com.example.appblocker.data.DailyFocusStatsDao
) {
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun getTodayUsageFromEvents(): List<AppUsageEventData> {
        val calendar = Calendar.getInstance()
        val endTime = System.currentTimeMillis()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        return calculateUsageFromEvents(startTime, endTime)
    }

    fun getUsageForRange(range: TimeRange): List<AppUsageEventData> {
        val (startTime, endTime) = getTimeRange(range)
        return calculateUsageFromEvents(startTime, endTime)
    }

    private fun getTimeRange(range: TimeRange): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        val endTime = System.currentTimeMillis()
        
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        when (range) {
            TimeRange.DAY -> {
                // Already at 00:00 today
                return Pair(cal.timeInMillis, endTime)
            }
            TimeRange.WEEK -> {
                val dow = cal.get(Calendar.DAY_OF_WEEK)
                val diff = (dow - Calendar.MONDAY + 7) % 7
                cal.add(Calendar.DAY_OF_YEAR, -diff) // This Monday
                cal.add(Calendar.DAY_OF_YEAR, -7) // Last Monday
                val start = cal.timeInMillis
                
                cal.add(Calendar.DAY_OF_YEAR, 7) // End of Sunday / Start of this Monday
                val end = cal.timeInMillis
                return Pair(start, end)
            }
            TimeRange.MONTH -> {
                cal.add(Calendar.DAY_OF_YEAR, -29)
                return Pair(cal.timeInMillis, endTime)
            }
            TimeRange.YEAR -> {
                cal.add(Calendar.DAY_OF_YEAR, -364)
                return Pair(cal.timeInMillis, endTime)
            }
        }
    }

    fun getDailyUsageForWeek(): List<Long> {
        val lastMondayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            val dow = get(Calendar.DAY_OF_WEEK)
            val diff = (dow - Calendar.MONDAY + 7) % 7
            add(Calendar.DAY_OF_YEAR, -diff) // This Monday
            add(Calendar.DAY_OF_YEAR, -7) // Last Monday
        }
        
        val dailyTotals = mutableListOf<Long>()
        val tempCal = lastMondayCal.clone() as Calendar
        for (i in 0 until 7) {
            val start = tempCal.timeInMillis
            tempCal.add(Calendar.DAY_OF_YEAR, 1)
            val end = tempCal.timeInMillis
            
            val usage = calculateUsageFromEvents(start, end).sumOf { it.totalTimeInForeground }
            dailyTotals.add(usage)
        }
        return dailyTotals
    }

    fun getDailyUsageForMonth(): List<Long> {
        val cal = Calendar.getInstance()
        val endTime = System.currentTimeMillis()
        cal.add(Calendar.DAY_OF_YEAR, -29)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val dailyTotals = mutableListOf<Long>()
        for (i in 0 until 30) {
            val start = cal.timeInMillis
            val dayEndCal = cal.clone() as Calendar
            dayEndCal.add(Calendar.DAY_OF_YEAR, 1)
            val end = minOf(dayEndCal.timeInMillis, endTime)
            
            if (start < endTime) {
                val usage = calculateUsageFromEvents(start, end).sumOf { it.totalTimeInForeground }
                dailyTotals.add(usage)
            } else {
                dailyTotals.add(0L)
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return dailyTotals
    }

    fun getMonthlyUsageForYear(): List<Long> {
        val cal = Calendar.getInstance()
        val endTime = System.currentTimeMillis()
        
        // Go back 11 months from current month
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.MONTH, -11)

        val monthlyTotals = mutableListOf<Long>()
        for (i in 0 until 12) {
            val start = cal.timeInMillis
            val nextMonthCal = cal.clone() as Calendar
            nextMonthCal.add(Calendar.MONTH, 1)
            val end = minOf(nextMonthCal.timeInMillis, endTime)
            
            if (start < endTime) {
                val usage = calculateUsageFromEvents(start, end).sumOf { it.totalTimeInForeground }
                monthlyTotals.add(usage)
            } else {
                monthlyTotals.add(0L)
            }
            cal.add(Calendar.MONTH, 1)
        }
        return monthlyTotals
    }

    private val EXCLUDED_PACKAGES = listOf(
        "com.android.systemui",
        "com.android.launcher",
        "com.google.android.inputmethod",
        "com.android.settings",
        "com.google.android.apps.nexuslauncher",
        "com.transsion.hilauncher",
        "com.transsion.phonemanager",
        context.packageName
    )

    fun isValidForStats(packageName: String): Boolean {
        if (packageName in EXCLUDED_PACKAGES) return false
        if (packageName.contains("launcher", true)) return false
        // Basic check for system apps that don't have a launch intent
        return try {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (e: Exception) {
            false
        }
    }

    private fun calculateUsageFromEvents(startTime: Long, endTime: Long): List<AppUsageEventData> {
        // Use queryAndAggregateUsageStats for higher accuracy in total time reporting
        val statsMap = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        
        return statsMap.values
            .filter { isValidForStats(it.packageName) }
            .map { usageStats ->
                val pkg = usageStats.packageName
                val duration = usageStats.totalTimeInForeground
                val category = when {
                    isDistractingApp(pkg) -> AppCategory.DISTRACTING
                    isProductiveApp(pkg) -> AppCategory.PRODUCTIVE
                    else -> AppCategory.NEUTRAL
                }
                AppUsageEventData(pkg, duration, getAppLabel(pkg), category)
            }
            .filter { it.totalTimeInForeground > 1000 } // Minimum 1 second for raw data
            .sortedByDescending { it.totalTimeInForeground }
    }

    fun getBlockedAppsToday(): Flow<Map<String, Int>> {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return focusStatsDao.getBlockCountsForDate(today).map { list ->
            list.associate { 
                val label = getAppLabel(it.packageName)
                label to it.count
            }
        }
    }

    fun analyzeUsage(stats: DailyFocusStats?, yesterdayStats: DailyFocusStats?, usage: List<AppUsageEventData>): AnalysisResult {
        val suggestions = mutableListOf<String>()
        val impacts = mutableListOf<com.example.appblocker.ScoreImpact>()

        // Base 100
        impacts.add(com.example.appblocker.ScoreImpact("Base Score", 100, true))

        var score = 100

        if (stats != null) {
            // Formula: (Protected Minutes / Planned Minutes * 100) - (Bypass Count * 10)
            if (stats.plannedMinutes > 0) {
                val baseFocusScore = (stats.protectedMinutes.toFloat() / stats.plannedMinutes * 100).toInt()
                val diff = 100 - baseFocusScore
                if (diff > 0) {
                    score -= diff
                    impacts.add(com.example.appblocker.ScoreImpact("Focus missed", -diff, false))
                }
            }

            if (stats.bypassCount > 0) {
                val bypassPenalty = stats.bypassCount * 10
                score -= bypassPenalty
                impacts.add(com.example.appblocker.ScoreImpact("${stats.bypassCount} Bypasses", -bypassPenalty, false))
                suggestions.add("You bypassed your blocks ${stats.bypassCount} times. Try to stay committed!")
            }
        }

        val totalScreenTime = usage.sumOf { it.totalTimeInForeground }
        val socialMediaTime = usage.filter { isDistractingApp(it.packageName) }.sumOf { it.totalTimeInForeground }

        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        if (hour >= 23 || hour < 6) {
            score -= 10
            impacts.add(com.example.appblocker.ScoreImpact("Late night usage", -10, false))
            suggestions.add("Usage detected after 11 PM. Try setting a 'Bedtime Block' to improve sleep.")
        }

        if (stats != null) {
            if (stats.unlockCount > 30) {
                val overLimit = stats.unlockCount - 30
                val penalty = (overLimit * 1.5).toInt().coerceAtMost(30)
                score -= penalty
                impacts.add(com.example.appblocker.ScoreImpact("${stats.unlockCount} unlocks", -penalty, false))
            } else if (stats.unlockCount < 15 && stats.unlockCount > 0) {
                score += 10
                impacts.add(com.example.appblocker.ScoreImpact("Low unlock frequency", 10, true))
            }
        }

        if (socialMediaTime > 2 * 3600000) {
            score -= 15
            impacts.add(com.example.appblocker.ScoreImpact("Heavy social media", -15, false))
        }

        if (suggestions.isEmpty()) {
            if (score >= 80) {
                suggestions.add("Your focus is looking good today! Keep it up.")
            } else {
                suggestions.add("Try to reduce your screen time to improve your focus score.")
            }
        }

        return AnalysisResult(suggestions, score.coerceIn(0, 100), impacts)
    }

    fun isDistractingApp(packageName: String): Boolean {
        val distractingKeywords = listOf("whatsapp", "facebook", "instagram", "tiktok", "twitter", "youtube", "snapchat", "reddit", "netflix")
        return distractingKeywords.any { packageName.lowercase().contains(it) }
    }

    fun isProductiveApp(packageName: String): Boolean {
        val productiveKeywords = listOf("notion", "evernote", "calendar", "keep", "docs", "sheets", "slack", "trello", "todoist", "obsidian")
        return productiveKeywords.any { packageName.lowercase().contains(it) }
    }

    data class HourlyUsage(val hour: Int, val durationMillis: Long)

    fun getTodayHourlyUsage(): List<HourlyUsage> {
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val hourlyMap = LongArray(24)
        val lastResumeMap = mutableMapOf<String, Long>()
        val event = UsageEvents.Event()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val timestamp = event.timeStamp

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    lastResumeMap[pkg] = timestamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val resumedTime = lastResumeMap[pkg]
                    if (resumedTime != null) {
                        val duration = timestamp - resumedTime
                        if (duration > 0) {
                            addUsageToHourlyMap(hourlyMap, resumedTime, timestamp)
                        }
                        lastResumeMap.remove(pkg)
                    }
                }
            }
        }

        // Still open apps
        lastResumeMap.forEach { (pkg, resumedTime) ->
            addUsageToHourlyMap(hourlyMap, resumedTime, endTime)
        }

        return hourlyMap.mapIndexed { index, duration -> HourlyUsage(index, duration) }
    }

    private fun addUsageToHourlyMap(hourlyMap: LongArray, start: Long, end: Long) {
        val cal = Calendar.getInstance()
        var current = start
        while (current < end) {
            cal.timeInMillis = current
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.HOUR_OF_DAY, 1)
            val nextHourStart = cal.timeInMillis
            
            val durationInThisHour = minOf(end, nextHourStart) - current
            if (hour in 0..23) {
                hourlyMap[hour] += durationInThisHour
            }
            current = nextHourStart
        }
    }

    data class FocusMetrics(val longestFocusMillis: Long, val longestContinuousUseMillis: Long)

    fun getTodayFocusMetrics(): FocusMetrics {
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val sessions = mutableListOf<Pair<Long, Long>>()
        val lastResumeMap = mutableMapOf<String, Long>()
        val event = UsageEvents.Event()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val timestamp = event.timeStamp

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    lastResumeMap[event.packageName] = timestamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val resumedTime = lastResumeMap.remove(event.packageName)
                    if (resumedTime != null) {
                        sessions.add(resumedTime to timestamp)
                    }
                }
            }
        }
        
        lastResumeMap.forEach { (_, resumedTime) ->
            sessions.add(resumedTime to endTime)
        }

        if (sessions.isEmpty()) return FocusMetrics(0, 0)

        // Merge overlapping sessions to find continuous phone use
        val sortedSessions = sessions.sortedBy { it.first }
        val mergedSessions = mutableListOf<Pair<Long, Long>>()
        if (sortedSessions.isNotEmpty()) {
            var currentStart = sortedSessions[0].first
            var currentEnd = sortedSessions[0].second
            for (i in 1 until sortedSessions.size) {
                if (sortedSessions[i].first <= currentEnd) {
                    currentEnd = maxOf(currentEnd, sortedSessions[i].second)
                } else {
                    mergedSessions.add(currentStart to currentEnd)
                    currentStart = sortedSessions[i].first
                    currentEnd = sortedSessions[i].second
                }
            }
            mergedSessions.add(currentStart to currentEnd)
        }

        val longestContinuousUse = mergedSessions.maxOfOrNull { it.second - it.first } ?: 0L

        // Gaps between merged sessions are focus periods
        var longestFocus = 0L
        for (i in 0 until mergedSessions.size - 1) {
            val focusDuration = mergedSessions[i+1].first - mergedSessions[i].second
            if (focusDuration > longestFocus) longestFocus = focusDuration
        }
        
        // Also consider gap from start of day to first session and last session to now
        if (mergedSessions.isNotEmpty()) {
            longestFocus = maxOf(longestFocus, mergedSessions[0].first - startTime)
            longestFocus = maxOf(longestFocus, endTime - mergedSessions.last().second)
        } else {
            longestFocus = endTime - startTime
        }

        return FocusMetrics(longestFocus, longestContinuousUse)
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}
