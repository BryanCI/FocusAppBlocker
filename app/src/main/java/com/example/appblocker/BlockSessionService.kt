package com.example.appblocker

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.appblocker.data.AppDatabase
import kotlinx.coroutines.*
import java.util.Locale

class BlockSessionService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null

    companion object {
        const val CHANNEL_ID = "BlockSessionServiceChannel"
        const val NOTIFICATION_ID = 2025
        const val PREFS_NAME = "block_prefs"
        const val KEY_SESSION_END_TIME = "session_end_time"

        const val ACTION_START = "com.example.appblocker.ACTION_START"
        const val ACTION_STOP = "com.example.appblocker.ACTION_STOP"
        const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSession()
            return START_NOT_STICKY
        }
        
        // Start foreground immediately with pre-built notification to avoid lag
        startForeground(NOTIFICATION_ID, buildNotification("Focus session active"))

        if (intent != null) {
            when (intent.action) {
                ACTION_START -> {
                    val durationMinutes = intent.getIntExtra(EXTRA_DURATION_MINUTES, 15)
                    startSession(durationMinutes)
                }
            }
        }
        return START_STICKY
    }

    private fun startSession(durationMinutes: Int) {
        FocusSessionManager.startSession(this, durationMinutes)
        val now = System.currentTimeMillis()
        val sessionEndTime = if (durationMinutes == -1) {
            -1L
        } else {
            now + (durationMinutes.toLong() * 60 * 1000)
        }

        // 2. Save to Room DB settings so that existing blocking logic kicks in
        serviceScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            db.settingsDao().updateQuickBlockEndTime(if (durationMinutes == -1) Long.MAX_VALUE else sessionEndTime)
        }

        // Send START_BLOCKING broadcast to accessibility service
        try {
            val startIntent = Intent("com.example.appblocker.START_BLOCKING")
            startIntent.setPackage(this.packageName)
            sendBroadcast(startIntent)
        } catch (e: Exception) {
            Log.e("BLOCKER", "START_BLOCKING Broadcast fail", e)
        }

        // 3. start countdown update loop
        startTimerLoop(sessionEndTime)
    }

    private fun startTimerLoop(sessionEndTime: Long) {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                if (sessionEndTime == -1L) {
                    // Infinite Focus
                    updateNotification("Focusing: Infinite Focus")
                } else {
                    val remainingMillis = sessionEndTime - now
                    if (remainingMillis <= 0) {
                        stopSession()
                        break
                    }
                    val totalSecs = remainingMillis / 1000
                    val hours = totalSecs / 3600
                    val mins = (totalSecs % 3600) / 60
                    val secs = totalSecs % 60

                    val timeStr = if (hours > 0) {
                        String.format(java.util.Locale.US, "%02d:%02d:%02d left", hours, mins, secs)
                    } else {
                        String.format(java.util.Locale.US, "%02d:%02d left", mins, secs)
                    }
                    updateNotification("Focusing: $timeStr")
                }
                delay(1000)
            }
        }
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Focus Mode")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_focus_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun stopSession() {
        val totalMillis = FocusSessionManager.getTotalSessionMillis(this)
        timerJob?.cancel()

        serviceScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            // Capture count before clearing
            val blockedCount = try { db.blockedAppDao().getBlockedAppsList().size } catch(e: Exception) { 1 }

            withContext(Dispatchers.Main) {
                // ALWAYS show celebration notification
                NotificationHelper.showSessionEnded(this@BlockSessionService, blockedCount, totalMillis)
            }

            // 2. Clear Room settings so app blockers stop
            db.settingsDao().updateQuickBlockEndTime(0)

            withContext(Dispatchers.Main) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                // FocusSessionManager.endSession MUST be last to ensure cache integrity during notification
                FocusSessionManager.endSession(this@BlockSessionService)
                stopSelf()
            }
        }
    }

    private fun isAppInForeground(): Boolean {
        val appProcess = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(appProcess)
        return appProcess.importance == IMPORTANCE_FOREGROUND
    }

    private fun getCurrentDateString(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Focus Session Notification",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live remaining time for active focus block session"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        serviceScope.cancel()
        Log.d("BLOCKER", "FocusService destroyed")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }
}
