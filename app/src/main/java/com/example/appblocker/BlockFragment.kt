package com.example.appblocker

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.example.appblocker.databinding.FragmentBlockBinding
import com.example.appblocker.security.PermissionChecker
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class BlockFragment : Fragment() {

    private var _binding: FragmentBlockBinding? = null
    private val binding get() = _binding


    private val viewModel: MainViewModel by activityViewModels()
    private var countdownJob: Job? = null
    private var currentIsAllGranted: Boolean? = null
    private var isPermissionsExpanded = false
    private var isStarting = false
    private var pollingJob: Job? = null
    private var breathingAnimator: AnimatorSet? = null
    private var successAnimator: ValueAnimator? = null
    private var warningAnimator: ValueAnimator? = null
    private var dotPulseAnimator: ObjectAnimator? = null

    private var missingCount = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBlockBinding.inflate(inflater, container, false)
        return _binding!!.root
    }

    private var timerJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val b = _binding ?: return
        b.settingsIcon.setOnClickListener {
            val intent = Intent(requireContext(), CustomizeBlockActivity::class.java)
            startActivity(intent)
        }

        b.btnFixNow.setOnClickListener {
            openPermissionHub()
        }

        b.ivProfile.setOnClickListener {
            val intent = Intent(requireContext(), com.example.appblocker.ui.profile.AccountManagementActivity::class.java)
            startActivity(intent)
        }

        b.cardCustomizeApps.setOnClickListener {
            val intent = Intent(requireContext(), CustomizeBlockActivity::class.java)
            startActivity(intent)
        }

        view.post {
            viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                viewModel.settings.collect { appSettings ->
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val innerB = _binding ?: return@withContext
                        if (appSettings != null) {
                            innerB.switchStrictMode.isChecked = appSettings.isStrictModeEnabled
                        }
                        updatePermissionHealth(isInitial = currentIsAllGranted == null)
                    }
                }
            }
        }

        b.switchStrictMode.setOnClickListener {
            val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val isCurrentlyOn = prefs.getBoolean("strict_mode", false)
            val pin = prefs.getString("strict_pin", null)
            val innerB = _binding ?: return@setOnClickListener
            innerB.switchStrictMode.isChecked = isCurrentlyOn

            if (!isCurrentlyOn) {
                if (pin.isNullOrEmpty()) {
                    showStrictPinSheet(StrictModePinBottomSheet.Mode.SET)
                } else {
                    prefs.edit().putBoolean("strict_mode", true).apply()
                    viewModel.toggleStrictMode(true)
                    innerB.switchStrictMode.isChecked = true
                    Toast.makeText(requireContext(), "Strict Mode enabled 🔒", Toast.LENGTH_SHORT).show()
                    triggerSuccessFeedback()
                    updateStrictModeUI()
                }
            } else {
                showStrictPinSheet(StrictModePinBottomSheet.Mode.VERIFY)
            }
        }

        setupQuickBlockListeners()
        setupEndEarlyHoldListener()

        b.layoutBlockedPreviewContainer.setOnClickListener {
            val intent = Intent(requireContext(), CustomizeBlockActivity::class.java)
            startActivity(intent)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isStrictBlockActive.collect { isActive ->
                updateStrictModeUI()
                if (FocusSessionManager.isSessionActive(requireContext())) {
                    showActiveState()
                } else {
                    val innerB = _binding ?: return@collect
                    innerB.layoutActive.visibility = View.GONE
                    innerB.layoutIdle.visibility = View.VISIBLE
                }
            }
        }

        // Bug 1 Fix: Use Flow-based timer
        timerJob?.cancel()
        timerJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.remainingMillisFlow.collect { remaining ->
                    val innerB = _binding ?: return@collect
                    if (remaining <= 0) {
                        if (innerB.layoutActive.visibility == View.VISIBLE) {
                            showActiveState() // refreshes to idle
                        }
                    } else {
                        if (innerB.layoutActive.visibility != View.VISIBLE) {
                            showActiveState()
                        }
                        updateTimerUI(remaining)
                    }
                }
            }
        }

        b.systemStatusCard.setOnClickListener {
            if (currentIsAllGranted == true) {
                togglePermissionsExpand()
            } else {
                openPermissionHub()
            }
        }
        
        b.btnFixNow.setOnClickListener { openPermissionHub() }

        ViewCompat.setOnApplyWindowInsetsListener(b.topBar) { v, insets ->
            val status = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, status + 12, v.paddingRight, 16)
            insets
        }
    }

    private fun updateTimerUI(remaining: Long) {
        val b = _binding ?: return
        val totalMillis = viewModel.sessionTotalDuration
        
        // Update Progress Ring
        if (totalMillis > 0) {
            val progress = ((totalMillis - remaining).toFloat() / totalMillis.toFloat() * 100).toInt()
            b.progressRing.setProgress(progress, true)
        }

        if (remaining > 1000L * 60 * 60 * 24 * 365) {
            b.tvCountdown.text = "∞"
            b.progressRing.isIndeterminate = true
        } else {
            b.progressRing.isIndeterminate = false
            val seconds = (remaining / 1000) % 60
            val minutes = (remaining / (1000 * 60)) % 60
            val hours = (remaining / (1000 * 60 * 60))
            
            val timeStr = if (hours > 0) {
                String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }
            
            if (b.tvCountdown.text != timeStr) {
                b.tvCountdown.text = timeStr
                // Scale animation on each second safely
                b.tvCountdown.animate().cancel()
                b.tvCountdown.scaleX = 1f
                b.tvCountdown.scaleY = 1f
                b.tvCountdown.animate()
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(150)
                    .withEndAction {
                        _binding?.let { safeB ->
                            safeB.tvCountdown.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(150)
                                .start()
                        }
                    }
                    .start()
            }

            // Haptic tick every minute
            if (remaining % 60000 < 1000 && remaining > 1000) {
                b.root.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }

    private fun openPermissionHub() {
        triggerHapticFeedback()
        findNavController().navigate(R.id.action_navigation_block_to_setupPermissionFragmentMain)
    }

    private fun updateStrictModeUI() {
        val b = _binding ?: return
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isStrict = prefs.getBoolean("strict_mode", false)
        b.switchStrictMode.isChecked = isStrict

        if (isStrict) {
            b.iconStrictModeContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2A1515"))
            b.ivStrictModeIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#FF4B4B"))
        } else {
            b.iconStrictModeContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1A1A1A"))
            b.ivStrictModeIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#4A4A4A"))
        }
    }

    private fun showStrictPinSheet(mode: StrictModePinBottomSheet.Mode) {
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val correctPin = prefs.getString("strict_pin", null)
        val sheet = StrictModePinBottomSheet.newInstance(mode, correctPin)
        sheet.setListener(object : StrictModePinBottomSheet.StrictModePinListener {
            override fun onPinSet(pin: String) {
                prefs.edit().putString("strict_pin", pin).putBoolean("strict_mode", true).apply()
                viewModel.toggleStrictMode(true)
                updateStrictModeUI()
                triggerSuccessFeedback()
                Toast.makeText(requireContext(), "PIN set & Strict Mode enabled 🔒", Toast.LENGTH_SHORT).show()
            }

            override fun onPinVerified() {
                if (FocusSessionManager.isSessionActive(requireContext())) {
                    FocusSessionManager.endSession(requireContext())
                    showActiveState() // This will refresh to idle
                    Toast.makeText(requireContext(), "Session ended early", Toast.LENGTH_SHORT).show()
                } else {
                    prefs.edit().putBoolean("strict_mode", false).apply()
                    viewModel.toggleStrictMode(false)
                    updateStrictModeUI()
                    Toast.makeText(requireContext(), "Strict Mode disabled", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancel() {}
        })
        sheet.show(parentFragmentManager, "StrictPinSheet")
    }

    private fun handleEndEarlyClick() {
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isStrict = prefs.getBoolean("strict_mode", false)
        if (isStrict) {
            showStrictPinSheet(StrictModePinBottomSheet.Mode.VERIFY)
        } else {
            FocusSessionManager.endSession(requireContext())
            showActiveState()
        }
    }

    private var holdJob: kotlinx.coroutines.Job? = null

    private fun setupEndEarlyHoldListener() {
        val b = _binding ?: return
        b.btnEndEarly.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    val isStrict = prefs.getBoolean("strict_mode", false)
                    if (isStrict) {
                        b.btnEndEarly.text = "Strict mode: Cannot end early"
                        return@setOnTouchListener true
                    }
                    
                    b.btnEndEarly.text = "Hold 5s to end..."
                    holdJob = viewLifecycleOwner.lifecycleScope.launch {
                        for (i in 5 downTo 1) {
                            b.btnEndEarly.text = "Hold ${i}s..."
                            kotlinx.coroutines.delay(1000)
                        }
                        // Show confirm dialog
                        androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle("End focus session early?")
                            .setMessage("Your streak might break. Are you sure?")
                            .setPositiveButton("Yes, end") { _, _ ->
                                FocusSessionManager.endSession(requireContext())
                                showActiveState()
                            }
                            .setNegativeButton("Keep focusing", null)
                            .show()
                        b.btnEndEarly.text = "End Session Early"
                    }
                    b.btnEndEarly.performClick()
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    holdJob?.cancel()
                    b.btnEndEarly.text = "End Session Early"
                    true
                }
                else -> false
            }
        }
    }

    private fun showActiveState() {
        val b = _binding ?: return
        val isActive = FocusSessionManager.isSessionActive(requireContext())
        if (isActive) {
            b.layoutIdle.visibility = View.GONE
            b.layoutActive.visibility = View.VISIBLE
            updateActiveSummary()
            startDotPulseAnimation()
        } else {
            b.layoutIdle.visibility = View.VISIBLE
            b.layoutActive.visibility = View.GONE
            stopDotPulseAnimation()
        }
    }

    private fun startDotPulseAnimation() {
        val b = _binding ?: return
        dotPulseAnimator?.cancel()
        dotPulseAnimator = ObjectAnimator.ofFloat(b.dotPulse, "alpha", 1f, 0.3f).apply {
            duration = 800
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopDotPulseAnimation() {
        dotPulseAnimator?.cancel()
        dotPulseAnimator = null
        _binding?.dotPulse?.alpha = 1f
    }

    private fun updateActiveSummary() {
        val b = _binding ?: return
        val blockedAppsList = viewModel.blockedApps.value
        val appsCount = blockedAppsList.filter { !it.isKeyword }.size
        val keywordsCount = blockedAppsList.filter { it.isKeyword }.size
        
        val summaryText = when {
            appsCount > 0 && keywordsCount > 0 -> "Blocking $appsCount apps & $keywordsCount keywords"
            appsCount > 0 -> "Blocking $appsCount apps"
            keywordsCount > 0 -> "Blocking $keywordsCount keywords"
            else -> "Active session"
        }
        
        b.textBlockingStatusTop.text = "• $summaryText"

        // Update Icons stack
        b.layoutBlockedIcons.removeAllViews()
        val pm = requireContext().packageManager
        val blockedOnlyApps = blockedAppsList.filter { !it.isKeyword }
        
        blockedOnlyApps.take(4).forEach { app ->
            val iv = android.widget.ImageView(requireContext()).apply {
                val size = dpToPx(36)
                layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = dpToPx(-8) // overlap
                }
                
                try {
                    val icon = pm.getApplicationIcon(app.pattern)
                    setImageDrawable(icon)
                } catch (e: Exception) {
                    setImageResource(R.drawable.ic_block)
                }
                
                background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_circle)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2A2A2A"))
                elevation = dpToPx(2).toFloat()
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
            }
            b.layoutBlockedIcons.addView(iv)
        }

        if (blockedOnlyApps.size > 4) {
            val chip = android.widget.TextView(requireContext()).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    dpToPx(36)
                ).apply { marginStart = dpToPx(4) }
                
                text = "+${blockedOnlyApps.size - 4}"
                setTextColor(Color.WHITE)
                textSize = 12f
                gravity = android.view.Gravity.CENTER
                setPadding(dpToPx(10), 0, dpToPx(10), 0)
                background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_chip_purple)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            b.layoutBlockedIcons.addView(chip)
        }

        // Detailed summary text: "Blocking AI Gallery, Camera +2"
        val names = blockedOnlyApps.take(2).map {
            try {
                val info = pm.getApplicationInfo(it.pattern, 0)
                pm.getApplicationLabel(info).toString()
            } catch (e: Exception) {
                it.pattern.split(".").lastOrNull() ?: it.pattern
            }
        }
        
        val detailText = when {
            blockedOnlyApps.isEmpty() -> if (keywordsCount > 0) "Blocking $keywordsCount keywords" else "No apps selected"
            blockedOnlyApps.size == 1 -> "Blocking ${names[0]}"
            blockedOnlyApps.size == 2 -> "Blocking ${names[0]} and ${names[1]}"
            else -> "Blocking ${names[0]}, ${names[1]} +${blockedOnlyApps.size - 2}"
        }
        b.tvActiveSummary.text = detailText
    }

    private fun startCountdownUpdates() {
        countdownJob?.cancel()
        countdownJob = viewLifecycleOwner.lifecycleScope.launch {
            val totalMillis = FocusSessionManager.getTotalSessionMillis(requireContext())
            while (isActive) {
                val b = _binding ?: break
                val remaining = FocusSessionManager.getRemainingMillis(requireContext())
                if (remaining <= 0) {
                    showActiveState()
                    break
                }

                // Update Progress Ring
                if (totalMillis > 0) {
                    val progress = ((totalMillis - remaining).toFloat() / totalMillis.toFloat() * 100).toInt()
                    b.progressRing.setProgress(progress, true)
                }

                if (remaining > 1000L * 60 * 60 * 24 * 365) {
                    b.tvCountdown.text = "∞"
                    b.progressRing.isIndeterminate = true
                } else {
                    b.progressRing.isIndeterminate = false
                    val seconds = (remaining / 1000) % 60
                    val minutes = (remaining / (1000 * 60)) % 60
                    val hours = (remaining / (1000 * 60 * 60))
                    
                    val timeStr = if (hours > 0) {
                        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
                    } else {
                        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                    }
                    
                    if (b.tvCountdown.text != timeStr) {
                        b.tvCountdown.text = timeStr
                        // Scale animation on each second
                        b.tvCountdown.animate()
                            .scaleX(1.1f)
                            .scaleY(1.1f)
                            .setDuration(150)
                            .withEndAction {
                                _binding?.tvCountdown?.animate()
                                    ?.scaleX(1.0f)
                                    ?.scaleY(1.0f)
                                    ?.setDuration(150)
                                    ?.start()
                            }
                            .start()
                    }

                    // Haptic tick every minute
                    if (remaining % 60000 < 1000 && remaining > 1000) {
                        b.root.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
                delay(1000)
            }
        }
    }
    private fun refreshBlockBanner() {}

    private fun updateSystemStatusBanner() {
        try {
            val b = _binding ?: return
            val context = requireContext()
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
            val list = listOf(usage, accessibility, overlay, battery, exact)
            val granted = list.count { it }
            val missing = 5 - granted

            Log.e("PermCheck", "U=$usage A=$accessibility O=$overlay B=$battery Ex=$exact granted=$granted missing=$missing")

            val banners = listOfNotNull(b.systemStatusCard).distinct()
            if (missing <= 0) {
                banners.forEach { it.visibility = View.GONE; val lp = it.layoutParams; lp.height = 0; it.layoutParams = lp; it.requestLayout() }
            } else {
                banners.forEach { it.visibility = View.VISIBLE; val lp = it.layoutParams; lp.height = ViewGroup.LayoutParams.WRAP_CONTENT; it.layoutParams = lp; it.requestLayout() }
                b.tvHealthSubtitle.text = "$missing permissions needed"
                b.btnFixNow.text = "FIX NOW ($missing LEFT)"
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun updatePermissionHealth(isInitial: Boolean = false) {
        updateSystemStatusBanner()
        val context = context ?: return
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
        val grantedList = listOf(usage, accessibility, overlay, battery, exact)
        missingCount = 5 - grantedList.count { it }
        val allGranted = missingCount == 0
        if (currentIsAllGranted != allGranted) {
            if (!isInitial) {
                if (allGranted) { animateToSuccessState(); triggerSuccessFeedback() } else animateToWarningState()
            } else setHealthCardState(allGranted)
            currentIsAllGranted = allGranted
        }
    }

    private fun animateToSuccessState() {
        val context = context ?: return
        stopBreathingAnimation()
        successAnimator?.cancel()
        successAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                _binding?.systemStatusCard?.strokeColor = ArgbEvaluator().evaluate(fraction, Color.parseColor("#FFC107"), ContextCompat.getColor(context, R.color.stroke_card_opal)) as Int
            }
            start()
        }
    }

    private fun animateToWarningState() {
        val context = context ?: return
        warningAnimator?.cancel()
        warningAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                _binding?.systemStatusCard?.strokeColor = ArgbEvaluator().evaluate(fraction, ContextCompat.getColor(context, R.color.stroke_card_opal), Color.parseColor("#FFC107")) as Int
            }
            start()
        }
        startBreathingAnimation()
    }

    private fun startBreathingAnimation() {
        val card = _binding?.systemStatusCard ?: return
        breathingAnimator?.cancel()

        val scaleX = ObjectAnimator.ofFloat(card, "scaleX", 1f, 1.02f, 1f)
        val scaleY = ObjectAnimator.ofFloat(card, "scaleY", 1f, 1.02f, 1f)
        val alpha = ObjectAnimator.ofFloat(card, "alpha", 1f, 0.85f, 1f)

        val duration = 1500L
        scaleX.duration = duration
        scaleY.duration = duration
        alpha.duration = duration

        val infinite = ValueAnimator.INFINITE
        scaleX.repeatCount = infinite
        scaleY.repeatCount = infinite
        alpha.repeatCount = infinite

        val reverse = ValueAnimator.REVERSE
        scaleX.repeatMode = reverse
        scaleY.repeatMode = reverse
        alpha.repeatMode = reverse

        val interpolator = AccelerateDecelerateInterpolator()
        scaleX.interpolator = interpolator
        scaleY.interpolator = interpolator
        alpha.interpolator = interpolator

        breathingAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            start()
        }
    }

    private fun stopBreathingAnimation() {
        breathingAnimator?.cancel()
        breathingAnimator = null
        _binding?.systemStatusCard?.apply {
            scaleX = 1f
            scaleY = 1f
            alpha = 1f
        }
    }

    private fun setupQuickBlockListeners() {
        val b = _binding ?: return
        b.btn15m.setOnClickListener { view -> handleQuickBlock(15, view) }
        b.btn30m.setOnClickListener { view -> handleQuickBlock(30, view) }
        b.btn1h.setOnClickListener { view -> handleQuickBlock(60, view) }
        b.btn2h.setOnClickListener { view -> handleQuickBlock(120, view) }
        b.btn4h.setOnClickListener { view -> handleQuickBlock(240, view) }
        b.btn6h.setOnClickListener { view -> handleQuickBlock(360, view) }
        b.btnInf.setOnClickListener { view -> handleQuickBlock(-1, view) }
        b.btnCustom.setOnClickListener { showCustomDurationDialog() }
    }

    private fun showCustomDurationDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_custom_duration, null)
        val hourPicker = dialogView.findViewById<android.widget.NumberPicker>(R.id.hourPicker)
        val minutePicker = dialogView.findViewById<android.widget.NumberPicker>(R.id.minutePicker)
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancel)
        val btnStart = dialogView.findViewById<View>(R.id.btnStart)

        hourPicker.minValue = 0
        hourPicker.maxValue = 23
        minutePicker.minValue = 0
        minutePicker.maxValue = 59
        minutePicker.value = 15

        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnStart.setOnClickListener {
            val totalMinutes = (hourPicker.value * 60) + minutePicker.value
            if (totalMinutes > 0) {
                handleQuickBlock(totalMinutes, it)
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Please select a duration", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun togglePermissionsExpand() {}
    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
    private fun setHealthCardState(isSuccess: Boolean) {
        val context = context ?: return
        val b = _binding ?: return
        val strokeColor = if (isSuccess) ContextCompat.getColor(context, R.color.stroke_card_opal) else Color.parseColor("#FFC107")
        b.systemStatusCard.strokeColor = strokeColor
        if (isSuccess) stopBreathingAnimation() else startBreathingAnimation()
    }

    private fun triggerHapticFeedback() { _binding?.root?.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY) }
    private fun triggerSuccessFeedback() { _binding?.root?.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM) }

    private fun handleQuickBlock(durationMinutes: Int, clickedView: View) {
        if (isStarting) return
        
        // Quick Block Logic: Tapping a duration should only start a session if at least one app/keyword/web is selected in the blocking list.
        val blockedList = viewModel.blockedApps.value
        if (blockedList.isEmpty()) {
            Toast.makeText(requireContext(), "Please select at least one app or keyword to block first", Toast.LENGTH_LONG).show()
            // Navigate or direct user to Customize Blocking
            val intent = Intent(requireContext(), CustomizeBlockActivity::class.java)
            startActivity(intent)
            return
        }

        isStarting = true
        triggerHapticFeedback()
        viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            FocusSessionManager.startSession(requireContext(), durationMinutes)
            requireContext().sendBroadcast(Intent("com.example.appblocker.START_BLOCKING"))
            withContext(kotlinx.coroutines.Dispatchers.Main) { 
                isStarting = false 
                showActiveState()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionHealth()
    }

    override fun onPause() {
        super.onPause()
        stopBreathingAnimation()
    }

    override fun onDestroyView() {
        timerJob?.cancel()
        timerJob = null
        
        dotPulseAnimator?.cancel()
        dotPulseAnimator = null
        
        successAnimator?.cancel()
        successAnimator = null
        warningAnimator?.cancel()
        warningAnimator = null
        
        _binding?.let { b ->
            b.tvCountdown.animate().cancel()
            b.progressRing.animate().cancel()
            b.dotPulse?.animate()?.cancel()
        }
        
        stopBreathingAnimation()
        _binding = null
        super.onDestroyView()
    }
}
