package com.example.appblocker.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.example.appblocker.MainActivity
import com.example.appblocker.R
import com.example.appblocker.databinding.ActivityOnboardingBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val navController = navHostFragment?.navController

        val startDest = intent.getStringExtra("START_DESTINATION")
        if (startDest == "paywall") {
            navController?.navigate(R.id.paywallFragment)
        } else if (startDest == "permissions") {
            navController?.navigate(R.id.setupPermissionFragment)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (intent.getStringExtra("START_DESTINATION") == "paywall") {
                    finish()
                } else {
                    val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                    val navController = navHostFragment?.navController
                    if (navController?.popBackStack() == false) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    fun nextPage() {
        // This was used for ViewPager2. Since we switched to NavComponent, 
        // this can be empty or handle specific navigation if needed.
    }

    fun finishOnboarding() {
        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("onboarding_done", true)
            apply()
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("TARGET_TAB", 0)
            putExtra("SKIP_SPLASH", true)
        }
        startActivity(intent)
        finish()
    }
}