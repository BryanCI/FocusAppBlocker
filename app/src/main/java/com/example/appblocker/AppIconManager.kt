package com.example.appblocker

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object AppIconManager {
    const val PREF_ICON = "selected_app_icon"

    enum class AppIcon(val aliasName: String, val displayName: String, val isPremium: Boolean, @DrawableRes val preview: Int) {
        DEFAULT(".LauncherDefault", "Default Purple", false, R.drawable.preview_icon_default),
        MINIMAL(".LauncherMinimal", "Minimal", false, R.drawable.preview_icon_minimal),
        GOLD(".LauncherGold", "Midnight Gold", true, R.drawable.preview_icon_gold),
        BLUE(".LauncherBlue", "Neon Blue", true, R.drawable.preview_icon_blue),
        GREEN(".LauncherGreen", "Forest", true, R.drawable.preview_icon_green),
        WHITE(".LauncherWhite", "Pure White", true, R.drawable.preview_icon_white)
    }

    fun getCurrentIcon(context: Context): AppIcon {
        val pm = context.packageManager
        val packageName = context.packageName
        
        // First check if MainActivity is enabled
        if (pm.getComponentEnabledSetting(ComponentName(packageName, MainActivity::class.java.name)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            return AppIcon.DEFAULT
        }
        
        return AppIcon.values().find {
            pm.getComponentEnabledSetting(ComponentName(packageName, packageName + it.aliasName)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } ?: AppIcon.DEFAULT
    }

    fun setIcon(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        val packageName = context.packageName
        
        // Disable all
        val components = mutableListOf(MainActivity::class.java.name)
        AppIcon.values().forEach { components.add(packageName + it.aliasName) }
        
        components.forEach { comp ->
            try {
                pm.setComponentEnabledSetting(
                    ComponentName(packageName, comp),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Exception) {}
        }
        
        // Enable chosen
        val target = if (icon == AppIcon.DEFAULT) {
            MainActivity::class.java.name
        } else {
            packageName + icon.aliasName
        }
        
        pm.setComponentEnabledSetting(
            ComponentName(packageName, target),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        // Save to DataStore
        CoroutineScope(Dispatchers.IO).launch {
            context.settingsDataStore.edit { it[stringPreferencesKey(PREF_ICON)] = icon.name }
        }
    }

    fun syncIcon(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val savedIconName = context.settingsDataStore.data.map { it[stringPreferencesKey(PREF_ICON)] }.first()
            if (savedIconName != null) {
                val savedIcon = AppIcon.values().find { it.name == savedIconName }
                if (savedIcon != null && savedIcon != getCurrentIcon(context)) {
                    // This might cause an app restart loop if not careful, but usually getCurrentIcon reflects the system state.
                    // Only apply if there's a mismatch (e.g., after reinstall or system reset)
                    launch(Dispatchers.Main) {
                        setIcon(context, savedIcon)
                    }
                }
            }
        }
    }
}