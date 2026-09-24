package com.example.appblocker.ui.profile

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.example.appblocker.onboarding.OnboardingPersonalizeActivity
import com.example.appblocker.R

import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import com.example.appblocker.databinding.ActivityAccountManagementBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.activity.viewModels
import com.example.appblocker.MainViewModel

@AndroidEntryPoint
class AccountManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountManagementBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var upgradeLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Account Management"
        
        upgradeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if(result.resultCode == RESULT_OK) {
                refreshAccountUI()
            }
        }
        
        binding.btnUpgradeAccount.setOnClickListener {
            val intent = Intent(this, OnboardingPersonalizeActivity::class.java)
            intent.putExtra("IS_UPGRADE_FLOW", true)
            upgradeLauncher.launch(intent)
        }

        binding.btnViewPremium.setOnClickListener {
            val intent = Intent(this, PremiumPaywallActivity::class.java)
            startActivity(intent)
        }
        
        binding.btnSignOut.setOnClickListener { signOut() }
        
        refreshAccountUI() // Load on start
        observePremiumStatus()
    }
    
    private fun observePremiumStatus() {
        lifecycleScope.launch {
            viewModel.isPremium.collectLatest { isPremium ->
                if (isPremium) {
                    binding.tvPremiumStatus.text = "Active"
                    binding.btnViewPremium.text = "Manage"
                } else {
                    binding.tvPremiumStatus.text = "Unlock all features"
                    binding.btnViewPremium.text = "View"
                }
            }
        }
    }

    private fun refreshAccountUI() {
        val user = FirebaseAuth.getInstance().currentUser
        
        if(user != null && !user.isAnonymous) {
            binding.tvUserName.text = user.displayName ?: user.email ?: "User"
            binding.cardGuestMode.visibility = View.GONE
        } else {
            binding.tvUserName.text = "Guest User"
            binding.cardGuestMode.visibility = View.VISIBLE
        }
    }
    
    private fun signOut() {
        FirebaseAuth.getInstance().signOut()
        refreshAccountUI()
    }
    
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
