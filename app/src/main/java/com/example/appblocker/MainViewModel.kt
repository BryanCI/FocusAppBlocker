package com.example.appblocker

import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.os.Build
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.appblocker.data.AppDatabase
import com.example.appblocker.data.AppSettings
import com.example.appblocker.data.BlockedApp
import com.example.appblocker.data.AllowedApp
import com.example.appblocker.data.InternetSchedule
import com.example.appblocker.data.auth.AuthRepository
import com.android.billingclient.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class AppUsageInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    val usageTimeMillis: Long
)

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable? = null,
    val isBlocked: Boolean,
    val isAllowed: Boolean = false,
    val category: Int = -1
)

data class ScoreImpact(
    val label: String,
    val impact: Int,
    val isPositive: Boolean
)

data class FocusScoreBreakdown(
    val totalScore: Int = 100,
    val impacts: List<ScoreImpact> = emptyList(),
    val focusTimeMillis: Long = 0,
    val focusGoalMillis: Long = 5 * 3600000L,
    val unlockCount: Int = 0
)

sealed class StatsState {
    object Loading : StatsState()
    data class Success(
        val range: com.example.appblocker.repository.TimeRange,
        val appUsage: List<AppUsageInfo>,
        val chartData: List<Long>,
        val labels: List<String>,
        val totalTimeMillis: Long,
        val avgTimeMillis: Long
    ) : StatsState()
    data class Error(val message: String) : StatsState()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val dao: com.example.appblocker.data.BlockedAppDao,
    private val allowedDao: com.example.appblocker.data.AllowedAppDao,
    private val settingsDao: com.example.appblocker.data.SettingsDao,
    private val scheduleDao: com.example.appblocker.data.InternetScheduleDao,
    private val focusStatsDao: com.example.appblocker.data.DailyFocusStatsDao,
    private val templateDao: com.example.appblocker.data.TemplateDao,
    private val streakDao: com.example.appblocker.data.StreakDao,
    private val statsRepository: com.example.appblocker.repository.StatsRepository,
    private val authRepository: AuthRepository
) : AndroidViewModel(application) {

    private val billingClient = BillingClient.newBuilder(application)
        .setListener { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                for (purchase in purchases) {
                    handlePurchase(purchase)
                }
            }
        }
        .enablePendingPurchases()
        .build()

    private val _premiumProducts = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val premiumProducts: StateFlow<Map<String, ProductDetails>> = _premiumProducts.asStateFlow()

    val isPremium: StateFlow<Boolean> = settingsDao.getSettings()
        .map { it?.isPremium == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _trialDaysLeft = MutableStateFlow<Int?>(null)
    val trialDaysLeft: StateFlow<Int?> = _trialDaysLeft.asStateFlow()

    init {
        startBillingConnection()
    }

    private fun startBillingConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryPremiumProduct()
                    restorePurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Try to restart the connection on the next request
            }
        })
    }

    private fun queryPremiumProduct() {
        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId("annual_6_99")
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId("monthly_1")
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val productMap = productDetailsList.associateBy { it.productId }
                _premiumProducts.value = productMap
            }
        }
    }

    fun startBillingFlow(activity: android.app.Activity, productId: String) {
        val productDetails = _premiumProducts.value[productId] ?: return
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: ""
        
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        updatePremiumStatus(true, purchase)
                        // Trigger success navigation in MainActivity
                        val intent = Intent(getApplication(), MainActivity::class.java).apply {
                            putExtra("PREMIUM_SUCCESS", true)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        getApplication<Application>().startActivity(intent)
                    }
                }
            } else {
                updatePremiumStatus(true, purchase)
            }
        }
    }

    private fun updatePremiumStatus(isPremium: Boolean, purchase: Purchase? = null) {
        viewModelScope.launch {
            val currentSettings = settings.value ?: AppSettings()
            var isTrial = false
            var expiryDate: Long? = null

            if (isPremium && purchase != null) {
                // In a real app, you'd verify this on a backend
                // For now, we use the purchase time + 7 days for trial detection if it's new
                // and store it in local DB.
                
                // Logic to check if it's a trial (simplified for this task)
                // premium_monthly has a 7-day free trial configured in Play Console
                // We'll assume if it's recently purchased, it might be in trial.
                // For a more robust solution, check acknowledge status or specific trial IDs
                
                expiryDate = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000) // Assume 30 days for sub
                
                // Check if in 7-day trial
                val purchaseTime = purchase.purchaseTime
                val sevenDaysInMillis = 7L * 24 * 60 * 60 * 1000
                if (System.currentTimeMillis() - purchaseTime < sevenDaysInMillis) {
                    isTrial = true
                    val daysLeft = (sevenDaysInMillis - (System.currentTimeMillis() - purchaseTime)) / (24 * 60 * 60 * 1000)
                    _trialDaysLeft.value = daysLeft.toInt()
                } else {
                    _trialDaysLeft.value = null
                }
            } else if (!isPremium) {
                _trialDaysLeft.value = null
            }

            settingsDao.updateSettings(currentSettings.copy(
                isPremium = isPremium,
                premiumExpiryDate = expiryDate,
                isTrialPeriod = isTrial
            ))
            // Sync with Firebase
            authRepository.setPremium(isPremium)
        }
    }

    fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val activePurchase = purchases.find { 
                    (it.products.contains("annual_6_99") || it.products.contains("monthly_1")) &&
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                
                if (activePurchase != null) {
                    updatePremiumStatus(true, activePurchase)
                    // If we just restored, also show success if it wasn't already premium
                    if (settings.value?.isPremium == false) {
                        val intent = Intent(getApplication(), MainActivity::class.java).apply {
                            putExtra("PREMIUM_SUCCESS", true)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        getApplication<Application>().startActivity(intent)
                    }
                } else {
                    // Check if premium was previously enabled and expired
                    viewModelScope.launch {
                        val currentSettings = settings.value
                        if (currentSettings?.isPremium == true) {
                            val expiry = currentSettings.premiumExpiryDate
                            if (expiry != null && System.currentTimeMillis() > expiry) {
                                settingsDao.updateSettings(currentSettings.copy(isPremium = false))
                                authRepository.setPremium(false)
                            }
                        }
                        
                        // Also check Firebase for previous purchases
                        authRepository.isPremium().onSuccess { firebasePremium ->
                            if (firebasePremium && currentSettings?.isPremium == false) {
                                settingsDao.updateSettings(currentSettings.copy(isPremium = true))
                            }
                        }
                    }
                }
            }
        }
    }

    fun startLocalTrial() {
        viewModelScope.launch {
            val currentSettings = settings.value ?: AppSettings()
            val expiryDate = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000)
            settingsDao.updateSettings(currentSettings.copy(
                isTrialPeriod = true,
                premiumExpiryDate = expiryDate
            ))
        }
    }

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val blockedApps = dao.getAllBlockedApps().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allowedApps = allowedDao.getAllAllowedApps().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Timer flows for Bug 1
    val remainingMillisFlow: Flow<Long> = flow {
        while (true) {
            emit(FocusSessionManager.getRemainingMillis(getApplication()))
            delay(1000)
        }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val sessionTotalDuration: Long
        get() = FocusSessionManager.getTotalSessionMillis(getApplication())
    val settings = settingsDao.getSettings().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val schedules = scheduleDao.getAllSchedules().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val templates = templateDao.getAllTemplates().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _webs = MutableStateFlow<List<com.example.appblocker.adapter.WebItem>>(emptyList())
    val webs: StateFlow<List<com.example.appblocker.adapter.WebItem>> = combine(_webs, blockedApps) { current, blocked ->
        val blockedWebs = blocked.filter { it.isKeyword && it.pattern.startsWith("web:") }.map { it.pattern.removePrefix("web:") }.toSet()
        
        val suggestions = listOf(
            "earth.google.com",
            "vlc.onl",
            "drive.google.com",
            "duo.google.com",
            "mail.google.com",
            "maps.google.com",
            "messages.google.com"
        )
        
        val allDomains = (suggestions + current.map { it.domain } + blockedWebs).distinct()
        allDomains.map { domain ->
            com.example.appblocker.adapter.WebItem(domain, blockedWebs.contains(domain))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _usageStats = MutableStateFlow<List<AppUsageInfo>>(emptyList())
    val usageStats: StateFlow<List<AppUsageInfo>> = _usageStats.asStateFlow()

    private val _todayUsageStats = MutableStateFlow<List<AppUsageInfo>>(emptyList())
    val todayUsageStats: StateFlow<List<AppUsageInfo>> = _todayUsageStats.asStateFlow()

    val topDistractorToday: StateFlow<AppUsageInfo?> = _todayUsageStats.map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private var refreshJob: Job? = null

    fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                loadUsageStats()
                delay(60_000) // refresh every 1 minute so "1m" ticks up
            }
        }
    }

    override fun onCleared() {
        refreshJob?.cancel()
        billingClient.endConnection()
        super.onCleared()
    }

    private val _weeklyUsageStats = MutableStateFlow<List<AppUsageInfo>>(emptyList())
    val weeklyUsageStats: StateFlow<List<AppUsageInfo>> = _weeklyUsageStats.asStateFlow()

    private val _monthlyUsageStats = MutableStateFlow<List<AppUsageInfo>>(emptyList())
    val monthlyUsageStats: StateFlow<List<AppUsageInfo>> = _monthlyUsageStats.asStateFlow()

    private val _yearlyUsageStats = MutableStateFlow<List<AppUsageInfo>>(emptyList())
    val yearlyUsageStats: StateFlow<List<AppUsageInfo>> = _yearlyUsageStats.asStateFlow()

    private val _todayUsageRawStats = MutableStateFlow<List<com.example.appblocker.repository.AppUsageEventData>>(emptyList())

    val dailyStats = focusStatsDao.getStatsForDate(getCurrentDateString())
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val yesterdayStats = focusStatsDao.getStatsForDate(getYesterdayDateString())
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val blockedAppsTodayCount: StateFlow<Int> = dailyStats.map { it?.appsBlockedToday ?: 0 }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val blockedAppsTodayDetails: StateFlow<Map<String, Int>> = statsRepository.getBlockedAppsToday()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    private val _isAppUnlocked = MutableStateFlow(false)
    val isAppUnlocked: StateFlow<Boolean> = _isAppUnlocked.asStateFlow()

    private var lastForegroundTime = System.currentTimeMillis()

    fun onAppResume() {
        val now = System.currentTimeMillis()
        if (settings.value?.appLaunchPin != null && _isAppUnlocked.value) {
            if (now - lastForegroundTime > 30000) {
                _isAppUnlocked.value = false
            }
        }
        lastForegroundTime = now
        // Sync icon on resume to ensure consistency without blocking onCreate
        viewModelScope.launch(Dispatchers.IO) {
            AppIconManager.syncIcon(getApplication())
        }
    }

    fun onAppPause() {
        lastForegroundTime = System.currentTimeMillis()
    }

    fun setAppUnlocked(unlocked: Boolean) {
        _isAppUnlocked.value = unlocked
    }

    private fun getCurrentDateString(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    private fun getYesterdayDateString(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return sdf.format(cal.time)
    }

    val suggestions: StateFlow<List<String>> = combine(dailyStats, yesterdayStats, _todayUsageRawStats) { stats, yStats, usage ->
        statsRepository.analyzeUsage(stats, yStats, usage).suggestions
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val topDistractingApps = todayUsageStats.map { stats ->
        stats.filter { it.packageName != "" && it.usageTimeMillis > 0 }.sortedByDescending { it.usageTimeMillis }.take(2)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val focusScoreDetails = combine(dailyStats, yesterdayStats, _todayUsageRawStats) { stats, yStats, usage ->
        val result = statsRepository.analyzeUsage(stats, yStats, usage)
        FocusScoreBreakdown(
            totalScore = result.focusScore,
            impacts = result.impacts,
            focusTimeMillis = stats?.totalFocusTimeMillis ?: 0,
            unlockCount = stats?.unlockCount ?: 0
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FocusScoreBreakdown())

    val focusScore: StateFlow<Int> = focusScoreDetails.map { it.totalScore }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100)

    val currentStreak: StateFlow<Int> = (streakDao.getLast7DaysStreaks() as Flow<List<com.example.appblocker.data.Streak>>)
        .map { streaks ->
            var count = 0
            for (streak in streaks) {
                if (streak.isPerfect) count++
                else break
            }
            count
        }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val bestStreak: StateFlow<Int> = (streakDao.getAllStreaks() as Flow<List<com.example.appblocker.data.Streak>>)
        .map { streaks ->
            var max = 0
            var current = 0
            streaks.sortedBy { it.date }.forEach {
                if (it.isPerfect) {
                    current++
                    if (current > max) max = current
                } else {
                    current = 0
                }
            }
            max
        }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val scoreDelta: StateFlow<Int> = combine(focusScore, yesterdayStats) { current, yesterday ->
        current - (yesterday?.focusScore ?: 100)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun isDistractingApp(packageName: String): Boolean {
        return statsRepository.isDistractingApp(packageName)
    }

    fun isProductiveApp(packageName: String): Boolean {
        return statsRepository.isProductiveApp(packageName)
    }

    private val _hourlyUsage = MutableStateFlow<List<com.example.appblocker.repository.StatsRepository.HourlyUsage>>(emptyList())
    val hourlyUsage: StateFlow<List<com.example.appblocker.repository.StatsRepository.HourlyUsage>> = _hourlyUsage.asStateFlow()

    private val _focusMetrics = MutableStateFlow(com.example.appblocker.repository.StatsRepository.FocusMetrics(0, 0))
    val focusMetrics: StateFlow<com.example.appblocker.repository.StatsRepository.FocusMetrics> = _focusMetrics.asStateFlow()

    private val _tick = MutableStateFlow(System.currentTimeMillis())

    init {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                _tick.value = System.currentTimeMillis()
            }
        }
    }

    val isStrictBlockActive: StateFlow<Boolean> = combine(settings, schedules, blockedApps, _tick) { settings, schedules, blocked, _ ->
        val hasActiveSchedule = schedules.any { it.isActive() }
        val isQuickBlockActive = FocusSessionManager.isActiveCache
        (settings?.isStrictModeEnabled == true) && (hasActiveSchedule || isQuickBlockActive)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val uiState: StateFlow<List<AppInfo>> = combine(_allApps, blockedApps, allowedApps, _searchQuery) { all, blocked, allowed, query ->
        val blockedPatterns = blocked.filter { !it.isKeyword }.map { it.pattern }.toSet()
        val allowedPackages = allowed.map { it.packageName }.toSet()
        
        val mappedApps = all.map { 
            it.copy(
                isBlocked = blockedPatterns.contains(it.packageName),
                isAllowed = allowedPackages.contains(it.packageName)
            ) 
        }
        if (query.isBlank()) {
            mappedApps
        } else {
            mappedApps.filter { 
                it.name.contains(query, ignoreCase = true) || 
                it.packageName.contains(query, ignoreCase = true) 
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    private val _focusTimeSeconds = MutableStateFlow(0L)
    val focusTimeSeconds: StateFlow<Long> = _focusTimeSeconds.asStateFlow()

    private var focusTimerJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            loadUsageStats()
            startFocusTimer()
            startAutoRefresh()
            checkAndInsertDefaultTemplates()
        }
    }

    fun loadAllApps() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_allApps.value.isNotEmpty()) return@launch
            loadInstalledApps()
        }
    }

    private fun checkAndInsertDefaultTemplates() {
        viewModelScope.launch(Dispatchers.IO) {
            if (templateDao.getTemplatesCount() == 0) {
                restoreDefaultTemplates()
            }
        }
    }

    fun restoreDefaultTemplates() {
        viewModelScope.launch(Dispatchers.IO) {
            val defaults = listOf(
                com.example.appblocker.data.Template(name = "Study Time", subtitle = "Deep focus for learning", icon = "📚", timeLabel = "16:00 - 20:00", startHour = 16, startMin = 0, endHour = 20, endMin = 0, daysMask = 62, defaultApps = "", isCustom = false, sortOrder = 1),
                com.example.appblocker.data.Template(name = "Deep Work", subtitle = "Stay focused during work", icon = "💼", timeLabel = "09:00 - 17:00", startHour = 9, startMin = 0, endHour = 17, endMin = 0, daysMask = 62, defaultApps = "", isCustom = false, sortOrder = 2),
                com.example.appblocker.data.Template(name = "Focus Hour", subtitle = "Intense concentration", icon = "🎯", timeLabel = "1 hour", startHour = 14, startMin = 0, endHour = 15, endMin = 0, daysMask = 127, defaultApps = "", isCustom = false, sortOrder = 3),
                com.example.appblocker.data.Template(name = "Zen Mode", subtitle = "Meditation and peace", icon = "🧘", timeLabel = "07:00 - 08:00", startHour = 7, startMin = 0, endHour = 8, endMin = 0, daysMask = 127, defaultApps = "", isCustom = false, sortOrder = 4),
                com.example.appblocker.data.Template(name = "House Chores", subtitle = "Get things done at home", icon = "🏠", timeLabel = "18:00 - 19:00", startHour = 18, startMin = 0, endHour = 19, endMin = 0, daysMask = 127, defaultApps = "", isCustom = false, sortOrder = 5),
                com.example.appblocker.data.Template(name = "Digital Sabbath", subtitle = "No screens allowed", icon = "🚫", timeLabel = "Sunday", startHour = 0, startMin = 0, endHour = 23, endMin = 59, daysMask = 1, defaultApps = "", isCustom = false, sortOrder = 6),
                com.example.appblocker.data.Template(name = "Quick Break", subtitle = "Short focus interval", icon = "⏱️", timeLabel = "15 mins", startHour = 11, startMin = 0, endHour = 11, endMin = 15, daysMask = 127, defaultApps = "", isCustom = false, sortOrder = 7),
                com.example.appblocker.data.Template(name = "Morning Clarity", subtitle = "Start your day right", icon = "🌅", timeLabel = "06:00 - 08:00", startHour = 6, startMin = 0, endHour = 8, endMin = 0, daysMask = 127, defaultApps = "", isCustom = false, sortOrder = 8),
                com.example.appblocker.data.Template(name = "Wind Down", subtitle = "Wind down without distractions", icon = "🌙", timeLabel = "21:00 - 23:00", startHour = 21, startMin = 0, endHour = 23, endMin = 0, daysMask = 127, defaultApps = "", isCustom = false, sortOrder = 9),
                com.example.appblocker.data.Template(name = "Weekend Detox", subtitle = "Unplug and recharge", icon = "🌴", timeLabel = "Weekend", startHour = 9, startMin = 0, endHour = 18, endMin = 0, daysMask = 65, defaultApps = "", isCustom = false, sortOrder = 10)
            )
            templateDao.insertTemplates(defaults)
        }
    }

    fun updateTemplate(template: com.example.appblocker.data.Template) {
        viewModelScope.launch(Dispatchers.IO) {
            templateDao.updateTemplate(template)
        }
    }

    fun deleteTemplate(template: com.example.appblocker.data.Template) {
        viewModelScope.launch(Dispatchers.IO) {
            templateDao.deleteTemplate(template)
        }
    }

    fun createTemplate(template: com.example.appblocker.data.Template) {
        viewModelScope.launch(Dispatchers.IO) {
            templateDao.insertTemplate(template)
        }
    }

    private fun startFocusTimer() {
        focusTimerJob?.cancel()
        focusTimerJob = viewModelScope.launch {
            while (true) {
                if (isStrictBlockActive.value) {
                    _focusTimeSeconds.value += 1
                } else {
                    _focusTimeSeconds.value = 0
                }
                delay(1000)
            }
        }
    }


    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private val _weeklyChartData = MutableStateFlow<List<Long>>(emptyList())
    val weeklyChartData: StateFlow<List<Long>> = _weeklyChartData.asStateFlow()

    private val _monthlyChartData = MutableStateFlow<List<Long>>(emptyList())
    val monthlyChartData: StateFlow<List<Long>> = _monthlyChartData.asStateFlow()

    private val _yearlyChartData = MutableStateFlow<List<Long>>(emptyList())
    val yearlyChartData: StateFlow<List<Long>> = _yearlyChartData.asStateFlow()

    private val _currentRangeStats = MutableStateFlow<List<AppUsageInfo>>(emptyList())
    val currentRangeStats: StateFlow<List<AppUsageInfo>> = _currentRangeStats.asStateFlow()

    private val _statsState = MutableStateFlow<StatsState>(StatsState.Loading)
    val statsState: StateFlow<StatsState> = _statsState.asStateFlow()

    private val statsCache = mutableMapOf<com.example.appblocker.repository.TimeRange, Pair<Long, StatsState.Success>>()
    private val CACHE_DURATION = 5 * 60 * 1000L // 5 minutes
    private var statsJob: Job? = null

    fun loadStatsForRange(range: com.example.appblocker.repository.TimeRange) {
        val now = System.currentTimeMillis()
        val cached = statsCache[range]
        if (cached != null && now - cached.first < CACHE_DURATION) {
            _statsState.value = cached.second
            _currentRangeStats.value = cached.second.appUsage
            return
        }

        _statsState.value = StatsState.Loading
        statsJob?.cancel()
        statsJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val events = statsRepository.getUsageForRange(range)
                val processed = processUsageEvents(events)
                
                val chartData: List<Long>
                val labels: List<String>
                val totalTime = processed.sumOf { it.usageTimeMillis }
                var avgTime = totalTime

                when (range) {
                    com.example.appblocker.repository.TimeRange.DAY -> {
                        val hourly = statsRepository.getTodayHourlyUsage()
                        // Group into 12 buckets (2 hours each) for the UI
                        val buckets = LongArray(12)
                        hourly.forEach { bucket ->
                            val idx = bucket.hour / 2
                            if (idx in 0..11) buckets[idx] += bucket.durationMillis
                        }
                        chartData = buckets.toList()
                        labels = listOf("00h","02h","04h","06h","08h","10h","12h","14h","16h","18h","20h","22h")
                        _hourlyUsage.value = hourly
                    }
                    com.example.appblocker.repository.TimeRange.WEEK -> {
                        chartData = statsRepository.getDailyUsageForWeek()
                        labels = listOf("M", "T", "W", "T", "F", "S", "S")
                        val todayIndex = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
                        avgTime = if (todayIndex >= 0) totalTime / (todayIndex + 1) else totalTime
                    }
                    com.example.appblocker.repository.TimeRange.MONTH -> {
                        // Month view in UI shows 30 days or 5 weeks?
                        // The original fragment showed 5 weeks.
                        // Let's stick to what StatsRepository provides for now, but the fragment had custom logic for weeks.
                        // Actually, let's use the repository's 30 days and provide appropriate labels or adjust.
                        // For the horizontal RecyclerView chart, 30 bars might be better.
                        chartData = statsRepository.getDailyUsageForMonth()
                        labels = (1..30).map { it.toString() }
                        val todayDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                        avgTime = if (todayDay > 0) totalTime / todayDay else totalTime
                    }
                    com.example.appblocker.repository.TimeRange.YEAR -> {
                        chartData = statsRepository.getMonthlyUsageForYear()
                        labels = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
                        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
                        avgTime = if (currentMonth >= 0) totalTime / (currentMonth + 1) else totalTime
                    }
                }

                val successState = StatsState.Success(
                    range = range,
                    appUsage = processed,
                    chartData = chartData,
                    labels = labels,
                    totalTimeMillis = totalTime,
                    avgTimeMillis = avgTime
                )
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    statsCache[range] = now to successState
                    _statsState.value = successState
                    _currentRangeStats.value = processed
                }
                
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _statsState.value = StatsState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    fun loadUsageStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val todayEvents = statsRepository.getTodayUsageFromEvents()
            _todayUsageRawStats.value = todayEvents
            
            val todayProcessed = processUsageEvents(todayEvents)
            _todayUsageStats.value = todayProcessed
            
            _hourlyUsage.value = statsRepository.getTodayHourlyUsage()
            _focusMetrics.value = statsRepository.getTodayFocusMetrics()

            // Default to today
            _currentRangeStats.value = todayProcessed
        }
    }

    private fun processUsageEvents(events: List<com.example.appblocker.repository.AppUsageEventData>): List<AppUsageInfo> {
        val pm = getApplication<Application>().packageManager
        return events.mapNotNull { event ->
            try {
                val appInfo = pm.getApplicationInfo(event.packageName, 0)
                AppUsageInfo(
                    name = pm.getApplicationLabel(appInfo).toString(),
                    packageName = event.packageName,
                    icon = pm.getApplicationIcon(appInfo),
                    usageTimeMillis = event.totalTimeInForeground
                )
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.usageTimeMillis }
    }

    fun setPassword(password: String) {
        viewModelScope.launch {
            val currentSettings = settings.value ?: AppSettings()
            settingsDao.updateSettings(currentSettings.copy(password = password))
        }
    }

    fun updatePin(pin: String) {
        viewModelScope.launch {
            val currentSettings = settings.value ?: AppSettings()
            settingsDao.updateSettings(currentSettings.copy(password = pin))
        }
    }

    fun setLoggedIn(isLoggedIn: Boolean, email: String? = null) {
        viewModelScope.launch {
            val currentSettings = settings.value ?: AppSettings()
            settingsDao.updateSettings(currentSettings.copy(isLoggedIn = isLoggedIn, userEmail = email))
            if (isLoggedIn) {
                restorePurchases()
            }
        }
    }

    fun setStartOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            val currentSettings = settings.value ?: AppSettings()
            settingsDao.updateSettings(currentSettings.copy(startOnBoot = enabled))
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            val currentSettings = settings.value ?: AppSettings()
            settingsDao.updateSettings(currentSettings.copy(language = lang))
        }
    }

    fun setCustomBlockMessage(message: String) {
        viewModelScope.launch {
            val currentSettings = settings.value ?: AppSettings()
            settingsDao.updateSettings(currentSettings.copy(customBlockMessage = message))
        }
    }

    fun setShowMotivationalQuote(show: Boolean) {
        viewModelScope.launch {
            val currentSettings = settings.value ?: AppSettings()
            settingsDao.updateSettings(currentSettings.copy(showMotivationalQuote = show))
        }
    }

    fun setNotificationSetting(type: String, enabled: Boolean) {
        viewModelScope.launch {
            val currentSettings = settings.value ?: AppSettings()
            val newSettings = when (type) {
                "started" -> currentSettings.copy(notifyBlockStarted = enabled)
                "ended" -> currentSettings.copy(notifyBlockEnded = enabled)
                "summary" -> currentSettings.copy(notifyDailySummary = enabled)
                "streak" -> currentSettings.copy(notifyStreakAlerts = enabled)
                else -> currentSettings
            }
            settingsDao.updateSettings(newSettings)
        }
    }

    fun triggerBackup() {
        viewModelScope.launch {
            val currentSettings = settings.value ?: AppSettings()
            settingsDao.updateSettings(currentSettings.copy(lastBackup = System.currentTimeMillis()))
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch {
            val currentSettings = settings.value ?: AppSettings()
            settingsDao.updateSettings(currentSettings.copy(theme = theme))
        }
    }

    fun setAppLaunchPin(pin: String?) {
        viewModelScope.launch {
            val currentSettings = settings.value ?: AppSettings()
            settingsDao.updateSettings(currentSettings.copy(appLaunchPin = pin))
        }
    }

    fun toggleStrictMode(enabled: Boolean) {
        viewModelScope.launch {
            val currentSettings = settings.value ?: AppSettings()
            val passwordToUse = currentSettings.password ?: "1234"
            // Prevent disabling if a session is active
            if (!enabled && isStrictBlockActive.value) {
                return@launch
            }
            settingsDao.updateSettings(currentSettings.copy(
                isStrictModeEnabled = enabled,
                password = passwordToUse
            ))
        }
    }

    fun startQuickBlock(durationMinutes: Int) {
        viewModelScope.launch {
            val endTime = if (durationMinutes == -1) {
                Long.MAX_VALUE
            } else {
                System.currentTimeMillis() + (durationMinutes.toLong() * 60 * 1000)
            }
            settingsDao.updateQuickBlockEndTime(endTime)
        }
    }

    fun endQuickBlock() {
        viewModelScope.launch {
            settingsDao.updateQuickBlockEndTime(0)
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val packages = try {
                pm.getInstalledApplications(0)
            } catch (e: Exception) {
                emptyList()
            }
            val apps = packages.mapNotNull { appInfo ->
                val pkgName = appInfo.packageName
                if (pkgName == getApplication<Application>().packageName) return@mapNotNull null
                
                val name = pm.getApplicationLabel(appInfo).toString()
                val isLauncher = pm.getLaunchIntentForPackage(pkgName) != null
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isBrowser = pkgName.contains("browser", ignoreCase = true) || 
                               pkgName.contains("chrome", ignoreCase = true)

                if (isLauncher || !isSystem || isBrowser) {
                    val category = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        appInfo.category
                    } else {
                        -1
                    }
                    
                    try {
                        AppInfo(
                            name = name,
                            packageName = pkgName,
                            icon = pm.getApplicationIcon(appInfo), // keep it for other screens to avoid breaking compose compose views
                            isBlocked = false,
                            category = category
                        )
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }
            }.distinctBy { it.packageName }
                .sortedBy { it.name }
            
            _allApps.value = apps
        }
    }

    fun toggleBlock(appInfo: AppInfo) {
        viewModelScope.launch {
            if (appInfo.packageName == getApplication<Application>().packageName) return@launch
            if (appInfo.isBlocked && isStrictBlockActive.value) return@launch
            if (appInfo.isBlocked) {
                dao.unblockApp(appInfo.packageName)
            } else {
                dao.blockApp(BlockedApp(pattern = appInfo.packageName, isKeyword = false))
            }
            
            // Dual save for accessibility service fast sync
            val allBlocked = dao.getBlockedAppsList()
            val packages = allBlocked.filter { !it.isKeyword && it.pattern != getApplication<Application>().packageName }.map { it.pattern }.toSet()
            val prefs = getApplication<Application>().getSharedPreferences("block_prefs", Context.MODE_PRIVATE)
            prefs.edit().putStringSet("blocked_apps", packages).apply()
            Log.d("BLOCKER", "Saved blocked_apps to prefs: $packages")
        }
    }

    fun addKeywordBlock(keyword: String) {
        viewModelScope.launch {
            dao.blockApp(BlockedApp(pattern = keyword, isKeyword = true))
        }
    }
    
    fun removeKeywordBlock(keyword: String) {
        viewModelScope.launch {
            if (isStrictBlockActive.value) return@launch
            dao.unblockApp(keyword)
        }
    }

    fun saveInternetSchedule(schedule: InternetSchedule) {
        viewModelScope.launch {
            scheduleDao.insertSchedule(schedule)
        }
    }

    fun pauseSchedule(id: Int, durationMillis: Long) {
        viewModelScope.launch {
            val schedule = schedules.value.find { it.id == id } ?: return@launch
            val pauseUntil = System.currentTimeMillis() + durationMillis
            scheduleDao.insertSchedule(schedule.copy(pauseUntil = pauseUntil))
        }
    }

    fun resumeSchedule(id: Int) {
        viewModelScope.launch {
            val schedule = schedules.value.find { it.id == id } ?: return@launch
            scheduleDao.insertSchedule(schedule.copy(pauseUntil = 0))
        }
    }

    fun checkConflict(newSchedule: InternetSchedule): List<InternetSchedule> {
        return schedules.value.filter { existing ->
            existing.id != newSchedule.id &&
                    daysOverlap(existing.days, newSchedule.days) &&
                    timeOverlap(existing.startHour, existing.startMinute, existing.endHour, existing.endMinute,
                        newSchedule.startHour, newSchedule.startMinute, newSchedule.endHour, newSchedule.endMinute)
        }
    }

    private fun daysOverlap(mask1: Int, mask2: Int): Boolean {
        return (mask1 and mask2) != 0
    }

    private fun timeOverlap(h1s: Int, m1s: Int, h1e: Int, m1e: Int, h2s: Int, m2s: Int, h2e: Int, m2e: Int): Boolean {
        val s1 = h1s * 60 + m1s
        val e1 = if (h1e * 60 + m1e < s1) (h1e * 60 + m1e) + 1440 else h1e * 60 + m1e
        val s2 = h2s * 60 + m2s
        val e2 = if (h2e * 60 + m2e < s2) (h2e * 60 + m2e) + 1440 else h2e * 60 + m2e
        return s1 < e2 && s2 < e1
    }

    fun deleteInternetSchedule(id: Int) {
        viewModelScope.launch {
            if (isStrictBlockActive.value) return@launch
            scheduleDao.deleteSchedule(id)
        }
    }

    fun toggleInternetSchedule(id: Int, enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled && isStrictBlockActive.value) return@launch
            scheduleDao.toggleSchedule(id, enabled)
        }
    }

    fun toggleAllApps(block: Boolean) {
        viewModelScope.launch {
            if (!block && isStrictBlockActive.value) return@launch
            _allApps.value.forEach { app ->
                if (block && !app.isBlocked) {
                    dao.blockApp(BlockedApp(app.packageName))
                } else if (!block && app.isBlocked) {
                    dao.unblockApp(app.packageName)
                }
            }
            loadInstalledApps()
        }
    }

    fun addWebsite(input: String) {
        val domain = input.trim().lowercase()
            .removePrefix("web:")
            .removePrefix("https://").removePrefix("http://").removePrefix("www.")
            .split("/")[0]
        if (domain.isEmpty()) return
        
        viewModelScope.launch {
            dao.blockApp(BlockedApp(pattern = "web:$domain", isKeyword = true))
            _webs.value = _webs.value + com.example.appblocker.adapter.WebItem(domain, true)
        }
    }

    fun toggleWeb(domain: String) {
        viewModelScope.launch {
            val isBlocked = blockedApps.value.any { it.isKeyword && it.pattern == "web:$domain" }
            if (isBlocked) {
                if (isStrictBlockActive.value) return@launch
                dao.unblockApp("web:$domain")
            } else {
                dao.blockApp(BlockedApp(pattern = "web:$domain", isKeyword = true))
            }
        }
    }

    fun toggleAllowApp(appInfo: AppInfo) {
        viewModelScope.launch {
            val isAllowed = allowedApps.value.any { it.packageName == appInfo.packageName }
            if (isAllowed) {
                allowedDao.removeAllowedApp(appInfo.packageName)
            } else {
                allowedDao.allowApp(AllowedApp(appInfo.packageName))
            }
        }
    }

    fun createScheduleFromTemplate(template: com.example.appblocker.data.Template) {
        viewModelScope.launch {
            val newSchedule = InternetSchedule(
                name = template.name,
                startHour = template.startHour,
                startMinute = template.startMin,
                endHour = template.endHour,
                endMinute = template.endMin,
                days = template.daysMask,
                isEnabled = true,
                apps = template.defaultApps
            )
            scheduleDao.insertSchedule(newSchedule)
        }
    }
}
