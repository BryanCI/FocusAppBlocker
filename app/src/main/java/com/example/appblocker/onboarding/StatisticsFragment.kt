package com.example.appblocker.onboarding

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.appblocker.MainActivity
import com.example.appblocker.MainViewModel
import com.example.appblocker.R
import com.example.appblocker.StatsState
import com.example.appblocker.databinding.FragmentStatisticsBinding
import com.example.appblocker.repository.TimeRange
import com.example.appblocker.ui.statistics.ChartBarAdapter
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StatisticsFragment : Fragment(R.layout.fragment_statistics) {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var chartAdapter: ChartBarAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentStatisticsBinding.bind(view)

        // Hide bottom nav
        (requireActivity() as? MainActivity)?.findViewById<View>(R.id.bottom_nav_container)?.visibility = View.GONE

        binding.buttonBack.setOnClickListener {
            findNavController().popBackStack()
        }

        setupRecyclerView()
        observeViewModel()

        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission()
        } else {
            viewModel.loadStatsForRange(TimeRange.WEEK)
        }
    }

    private fun setupRecyclerView() {
        chartAdapter = ChartBarAdapter()
        binding.recyclerChart.apply {
            adapter = chartAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.statsState.collectLatest { state ->
                if (state is StatsState.Success && state.range == TimeRange.WEEK) {
                    renderChart(state)
                }
            }
        }
    }

    private fun renderChart(state: StatsState.Success) {
        val maxVal = state.chartData.maxOrNull() ?: 1L
        val screenWidth = resources.displayMetrics.widthPixels - dpToPx(80)
        val itemWidth = screenWidth / 7

        val chartItems = state.chartData.mapIndexed { index, value ->
            ChartBarAdapter.ChartItem(
                value = value,
                label = state.labels[index],
                isCurrent = index == 6, // In the onboarding view it seems to expect last 7 days
                isPeak = false,
                isFuture = false
            )
        }
        chartAdapter.submitData(chartItems, maxVal, itemWidth)
    }

    private fun requestUsageStatsPermission() {
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (e: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasUsageStatsPermission()) {
            viewModel.loadStatsForRange(TimeRange.WEEK)
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = requireContext().getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            requireContext().packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        (requireActivity() as? MainActivity)?.findViewById<View>(R.id.bottom_nav_container)?.visibility = View.VISIBLE
        _binding = null
    }
}
