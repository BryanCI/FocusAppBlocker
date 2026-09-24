package com.example.appblocker

import android.content.Context
import kotlinx.coroutines.flow.first

object AppLockManager {
    var isUnlocked = false
    var lastBackgroundTime = 0L

    suspend fun shouldLock(context: Context): Boolean {
        val appLockEnabled = ThemeManager.getAppLockEnabledFlow(context).first()
        if (!appLockEnabled) return false

        if (isUnlocked) {
            val timer = ThemeManager.getAppLockTimerFlow(context).first()
            if (timer == "immediately") return true
            
            val elapsed = System.currentTimeMillis() - lastBackgroundTime
            val threshold = when (timer) {
                "1min" -> 60_000L
                "5min" -> 300_000L
                "15min" -> 900_000L
                else -> 0L
            }
            return elapsed > threshold
        }
        return true
    }
}
