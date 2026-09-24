package com.example.appblocker.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.appblocker.MainViewModel
import com.example.appblocker.MorningSummaryWorker
import com.example.appblocker.StreakReminderWorker
import com.example.appblocker.ThemeManager
import com.example.appblocker.data.AppDatabase
import com.example.appblocker.data.DailyFocusStats
import com.example.appblocker.databinding.BottomSheetDeveloperMenuBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class DeveloperMenuBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetDeveloperMenuBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetDeveloperMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnTriggerMorningSummary.setOnClickListener {
            val request = OneTimeWorkRequestBuilder<MorningSummaryWorker>().build()
            WorkManager.getInstance(requireContext()).enqueue(request)
            Toast.makeText(context, "Morning Summary Worker Queued", Toast.LENGTH_SHORT).show()
        }

        binding.btnTriggerStreakReminder.setOnClickListener {
            val request = OneTimeWorkRequestBuilder<StreakReminderWorker>().build()
            WorkManager.getInstance(requireContext()).enqueue(request)
            Toast.makeText(context, "Streak Reminder Worker Queued", Toast.LENGTH_SHORT).show()
        }

        binding.btnResetStats.setOnClickListener {
            lifecycleScope.launch {
                val db = AppDatabase.getDatabase(requireContext())
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                db.dailyFocusStatsDao().insertStats(DailyFocusStats(date = today))
                Toast.makeText(context, "Today's stats reset", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnTogglePremium.setOnClickListener {
            lifecycleScope.launch {
                val current = viewModel.isPremium.value
                // Assuming we have a way to toggle premium in ViewModel or ThemeManager
                // For now, let's use ThemeManager as a proxy if it holds it
                // Or if it's in the DB, update it there.
                val db = AppDatabase.getDatabase(requireContext())
                val settings = db.settingsDao().getSettingsList().firstOrNull() ?: com.example.appblocker.data.AppSettings()
                db.settingsDao().updateSettings(settings.copy(isPremium = !current))
                Toast.makeText(context, "Premium: ${!current}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}