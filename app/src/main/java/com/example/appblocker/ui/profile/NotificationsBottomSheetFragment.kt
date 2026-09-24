package com.example.appblocker.ui.profile

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.appblocker.MainViewModel
import com.example.appblocker.NotificationHelper
import com.example.appblocker.NotificationScheduler
import com.example.appblocker.R
import com.example.appblocker.ThemeManager
import com.example.appblocker.data.AppDatabase
import com.example.appblocker.databinding.BottomSheetNotificationsBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class NotificationsBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetNotificationsBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: MainViewModel by activityViewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            lifecycleScope.launch {
                ThemeManager.setNotifMaster(requireContext(), true)
                binding.permissionCard.visibility = View.GONE
                scheduleWorkers()
                Toast.makeText(requireContext(), "Notifications enabled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var isProgrammaticChange = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val behavior = BottomSheetBehavior.from(binding.root.parent as View)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED

        checkPermission()
        setupListeners()
        observeData()
    }

    private fun checkPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                binding.permissionCard.visibility = View.VISIBLE
            }
        }
    }

    private fun setupListeners() {
        binding.btnEnableNotif.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        binding.switchMaster.setOnCheckedChangeListener { _, isChecked ->
            if (isProgrammaticChange) return@setOnCheckedChangeListener
            lifecycleScope.launch {
                try {
                    ThemeManager.setNotifMaster(requireContext(), isChecked)
                    if (isChecked) scheduleWorkers() else cancelWorkers()
                    updateAlpha(isChecked)
                } catch (e: Exception) {
                    android.util.Log.e("NotificationsSheet", "Error saving master notif", e)
                }
            }
        }

        binding.switchFocus.setOnCheckedChangeListener { _, isChecked ->
            if (isProgrammaticChange) return@setOnCheckedChangeListener
            lifecycleScope.launch {
                try {
                    ThemeManager.setNotifFocus(requireContext(), isChecked)
                    if (isChecked) {
                        val time = ThemeManager.getNotifTimeFocusFlow(requireContext()).first()
                        NotificationScheduler.scheduleFocusReminder(requireContext(), time)
                    } else {
                        NotificationScheduler.cancelFocusReminder(requireContext())
                    }
                } catch (e: Exception) {
                    android.util.Log.e("NotificationsSheet", "Error saving focus notif", e)
                }
            }
        }

        binding.switchSummary.setOnCheckedChangeListener { _, isChecked ->
            if (isProgrammaticChange) return@setOnCheckedChangeListener
            
            lifecycleScope.launch {
                val isPremium = viewModel.isPremium.value
                if (isChecked && !isPremium) {
                    isProgrammaticChange = true
                    binding.switchSummary.isChecked = false
                    isProgrammaticChange = false
                    
                    binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    
                    try {
                        findNavController().navigate(R.id.navigation_paywall)
                        dismiss()
                    } catch (e: Exception) {
                        android.util.Log.e("NotificationsSheet", "Failed to navigate to paywall", e)
                    }
                    return@launch
                }
                
                try {
                    ThemeManager.setNotifSummary(requireContext(), isChecked)
                    if (isChecked) {
                        val time = ThemeManager.getNotifTimeMorningFlow(requireContext()).first()
                        NotificationScheduler.scheduleMorningSummary(requireContext(), time)
                    } else {
                        NotificationScheduler.cancelMorningSummary(requireContext())
                    }
                } catch (e: Exception) {
                    android.util.Log.e("NotificationsSheet", "Error saving summary notif", e)
                }
            }
        }

        binding.switchBlock.setOnCheckedChangeListener { _, isChecked ->
            if (isProgrammaticChange) return@setOnCheckedChangeListener
            lifecycleScope.launch {
                try {
                    ThemeManager.setNotifBlock(requireContext(), isChecked)
                } catch (e: Exception) {
                    android.util.Log.e("NotificationsSheet", "Error saving block notif", e)
                }
            }
        }

        binding.chipTimeFocus.setOnClickListener {
            showTimePicker(true)
        }

        binding.chipTimeSummary.setOnClickListener {
            showTimePicker(false)
        }

        binding.btnTestNotif.setOnClickListener {
            try {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return@setOnClickListener
                }

                val masterEnabled = binding.switchMaster.isChecked
                if (!masterEnabled) {
                    Toast.makeText(requireContext(), "Enable notifications first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                NotificationHelper.showStreakNotification(requireContext())
                
                lifecycleScope.launch {
                    delay(2000)
                    val db = AppDatabase.getDatabase(requireContext())
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.DAY_OF_YEAR, -1)
                    val yesterdayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
                    
                    val stats = db.dailyFocusStatsDao().getStatsForDateList(yesterdayStr)
                    val blockCount = db.dailyFocusStatsDao().getBlockCountForDateSync(yesterdayStr)
                    val streak = db.streakDao().getCurrentStreak() ?: 0
                    
                    // If 0 for testing, use mock to show nice notification
                    val statsVal = stats
                    val minutes = if ((statsVal?.protectedMinutes ?: 0) == 0) 135 else statsVal!!.protectedMinutes
                    val blocks = if (blockCount == 0) 12 else blockCount
                    val score = if ((statsVal?.focusScore ?: 0) == 0) 85 else statsVal!!.focusScore

                    NotificationHelper.showMorningSummary(
                        requireContext(),
                        minutes,
                        blocks,
                        score,
                        streak
                    )
                    Toast.makeText(requireContext(), "Morning summary test sent ✨", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("NotificationsSheet", "Test notification failed", e)
            }
        }
    }

    private fun showTimePicker(isFocus: Boolean) {
        if (!isFocus && !viewModel.isPremium.value) {
            binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            try {
                findNavController().navigate(R.id.navigation_paywall)
                dismiss()
            } catch (e: Exception) {}
            return
        }

        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        try {
            TimePickerDialog(requireContext(), { _, h, m ->
                val time = String.format(Locale.getDefault(), "%02d:%02d", h, m)
                lifecycleScope.launch {
                    if (isFocus) {
                        ThemeManager.setNotifTimeFocus(requireContext(), time)
                        binding.chipTimeFocus.text = time
                        NotificationScheduler.scheduleFocusReminder(requireContext(), time)
                    } else {
                        ThemeManager.setNotifTimeMorning(requireContext(), time)
                        binding.chipTimeSummary.text = time
                        NotificationScheduler.scheduleMorningSummary(requireContext(), time)
                    }
                    binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                }
            }, hour, minute, true).show()
        } catch (e: Exception) {
            android.util.Log.e("NotificationsSheet", "Failed to show time picker", e)
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewModel.isPremium.collect { isPremium ->
                binding.proBadgeSummary.visibility = if (isPremium) View.GONE else View.VISIBLE
                
                // If user lost premium, disable summary if it was active
                if (!isPremium && ThemeManager.getNotifSummaryFlow(requireContext()).first()) {
                    ThemeManager.setNotifSummary(requireContext(), false)
                    NotificationScheduler.cancelMorningSummary(requireContext())
                    isProgrammaticChange = true
                    binding.switchSummary.isChecked = false
                    isProgrammaticChange = false
                }
            }
        }
        lifecycleScope.launch {
            ThemeManager.getNotifMasterFlow(requireContext()).collect {
                isProgrammaticChange = true
                binding.switchMaster.isChecked = it
                isProgrammaticChange = false
                updateAlpha(it)
            }
        }
        lifecycleScope.launch {
            ThemeManager.getNotifFocusFlow(requireContext()).collect {
                isProgrammaticChange = true
                binding.switchFocus.isChecked = it
                isProgrammaticChange = false
            }
        }
        lifecycleScope.launch {
            ThemeManager.getNotifSummaryFlow(requireContext()).collect {
                isProgrammaticChange = true
                binding.switchSummary.isChecked = it
                isProgrammaticChange = false
            }
        }
        lifecycleScope.launch {
            ThemeManager.getNotifBlockFlow(requireContext()).collect {
                isProgrammaticChange = true
                binding.switchBlock.isChecked = it
                isProgrammaticChange = false
            }
        }
        lifecycleScope.launch {
            ThemeManager.getNotifTimeFocusFlow(requireContext()).collect {
                binding.chipTimeFocus.text = it
            }
        }
        lifecycleScope.launch {
            ThemeManager.getNotifTimeMorningFlow(requireContext()).collect {
                binding.chipTimeSummary.text = it
            }
        }
    }

    private fun updateAlpha(enabled: Boolean) {
        val alpha = if (enabled) 1.0f else 0.5f
        binding.cardFocus.alpha = alpha
        binding.cardSummary.alpha = alpha
        binding.cardBlock.alpha = alpha
        binding.cardFocus.isEnabled = enabled
        binding.cardSummary.isEnabled = enabled
        binding.cardBlock.isEnabled = enabled
    }

    private suspend fun scheduleWorkers() {
        val context = requireContext()
        if (ThemeManager.getNotifFocusFlow(context).first()) {
            NotificationScheduler.scheduleFocusReminder(context, ThemeManager.getNotifTimeFocusFlow(context).first())
        }
        if (ThemeManager.getNotifSummaryFlow(context).first()) {
            NotificationScheduler.scheduleMorningSummary(context, ThemeManager.getNotifTimeMorningFlow(context).first())
        }
    }

    private fun cancelWorkers() {
        NotificationScheduler.cancelFocusReminder(requireContext())
        NotificationScheduler.cancelMorningSummary(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
