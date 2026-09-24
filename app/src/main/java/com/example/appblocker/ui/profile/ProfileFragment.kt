package com.example.appblocker.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.appblocker.AppIconManager
import com.example.appblocker.MainViewModel
import com.example.appblocker.PermissionManager
import com.example.appblocker.R
import com.example.appblocker.ThemeManager
import com.example.appblocker.security.PermissionChecker
import com.example.appblocker.databinding.FragmentProfileBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private var tapCount = 0
    private val resetHandler = Handler(Looper.getMainLooper())
    private val resetRunnable = Runnable { 
        tapCount = 0
        _binding?.tvFooterTap?.animate()?.alpha(0f)?.setDuration(300)?.start()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRows()
        setupAchievements()
        setupClickListeners()
        observeViewModel()
        observeSecurity()
        setupAnimations()
        updateAppIconSubtitle()
        observeAppearance()
        setupFooter()

        view.post { refreshSecurityPrivacyRow() }
    }

    private fun setupFooter() {
        binding.tvFooterMade.setOnClickListener {
            tapCount++
            triggerHapticFeedback()
            animateClick(it)
            
            resetHandler.removeCallbacks(resetRunnable)
            resetHandler.postDelayed(resetRunnable, 3000)

            if (tapCount >= 3 && tapCount < 7) {
                binding.tvFooterTap.text = "${7 - tapCount} more..."
                binding.tvFooterTap.animate().alpha(1f).setDuration(200).start()
            }

            if (tapCount == 7) {
                triggerHeavyHaptic()
                Toast.makeText(requireContext(), "You found the secret! 🇺🇬💜", Toast.LENGTH_SHORT).show()
                tapCount = 0
                binding.tvFooterTap.animate().alpha(0f).setDuration(200).start()
            }
        }

        binding.tvFooterVersion.setOnClickListener {
            triggerHapticFeedback()
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Version", "38.1.0 Build 45")
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Version copied 📋", Toast.LENGTH_SHORT).show()
        }
        
        binding.tvFooterVersion.setOnLongClickListener {
            triggerHapticFeedback()
            DeveloperMenuBottomSheet().show(childFragmentManager, "DeveloperMenu")
            true
        }
    }

    private fun triggerHeavyHaptic() {
        val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    private fun openUrl(url: String) {
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setToolbarColor(ContextCompat.getColor(requireContext(), R.color.primary_purple))
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(requireContext(), Uri.parse(url))
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }
    }

    private fun observeAppearance() {
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                ThemeManager.getThemeModeFlow(requireContext()),
                ThemeManager.getAccentFlow(requireContext())
            ) { theme, accent ->
                val themeTitle = theme.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                val accentTitle = accent.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                
                binding.rowTheme.subtitle.text = "$themeTitle • $accentTitle"
                ThemeManager.getAccentColor(accent)
            }.collectLatest { accentColor ->
                applyAccentColor(accentColor)
            }
        }
    }

    private fun applyAccentColor(color: Int) {
        binding.scoreRing.setAccentColor(color)
        
        binding.rowAppIcon.iconBg.backgroundTintList = ColorStateList.valueOf(color)
        binding.achievementBlocks.iconBg.backgroundTintList = ColorStateList.valueOf(color)
        binding.rowPremium.proBadge.backgroundTintList = ColorStateList.valueOf(color)
        binding.statFocusedHint.setTextColor(color)
        
        binding.tvFooterMade.text = android.text.Html.fromHtml("Made with <font color='${String.format("#%06X", 0xFFFFFF and color)}'>💜</font> in Uganda", android.text.Html.FROM_HTML_MODE_LEGACY)
    }

    private fun updateAppIconSubtitle() {
        val currentIcon = AppIconManager.getCurrentIcon(requireContext())
        binding.rowAppIcon.subtitle.text = "Current: ${currentIcon.displayName}"
    }

    private fun setupRows() {
        binding.rowPremium.apply {
            title.text = getString(R.string.row_premium_title)
            subtitle.text = getString(R.string.row_premium_subtitle)
            iconEmoji.text = "⭐"
            iconBg.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.bg_gold_icon))
            proBadge.visibility = View.VISIBLE
        }

        binding.rowAppIcon.apply {
            title.text = getString(R.string.row_app_icon_title)
            subtitle.text = getString(R.string.row_app_icon_subtitle)
            iconEmoji.text = "🎨"
            iconBg.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.bg_purple_icon))
        }

        binding.rowTheme.apply {
            title.text = getString(R.string.row_appearance_title)
            subtitle.text = getString(R.string.row_appearance_subtitle)
            iconEmoji.text = "👁️"
            iconBg.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.bg_blue_icon))
        }

        binding.rowNotifications.apply {
            title.text = getString(R.string.row_notifications_title)
            subtitle.text = getString(R.string.row_notifications_subtitle)
            iconEmoji.text = "🔔"
            iconBg.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.bg_orange_icon))
        }

        binding.rowPrivacy.apply {
            title.text = getString(R.string.row_privacy_title)
            subtitle.text = getString(R.string.row_privacy_subtitle)
            iconEmoji.text = "🔒"
            iconBg.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.bg_red_icon))
        }
    }

    private fun setupAchievements() {
        binding.achievementStreak.apply {
            accentBar.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.accent_orange))
            iconEmoji.text = "🔥"
            iconBg.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.bg_orange_icon))
            title.text = "7 Day Streak"

            dotsContainer.visibility = View.VISIBLE
            progressBar.visibility = View.GONE
            appPlaceholders.visibility = View.GONE

            achievementRoot.setOnClickListener {
                triggerHapticFeedback()
                animateClick(it)
            }
        }

        binding.achievementDeepWork.apply {
            accentBar.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.accent_blue))
            iconEmoji.text = "🎯"
            iconBg.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.bg_blue_icon))
            title.text = "Deep Work"

            dotsContainer.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
            appPlaceholders.visibility = View.GONE

            achievementRoot.setOnClickListener {
                triggerHapticFeedback()
                animateClick(it)
            }
        }

        binding.achievementBlocks.apply {
            accentBar.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.accent_purple))
            iconEmoji.text = "🚧"
            iconBg.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.bg_purple_icon))
            title.text = "Apps Blocked"

            dotsContainer.visibility = View.GONE
            progressBar.visibility = View.GONE
            appPlaceholders.visibility = View.VISIBLE

            achievementRoot.setOnClickListener {
                triggerHapticFeedback()
                animateClick(it)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.focusScore.collectLatest { score ->
                binding.scoreValue.text = score.toString()
                binding.scoreRing.setProgress(score)
            }
        }
    }

    private fun updateStats() {
        val seconds = viewModel.focusTimeSeconds.value
        val hours = seconds / 3600
        val count = viewModel.blockedAppsTodayCount.value
        val streak = viewModel.currentStreak.value
        binding.statFocusedValue.text = "${hours}h"
        binding.statBlocksValue.text = count.toString()
        binding.statStreakValue.text = "${streak}d"
    }

    private fun observeSecurity() {
        // We handle this via refreshSecurityPrivacyRow now
    }

    private fun setupAnimations() {
        val fadeIn = AnimationUtils.loadAnimation(context, android.R.anim.fade_in)
        binding.heroCard.startAnimation(fadeIn)
    }

    private fun setupClickListeners() {
        binding.rowPremium.rootCard.setOnClickListener { triggerHapticFeedback(); animateClick(it); findNavController().navigate(R.id.navigation_paywall) }
        binding.rowAppIcon.rootCard.setOnClickListener { triggerHapticFeedback(); animateClick(it); AppIconBottomSheetFragment().show(childFragmentManager, "AppIconPicker") }
        binding.rowTheme.rootCard.setOnClickListener { triggerHapticFeedback(); animateClick(it); AppearanceBottomSheetFragment().show(parentFragmentManager, "AppearancePicker") }
        binding.rowNotifications.rootCard.setOnClickListener { triggerHapticFeedback(); animateClick(it); NotificationsBottomSheetFragment().show(parentFragmentManager, "notifications") }
        binding.rowPrivacy.rootCard.setOnClickListener { triggerHapticFeedback(); animateClick(it); findNavController().navigate(R.id.action_profile_to_securityPrivacy) }
        binding.rowPrivacyPolicy.setOnClickListener { triggerHapticFeedback(); openUrl("https://focusapp.ug/privacy") }
        binding.rowTerms.setOnClickListener { triggerHapticFeedback(); openUrl("https://focusapp.ug/terms") }
        binding.rowLicenses.setOnClickListener { triggerHapticFeedback(); OpenSourceLicensesBottomSheet().show(childFragmentManager, "licenses") }
    }

    private fun getLivePrivacyCountForProfile(): Int {
        val ctx = requireContext()
        var total = 0

        var permCount = 0
        try {
            val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            if (appOps.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), ctx.packageName) == android.app.AppOpsManager.MODE_ALLOWED) permCount++
        } catch (e: Exception) {}
        try {
            if (android.provider.Settings.Secure.getString(ctx.contentResolver, "enabled_accessibility_services")?.contains(ctx.packageName) == true) permCount++
        } catch (e: Exception) {}
        try { if (android.provider.Settings.canDrawOverlays(ctx)) permCount++ } catch (e: Exception) {}
        try {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (pm.isIgnoringBatteryOptimizations(ctx.packageName)) permCount++
        } catch (e: Exception) { permCount++ }
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val am = ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                if (am.canScheduleExactAlarms()) permCount++
            } else permCount++
        } catch (e: Exception) { permCount++ }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) permCount++
            } else permCount++
        } catch (e: Exception) { permCount++ }
        if (permCount == 6) total++

        val sp1 = ctx.getSharedPreferences("app_prefs", 0)
        val sp2 = ctx.getSharedPreferences("security_prefs", 0)
        
        if (sp1.getBoolean("app_lock_enabled", false) || sp2.getBoolean("app_lock_enabled", false) || sp1.getBoolean("isAppLockEnabled", false)) total++
        if (sp1.getBoolean("stealth_mode_enabled", false) || sp2.getBoolean("stealth_mode_enabled", false) || sp1.getBoolean("stealth_enabled", false)) total++

        return total
    }

    private fun refreshSecurityPrivacyRow() {
        val cnt = getLivePrivacyCountForProfile()
        val text = if (cnt == 3) "Unlocked • 3/3 OK" else "Unlocked • $cnt/3"
        val color = if (cnt == 3) "#4CAF50" else "#FF5252"

        try {
            fun scan(vg: ViewGroup) {
                for (i in 0 until vg.childCount) {
                    val c = vg.getChildAt(i)
                    if (c is TextView) {
                        if (c.text.toString().contains("Unlocked") && c.text.toString().contains("/3")) {
                            c.text = text
                            try { c.setTextColor(Color.parseColor(color)) } catch (e: Exception) {}
                        }
                    } else if (c is ViewGroup) { scan(c) }
                }
            }
            view?.let { if (it is ViewGroup) scan(it) }
        } catch (e: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        refreshSecurityPrivacyRow()
    }

    private fun triggerHapticFeedback() {
        val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(10)
        }
    }

    private fun animateClick(view: View) {
        view.animate().scaleX(0.98f).scaleY(0.98f).setDuration(100).withEndAction { view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start() }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
