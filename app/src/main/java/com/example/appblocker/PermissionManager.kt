package com.example.appblocker

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

data class PermissionItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: String,
    val color: Int,
    val isGranted: Boolean,
    val action: (Context) -> Unit
)

object PermissionManager {

    fun isUsageAccessGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isAccessibilityGranted(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(context.packageName) == true
    }

    fun isOverlayGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isBatteryIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun isExactAlarmGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else true
    }

    fun isNotificationGranted(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.areNotificationsEnabled()
    }

    fun getMissingCount(context: Context): Int {
        var missing = 0
        if (!isUsageAccessGranted(context)) missing++
        if (!isAccessibilityGranted(context)) missing++
        if (!isOverlayGranted(context)) missing++
        if (!isBatteryIgnored(context)) missing++
        if (!isExactAlarmGranted(context)) missing++
        if (!isNotificationGranted(context)) missing++
        return missing
    }

    fun getAllStatus(context: Context): List<PermissionItem> {
        return listOf(
            PermissionItem(
                "usage", "Usage Access", "Track app screen time", "📊", 0xFF0A84FF.toInt(),
                isUsageAccessGranted(context)
            ) { ctx ->
                ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            PermissionItem(
                "accessibility", "Accessibility", "Enforce app blocking", "♿", 0xFFA855F7.toInt(),
                isAccessibilityGranted(context)
            ) { ctx ->
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            PermissionItem(
                "overlay", "Display Over Other Apps", "Show lock screens", "🪟", 0xFFFACC15.toInt(),
                isOverlayGranted(context)
            ) { ctx ->
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            PermissionItem(
                "battery", "Battery Optimization", "Keep service running", "🔋", 0xFF4ADE80.toInt(),
                isBatteryIgnored(context)
            ) { ctx ->
                try {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            PermissionItem(
                "alarm", "Exact Alarms", "Precise schedule timing", "⏰", 0xFFFF3B30.toInt(),
                isExactAlarmGranted(context)
            ) { ctx ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            PermissionItem(
                "notification", "Notifications", "Show active session info", "🔔", 0xFF8B5CF6.toInt(),
                isNotificationGranted(context)
            ) { ctx ->
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
            }
        )
    }
}
