package com.example.appblocker.di

import android.content.Context
import com.example.appblocker.data.AppDatabase
import com.example.appblocker.data.BlockedAppDao
import com.example.appblocker.data.AllowedAppDao
import com.example.appblocker.data.SettingsDao
import com.example.appblocker.data.InternetScheduleDao
import com.example.appblocker.data.DailyFocusStatsDao
import com.example.appblocker.data.TemplateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun provideBlockedAppDao(database: AppDatabase): BlockedAppDao {
        return database.blockedAppDao()
    }

    @Provides
    fun provideAllowedAppDao(database: AppDatabase): AllowedAppDao {
        return database.allowedAppDao()
    }

    @Provides
    fun provideSettingsDao(database: AppDatabase): SettingsDao {
        return database.settingsDao()
    }

    @Provides
    fun provideInternetScheduleDao(database: AppDatabase): InternetScheduleDao {
        return database.internetScheduleDao()
    }

    @Provides
    fun provideDailyFocusStatsDao(database: AppDatabase): DailyFocusStatsDao {
        return database.dailyFocusStatsDao()
    }

    @Provides
    fun provideTemplateDao(database: AppDatabase): TemplateDao {
        return database.templateDao()
    }

    @Provides
    fun provideStreakDao(database: AppDatabase): com.example.appblocker.data.StreakDao {
        return database.streakDao()
    }
}
