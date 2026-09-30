package com.example.appblocker

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.appblocker.settingsDataStore

object FocusSessionManager {
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_FOCUS_ACTIVE = "focus_active"
    private const val KEY_FOCUS_START = "focus_start"
    private const val KEY_FOCUS_END = "focus_end"
    private const val KEY_FOCUS_INFINITE = "focus_infinite"
    private const val KEY_STRICT_ACTIVE_START = "strict_active_at_start"
    private const val KEY_STRICT_MODE = "strict_mode"

    @Volatile
    var isActiveCache: Boolean = false
    @Volatile
    var isInfiniteCache: Boolean = false

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun startSession(context: Context, durationMinutes: Int) {
        val prefs = getPrefs(context)
        val startTime = System.currentTimeMillis()
        val isInfinite = durationMinutes == -1 || durationMinutes == 0 || durationMinutes >= 9999
        val endTime = if (isInfinite) Long.MAX_VALUE else startTime + (durationMinutes.toLong() * 60 * 1000)
        
        val strictNow = prefs.getBoolean(KEY_STRICT_MODE, false)
        
        isActiveCache = true
        isInfiniteCache = isInfinite
        prefs.edit().apply {
            putBoolean(KEY_FOCUS_ACTIVE, true)
            putLong(KEY_FOCUS_START, startTime)
            putLong(KEY_FOCUS_END, endTime)
            putBoolean(KEY_FOCUS_INFINITE, isInfinite)
            putBoolean(KEY_STRICT_ACTIVE_START, strictNow)
            apply()
        }
        Log.d("BLOCKER", "Focus started infinite=$isInfinite start=$startTime end=$endTime strictAtStart=$strictNow")

        // FIX - START SERVICE
        try {
            val svc = Intent(context, AppBlockerService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(svc)
            } else {
                context.startService(svc)
            }
            Log.d("AppBlocker", "SERVICE START REQUESTED from FocusSessionManager")
        } catch(e: Exception){
            Log.e("AppBlocker", "Failed to start AppBlockerService", e)
        }
    }

    fun isSessionActive(context: Context): Boolean {
        // Fast path: trust cache if it's set to true
        if (isActiveCache) return true
        
        val prefs = getPrefs(context)
        val active = prefs.getBoolean(KEY_FOCUS_ACTIVE, false)
        if (!active) {
            isActiveCache = false
            return false
        }
        
        val isInfinite = prefs.getBoolean(KEY_FOCUS_INFINITE, false)
        if (isInfinite) {
            isActiveCache = true
            isInfiniteCache = true
            return true
        }
        
        val endTime = prefs.getLong(KEY_FOCUS_END, 0L)
        val now = System.currentTimeMillis()
        val stillActive = now < endTime
        
        if (!stillActive && endTime != 0L) {
            // Auto-clean expired session
            endSession(context)
        }
        
        isActiveCache = stillActive
        return stillActive
    }

    fun getRemainingMillis(context: Context): Long {
        val prefs = getPrefs(context)
        if (!prefs.getBoolean(KEY_FOCUS_ACTIVE, false)) return 0
        if (prefs.getBoolean(KEY_FOCUS_INFINITE, false)) return Long.MAX_VALUE
        
        val endTime = prefs.getLong(KEY_FOCUS_END, 0L)
        return (endTime - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun getTotalSessionMillis(context: Context): Long {
        val prefs = getPrefs(context)
        val start = prefs.getLong(KEY_FOCUS_START, 0L)
        val end = prefs.getLong(KEY_FOCUS_END, 0L)
        if (start == 0L || end == 0L) return 0L
        if (end == Long.MAX_VALUE) {
            return (System.currentTimeMillis() - start).coerceAtLeast(0L)
        }
        return (end - start).coerceAtLeast(0L)
    }

    fun endSession(context: Context) {
        android.util.Log.d("AppBlocker", "STOPPING SESSION - clearing blocks")
        
        // 1. Clear in-memory
        isActiveCache = false
        isInfiniteCache = false
        
        // 2. Clear blocked list in SharedPreferences for immediate unblock
        context.getSharedPreferences("block_prefs", Context.MODE_PRIVATE)
            .edit().putStringSet("blocked_apps", emptySet()).apply()
            
        // 3. Reset session flags in app_prefs
        getPrefs(context).edit().apply {
            putBoolean(KEY_FOCUS_ACTIVE, false)
            putBoolean(KEY_FOCUS_INFINITE, false)
            putLong(KEY_FOCUS_START, 0L)
            putLong(KEY_FOCUS_END, 0L)
            putBoolean(KEY_STRICT_ACTIVE_START, false)
            commit() // Synchronous persistence
        }

        // 4. Clear DataStore asynchronously too
        CoroutineScope(Dispatchers.IO).launch {
            try {
                context.settingsDataStore.edit { store ->
                    store[booleanPreferencesKey(KEY_FOCUS_ACTIVE)] = false
                    store[booleanPreferencesKey(KEY_FOCUS_INFINITE)] = false
                    store[longPreferencesKey(KEY_FOCUS_START)] = 0L
                    store[longPreferencesKey(KEY_FOCUS_END)] = 0L
                    store[booleanPreferencesKey(KEY_STRICT_ACTIVE_START)] = false
                }
            } catch (e: Exception) {
                Log.e("AppBlocker", "DataStore clear fail", e)
            }
        }

        // 5. Stop the blocker services
        try {
            val stopIntent = Intent(context, AppBlockerService::class.java)
            context.stopService(stopIntent)
            
            val blockSvcIntent = Intent(context, BlockSessionService::class.java)
            context.stopService(blockSvcIntent)
            
            // Also stop via broadcast for components that can't be reached by intent
            context.sendBroadcast(Intent("com.example.appblocker.STOP_BLOCKING").apply {
                setPackage(context.packageName)
            })
        } catch(e: Exception) {
            android.util.Log.e("AppBlocker", "stop service fail", e)
        }
        
        // 6. Cancel WorkManager tasks
        try {
            WorkManager.getInstance(context).cancelAllWorkByTag("blocker")
            WorkManager.getInstance(context).cancelUniqueWork("AppBlockerKeepAlive")
        } catch(e: Exception) {}

        // 7. Cancel notification
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(BlockSessionService.NOTIFICATION_ID)
        } catch (e: Exception) {}

        Log.d("AppBlocker", "Focus session ended and flags cleared")
    }
}
