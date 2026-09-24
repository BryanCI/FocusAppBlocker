package com.example.appblocker.onboarding

import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.appblocker.MainActivity
import com.example.appblocker.R
import com.example.appblocker.databinding.FragmentSetupPermissionBinding
import com.example.appblocker.databinding.ItemPermissionPremiumBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*

@AndroidEntryPoint
class SetupPermissionFragment : Fragment(R.layout.fragment_setup_permission) {
    private var _binding: FragmentSetupPermissionBinding? = null
    private val binding get() = _binding!!

    private var permissionPollJob: Job? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionPollJob?.cancel()
        refreshAllPermissions()
        view?.postDelayed({ if (isAdded) refreshAllPermissions() }, 500)
    }

    private fun startAutoReturnMonitoring(check: () -> Boolean) {
        permissionPollJob?.cancel()
        permissionPollJob = lifecycleScope.launch {
            repeat(120) { // 60 sec max
                delay(500)
                if (!isAdded) return@launch
                if (check()) {
                    withContext(Dispatchers.Main) {
                        if (!isAdded) return@withContext
                        refreshAllPermissions()
                        try {
                            val intent = Intent(requireContext(), MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            startActivity(intent)
                            Toast.makeText(requireContext(), "Permission granted ✓", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) { refreshAllPermissions() }
                    }
                    cancel()
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSetupPermissionBinding.bind(view)

        setupUI()
        setupClickListeners()
        refreshAllPermissions()
    }

    private fun setupUI() {
        // Initialize cards with static data
        setupCardData(binding.cardUsageAccess, "Usage Access", "Track app screen time", "📊")
        setupCardData(binding.cardAccessibility, "Accessibility", "Enforce app blocking", "♿")
        setupCardData(binding.cardOverlay, "Display Over Other Apps", "Show lock screens", "🪟")
        setupCardData(binding.cardBattery, "Battery Optimization", "Keep service running", "🔋")
        setupCardData(binding.cardExactAlarm, "Exact Alarms", "Precise schedule timing", "⏰")
    }

    private fun setupCardData(itemBinding: ItemPermissionPremiumBinding, title: String, subtitle: String, icon: String) {
        itemBinding.tvTitle.text = title
        itemBinding.tvSubtitle.text = subtitle
        itemBinding.tvIconEmoji.text = icon
    }

    private fun setupClickListeners() {
        binding.cardUsageAccess.root.setOnClickListener {
            startAutoReturnMonitoring { hasUsageAccessPermission() }
            permissionLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY) })
        }
        binding.cardUsageAccess.btnFix.setOnClickListener {
            startAutoReturnMonitoring { hasUsageAccessPermission() }
            permissionLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY) })
        }

        binding.cardAccessibility.root.setOnClickListener {
            startAutoReturnMonitoring { isAccessibilityServiceEnabled() }
            permissionLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY) })
        }
        binding.cardAccessibility.btnFix.setOnClickListener {
            startAutoReturnMonitoring { isAccessibilityServiceEnabled() }
            permissionLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY) })
        }

        binding.cardOverlay.root.setOnClickListener {
            startAutoReturnMonitoring { hasOverlayPermission() }
            permissionLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${requireContext().packageName}")).apply { addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY) })
        }
        binding.cardOverlay.btnFix.setOnClickListener {
            startAutoReturnMonitoring { hasOverlayPermission() }
            permissionLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${requireContext().packageName}")).apply { addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY) })
        }

        binding.cardBattery.root.setOnClickListener {
            startAutoReturnMonitoring { hasBatteryOptimizationIgnored() }
            try {
                permissionLauncher.launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${requireContext().packageName}")).apply { addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY) })
            } catch (e: Exception) {
                permissionLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY) })
            }
        }
        binding.cardBattery.btnFix.setOnClickListener {
            startAutoReturnMonitoring { hasBatteryOptimizationIgnored() }
            try {
                permissionLauncher.launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${requireContext().packageName}")).apply { addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY) })
            } catch (e: Exception) {
                permissionLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY) })
            }
        }

        binding.cardExactAlarm.root.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startAutoReturnMonitoring { hasExactAlarmPermission() }
                permissionLauncher.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${requireContext().packageName}")).apply { addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY) })
            }
        }
        binding.cardExactAlarm.btnFix.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startAutoReturnMonitoring { hasExactAlarmPermission() }
                permissionLauncher.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${requireContext().packageName}")).apply { addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY) })
            }
        }

        binding.finishedButton.setOnClickListener {
            requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().putBoolean("onboarding_done", true).apply()
            try {
                val intent = Intent(requireContext(), MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                requireActivity().finish()
            } catch (e: Exception) {
                findNavController().navigate(R.id.navigation_block)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionPollJob?.cancel()
        refreshAllPermissions() // instant
        // Accessibility and Usage Access need second check after system writes
        view?.postDelayed({ if (isAdded) refreshAllPermissions() }, 700)
        view?.postDelayed({ if (isAdded) refreshAllPermissions() }, 1500)
    }

    fun refreshAllPermissions() {
        if (_binding == null) return
        
        val usageGranted = hasUsageAccessPermission()
        val accessibilityGranted = isAccessibilityServiceEnabled()
        val overlayGranted = hasOverlayPermission()
        val batteryGranted = hasBatteryOptimizationIgnored()
        val alarmGranted = hasExactAlarmPermission()

        updatePermissionCard(binding.cardUsageAccess, usageGranted)
        updatePermissionCard(binding.cardAccessibility, accessibilityGranted)
        updatePermissionCard(binding.cardOverlay, overlayGranted)
        updatePermissionCard(binding.cardBattery, batteryGranted)
        updatePermissionCard(binding.cardExactAlarm, alarmGranted)

        val grantedCount = listOf(usageGranted, accessibilityGranted, overlayGranted, batteryGranted, alarmGranted).count { it }
        binding.progressText.text = "$grantedCount of 5 granted"
        binding.progressBar.progress = grantedCount * 20
        binding.progressPercent.text = "${grantedCount * 20}%"

        val allGranted = usageGranted && accessibilityGranted && overlayGranted && batteryGranted && alarmGranted
        updateFinishedButton(allGranted)
    }

    private fun updatePermissionCard(itemBinding: ItemPermissionPremiumBinding, granted: Boolean) {
        if (granted) {
            itemBinding.cardRoot.setBackgroundResource(R.drawable.bg_permission_granted)
            itemBinding.cardRoot.strokeColor = ContextCompat.getColor(requireContext(), android.R.color.holo_green_light)
            itemBinding.btnFix.visibility = View.GONE
            itemBinding.grantedPillContainer.visibility = View.VISIBLE
            itemBinding.grantedText.text = "✓ GRANTED"
            itemBinding.grantedText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_light))
            itemBinding.grantedText.setTypeface(null, Typeface.BOLD)
        } else {
            itemBinding.cardRoot.setBackgroundResource(R.drawable.bg_permission_needed)
            itemBinding.cardRoot.strokeColor = Color.parseColor("#FFC107")
            itemBinding.btnFix.visibility = View.VISIBLE
            itemBinding.btnFix.text = "Grant"
            itemBinding.grantedPillContainer.visibility = View.GONE
        }
    }

    private fun updateFinishedButton(allGranted: Boolean) {
        binding.finishedButton.text = "Finished ✓ Back to Home"
        binding.finishedButton.isEnabled = true
        binding.finishedButton.isClickable = true
        binding.finishedButton.alpha = 1f
        if (allGranted) {
            binding.finishedButton.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
        } else {
            binding.finishedButton.setBackgroundColor(Color.parseColor("#2A2A2A"))
        }
    }

    fun hasUsageAccessPermission(): Boolean {
        return try {
            val appOps = requireContext().getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), requireContext().packageName)
            } else {
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), requireContext().packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) { false }
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.contains(requireContext().packageName) // contains is most reliable
    }

    fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(requireContext())

    fun hasBatteryOptimizationIgnored(): Boolean {
        return try {
            val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(requireContext().packageName)
        } catch (e: Exception) { false }
    }

    fun hasExactAlarmPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else true
    }

    override fun onDestroyView() {
        permissionPollJob?.cancel()
        super.onDestroyView()
        _binding = null
    }
}
