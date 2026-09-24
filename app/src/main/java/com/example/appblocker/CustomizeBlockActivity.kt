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

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val accPrefs = getSharedPreferences("accessibility_block_prefs", Context.MODE_PRIVATE)

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

        binding.switchYoutubeShorts.isChecked = prefs.getBoolean(KEY_YOUTUBE_SHORTS, false)
        binding.switchFacebookReels.isChecked = prefs.getBoolean(KEY_FACEBOOK_REELS, false)
        binding.switchWhatsappChannels.isChecked = prefs.getBoolean(KEY_WHATSAPP_CHANNELS, false)

        binding.switchYoutubeShorts.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_YOUTUBE_SHORTS, isChecked).apply()
            accPrefs.edit().putBoolean(KEY_YOUTUBE_SHORTS, isChecked).apply()
        }

        binding.switchFacebookReels.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_FACEBOOK_REELS, isChecked).apply()
            accPrefs.edit().putBoolean(KEY_FACEBOOK_REELS, isChecked).apply()
        }

        binding.switchWhatsappChannels.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_WHATSAPP_CHANNELS, isChecked).apply()
            accPrefs.edit().putBoolean(KEY_WHATSAPP_CHANNELS, isChecked).apply()
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
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionsCard()
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
}
