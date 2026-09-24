package com.example.appblocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // When an alarm triggers, we want to ensure the AppBlockerService is running
        // so it can evaluate the new state (active/inactive schedules).
        val serviceIntent = Intent(context, AppBlockerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        
        // Also reschedule the next alarm
        AppBlockerService.scheduleNextAlarm(context)
    }
}
