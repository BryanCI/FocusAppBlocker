package com.example.appblocker.data

import android.content.Context
import android.os.Build
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedAppDao {
    @Query("SELECT * FROM blocked_apps")
    fun getAllBlockedApps(): Flow<List<BlockedApp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockApp(app: BlockedApp)

    @Query("DELETE FROM blocked_apps WHERE pattern = :pattern")
    suspend fun unblockApp(pattern: String)

    @Query("SELECT * FROM blocked_apps")
    suspend fun getBlockedAppsList(): List<BlockedApp>
}

@Dao
interface AllowedAppDao {
    @Query("SELECT * FROM allowed_apps")
    fun getAllAllowedApps(): Flow<List<AllowedApp>>

    @Query("SELECT * FROM allowed_apps")
    suspend fun getAllAllowedAppsList(): List<AllowedApp>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun allowApp(app: AllowedApp)

    @Query("DELETE FROM allowed_apps WHERE packageName = :packageName")
    suspend fun removeAllowedApp(packageName: String)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun getSettings(): Flow<AppSettings?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun getSettingsList(): List<AppSettings>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSettings(settings: AppSettings)

    @Query("UPDATE settings SET quickBlockEndTime = :endTime WHERE id = 1")
    suspend fun updateQuickBlockEndTime(endTime: Long)
}

@Dao
interface InternetScheduleDao {
    @Query("SELECT * FROM internet_schedules")
    fun getAllSchedules(): Flow<List<InternetSchedule>>

    @Query("SELECT * FROM internet_schedules")
    suspend fun getAllSchedulesList(): List<InternetSchedule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: InternetSchedule): Long

    @Query("DELETE FROM internet_schedules WHERE id = :id")
    suspend fun deleteSchedule(id: Int)

    @Query("UPDATE internet_schedules SET isEnabled = :enabled WHERE id = :id")
    suspend fun toggleSchedule(id: Int, enabled: Boolean)
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY sortOrder ASC")
    fun getAllTemplates(): Flow<List<Template>>

    @Query("SELECT COUNT(*) FROM templates")
    suspend fun getTemplatesCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: Template)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplates(templates: List<Template>)

    @Update
    suspend fun updateTemplate(template: Template)

    @Delete
    suspend fun deleteTemplate(template: Template)
}

@Dao
interface DailyFocusStatsDao {
    @Query("SELECT * FROM daily_focus_stats WHERE date = :date")
    fun getStatsForDate(date: String): Flow<DailyFocusStats?>

    @Query("SELECT * FROM daily_focus_stats WHERE date = :date")
    suspend fun getStatsForDateList(date: String): DailyFocusStats?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStats(stats: DailyFocusStats)

    @Query("SELECT * FROM daily_focus_stats ORDER BY date DESC LIMIT 7")
    fun getLast7DaysStats(): Flow<List<DailyFocusStats>>

    @Query("SELECT SUM(protectedMinutes) FROM daily_focus_stats WHERE date BETWEEN :start AND :end")
    fun getProtectedMinutesRange(start: String, end: String): Flow<Int?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockEvent(event: BlockEvent)

    @Query("SELECT packageName, COUNT(*) as count FROM block_events WHERE date = :date GROUP BY packageName")
    fun getBlockCountsForDate(date: String): Flow<List<PackageBlockCount>>

    @Query("SELECT packageName, COUNT(*) as count FROM block_events WHERE date BETWEEN :start AND :end GROUP BY packageName ORDER BY count DESC LIMIT 1")
    fun getTopBlockedAppInRange(start: String, end: String): Flow<PackageBlockCount?>

    @Query("DELETE FROM block_events WHERE timestamp < :timestamp")
    suspend fun clearOldBlockEvents(timestamp: Long)

    @Query("SELECT COUNT(*) FROM block_events WHERE date = :date")
    fun getBlockCountForDate(date: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM block_events WHERE date = :date")
    suspend fun getBlockCountForDateSync(date: String): Int
}

@Dao
interface StreakDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreak(streak: Streak)

    @Query("SELECT * FROM streaks ORDER BY date DESC LIMIT 7")
    fun getLast7DaysStreaks(): Flow<List<Streak>>

    @Query("SELECT * FROM streaks ORDER BY date ASC")
    fun getAllStreaks(): Flow<List<Streak>>

    @Query("SELECT * FROM streaks WHERE date = :date")
    suspend fun getStreakForDate(date: String): Streak?

    @Query("SELECT streakCount FROM streaks ORDER BY date DESC LIMIT 1")
    suspend fun getCurrentStreak(): Int?
}

@Database(entities = [BlockedApp::class, AllowedApp::class, AppSettings::class, InternetSchedule::class, DailyFocusStats::class, BlockEvent::class, Template::class, Streak::class], version = 32)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun allowedAppDao(): AllowedAppDao
    abstract fun settingsDao(): SettingsDao
    abstract fun internetScheduleDao(): InternetScheduleDao
    abstract fun dailyFocusStatsDao(): DailyFocusStatsDao
    abstract fun templateDao(): TemplateDao
    abstract fun streakDao(): StreakDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val appContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val deviceContext = context.createDeviceProtectedStorageContext()
                    val dbName = "app_blocker_db"
                    
                    // Only move if the database doesn't exist in device protected storage yet
                    if (!deviceContext.getDatabasePath(dbName).exists()) {
                        val userManager = context.getSystemService(Context.USER_SERVICE) as? android.os.UserManager
                        val isUserUnlocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            userManager?.isUserUnlocked ?: false
                        } else true

                        if (isUserUnlocked) {
                            // Can only move from credential storage if the user is unlocked
                            try {
                                deviceContext.moveDatabaseFrom(context, dbName)
                            } catch (e: Exception) {
                                // Log or handle migration failure
                            }
                        }
                    }
                    deviceContext
                } else {
                    context.applicationContext
                }
                
                val instance = Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    "app_blocker_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
