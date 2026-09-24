package com.example.appblocker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    const val CHANNEL_FOCUS = "focus_reminders"
    const val CHANNEL_SUMMARY = "morning_summary"
    const val CHANNEL_BLOCK = "block_alerts"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Focus Reminders Channel
            val focusChannel = NotificationChannel(
                CHANNEL_FOCUS,
                "Focus Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily streak and focus nudges"
                enableLights(true)
                lightColor = Color.parseColor("#BF00FF")
                enableVibration(true)
            }

            // Morning Summary Channel
            val summaryChannel = NotificationChannel(
                CHANNEL_SUMMARY,
                "Morning Summary",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Daily statistics of your focus"
            }

            // Block Alerts Channel
            val blockChannel = NotificationChannel(
                CHANNEL_BLOCK,
                "Block Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "When an app is blocked"
                enableLights(true)
                lightColor = Color.RED
            }

            manager.createNotificationChannels(listOf(focusChannel, summaryChannel, blockChannel))
        }
    }

    fun showStreakNotification(context: Context, streak: Int = 3, focusedMinutes: Int = 45) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Don't break the chain"
        val body = "🔥 Day Streak! Keep your $streak day streak alive\nYou focused ${focusedMinutes}m today • 0/7 goal"

        val builder = NotificationCompat.Builder(context, CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.focussapp)
            .setContentTitle(title)
            .setContentText("🔥 Day Streak! Keep your $streak day streak alive")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(Color.parseColor("#BF00FF"))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(0, "Focus Now", pendingIntent)

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(1001, builder.build())
            } catch (e: SecurityException) {
            }
        }
    }

    fun showMorningSummary(context: Context, focusedMinutes: Int, blocks: Int, score: Int, streak: Int = 0) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val hours = focusedMinutes / 60
        val mins = focusedMinutes % 60
        val timeText = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
        
        val message = when {
            score >= 80 -> "Excellent!"
            score >= 60 -> "Good progress!"
            else -> "You can do better today!"
        }

        val body = if (focusedMinutes == 0) {
            "Yesterday: No focus sessions. Let's make today count! 🔥 Current streak: $streak days"
        } else {
            "Yesterday: $timeText focused, $blocks blocks avoided, score $score/100. $message Streak: $streak days"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_SUMMARY)
            .setSmallIcon(R.drawable.focussapp)
            .setContentTitle("☀️ Morning Summary")
            .setContentText("Yesterday: $timeText focused, $blocks blocks avoided")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body).setSummaryText("Focus Summary"))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(Color.parseColor("#BF00FF"))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(0, "Start Focus", pendingIntent)

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(1002, builder.build())
            } catch (e: SecurityException) {}
        }
    }

    fun showBlockAlert(context: Context, appName: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_BLOCK)
            .setSmallIcon(R.drawable.focussapp)
            .setContentTitle("🚫 Blocked")
            .setContentText("$appName is blocked • Stay focused for 25m more")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(Color.parseColor("#BF00FF"))
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(1003, builder.build())
            } catch (e: SecurityException) {}
        }
    }
}
