package com.example.appblocker.ui.insights

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.appblocker.MainViewModel
import com.example.appblocker.R
import com.example.appblocker.databinding.FragmentInsightsBinding
import com.example.appblocker.databinding.ItemDistractorPremiumBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class InsightsFragment : Fragment() {

    private var _binding: FragmentInsightsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInsightsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.loadUsageStats()

        binding.cardStatistics.setOnClickListener {
            findNavController().navigate(R.id.navigation_statistics)
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.todayUsageStats.collect { usageStats ->
                val ctx = context ?: return@collect
                val b = _binding ?: return@collect
                
                val filtered = usageStats.filter {
                    it.packageName != ctx.packageName &&
                            !it.packageName.contains("launcher", ignoreCase = true) &&
                            !it.packageName.contains("systemui") &&
                            it.usageTimeMillis > 60_000
                }.sortedByDescending { it.usageTimeMillis }.take(3)

                b.containerDistractors.removeAllViews()
                if (filtered.isEmpty()) {
                    b.cardEmptyDistractors.visibility = View.VISIBLE
                    b.cardDistractors.visibility = View.GONE
                } else {
                    b.cardEmptyDistractors.visibility = View.GONE
                    b.cardDistractors.visibility = View.VISIBLE
                    val uiState = viewModel.uiState.value
                    filtered.forEachIndexed { index, app ->
                        val itemBinding = ItemDistractorPremiumBinding.inflate(
                            layoutInflater,
                            b.containerDistractors,
                            false
                        )
                        itemBinding.textRank.text = ctx.getString(R.string.text_rank_format, index + 1)
                        itemBinding.textName.text = app.name

                        val timeMinutes = (app.usageTimeMillis / 60000).toInt()
                        itemBinding.textTime.text = ctx.getString(R.string.time_today_format, timeMinutes)
                        itemBinding.imageIcon.setImageDrawable(app.icon)

                        val isBlocked = uiState.find { it.packageName == app.packageName }?.isBlocked == true
                        itemBinding.buttonBlock.text = if (isBlocked) "Unblock" else "Block"
                        itemBinding.buttonBlock.setOnClickListener {
                            val appInfo = viewModel.uiState.value.find { it.packageName == app.packageName }
                            if (appInfo != null) {
                                viewModel.toggleBlock(appInfo)
                            }
                        }

                        b.containerDistractors.addView(itemBinding.root)
                        
                        // Hide divider for last item
                        if (index == filtered.size - 1) {
                            itemBinding.divider.visibility = View.GONE
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val score = 85
            val b = _binding ?: return@launch
            val ctx = context ?: return@launch

            b.textScore.text = score.toString()
            b.textLevel.text = ctx.getString(R.string.lvl_badge, 4)
            b.textStreak.text = ctx.getString(R.string.streak_text, 3)
            b.textBest.text = ctx.getString(R.string.best_streak, 12)

            b.viewProgress.post {
                _binding?.let { b2 ->
                    val params = b2.viewProgress.layoutParams
                    params.width = (b2.viewProgress.parent as View).width * score / 100
                    b2.viewProgress.layoutParams = params
                }
            }

            b.textScoreMessage.text = ctx.getString(R.string.score_message_great)
            b.textScoreMessage.setTextColor(
                androidx.core.content.ContextCompat.getColor(ctx, R.color.text_secondary)
            )

            // Setup bonus suggestion text with partial bold
            val bonusText = "for a +20% bonus."
            val spannable = android.text.SpannableString(bonusText)
            val start = bonusText.indexOf("+20%")
            val end = start + "+20%".length
            if (start != -1) {
                spannable.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    start,
                    end,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            b.textBonusSuggestion.text = spannable
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
