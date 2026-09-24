package com.example.appblocker.ui.security

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.appblocker.MainViewModel
import com.example.appblocker.R
import com.example.appblocker.ThemeManager
import com.example.appblocker.databinding.BottomSheetSecurityBinding
import com.example.appblocker.databinding.DialogDeleteConfirmationBinding
import com.example.appblocker.security.BiometricHelper
import com.example.appblocker.security.DataExportHelper
import com.example.appblocker.security.PermissionChecker
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SecurityBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSecurityBinding? = null
    private val binding get() = _binding!!
    private var isProgrammaticChange = false
    
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetSecurityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAppLock()
        setupPermissions()
        setupPrivacy()
        setupData()
        refreshPermissions()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    private fun setupAppLock() {
        lifecycleScope.launch {
            val enabled = ThemeManager.getAppLockEnabledFlow(requireContext()).first()
            isProgrammaticChange = true
            binding.switchAppLock.isChecked = enabled
            binding.layoutLockTimer.visibility = if (enabled) View.VISIBLE else View.GONE
            isProgrammaticChange = false

            val timer = ThemeManager.getAppLockTimerFlow(requireContext()).first()
            updateTimerSelection(timer)
        }

        binding.switchAppLock.setOnCheckedChangeListener { _, isChecked ->
            if (isProgrammaticChange) return@setOnCheckedChangeListener
            
            view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            
            if (isChecked) {
                if (BiometricHelper.canAuthenticate(requireContext())) {
                    lifecycleScope.launch {
                        ThemeManager.setAppLockEnabled(requireContext(), true)
                        binding.layoutLockTimer.visibility = View.VISIBLE
                        Toast.makeText(context, "App lock enabled", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "No biometric enrolled", Toast.LENGTH_SHORT).show()
                    isProgrammaticChange = true
                    binding.switchAppLock.isChecked = false
                    isProgrammaticChange = false
                }
            } else {
                lifecycleScope.launch {
                    ThemeManager.setAppLockEnabled(requireContext(), false)
                    binding.layoutLockTimer.visibility = View.GONE
                }
            }
        }

        binding.chipImmediately.setOnClickListener { setTimer("immediately") }
        binding.chip1Min.setOnClickListener { setTimer("1min") }
        binding.chip5Min.setOnClickListener { setTimer("5min") }
        binding.chip15Min.setOnClickListener { setTimer("15min") }
    }

    private fun setTimer(timer: String) {
        view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        lifecycleScope.launch {
            ThemeManager.setAppLockTimer(requireContext(), timer)
            updateTimerSelection(timer)
        }
    }

    private fun updateTimerSelection(selected: String) {
        val chips = mapOf(
            "immediately" to binding.chipImmediately,
            "1min" to binding.chip1Min,
            "5min" to binding.chip5Min,
            "15min" to binding.chip15Min
        )

        chips.forEach { (key, chip) ->
            if (key == selected) {
                chip.setChipBackgroundColorResource(android.R.color.transparent)
                chip.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#262626"))
                chip.setTextColor(Color.WHITE)
                chip.typeface = android.graphics.Typeface.DEFAULT_BOLD
                chip.chipStrokeWidth = 1f
                chip.chipStrokeColor = ColorStateList.valueOf(Color.parseColor("#3A3A3A"))
                // Purple dot simulation via chip icon
                chip.chipIcon = ContextCompat.getDrawable(requireContext(), R.drawable.bg_dot_purple)
                chip.chipIconSize = 18f // 6dp roughly
                chip.isChipIconVisible = true
            } else {
                chip.setChipBackgroundColorResource(android.R.color.transparent)
                chip.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#1E1E1E"))
                chip.setTextColor(Color.parseColor("#8A8A8E"))
                chip.typeface = android.graphics.Typeface.DEFAULT
                chip.chipStrokeWidth = 0f
                chip.isChipIconVisible = false
            }
        }
    }

    private fun setupPermissions() {
        binding.btnFixUsage.setOnClickListener {
            view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            PermissionChecker.openUsageSettings(requireContext())
        }
        binding.btnFixAccess.setOnClickListener {
            view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            PermissionChecker.openAccessibilitySettings(requireContext())
        }
        binding.btnFixOverlay.setOnClickListener {
            view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            PermissionChecker.openOverlaySettings(requireContext())
        }
    }

    private fun refreshPermissions() {
        val context = requireContext()
        
        val usageOk = PermissionChecker.isUsageAccessGranted(context)
        updatePermissionUI(usageOk, binding.dotUsage, binding.statusUsage, binding.btnFixUsage, binding.iconUsageChecked, binding.cardUsageAccess)
        
        val accessOk = PermissionChecker.isAccessibilityGranted(context)
        updatePermissionUI(accessOk, binding.dotAccess, binding.statusAccess, binding.btnFixAccess, binding.iconAccessChecked, binding.cardAccessibility)
        
        val overlayOk = PermissionChecker.isOverlayGranted(context)
        updatePermissionUI(overlayOk, binding.dotOverlay, binding.statusOverlay, binding.btnFixOverlay, binding.iconOverlayChecked, binding.cardOverlay)
    }

    private fun updatePermissionUI(granted: Boolean, dot: View, status: android.widget.TextView, btn: View, check: View, card: com.google.android.material.card.MaterialCardView) {
        if (granted) {
            dot.setBackgroundResource(R.drawable.bg_dot_green)
            status.text = "Granted"
            status.setTextColor(Color.parseColor("#10B981"))
            btn.visibility = View.GONE
            check.visibility = View.VISIBLE
            card.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#262626")))
            card.setCardBackgroundColor(Color.parseColor("#161616"))
        } else {
            dot.setBackgroundResource(R.drawable.bg_dot_red)
            status.text = "Not granted"
            status.setTextColor(Color.parseColor("#EF4444"))
            btn.visibility = View.VISIBLE
            check.visibility = View.GONE
            card.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#3A1A1A")))
            card.setCardBackgroundColor(Color.parseColor("#1A0F0F"))
        }
    }

    private fun setupPrivacy() {
        lifecycleScope.launch {
            val stealth = ThemeManager.getStealthModeFlow(requireContext()).first()
            val screenshot = ThemeManager.getScreenshotPreventionFlow(requireContext()).first()
            
            isProgrammaticChange = true
            binding.switchStealth.isChecked = stealth
            binding.switchScreenshot.isChecked = screenshot
            isProgrammaticChange = false
        }

        binding.switchStealth.setOnCheckedChangeListener { _, isChecked ->
            if (isProgrammaticChange) return@setOnCheckedChangeListener
            // Gate PRO check here (assume for now we check a premium flow)
            handleProToggle("stealth", isChecked)
        }

        binding.switchScreenshot.setOnCheckedChangeListener { _, isChecked ->
            if (isProgrammaticChange) return@setOnCheckedChangeListener
            handleProToggle("screenshot", isChecked)
        }
    }

    private fun handleProToggle(feature: String, enabled: Boolean) {
        lifecycleScope.launch {
            val isPremium = viewModel.isPremium.value
            if (!isPremium) {
                isProgrammaticChange = true
                if (feature == "stealth") binding.switchStealth.isChecked = false
                else binding.switchScreenshot.isChecked = false
                isProgrammaticChange = false
                
                Handler(Looper.getMainLooper()).postDelayed({
                    dismiss()
                    try {
                        findNavController().navigate(
                            R.id.navigation_paywall,
                            Bundle().apply { putString("source", "stealth_mode") }
                        )
                    } catch (e: Exception) {}
                }, 300)
                return@launch
            }
            
            if (feature == "stealth") ThemeManager.setStealthMode(requireContext(), enabled)
            else ThemeManager.setScreenshotPrevention(requireContext(), enabled)
        }
    }

    private fun setupData() {
        binding.cardExport.setOnClickListener {
            view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            lifecycleScope.launch {
                try {
                    DataExportHelper.exportData(requireContext())
                    Toast.makeText(context, "Backup saved to Downloads", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        binding.cardDelete.setOnClickListener {
            view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showDeleteConfirmation()
        }
    }

    private fun showDeleteConfirmation() {
        val dialog = BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme)
        val deleteBinding = DialogDeleteConfirmationBinding.inflate(layoutInflater)
        dialog.setContentView(deleteBinding.root)

        deleteBinding.editDelete.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                deleteBinding.btnConfirmDelete.isEnabled = s.toString() == "DELETE"
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        deleteBinding.btnConfirmDelete.setOnClickListener {
            view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            lifecycleScope.launch {
                DataExportHelper.clearAllData(requireContext())
                // Restart app
                val intent = requireContext().packageManager.getLaunchIntentForPackage(requireContext().packageName)
                intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                if (intent != null) {
                    startActivity(intent)
                }
                Runtime.getRuntime().exit(0)
            }
        }

        deleteBinding.btnCancelDelete.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
