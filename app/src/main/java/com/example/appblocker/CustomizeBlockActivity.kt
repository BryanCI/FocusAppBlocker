package com.example.appblocker

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.appblocker.databinding.ActivityCustomizeBlockBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CustomizeBlockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomizeBlockBinding
    private val viewModel: MainViewModel by viewModels()

    // Universal Fix: standardizing preference access
    private val prefs by lazy { FocusSessionManager.getDeviceProtectedPrefs(this, BlockerConfig.BLOCKER_PREFS) }

    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            BlockerConfig.KEY_STRICT_ENABLED -> {
                binding.switchCommitMode.isChecked = prefs.getBoolean(BlockerConfig.KEY_STRICT_ENABLED, false)
                updateChangePasswordVisibility()
            }
            BlockerConfig.KEY_STRICT_PASSWORD -> {
                updateChangePasswordVisibility()
            }
        }
    }

    companion object {
        const val PREFS_NAME = "block_prefs"
        const val KEY_YOUTUBE_SHORTS = "block_youtube_shorts"
        const val KEY_FACEBOOK_REELS = "block_facebook_reels"
        const val KEY_WHATSAPP_CHANNELS = "block_whatsapp_channels"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomizeBlockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val blockPrefs = FocusSessionManager.getDeviceProtectedPrefs(this, PREFS_NAME)
        val accPrefs = FocusSessionManager.getDeviceProtectedPrefs(this, "accessibility_block_prefs")

        binding.btnBack.setOnClickListener { finish() }

        binding.cardPermissionsHub.setOnClickListener {
            val intent = Intent(this, com.example.appblocker.onboarding.OnboardingActivity::class.java).apply {
                putExtra("START_DESTINATION", "permissions")
            }
            startActivity(intent)
        }

        binding.itemApps.setOnClickListener {
            val intent = Intent(this, AppPickerActivity::class.java).apply {
                putExtra(AppPickerActivity.EXTRA_PICKER_TYPE, AppPickerActivity.TYPE_BLOCKED)
            }
            startActivity(intent)
        }

        binding.itemKeywords.setOnClickListener {
            startActivity(Intent(this, KeywordsActivity::class.java))
        }

        binding.itemAllowlist.setOnClickListener {
            val intent = Intent(this, AppPickerActivity::class.java).apply {
                putExtra(AppPickerActivity.EXTRA_PICKER_TYPE, AppPickerActivity.TYPE_ALLOWED)
            }
            startActivity(intent)
        }

        binding.switchYoutubeShorts.isChecked = blockPrefs.getBoolean(KEY_YOUTUBE_SHORTS, false)
        binding.switchFacebookReels.isChecked = blockPrefs.getBoolean(KEY_FACEBOOK_REELS, false)
        binding.switchWhatsappChannels.isChecked = blockPrefs.getBoolean(KEY_WHATSAPP_CHANNELS, false)

        binding.switchYoutubeShorts.setOnCheckedChangeListener { _, isChecked ->
            blockPrefs.edit().putBoolean(KEY_YOUTUBE_SHORTS, isChecked).apply()
            accPrefs.edit().putBoolean(KEY_YOUTUBE_SHORTS, isChecked).apply()
        }

        binding.switchFacebookReels.setOnCheckedChangeListener { _, isChecked ->
            blockPrefs.edit().putBoolean(KEY_FACEBOOK_REELS, isChecked).apply()
            accPrefs.edit().putBoolean(KEY_FACEBOOK_REELS, isChecked).apply()
        }

        binding.switchWhatsappChannels.setOnCheckedChangeListener { _, isChecked ->
            blockPrefs.edit().putBoolean(KEY_WHATSAPP_CHANNELS, isChecked).apply()
            accPrefs.edit().putBoolean(KEY_WHATSAPP_CHANNELS, isChecked).apply()
        }

        binding.layoutChangePassword.setOnClickListener { showChangePasswordDialog() }
        
        // Register listener on the correct prefs for Strict Mode synchronization
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        updateChangePasswordVisibility()

        binding.switchCommitMode.setOnClickListener {
            val isCurrentlyEnabled = prefs.getBoolean(BlockerConfig.KEY_STRICT_ENABLED, false)
            val pin = prefs.getString(BlockerConfig.KEY_STRICT_PASSWORD, null)
            
            // Revert switch state immediately, let the logic handle it
            binding.switchCommitMode.isChecked = isCurrentlyEnabled

            if (!isCurrentlyEnabled) {
                if (pin.isNullOrEmpty()) {
                    showStrictPinSheet(StrictModePinBottomSheet.Mode.SET)
                } else {
                    prefs.edit().putBoolean(BlockerConfig.KEY_STRICT_ENABLED, true).apply()
                    viewModel.toggleStrictMode(true)
                    Toast.makeText(this, "Strict Mode enabled 🔒", Toast.LENGTH_SHORT).show()
                }
            } else {
                showStrictPinSheet(StrictModePinBottomSheet.Mode.VERIFY)
            }
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.settings.collect { settings ->
                        if (settings != null) {
                            binding.switchCommitMode.isChecked = settings.isStrictModeEnabled
                        }
                    }
                }
                launch {
                    viewModel.blockedApps.collect { blockedList ->
                        val appsList = blockedList.filter { !it.isKeyword }
                        val keywordsList = blockedList.filter { it.isKeyword }
                        binding.tvAppsCount.text = appsList.size.toString()
                        binding.tvKeywordsCount.text = keywordsList.size.toString()
                        
                        updateAppsSubtitle(appsList)
                        updateKeywordsSubtitle(keywordsList)
                    }
                }
                launch {
                    viewModel.allowedApps.collect { allowedList ->
                        binding.tvAllowlistCount.text = allowedList.size.toString()
                        updateAllowlistSubtitle(allowedList)
                    }
                }
            }
        }
    }

    private fun updateAppsSubtitle(apps: List<com.example.appblocker.data.BlockedApp>) {
        if (apps.isEmpty()) {
            binding.tvAppsSubtitle.text = "None selected"
            return
        }
        
        val pm = packageManager
        val names = apps.take(2).map {
            try {
                val info = pm.getApplicationInfo(it.pattern, 0)
                pm.getApplicationLabel(info).toString()
            } catch (e: Exception) {
                it.pattern.split(".").lastOrNull() ?: it.pattern
            }
        }
        
        val text = names.joinToString(", ")
        binding.tvAppsSubtitle.text = if (apps.size > 2) "$text +${apps.size - 2}" else text
    }

    private fun updateKeywordsSubtitle(keywords: List<com.example.appblocker.data.BlockedApp>) {
        if (keywords.isEmpty()) {
            binding.tvKeywordsSubtitle.text = "None selected"
            return
        }
        
        val names = keywords.take(2).map { it.pattern }
        val text = names.joinToString(", ")
        binding.tvKeywordsSubtitle.text = if (keywords.size > 2) "$text +${keywords.size - 2}" else text
    }

    private fun updateAllowlistSubtitle(allowed: List<com.example.appblocker.data.AllowedApp>) {
        if (allowed.isEmpty()) {
            binding.tvAllowlistSubtitle.text = "None selected"
            return
        }
        
        val pm = packageManager
        val names = allowed.take(2).map {
            try {
                val info = pm.getApplicationInfo(it.packageName, 0)
                pm.getApplicationLabel(info).toString()
            } catch (e: Exception) {
                it.packageName.split(".").lastOrNull() ?: it.packageName
            }
        }
        
        val text = names.joinToString(", ")
        binding.tvAllowlistSubtitle.text = if (allowed.size > 2) "$text +${allowed.size - 2}" else text
    }

    override fun onResume() {
        super.onResume()
        updatePermissionsCard()
        updateChangePasswordVisibility()
        
        binding.switchCommitMode.isChecked = prefs.getBoolean(BlockerConfig.KEY_STRICT_ENABLED, false)
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    private fun updatePermissionsCard() {
        val context = this
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val usage = try {
            appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) { false }
        val accessibility = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.contains("${context.packageName}/${AppBlockerAccessibilityService::class.java.name}") == true
        val overlay = android.provider.Settings.canDrawOverlays(context)
        val battery = pm.isIgnoringBatteryOptimizations(context.packageName)
        val exact = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
        
        val missing = 5 - listOf(usage, accessibility, overlay, battery, exact).count { it }

        if (missing == 0) {
            binding.cardPermissionsHub.clearAnimation()
            binding.tvPermissionsHubTitle.text = "All Set"
            binding.tvPermissionsHubSubtitle.text = "System fully optimized"
            binding.tvPermissionsHubSubtitle.setTextColor(ContextCompat.getColor(this, R.color.premium_green))
            binding.ivPermissionsHubIcon.setImageResource(R.drawable.ic_check)
            binding.iconPermissionsHub.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.bg_success_icon_opal))
            binding.ivPermissionsHubIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.premium_green))
        } else {
            val pulse = AnimationUtils.loadAnimation(this, R.anim.premium_pulse)
            binding.cardPermissionsHub.startAnimation(pulse)
            binding.tvPermissionsHubTitle.text = "System Permissions"
            binding.tvPermissionsHubSubtitle.text = "$missing actions required"
            binding.tvPermissionsHubSubtitle.setTextColor(ContextCompat.getColor(this, R.color.premium_yellow))
            binding.ivPermissionsHubIcon.setImageResource(R.drawable.ic_warning_amber)
            binding.iconPermissionsHub.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.bg_warning_icon_opal))
            binding.ivPermissionsHubIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.premium_yellow))
        }
    }

    private fun showStrictPinSheet(mode: StrictModePinBottomSheet.Mode) {
        val correctPin = prefs.getString(BlockerConfig.KEY_STRICT_PASSWORD, null)
        val sheet = StrictModePinBottomSheet.newInstance(mode, correctPin)
        sheet.setListener(object : StrictModePinBottomSheet.StrictModePinListener {
            override fun onPinSet(pin: String) {
                // Rely on ViewModel for dual-write to DB and Prefs
                viewModel.updatePin(pin)
                viewModel.toggleStrictMode(true)
                updateChangePasswordVisibility()
                Toast.makeText(this@CustomizeBlockActivity, "PIN set & Strict Mode enabled 🔒", Toast.LENGTH_SHORT).show()
            }

            override fun onPinVerified() {
                viewModel.toggleStrictMode(false)
                updateChangePasswordVisibility()
                Toast.makeText(this@CustomizeBlockActivity, "Strict Mode disabled", Toast.LENGTH_SHORT).show()
            }

            override fun onCancel() {}
        })
        sheet.show(supportFragmentManager, "StrictPinSheet")
    }

    private fun updateChangePasswordVisibility() {
        try {
            val hasPass = !prefs.getString(BlockerConfig.KEY_STRICT_PASSWORD, "").isNullOrEmpty()
            binding.dividerStrict.visibility = if (hasPass) android.view.View.VISIBLE else android.view.View.GONE
            binding.layoutChangePassword.visibility = if (hasPass) android.view.View.VISIBLE else android.view.View.GONE
        } catch (e: Exception) {}
    }

    private fun showChangePasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_strict_pin, null)
        val dialogBinding = com.example.appblocker.databinding.DialogChangeStrictPinBinding.bind(dialogView)
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
            
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        
        dialogBinding.btnChange.setOnClickListener {
            val currentPinInput = dialogBinding.etCurrentPin.text.toString()
            val newPin = dialogBinding.etNewPin.text.toString()
            val confirmPin = dialogBinding.etConfirmPin.text.toString()
            
            val savedPin = prefs.getString(BlockerConfig.KEY_STRICT_PASSWORD, "")
            
            if (currentPinInput != savedPin) {
                Toast.makeText(this, "Incorrect current PIN", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (newPin.length < 4) {
                Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (newPin != confirmPin) {
                Toast.makeText(this, "New PINs do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            viewModel.updatePin(newPin)
            Toast.makeText(this, "PIN changed successfully", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        
        dialog.show()
    }
}
