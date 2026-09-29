package com.example.appblocker

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.appblocker.security.AppLockManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class FocusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        NotificationHelper.createNotificationChannels(this)
        
        // Apply theme as early as possible but avoid runBlocking on Main
        CoroutineScope(Dispatchers.Main).launch {
            val themeMode = ThemeManager.getThemeModeFlow(this@FocusApp).first()
            ThemeManager.applyTheme(themeMode)
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // App came to foreground
            }

            override fun onStop(owner: LifecycleOwner) {
                // App went to background
                AppLockManager.lastBackgroundTime = System.currentTimeMillis()
            }
        })
    }
}
