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
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private var countdownJob: Job? = null
    private var currentIsAllGranted: Boolean? = null
    private var isPermissionsExpanded = false
    private var isStarting = false
    private var pollingJob: Job? = null
    private var breathingAnimator: AnimatorSet? = null

    private var missingCount = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBlockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.settingsIcon.setOnClickListener {
            val intent = Intent(requireContext(), CustomizeBlockActivity::class.java)
            startActivity(intent)
        }

        binding.btnFixNow.setOnClickListener {
            openPermissionHub()
        }

        binding.ivProfile.setOnClickListener {
            val intent = Intent(requireContext(), com.example.appblocker.ui.profile.AccountManagementActivity::class.java)
            startActivity(intent)
        }

        binding.cardCustomizeApps.setOnClickListener {
            val intent = Intent(requireContext(), CustomizeBlockActivity::class.java)
            startActivity(intent)
        }

        view.post {
            viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                viewModel.settings.collect { appSettings ->
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (appSettings != null) {
                            binding.switchStrictMode.isChecked = appSettings.isStrictModeEnabled
                        }
                        updatePermissionHealth(isInitial = currentIsAllGranted == null)
                    }
                }
            }
        }

        binding.switchStrictMode.setOnClickListener {
            val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val isCurrentlyOn = prefs.getBoolean("strict_mode", false)
            val pin = prefs.getString("strict_pin", null)
            binding.switchStrictMode.isChecked = isCurrentlyOn

            if (!isCurrentlyOn) {
                if (pin.isNullOrEmpty()) {
                    showStrictPinSheet(StrictModePinBottomSheet.Mode.SET)
                } else {
                    prefs.edit().putBoolean("strict_mode", true).apply()
                    viewModel.toggleStrictMode(true)
                    binding.switchStrictMode.isChecked = true
                    Toast.makeText(requireContext(), "Strict Mode enabled 🔒", Toast.LENGTH_SHORT).show()
                    triggerSuccessFeedback()
                    updateStrictModeUI()
                }
            } else {
                showStrictPinSheet(StrictModePinBottomSheet.Mode.VERIFY)
            }
        }

        setupQuickBlockListeners()
        binding.btnEndEarly.setOnClickListener { handleEndEarlyClick() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isStrictBlockActive.collect { isActive ->
                updateStrictModeUI()
                if (FocusSessionManager.isSessionActive(requireContext())) {
                    showActiveState()
                } else {
                    binding.layoutActive.visibility = View.GONE
                    binding.layoutIdle.visibility = View.VISIBLE
                }
            }
        }

        binding.systemStatusCard.setOnClickListener {
            if (currentIsAllGranted == true) {
                togglePermissionsExpand()
            } else {
                openPermissionHub()
            }
        }
        
        binding.btnFixNow.setOnClickListener { openPermissionHub() }

        ViewCompat.setOnApplyWindowInsetsListener(binding.topBar) { v, insets ->
            val status = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, status + 12, v.paddingRight, 16)
            insets
        }
    }

    private fun openPermissionHub() {
        triggerHapticFeedback()
        findNavController().navigate(R.id.action_navigation_block_to_setupPermissionFragmentMain)
    }

    private fun updateStrictModeUI() {
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isStrict = prefs.getBoolean("strict_mode", false)
        binding.switchStrictMode.isChecked = isStrict

        if (isStrict) {
            binding.iconStrictModeContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2A1515"))
            binding.ivStrictModeIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#FF4B4B"))
        } else {
            binding.iconStrictModeContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1A1A1A"))
            binding.ivStrictModeIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#4A4A4A"))
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

    private fun showActiveState() {
        val isActive = FocusSessionManager.isSessionActive(requireContext())
        if (isActive) {
            binding.layoutIdle.visibility = View.GONE
            binding.layoutActive.visibility = View.VISIBLE
            startCountdownUpdates()
        } else {
            binding.layoutIdle.visibility = View.VISIBLE
            binding.layoutActive.visibility = View.GONE
            countdownJob?.cancel()
        }
    }

    private fun startCountdownUpdates() {
        countdownJob?.cancel()
        countdownJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                val remaining = FocusSessionManager.getRemainingMillis(requireContext())
                if (remaining <= 0) {
                    showActiveState()
                    break
                }
                if (remaining > 1000L * 60 * 60 * 24 * 365) {
                    binding.tvCountdown.text = "Remaining: ∞"
                } else {
                    val seconds = (remaining / 1000) % 60
                    val minutes = (remaining / (1000 * 60)) % 60
                    val hours = (remaining / (1000 * 60 * 60))
                    binding.tvCountdown.text = if (hours > 0) {
                        String.format(Locale.getDefault(), "Remaining: %02d:%02d:%02d", hours, minutes, seconds)
                    } else {
                        String.format(Locale.getDefault(), "Remaining: %02d:%02d", minutes, seconds)
                    }
                }
                delay(1000)
            }
        }
    }
    private fun refreshBlockBanner() {}

    private fun updateSystemStatusBanner() {
        try {
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

            val banners = listOfNotNull(binding.systemStatusCard).distinct()
            if (missing <= 0) {
                banners.forEach { it.visibility = View.GONE; val lp = it.layoutParams; lp.height = 0; it.layoutParams = lp; it.requestLayout() }
            } else {
                banners.forEach { it.visibility = View.VISIBLE; val lp = it.layoutParams; lp.height = ViewGroup.LayoutParams.WRAP_CONTENT; it.layoutParams = lp; it.requestLayout() }
                binding.tvHealthSubtitle.text = "$missing permissions needed"
                binding.btnFixNow.text = "FIX NOW ($missing LEFT)"
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
        val context = requireContext()
        stopBreathingAnimation()
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                binding.systemStatusCard.strokeColor = ArgbEvaluator().evaluate(fraction, Color.parseColor("#FFC107"), ContextCompat.getColor(context, R.color.stroke_card_opal)) as Int
            }
            start()
        }
    }

    private fun animateToWarningState() {
        val context = requireContext()
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                binding.systemStatusCard.strokeColor = ArgbEvaluator().evaluate(fraction, ContextCompat.getColor(context, R.color.stroke_card_opal), Color.parseColor("#FFC107")) as Int
            }
            start()
        }
        startBreathingAnimation()
    }

    private fun startBreathingAnimation() {
        val card = binding.systemStatusCard
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
        binding.systemStatusCard.apply {
            scaleX = 1f
            scaleY = 1f
            alpha = 1f
        }
    }

    private fun setupQuickBlockListeners() {
        binding.btn15m.setOnClickListener { view -> handleQuickBlock(15, view) }
        binding.btn30m.setOnClickListener { view -> handleQuickBlock(30, view) }
        binding.btn1h.setOnClickListener { view -> handleQuickBlock(60, view) }
        binding.btn2h.setOnClickListener { view -> handleQuickBlock(120, view) }
        binding.btn4h.setOnClickListener { view -> handleQuickBlock(240, view) }
        binding.btn6h.setOnClickListener { view -> handleQuickBlock(360, view) }
        binding.btnInf.setOnClickListener { view -> handleQuickBlock(-1, view) }
        binding.btnCustom.setOnClickListener { showCustomDurationDialog() }
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
        val strokeColor = if (isSuccess) ContextCompat.getColor(context, R.color.stroke_card_opal) else Color.parseColor("#FFC107")
        binding.systemStatusCard.strokeColor = strokeColor
        if (isSuccess) stopBreathingAnimation() else startBreathingAnimation()
    }

    private fun triggerHapticFeedback() { binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY) }
    private fun triggerSuccessFeedback() { binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM) }

    private fun handleQuickBlock(durationMinutes: Int, clickedView: View) {
        if (isStarting) return
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
        super.onDestroyView()
        stopBreathingAnimation()
        _binding = null
    }
}
