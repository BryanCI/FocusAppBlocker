package com.example.appblocker.ui.statistics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.appblocker.MainViewModel
import com.example.appblocker.R
import com.example.appblocker.StatsState
import com.example.appblocker.databinding.FragmentStatisticsBinding
import com.example.appblocker.databinding.ItemDistractorPremiumBinding
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.Process
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.appblocker.repository.TimeRange

@AndroidEntryPoint
class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private var selectedTab = TimeRange.DAY
    private var lastQueryTime = 0L
    private val DEBOUNCE_DELAY = 300L

    private lateinit var chartAdapter: ChartBarAdapter

    private fun hasUsagePermission(): Boolean {
        val appOps = requireContext().getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            requireContext().packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        if (hasUsagePermission()) {
            binding.cardPermission.visibility = View.GONE
            viewModel.loadStatsForRange(selectedTab)
        } else {
            binding.cardPermission.visibility = View.VISIBLE
        }
    }

    private fun setupRecyclerView() {
        chartAdapter = ChartBarAdapter()
        binding.recyclerChart.apply {
            adapter = chartAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        }
    }

    private fun setupListeners() {
        binding.buttonBack.setOnClickListener { findNavController().popBackStack() }

        binding.buttonGrantPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        binding.tabDay.setOnClickListener { selectTab(TimeRange.DAY) }
        binding.tabWeek.setOnClickListener { selectTab(TimeRange.WEEK) }
        binding.tabMonth.setOnClickListener { selectTab(TimeRange.MONTH) }
        binding.tabYear.setOnClickListener { selectTab(TimeRange.YEAR) }
    }

    private fun selectTab(range: TimeRange) {
        if (selectedTab == range) return
        selectedTab = range
        updateSegmentedControl()
        
        val now = System.currentTimeMillis()
        if (now - lastQueryTime > DEBOUNCE_DELAY) {
            lastQueryTime = now
            viewModel.loadStatsForRange(range)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.statsState.collectLatest { state ->
                when (state) {
                    is StatsState.Loading -> {
                        binding.recyclerChart.visibility = View.INVISIBLE
                        binding.layoutShimmerChart.visibility = View.VISIBLE
                        // Simple shimmer effect
                        binding.layoutShimmerChart.alpha = 0.5f
                        binding.layoutShimmerChart.animate().alpha(1.0f).setDuration(500).withEndAction {
                            binding.layoutShimmerChart.animate().alpha(0.5f).setDuration(500).start()
                        }.start()
                    }
                    is StatsState.Success -> {
                        binding.layoutShimmerChart.visibility = View.GONE
                        binding.layoutShimmerChart.animate().cancel()
                        binding.recyclerChart.visibility = View.VISIBLE
                        if (state.range == selectedTab) {
                            renderSuccess(state)
                        }
                    }
                    is StatsState.Error -> {
                        binding.layoutShimmerChart.visibility = View.GONE
                        binding.layoutShimmerChart.animate().cancel()
                        binding.recyclerChart.visibility = View.VISIBLE
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.hourlyUsage.collectLatest { hourly ->
                updateSuggestion(hourly)
            }
        }
    }

    private fun renderSuccess(state: StatsState.Success) {
        val maxVal = state.chartData.maxOrNull() ?: 1L
        val currentIdx = getCurrentIndexForRange(state.range)
        val peakIdx = if (maxVal > 0) state.chartData.indexOf(maxVal) else -1

        val chartItems = state.chartData.mapIndexed { index, value ->
            ChartBarAdapter.ChartItem(
                value = value,
                label = state.labels[index],
                isCurrent = index == currentIdx,
                isPeak = index == peakIdx && value > 0,
                isFuture = index > currentIdx
            )
        }

        // Calculate item width based on screen width and tab
        val screenWidth = resources.displayMetrics.widthPixels - dpToPx(80) // subtracting padding
        val itemWidth = when (state.range) {
            TimeRange.DAY -> screenWidth / 12
            TimeRange.WEEK -> screenWidth / 7
            TimeRange.YEAR -> screenWidth / 12
            else -> dpToPx(40) // For Month, let it scroll
        }

        chartAdapter.submitData(chartItems, maxVal, itemWidth)

        // Update Text
        binding.textTotalTime.text = if (state.range == TimeRange.DAY) {
            formatMillis(state.totalTimeMillis)
        } else {
            "Avg ${formatMillis(state.avgTimeMillis)}/day"
        }

        updateLabels(state)
        updateBalanceCard(state.totalTimeMillis, state.avgTimeMillis)
        updateUsageBreakdown(state.appUsage)
        loadDistractors(state.appUsage)
    }

    private fun getCurrentIndexForRange(range: TimeRange): Int {
        val cal = Calendar.getInstance()
        return when (range) {
            TimeRange.DAY -> cal.get(Calendar.HOUR_OF_DAY) / 2
            TimeRange.WEEK -> (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
            TimeRange.MONTH -> cal.get(Calendar.DAY_OF_MONTH) - 1
            TimeRange.YEAR -> cal.get(Calendar.MONTH)
        }
    }

    private fun updateLabels(state: StatsState.Success) {
        binding.textChartLabel.text = when (state.range) {
            TimeRange.DAY -> "Today"
            TimeRange.WEEK -> "This week"
            TimeRange.MONTH -> "This month"
            TimeRange.YEAR -> "This year"
        }

        val sdf = java.text.SimpleDateFormat("d MMM", Locale.getDefault())
        binding.textAvg.text = when (state.range) {
            TimeRange.DAY -> "Today • ${sdf.format(Calendar.getInstance().time)}"
            TimeRange.WEEK -> {
                val cal = Calendar.getInstance()
                val dow = cal.get(Calendar.DAY_OF_WEEK)
                val diff = (dow - Calendar.MONDAY + 7) % 7
                cal.add(Calendar.DAY_OF_YEAR, -diff)
                val start = sdf.format(cal.time)
                cal.add(Calendar.DAY_OF_YEAR, 6)
                val end = sdf.format(cal.time)
                "$start - $end"
            }
            else -> ""
        }
        
        if (state.range == TimeRange.DAY) {
            val maxVal = state.chartData.maxOrNull() ?: 0L
            val peakIndex = if (maxVal > 0) state.chartData.indexOf(maxVal) else -1
            if (peakIndex != -1) {
                val peakStart = String.format(Locale.getDefault(), "%02d:00", peakIndex * 2)
                val peakEnd = String.format(Locale.getDefault(), "%02d:00", (peakIndex * 2 + 2) % 24)
                binding.textPeakRange.text = "Peak: $peakStart - $peakEnd"
                binding.textPeakRange.visibility = View.VISIBLE
            } else {
                binding.textPeakRange.visibility = View.GONE
            }
        } else {
            binding.textPeakRange.visibility = View.GONE
        }
    }

    private fun updateBalanceCard(totalMillis: Long, avgDailyMillis: Long) {
        val awakeTimePerDayMillis = 16L * 3600000L
        val compareMillis = if (selectedTab == TimeRange.DAY) totalMillis else avgDailyMillis
        val percent = ((compareMillis.toFloat() / awakeTimePerDayMillis) * 100).toInt().coerceIn(0, 100)
        
        binding.textBalancePercent.text = "$percent% of awake time"
        val colorStr = if (percent > 80) "#EF4444" else "#3B82F6"
        binding.textBalancePercent.setTextColor(android.graphics.Color.parseColor(colorStr))
        
        binding.textScreenTimeBalance.text = formatMillis(compareMillis) + (if (selectedTab != TimeRange.DAY) "/day avg" else "")
        binding.textScreenTimeBalance.setTextColor(android.graphics.Color.parseColor(colorStr))

        binding.viewBalanceProgress.post {
            val parentWidth = (binding.viewBalanceProgress.parent as View).width
            val params = binding.viewBalanceProgress.layoutParams
            params.width = (parentWidth * percent / 100)
            binding.viewBalanceProgress.layoutParams = params
            binding.viewBalanceProgress.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor(colorStr))
                cornerRadius = dpToPx(4).toFloat()
            }
        }
    }

    private fun updateUsageBreakdown(stats: List<com.example.appblocker.AppUsageInfo>) {
        var neutral = 0L
        var distracting = 0L
        var productive = 0L

        stats.forEach { app ->
            when {
                viewModel.isDistractingApp(app.packageName) -> distracting += app.usageTimeMillis
                viewModel.isProductiveApp(app.packageName) -> productive += app.usageTimeMillis
                else -> neutral += app.usageTimeMillis
            }
        }

        val total = neutral + distracting + productive
        val pNeutral = if (total > 0) (neutral * 100 / total).toInt() else 0
        val pDistracting = if (total > 0) (distracting * 100 / total).toInt() else 0
        val pProductive = if (total > 0) (productive * 100 / total).toInt() else 0

        binding.textNeutral.text = "$pNeutral%"
        binding.textDistracting.text = "$pDistracting%"
        binding.textProductive.text = "$pProductive%"
        
        updateBarWidth(binding.viewNeutral, pNeutral)
        updateBarWidth(binding.viewDistracting, pDistracting)
        updateBarWidth(binding.viewProductive, pProductive)
    }

    private fun updateBarWidth(view: View, percent: Int) {
        view.post {
            val parentWidth = (view.parent as View).width
            val params = view.layoutParams
            params.width = (parentWidth * percent / 100)
            view.layoutParams = params
        }
    }

    private fun loadDistractors(stats: List<com.example.appblocker.AppUsageInfo>) {
        binding.containerDistractorsStats.removeAllViews()
        val distractors = stats
            .filter { viewModel.isDistractingApp(it.packageName) && it.usageTimeMillis > 60000 }
            .take(3)

        if (distractors.isEmpty()) {
            binding.cardEmptyDistractors.visibility = View.VISIBLE
            binding.cardDistractors.visibility = View.GONE
        } else {
            binding.cardEmptyDistractors.visibility = View.GONE
            binding.cardDistractors.visibility = View.VISIBLE
            
            distractors.forEachIndexed { index, app ->
                val item = ItemDistractorPremiumBinding.inflate(layoutInflater, binding.containerDistractorsStats, false)
                item.textRank.text = (index + 1).toString()
                item.textName.text = app.name
                item.imageIcon.setImageDrawable(app.icon)
                item.textTime.text = formatMillis(app.usageTimeMillis)
                
                val appInfo = viewModel.uiState.value.find { it.packageName == app.packageName }
                item.buttonBlock.text = if (appInfo?.isBlocked == true) "Unblock" else "Block"
                item.buttonBlock.setOnClickListener {
                    appInfo?.let { viewModel.toggleBlock(it) }
                }
                
                binding.containerDistractorsStats.addView(item.root)
            }
        }
    }

    private fun updateSuggestion(hourly: List<com.example.appblocker.repository.StatsRepository.HourlyUsage>) {
        if (selectedTab != TimeRange.DAY) {
            binding.cardSuggestion.visibility = View.GONE
            return
        }
        
        val peak = hourly.maxByOrNull { it.durationMillis }
        if (peak != null && peak.durationMillis > 10 * 60000) {
            binding.cardSuggestion.visibility = View.VISIBLE
            val hourStr = if (peak.hour > 12) "${peak.hour - 12} PM" else "${peak.hour} AM"
            binding.textSuggestionTitle.text = "High usage at $hourStr"
            binding.textSuggestionDesc.text = "You spent ${formatMillis(peak.durationMillis)} in this hour. Consider a focus session."
        } else {
            binding.cardSuggestion.visibility = View.GONE
        }
    }

    private fun updateSegmentedControl() {
        val tabs = listOf(
            binding.tabDay to TimeRange.DAY,
            binding.tabWeek to TimeRange.WEEK,
            binding.tabMonth to TimeRange.MONTH,
            binding.tabYear to TimeRange.YEAR
        )

        tabs.forEach { (view, range) ->
            if (range == selectedTab) {
                view.setBackgroundResource(R.drawable.bg_segment_selected)
                view.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                view.typeface = android.graphics.Typeface.DEFAULT_BOLD
            } else {
                view.background = null
                view.setTextColor(android.graphics.Color.parseColor("#8A8A8E"))
                view.typeface = android.graphics.Typeface.DEFAULT
            }
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun formatMillis(millis: Long): String {
        val h = millis / 3600000
        val m = (millis % 3600000) / 60000
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}