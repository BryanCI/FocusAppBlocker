package com.example.appblocker.ui.profile

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.appblocker.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PremiumPaywallActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_premium_paywall)
        
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, InAppPremiumPaywallFragment())
                .commit()
        }
    }
}
