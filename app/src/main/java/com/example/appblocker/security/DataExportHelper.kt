package com.example.appblocker.security

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.appblocker.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

object DataExportHelper {

    suspend fun exportData(context: Context) = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(context)
        
        val exportJson = JSONObject().apply {
            put("export_date", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            put("blocked_apps", JSONArray().apply {
                database.blockedAppDao().getBlockedAppsList().forEach {
                    put(JSONObject().apply {
                        put("pattern", it.pattern)
                        put("isKeyword", it.isKeyword)
                    })
                }
            })
            put("allowed_apps", JSONArray().apply {
                database.allowedAppDao().getAllAllowedAppsList().forEach {
                    put(it.packageName)
                }
            })
            put("internet_schedules", JSONArray().apply {
                database.internetScheduleDao().getAllSchedulesList().forEach {
                    put(JSONObject().apply {
                        put("name", it.name)
                        put("startHour", it.startHour)
                        put("startMinute", it.startMinute)
                        put("endHour", it.endHour)
                        put("endMinute", it.endMinute)
                        put("days", it.days)
                        put("isEnabled", it.isEnabled)
                        put("isStrict", it.isStrict)
                    })
                }
            })
            // Add more data as needed (stats, streaks, etc.)
        }

        val fileName = "Focus_Backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(exportJson.toString(4).toByteArray())
                }
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = java.io.File(downloadsDir, fileName)
            file.writeText(exportJson.toString(4))
        }
    }
    
    suspend fun clearAllData(context: Context) = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(context)
        database.clearAllTables()
        
        com.example.appblocker.ThemeManager.resetAll(context)
    }
}
