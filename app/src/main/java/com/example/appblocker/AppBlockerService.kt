package com.example.appblocker

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.app.ActivityManager
import android.app.Application.ActivityLifecycleCallbacks
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.core.app.NotificationCompat
import com.example.appblocker.data.AllowedApp
import com.example.appblocker.data.AppDatabase
import com.example.appblocker.data.InternetSchedule
import com.example.appblocker.data.BlockedApp
import com.example.appblocker.data.DailyFocusStats
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

class AppBlockerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var monitoringJob: Job? = null

    private var cachedBlockedApps = listOf<BlockedApp>()
    private var cachedAllowedApps = listOf<AllowedApp>()
    private var cachedSchedules = listOf<InternetSchedule>()
    private var cachedPassword = "1234"
    private var isStrictModeEnabled = false
    private var cachedQuickBlockEndTime = 0L
    private var cachedIsPremium = false
    private var cachedIsTrialPeriod = false
    private var cachedPremiumExpiryDate: Long? = null
    private val internetPermissionCache = mutableMapOf<String, Boolean>()
    
    private var lastOurAppCheck = 0L
    private var cachedOurAppInForeground = false
    private var lastWhitelistRefresh = 0L

    private var lastForegroundApp: String? = null
    private var lastEventTime = System.currentTimeMillis() - 10000
    private var isScreenOn = true
    private var screenOffStartTime = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val now = System.currentTimeMillis()
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    screenOffStartTime = now
                }
                Intent.ACTION_USER_PRESENT -> {
                    lastUnlockTime = now
                    updateUnlockCount()
                    checkFocusBlock(now)
                }
                Intent.ACTION_TIMEZONE_CHANGED -> {
                    scheduleNextAlarm(applicationContext)
                }
                "android.intent.action.TIME_SET" -> {
                    scheduleNextAlarm(applicationContext)
                }
            }
        }
    }

    private fun updateUnlockCount() {
        serviceScope.launch {
            val date = getCurrentDateString()
            val dao = AppDatabase.getDatabase(applicationContext).dailyFocusStatsDao()
            val stats = dao.getStatsForDateList(date) ?: DailyFocusStats(date)
            dao.insertStats(stats.copy(unlockCount = stats.unlockCount + 1))
        }
    }

    private fun checkFocusBlock(now: Long) {
        if (screenOffStartTime > 0) {
            val durationMin = (now - screenOffStartTime) / 60000
            if (durationMin >= 25) {
                serviceScope.launch {
                    val date = getCurrentDateString()
                    val dao = AppDatabase.getDatabase(applicationContext).dailyFocusStatsDao()
                    val stats = dao.getStatsForDateList(date) ?: DailyFocusStats(date)
                    dao.insertStats(stats.copy(
                        focusBlocksCount = stats.focusBlocksCount + 1,
                        totalFocusTimeMillis = stats.totalFocusTimeMillis + (now - screenOffStartTime)
                    ))
                }
            }
            screenOffStartTime = 0
        }
    }

    private fun getCurrentDateString(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    private val lifecycleCallbacks = object : ActivityLifecycleCallbacks {
        private var startedActivities = 0
        override fun onActivityStarted(activity: Activity) {
            startedActivities++
            isAppVisible = true
        }
        override fun onActivityStopped(activity: Activity) {
            startedActivities--
            if (startedActivities <= 0) isAppVisible = false
        }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    override fun onCreate() {
        super.onCreate()
        BlockerWorker.scheduleKeepAlive(applicationContext)
        (applicationContext as Application).registerActivityLifecycleCallbacks(lifecycleCallbacks)
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction("android.intent.action.TIME_SET")
        }
        registerReceiver(screenReceiver, filter)
        
        // Initialize screen state
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        isScreenOn = powerManager.isInteractive

        createNotificationChannels()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("App Blocker is running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(1, notification)
        observeDatabase()
        startMonitoring()
    }

    private fun observeDatabase() {
        serviceScope.launch {
            val database = AppDatabase.getDatabase(applicationContext)
            launch {
                database.blockedAppDao().getAllBlockedApps().collect {
                    cachedBlockedApps = it
                }
            }
            launch {
                database.allowedAppDao().getAllAllowedApps().collect {
                    cachedAllowedApps = it
                }
            }
            launch {
                database.internetScheduleDao().getAllSchedules().collect {
                    cachedSchedules = it
                    scheduleNextAlarm(applicationContext)
                }
            }
            launch {
                database.settingsDao().getSettings().collect {
                    cachedPassword = it?.password ?: "1234"
                    isStrictModeEnabled = it?.isStrictModeEnabled ?: false
                    cachedQuickBlockEndTime = it?.quickBlockEndTime ?: 0L
                    cachedIsPremium = it?.isPremium ?: false
                    cachedIsTrialPeriod = it?.isTrialPeriod ?: false
                    cachedPremiumExpiryDate = it?.premiumExpiryDate
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        BlockerWorker.scheduleKeepAlive(applicationContext)
        scheduleNextAlarm(this)
        checkUpcomingSchedules()
        if (monitoringJob == null || !monitoringJob!!.isActive) {
            startMonitoring()
        }
        return START_STICKY
    }

    private fun checkUpcomingSchedules() {
        val now = System.currentTimeMillis()
        val warningLeadTime = 5 * 60 * 1000L
        val margin = 60 * 1000L // 1 minute margin to catch it

        serviceScope.launch {
            val database = AppDatabase.getDatabase(applicationContext)
            val schedules = database.internetScheduleDao().getAllSchedulesList()
            
            for (schedule in schedules) {
                if (!schedule.isEnabled) continue
                
                val startTime = getTriggerTime(schedule.startHour, schedule.startMinute, 0)
                val diff = startTime - now
                
                if (diff in (warningLeadTime - margin)..warningLeadTime) {
                    showWarningNotification(schedule)
                }
            }
        }
    }

    private fun showWarningNotification(schedule: InternetSchedule) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, WARNING_CHANNEL_ID)
            .setContentTitle("Schedule Starting Soon")
            .setContentText("The schedule '${schedule.name}' will start in 5 minutes.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(schedule.id + 1000, notification)
    }

    private fun startMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = serviceScope.launch {
            val database = AppDatabase.getDatabase(applicationContext)
            
            // Critical: Warm the cache immediately
            try {
                cachedBlockedApps = database.blockedAppDao().getBlockedAppsList()
                cachedAllowedApps = database.allowedAppDao().getAllAllowedAppsList()
                cachedSchedules = database.internetScheduleDao().getAllSchedulesList()
                val settings = database.settingsDao().getSettingsList().firstOrNull()
                cachedPassword = settings?.password ?: "1234"
                isStrictModeEnabled = settings?.isStrictModeEnabled ?: false
                cachedQuickBlockEndTime = settings?.quickBlockEndTime ?: 0L
                cachedIsPremium = settings?.isPremium ?: false
                cachedIsTrialPeriod = settings?.isTrialPeriod ?: false
                cachedPremiumExpiryDate = settings?.premiumExpiryDate
            } catch (e: Exception) {}

            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            
            while (isActive) {
                try {
                    val now = System.currentTimeMillis()
                    
                    if (!isScreenOn) {
                        delay(1000)
                        continue
                    }

                    // Priority 1: Check if we are in our own app (Quickest check first)
                    if (isAppVisible) {
                        delay(250)
                        continue
                    }

                    // Priority 2: Grace period
                    if (now - lastUnlockTime < 1500 || now - lastSystemUiTime < 1000) {
                        delay(100)
                        continue
                    }

                    // Priority 3: Check process state (less frequent)
                    if (now - lastOurAppCheck > 1000) {
                        cachedOurAppInForeground = isOurAppInForeground()
                        lastOurAppCheck = now
                    }
                    if (cachedOurAppInForeground) {
                        delay(250)
                        continue
                    }

                    // Priority 4: Get foreground app
                    val currentApp = getForegroundApp(usageStatsManager)
                    
                    if (currentApp == null || currentApp == packageName || isWhitelistedApp(currentApp)) {
                        delay(100)
                        continue
                    }
                    
                    var shouldBlock = false

                    val hasPremiumAccess = cachedIsPremium || (cachedIsTrialPeriod && (cachedPremiumExpiryDate == null || now < cachedPremiumExpiryDate!!))
                    val isQuickBlockActive = FocusSessionManager.isSessionActive(this@AppBlockerService)
                    
                    // Limit schedules for non-premium users
                    val applicableSchedules = if (hasPremiumAccess) cachedSchedules else cachedSchedules.take(3)
                    val activeSchedule = applicableSchedules.find { it.isActive() }
                    val isAnySessionActive = isQuickBlockActive || activeSchedule != null

                    // Priority 5: Strict Mode (Allowlist-only policy)
                    if (isStrictModeEnabled && isAnySessionActive) {
                        if (cachedAllowedApps.none { it.packageName == currentApp }) {
                            shouldBlock = true
                        }
                    }

                    // Priority 5.5: Regular Mode or specific app blocks during sessions
                    if (!shouldBlock && isAnySessionActive) {
                        // Check explicit BlockedApp list
                        val isExplicitlyBlocked = cachedBlockedApps.any { blocked ->
                            if (blocked.isKeyword) {
                                currentApp.contains(blocked.pattern, ignoreCase = true)
                            } else {
                                currentApp == blocked.pattern
                            }
                        }
                        if (isExplicitlyBlocked) {
                            shouldBlock = true
                        }

                        // Check schedule-specific apps
                        if (!shouldBlock && activeSchedule != null && activeSchedule.apps.isNotEmpty()) {
                            val scheduleApps = activeSchedule.apps.split(",").map { it.trim() }
                            if (scheduleApps.contains(currentApp)) {
                                shouldBlock = true
                            }
                        }
                    }

                    // Priority 6: Explicit blocks outside of active sessions
                    if (!shouldBlock && cachedBlockedApps.isNotEmpty()) {
                        shouldBlock = cachedBlockedApps.any { blocked ->
                            if (blocked.isKeyword) {
                                currentApp.contains(blocked.pattern, ignoreCase = true)
                            } else {
                                currentApp == blocked.pattern
                            }
                        }
                    }

                    if (shouldBlock) {
                        launchLockScreen(currentApp)
                        serviceScope.launch {
                            if (ThemeManager.getNotifBlockFlow(applicationContext).first()) {
                                NotificationHelper.showBlockAlert(applicationContext, currentApp)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Prevent loop from dying
                }
                
                delay(40) // Even snappier
            }
        }
    }

    private fun isOurAppInForeground(): Boolean {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val appProcesses = activityManager.runningAppProcesses ?: return false
            appProcesses.any { 
                it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && 
                it.processName == packageName 
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun isTimeInBlockWindow(startH: Int, startM: Int, endH: Int, endM: Int, blockDays: Int): Boolean {
        if (startH == -1 || endH == -1) return false
        val now = Calendar.getInstance()
        
        // Check day of week
        val dayOfWeek = now.get(Calendar.DAY_OF_WEEK) // Sun=1, Mon=2...
        val dayMask = 1 shl (dayOfWeek - 1)
        if ((blockDays and dayMask) == 0) return false

        val currentH = now.get(Calendar.HOUR_OF_DAY)
        val currentM = now.get(Calendar.MINUTE)

        val currentTime = currentH * 60 + currentM
        val startTime = startH * 60 + startM
        val endTime = endH * 60 + endM

        return if (startTime <= endTime) {
            currentTime in startTime..endTime
        } else {
            // Overlays midnight (e.g., 22:00 to 06:00)
            currentTime >= startTime || currentTime <= endTime
        }
    }

    private fun hasInternetPermissionCached(packageName: String): Boolean {
        return internetPermissionCache.getOrPut(packageName) {
            try {
                packageManager.checkPermission(
                    android.Manifest.permission.INTERNET,
                    packageName
                ) == PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun isSettingsApp(packageName: String): Boolean {
        val lower = packageName.lowercase()
        return lower.contains("settings") || 
               lower.contains("permissioncontroller") ||
               lower.contains("packageinstaller") ||
               lower.contains("com.android.vending") // Block Play Store to prevent uninstall/bypass
    }

    private fun isWhitelistedApp(packageName: String): Boolean {
        if (packageName == this.packageName) return true
        val normalizedPackage = packageName.lowercase().trim()
        
        // Critical system components that should NEVER be blocked
        val criticalWhitelist = listOf(
            "android",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.incallui",
            "com.google.android.dialer",
            "com.android.systemui",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.google.android.gms",
            "com.android.settings",
            "com.google.android.settings",
            "com.android.contacts",
            "com.google.android.contacts",
            "com.android.messaging",
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.samsung.android.messaging",
            "com.samsung.android.contacts",
            "com.samsung.android.dialer",
            "com.whatsapp",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.touchtype.swiftkey",
            "com.microsoft.emojikeyboard"
        )
        
        if (criticalWhitelist.contains(normalizedPackage)) {
            if (normalizedPackage == "com.android.systemui" || normalizedPackage == "android") {
                lastSystemUiTime = System.currentTimeMillis()
            }
            return true
        }
        
        // Refresh whitelist cache every 30 seconds
        val now = System.currentTimeMillis()
        if (now - lastWhitelistRefresh > 30000) {
            try {
                val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                cachedDefaultDialer = telecomManager.defaultDialerPackage?.lowercase()
                
                val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
                val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                cachedLauncherPackage = resolveInfo?.activityInfo?.packageName?.lowercase()
                
                lastWhitelistRefresh = now
            } catch (e: Exception) {}
        }

        val essentialWhitelist = listOf(
            "com.google.android.googlequicksearchbox"
        )
        
        return normalizedPackage == cachedDefaultDialer || 
               (normalizedPackage == cachedLauncherPackage && !normalizedPackage.contains("settings")) ||
               essentialWhitelist.any { it.lowercase().trim() == normalizedPackage }
    }

    private fun getForegroundApp(usageStatsManager: UsageStatsManager): String? {
        val now = System.currentTimeMillis()
        // Ensure we don't query too far back if lastEventTime got stale (e.g. after sleep)
        val startTime = if (now - lastEventTime > 30000) now - 30000 else lastEventTime
        val events = usageStatsManager.queryEvents(startTime, now)
        val event = UsageEvents.Event()
        
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastForegroundApp = event.packageName
                lastEventTime = event.timeStamp
            }
        }
        
        // Fallback if we haven't detected anything yet or if events are cleared
        if (lastForegroundApp == null) {
            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 1000 * 60, now)
            lastForegroundApp = stats?.maxByOrNull { it.lastTimeUsed }?.packageName
            lastEventTime = now
        }
        
        return lastForegroundApp
    }

    private fun launchLockScreen(blockedPackage: String) {
        incrementBlockedAppsCount()
        val intent = Intent(this, LockOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            putExtra("BLOCKED_PACKAGE", blockedPackage)
            putExtra("CORRECT_PIN", cachedPassword)
            putExtra("STRICT_MODE", isStrictModeEnabled)
        }
        startActivity(intent)
    }

    private fun incrementBlockedAppsCount() {
        serviceScope.launch {
            val date = getCurrentDateString()
            val dao = AppDatabase.getDatabase(applicationContext).dailyFocusStatsDao()
            val stats = dao.getStatsForDateList(date) ?: DailyFocusStats(date)
            dao.insertStats(stats.copy(appsBlockedToday = stats.appsBlockedToday + 1))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Clear foreground notification
        stopForeground(STOP_FOREGROUND_REMOVE)

        super.onDestroy()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {}
        try {
            (applicationContext as Application).unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
        } catch (e: Exception) {}
        serviceScope.cancel()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "App Blocker Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            
            val warningChannel = NotificationChannel(
                WARNING_CHANNEL_ID,
                "Schedule Warnings",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications to warn you before a schedule starts"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(warningChannel)
        }
    }

    companion object {
        const val CHANNEL_ID = "AppBlockerServiceChannel"
        const val WARNING_CHANNEL_ID = "AppBlockerWarningChannel"
        @Volatile var isAppVisible = false
        @Volatile var lastUnlockTime = 0L
        @Volatile var lastSystemUiTime = 0L
        @Volatile var cachedLauncherPackage: String? = null
        @Volatile var cachedDefaultDialer: String? = null

        fun scheduleNextAlarm(context: Context) {
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                val db = AppDatabase.getDatabase(context)
                val schedules = db.internetScheduleDao().getAllSchedulesList()
                val nextTime = calculateNextTriggerTime(schedules) ?: return@launch

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, ScheduleReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Build.VERSION.SDK_INT >= 31) {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pendingIntent)
                        }
                    } else {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pendingIntent)
                    }
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextTime, pendingIntent)
                }
            }
        }

        private fun calculateNextTriggerTime(schedules: List<InternetSchedule>): Long? {
            if (schedules.isEmpty()) return null
            val now = Calendar.getInstance()
            val potentialTriggers = mutableListOf<Long>()
            val warningLeadTime = 5 * 60 * 1000L

            for (schedule in schedules) {
                if (!schedule.isEnabled) continue
                
                val startToday = getTriggerTime(schedule.startHour, schedule.startMinute, 0)
                val endToday = getTriggerTime(schedule.endHour, schedule.endMinute, 0)
                
                potentialTriggers.add(startToday)
                potentialTriggers.add(startToday - warningLeadTime)
                potentialTriggers.add(endToday)
                
                val startTomorrow = getTriggerTime(schedule.startHour, schedule.startMinute, 1)
                val endTomorrow = getTriggerTime(schedule.endHour, schedule.endMinute, 1)
                
                potentialTriggers.add(startTomorrow)
                potentialTriggers.add(startTomorrow - warningLeadTime)
                potentialTriggers.add(endTomorrow)
            }

            return potentialTriggers
                .filter { it > now.timeInMillis }
                .minOrNull()
        }

        private fun getTriggerTime(hour: Int, minute: Int, daysOffset: Int): Long {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, daysOffset)
            }
            return calendar.timeInMillis
        }
    }
}
