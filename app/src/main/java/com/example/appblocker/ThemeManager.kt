package com.example.appblocker

import android.content.Context
import android.graphics.Color
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore by preferencesDataStore(name = "settings")

object ThemeManager {
    val NOTIF_BLOCK_ALERTS = booleanPreferencesKey("notif_block_alerts")
    val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    val APP_LOCK_TIMER = stringPreferencesKey("app_lock_timer")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val ACCENT_COLOR = stringPreferencesKey("accent_color")
    val AMOLED_ENABLED = booleanPreferencesKey("amoled_enabled")
    val NOTIF_ENABLED_MASTER = booleanPreferencesKey("notif_enabled_master")
    val NOTIF_FOCUS_REMINDERS = booleanPreferencesKey("notif_focus_reminders")
    val NOTIF_MORNING_SUMMARY = booleanPreferencesKey("notif_morning_summary")
    val NOTIF_TIME_FOCUS = stringPreferencesKey("notif_time_focus")
    val NOTIF_TIME_MORNING = stringPreferencesKey("notif_time_morning")
    val STEALTH_MODE_ENABLED = booleanPreferencesKey("stealth_enabled")
    val SCREENSHOT_PREVENTION_ENABLED = booleanPreferencesKey("screenshot_prevention")
    val LAST_PAUSED_TIMESTAMP = longPreferencesKey("last_paused_timestamp")

    fun getNotifBlockFlow(context: Context): Flow<Boolean> = context.settingsDataStore.data.map { it[NOTIF_BLOCK_ALERTS] ?: false }
    fun getAppLockEnabledFlow(context: Context): Flow<Boolean> = context.settingsDataStore.data.map { it[APP_LOCK_ENABLED] ?: false }
    fun getAppLockTimerFlow(context: Context): Flow<String> = context.settingsDataStore.data.map { it[APP_LOCK_TIMER] ?: "1" }
    fun getThemeModeFlow(context: Context): Flow<String> = context.settingsDataStore.data.map { it[THEME_MODE] ?: "system" }
    fun getAccentFlow(context: Context): Flow<String> = context.settingsDataStore.data.map { it[ACCENT_COLOR] ?: "blue" }
    fun getAmoledFlow(context: Context): Flow<Boolean> = context.settingsDataStore.data.map { it[AMOLED_ENABLED] ?: false }
    fun getNotifMasterFlow(context: Context): Flow<Boolean> = context.settingsDataStore.data.map { it[NOTIF_ENABLED_MASTER] ?: true }
    fun getNotifFocusFlow(context: Context): Flow<Boolean> = context.settingsDataStore.data.map { it[NOTIF_FOCUS_REMINDERS] ?: true }
    fun getNotifSummaryFlow(context: Context): Flow<Boolean> = context.settingsDataStore.data.map { it[NOTIF_MORNING_SUMMARY] ?: true }
    fun getNotifTimeFocusFlow(context: Context): Flow<String> = context.settingsDataStore.data.map { it[NOTIF_TIME_FOCUS] ?: "18:00" }
    fun getNotifTimeMorningFlow(context: Context): Flow<String> = context.settingsDataStore.data.map { it[NOTIF_TIME_MORNING] ?: "08:00" }
    fun getStealthModeFlow(context: Context): Flow<Boolean> = context.settingsDataStore.data.map { it[STEALTH_MODE_ENABLED] ?: false }
    fun getScreenshotPreventionFlow(context: Context): Flow<Boolean> = context.settingsDataStore.data.map { it[SCREENSHOT_PREVENTION_ENABLED] ?: false }
    fun getLastPausedTimestampFlow(context: Context): Flow<Long> = context.settingsDataStore.data.map { it[LAST_PAUSED_TIMESTAMP] ?: 0L }

    fun setAppLockEnabled(context: Context, enabled: Boolean) = CoroutineScope(Dispatchers.IO).launch { context.settingsDataStore.edit { it[APP_LOCK_ENABLED] = enabled } }
    fun setAppLockTimer(context: Context, timer: String) = CoroutineScope(Dispatchers.IO).launch { context.settingsDataStore.edit { it[APP_LOCK_TIMER] = timer } }
    fun setThemeMode(context: Context, mode: String) = CoroutineScope(Dispatchers.IO).launch { context.settingsDataStore.edit { it[THEME_MODE] = mode } }
    fun setAccentColor(context: Context, accent: String) = CoroutineScope(Dispatchers.IO).launch { context.settingsDataStore.edit { it[ACCENT_COLOR] = accent } }
    fun setAmoledEnabled(context: Context, enabled: Boolean) = CoroutineScope(Dispatchers.IO).launch { context.settingsDataStore.edit { it[AMOLED_ENABLED] = enabled } }
    fun setNotifMaster(context: Context, enabled: Boolean) = CoroutineScope(Dispatchers.IO).launch { context.settingsDataStore.edit { it[NOTIF_ENABLED_MASTER] = enabled } }
    fun setNotifFocus(context: Context, enabled: Boolean) = CoroutineScope(Dispatchers.IO).launch { context.settingsDataStore.edit { it[NOTIF_FOCUS_REMINDERS] = enabled } }
    fun setNotifSummary(context: Context, enabled: Boolean) = CoroutineScope(Dispatchers.IO).launch { context.settingsDataStore.edit { it[NOTIF_MORNING_SUMMARY] = enabled } }
    fun setNotifBlock(context: Context, enabled: Boolean) = CoroutineScope(Dispatchers.IO).launch { context.settingsDataStore.edit { it[NOTIF_BLOCK_ALERTS] = enabled } }
    fun setNotifTimeFocus(context: Context, time: String) = CoroutineScope(Dispatchers.IO).launch { context.settingsDataStore.edit { it[NOTIF_TIME_FOCUS] = time } }
    fun setNotifTimeMorning(context: Context, time: String) = CoroutineScope(Dispatchers.IO).launch { context.settingsDataStore.edit { it[NOTIF_TIME_MORNING] = time } }
    fun setStealthMode(context: Context, enabled: Boolean) = CoroutineScope(Dispatchers.IO).launch { context.settingsDataStore.edit { it[STEALTH_MODE_ENABLED] = enabled } }
    fun setScreenshotPrevention(context: Context, enabled: Boolean) = CoroutineScope(Dispatchers.IO).launch { context.settingsDataStore.edit { it[SCREENSHOT_PREVENTION_ENABLED] = enabled } }

    fun applyTheme(themeMode: String) {}
    fun applyWindowColors(activity: android.app.Activity, themeMode: String, amoled: Boolean) {}
    fun isDark(context: Context, themeMode: String): Boolean = true
    fun getBackgroundColor(amoled: Boolean, isDark: Boolean): Int = Color.BLACK
    fun getAccentColor(accent: String): Int = Color.BLUE
    fun getAccentGlow(accent: String): Int = Color.BLUE
    fun getAccentGradientEnd(accent: String): Int = Color.BLUE
    fun resetAll(context: Context) = CoroutineScope(Dispatchers.IO).launch { context.settingsDataStore.edit { it.clear() } }
}
