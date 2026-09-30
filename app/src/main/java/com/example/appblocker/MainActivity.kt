package com.example.appblocker
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.example.appblocker.security.AppLockManager
import com.example.appblocker.security.BiometricHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var navController: NavController
    private lateinit var navBlock: TextView
    private lateinit var navInsights: TextView
    private lateinit var navScheduler: TextView
    private lateinit var navProfile: TextView
    private lateinit var bottomContainer: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try{
            val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            if(!pm.isIgnoringBatteryOptimizations(packageName)){
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }catch(_:Exception){}

        navBlock = findViewById(R.id.nav_block)
        navInsights = findViewById(R.id.nav_insights)
        navScheduler = findViewById(R.id.nav_scheduler)
        navProfile = findViewById(R.id.nav_profile)
        bottomContainer = findViewById(R.id.bottom_nav_container)

        NotificationHelper.createNotificationChannels(this)

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val done = prefs.getBoolean("onboarding_done", false)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        navController.graph = navController.navInflater.inflate(if (done) R.navigation.mobile_navigation else R.navigation.nav_graph_onboarding)

        if (done) {
            bottomContainer.visibility = View.VISIBLE
            setupPillBar()
            checkAppLock()
            handleIntent(intent)
        } else {
            bottomContainer.visibility = View.GONE
        }
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra("open_tab")?.let { tab ->
            when (tab) {
                "block" -> selectTab(R.id.navigation_block, navBlock)
                "insights" -> selectTab(R.id.navigation_insights, navInsights)
                "schedule" -> selectTab(R.id.navigation_schedule, navScheduler)
                "profile" -> selectTab(R.id.navigation_profile, navProfile)
            }
        }
    }

    private fun checkAppLock() {
        lifecycleScope.launch {
            if (AppLockManager.shouldLock(this@MainActivity)) {
                if (BiometricHelper.canAuthenticate(this@MainActivity)) {
                    BiometricHelper.showBiometricPrompt(
                        activity = this@MainActivity,
                        onSuccess = {
                            AppLockManager.isUnlocked = true
                        },
                        onError = { error ->
                            Toast.makeText(this@MainActivity, "Authentication failed: $error", Toast.LENGTH_SHORT).show()
                            finishAffinity()
                        }
                    )
                }
            }
        }
    }

    private fun setupPillBar() {
        navBlock.setOnClickListener { selectTab(R.id.navigation_block, navBlock) }
        navInsights.setOnClickListener { selectTab(R.id.navigation_insights, navInsights) }
        navScheduler.setOnClickListener { selectTab(R.id.navigation_schedule, navScheduler) }
        navProfile.setOnClickListener { selectTab(R.id.navigation_profile, navProfile) }
        
        // Set initial state based on current destination
        updateTabStyles(navController.currentDestination?.id ?: R.id.navigation_block)
    }

    private fun selectTab(destId: Int, activeView: TextView) {
        try {
            if (navController.currentDestination?.id != destId) {
                navController.navigate(destId)
                updateTabStyles(destId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateTabStyles(currentId: Int) {
        val tabs = mapOf(
            R.id.navigation_block to navBlock,
            R.id.navigation_insights to navInsights,
            R.id.navigation_schedule to navScheduler,
            R.id.navigation_profile to navProfile
        )

        tabs.forEach { (id, view) ->
            if (id == currentId) {
                view.setBackgroundResource(R.drawable.bg_pill_active)
                view.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            } else {
                view.setBackgroundResource(0)
                view.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }
}