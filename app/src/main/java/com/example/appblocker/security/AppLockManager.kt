package com.example.appblocker.security

import android.content.Context
import com.example.appblocker.ThemeManager
import kotlinx.coroutines.flow.first

object AppLockManager {
    var isUnlocked = false
    var lastBackgroundTime = 0L

    suspend fun shouldLock(context: Context): Boolean {
        val enabled = ThemeManager.getAppLockEnabledFlow(context).first()
        if (!enabled) return false
        
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
    
    fun resetLockState() {
        isUnlocked = false
        lastBackgroundTime = 0L
    }
}