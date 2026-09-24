package com.example.appblocker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_apps")
data class BlockedApp(
    @PrimaryKey val pattern: String,
    val isKeyword: Boolean = false
)

@Entity(tableName = "allowed_apps")
data class AllowedApp(
    @PrimaryKey val packageName: String
)

@Entity(tableName = "settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val password: String? = null,
    val isLoggedIn: Boolean = false,
    val userEmail: String? = null,
    val isPremium: Boolean = false,
    val lastBackup: Long = 0,
    val theme: String = "system", // "light", "dark", "system"
    val isStrictModeEnabled: Boolean = false,
    val quickBlockEndTime: Long = 0,
    val startOnBoot: Boolean = true,
    val language: String = "en",
    val customBlockMessage: String = "This app is blocked for your focus.",
    val showMotivationalQuote: Boolean = true,
    val notifyBlockStarted: Boolean = true,
    val notifyBlockEnded: Boolean = true,
    val notifyDailySummary: Boolean = true,
    val notifyStreakAlerts: Boolean = true,
    val appLaunchPin: String? = null,
    val premiumExpiryDate: Long? = null,
    val isTrialPeriod: Boolean = false
)

@Entity(tableName = "internet_schedules")
data class InternetSchedule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "New Schedule",
    val startHour: Int = 0,
    val startMinute: Int = 0,
    val endHour: Int = 23,
    val endMinute: Int = 59,
    val days: Int = 127, // Default: Daily (all 7 days)
    val isEnabled: Boolean = true,
    val isStrict: Boolean = false,
    val apps: String = "",
    val keywords: String = "",
    val tagline: String? = null,
    val iconName: String? = null,
    val strictnessLevel: Int = 1,
    val locationTrigger: String? = null,
    val usageTriggerMinutes: Int = 0,
    val linkToBedtime: Boolean = false,
    val pauseUntil: Long = 0,
    val streakDays: Int = 0,
    val bypassCount: Int = 0,
    val lastRunDate: String? = null
) {
    fun isActive(): Boolean {
        if (!isEnabled) return false
        
        val now = java.util.Calendar.getInstance()
        if (now.timeInMillis < pauseUntil) return false
        
        val currentH = now.get(java.util.Calendar.HOUR_OF_DAY)
        val currentM = now.get(java.util.Calendar.MINUTE)
        val currentTime = currentH * 60 + currentM
        
        val startTime = (startHour.coerceIn(0, 23)) * 60 + (startMinute.coerceIn(0, 59))
        val endTime = (endHour.coerceIn(0, 23)) * 60 + (endMinute.coerceIn(0, 59))

        val dayOfWeek = now.get(java.util.Calendar.DAY_OF_WEEK)
        
        return if (startTime <= endTime) {
            val todayMask = getDayMask(dayOfWeek)
            (days and todayMask) != 0 && currentTime in startTime..endTime
        } else {
            // Over-midnight logic
            if (currentTime >= startTime) {
                // We are in the start-of-schedule part (e.g., 23:00 in a 22:00-02:00 schedule)
                val todayMask = getDayMask(dayOfWeek)
                (days and todayMask) != 0
            } else if (currentTime <= endTime) {
                // We are in the end-of-schedule part (e.g., 01:00 in a 22:00-02:00 schedule)
                // This counts if YESTERDAY was an active day.
                val yesterday = if (dayOfWeek == java.util.Calendar.SUNDAY) java.util.Calendar.SATURDAY else dayOfWeek - 1
                val yesterdayMask = getDayMask(yesterday)
                (days and yesterdayMask) != 0
            } else {
                false
            }
        }
    }

    private fun getDayMask(dayOfWeek: Int): Int {
        return when(dayOfWeek) {
            java.util.Calendar.SUNDAY -> 1
            java.util.Calendar.MONDAY -> 2
            java.util.Calendar.TUESDAY -> 4
            java.util.Calendar.WEDNESDAY -> 8
            java.util.Calendar.THURSDAY -> 16
            java.util.Calendar.FRIDAY -> 32
            java.util.Calendar.SATURDAY -> 64
            else -> 0
        }
    }
}

@Entity(tableName = "templates")
data class Template(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val subtitle: String,
    val icon: String,
    val timeLabel: String,
    val startHour: Int,
    val startMin: Int,
    val endHour: Int,
    val endMin: Int,
    val daysMask: Int,
    val defaultApps: String, // Comma separated package names
    val isCustom: Boolean,
    val sortOrder: Int
)

@Entity(tableName = "daily_focus_stats")
data class DailyFocusStats(
    @PrimaryKey val date: String, // Format: YYYY-MM-DD
    val unlockCount: Int = 0,
    val focusBlocksCount: Int = 0,
    val totalFocusTimeMillis: Long = 0,
    val focusScore: Int = 100,
    val lastYesterdayScore: Int = 100,
    val appsBlockedToday: Int = 0,
    val plannedMinutes: Int = 0,
    val protectedMinutes: Int = 0,
    val bypassCount: Int = 0
)

@Entity(tableName = "streaks")
data class Streak(
    @PrimaryKey val date: String,
    val isPerfect: Boolean,
    val streakCount: Int = 0
)

@Entity(tableName = "block_events")
data class BlockEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val date: String, // YYYY-MM-DD for easier querying
    val wasBlocked: Boolean = true
)

data class PackageBlockCount(
    val packageName: String,
    val count: Int
)
