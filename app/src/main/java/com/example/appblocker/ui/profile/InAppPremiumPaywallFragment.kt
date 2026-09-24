package com.example.appblocker.ui.profile

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.appblocker.MainActivity
import com.example.appblocker.MainViewModel
import com.example.appblocker.R
import com.example.appblocker.databinding.FragmentInappPremiumPaywallBinding
import com.example.appblocker.databinding.ItemPremiumFeatureLuxuryBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class InAppPremiumPaywallFragment : Fragment(R.layout.fragment_inapp_premium_paywall) {

    private val viewModel: MainViewModel by activityViewModels()
    private var selectedProductId = "annual_6_99"
    private var _binding: FragmentInappPremiumPaywallBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentInappPremiumPaywallBinding.bind(view)
        
        setupFeatureRows()
        setupClicks()
        
        // Initial UI state - Yearly selected by default
        updateSelectionUi(isAnnual = true)

        val source = arguments?.getString("source")
        if (source == "stealth_mode") {
            binding.tvHeroTitle2.text = "Privacy Protection &"
            binding.feature2.root.setBackgroundResource(R.drawable.bg_luxury_card_selected_purple)
            ItemPremiumFeatureLuxuryBinding.bind(binding.feature2.root).apply {
                featureTitle.setTextColor(Color.WHITE)
                featureSubtitle.setTextColor(Color.parseColor("#EBEBF5"))
            }
        }

        startHeroAnimations()
        animateFeatureEntrance()
        setupSwipeGestures(view)
    }

    private fun setupFeatureRows() {
        // Row 1: Gold icon circle
        ItemPremiumFeatureLuxuryBinding.bind(binding.feature1.root).apply {
            iconEmoji.text = "📅"
            iconContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2A2210"))
            iconEmoji.setTextColor(Color.parseColor("#FFD700"))
            featureTitle.text = "Unlimited Schedule blocks"
            featureSubtitle.text = "Set automatic blocks tailored to your needs"
        }

        // Row 2: Purple icon circle
        ItemPremiumFeatureLuxuryBinding.bind(binding.feature2.root).apply {
            iconEmoji.text = "⚡"
            iconContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2A103F"))
            iconEmoji.setTextColor(Color.parseColor("#BF00FF"))
            featureTitle.text = "Powerful Strict Mode"
            featureSubtitle.text = "Extra settings to lock in your focus"
        }

        // Row 3: Blue icon circle
        ItemPremiumFeatureLuxuryBinding.bind(binding.feature3.root).apply {
            iconEmoji.text = "⏱️"
            iconContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#0F1A2A"))
            iconEmoji.setTextColor(Color.parseColor("#3B82F6"))
            featureTitle.text = "Quick Block Timer & Pomodoro"
            featureSubtitle.text = "Flexible timer to elevate sessions"
        }

        // Row 4: Orange icon circle
        ItemPremiumFeatureLuxuryBinding.bind(binding.feature4.root).apply {
            iconEmoji.text = "🚫"
            iconContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2A1F0F"))
            iconEmoji.setTextColor(Color.parseColor("#F59E0B"))
            featureTitle.text = "Ad-free Experience"
            featureSubtitle.text = "Complete focus, zero ads"
        }
    }

    private fun startHeroAnimations() {
        // Floating diamond animation
        ObjectAnimator.ofFloat(binding.ivDiamond, "translationY", -10f, 10f).apply {
            duration = 1500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // Yearly card subtle pulse
        binding.layoutAnnual.postDelayed({
            binding.layoutAnnual.animate()
                .scaleX(1.02f)
                .scaleY(1.02f)
                .setDuration(1500)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    binding.layoutAnnual.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(1500)
                        .start()
                }
                .start()
        }, 500)
    }

    private fun animateFeatureEntrance() {
        val features = listOf(binding.feature1.root, binding.feature2.root, binding.feature3.root, binding.feature4.root)
        features.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 40f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .setStartDelay(100L * index)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun setupClicks() {
        binding.btnClose.setOnClickListener { 
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            animateClick(it)
            val activity = requireActivity()
            val intent = Intent(activity, MainActivity::class.java).apply {
                putExtra("navigate_to_tab", "home")
                putExtra("SKIP_SPLASH", true)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            if (activity !is MainActivity) activity.finish()
        }
        
        binding.layoutMonthly.setOnClickListener { 
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            animateClick(it)
            selectedProductId = "monthly_1" 
            updateSelectionUi(isAnnual = false)
        }
        
        binding.layoutAnnual.setOnClickListener { 
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            animateClick(it)
            selectedProductId = "annual_6_99" 
            updateSelectionUi(isAnnual = true)
        }
        
        binding.btnSubscribe.setOnClickListener { 
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            animateClick(it)
            viewModel.startBillingFlow(requireActivity(), selectedProductId)
        }

        binding.btnRestore.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            viewModel.restorePurchases()
        }
    }

    private fun animateClick(view: View) {
        view.animate()
            .scaleX(0.98f)
            .scaleY(0.98f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private fun updateSelectionUi(isAnnual: Boolean) {
        // Monthly Card Update
        if (!isAnnual) {
            binding.layoutMonthly.setBackgroundResource(R.drawable.bg_luxury_card_selected_purple)
            binding.radioMonthly.setBackgroundResource(R.drawable.bg_radio_selected_purple)
            binding.tvSelectedMonthly.visibility = View.VISIBLE
            binding.btnSubscribe.text = "Continue • $1.00/month"
        } else {
            binding.layoutMonthly.setBackgroundResource(R.drawable.bg_luxury_card)
            binding.radioMonthly.setBackgroundResource(R.drawable.bg_radio_unselected)
            binding.tvSelectedMonthly.visibility = View.GONE
        }

        // Annual Card Update
        if (isAnnual) {
            binding.layoutAnnual.setBackgroundResource(R.drawable.bg_pricing_card_selected_gold) // Using the gold tinted one
            binding.radioAnnual.setBackgroundResource(R.drawable.bg_radio_selected_gold)
            binding.layoutAnnual.animate().scaleX(1.02f).scaleY(1.02f).setDuration(200).start()
            binding.btnSubscribe.text = "Continue • $6.99/year"
        } else {
            binding.layoutAnnual.setBackgroundResource(R.drawable.bg_luxury_card)
            binding.radioAnnual.setBackgroundResource(R.drawable.bg_radio_unselected)
            binding.layoutAnnual.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
        }
    }

    private fun setupSwipeGestures(view: View) {
        var startX = 0f
        view.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    val endX = event.x
                    val diff = endX - startX
                    if (Math.abs(diff) > 150) {
                        // Removed swipe calls
                    }
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
