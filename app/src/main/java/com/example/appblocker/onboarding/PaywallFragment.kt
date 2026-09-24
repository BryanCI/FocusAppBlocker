package com.example.appblocker.onboarding

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import androidx.fragment.app.Fragment
import com.example.appblocker.MainActivity
import com.example.appblocker.R
import com.example.appblocker.databinding.FragmentOnboardingTrialBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PaywallFragment : Fragment(R.layout.fragment_onboarding_trial) {

    private var _binding: FragmentOnboardingTrialBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentOnboardingTrialBinding.bind(view)

        setupPricingText()
        
        binding.btnStartTrial.setOnClickListener {
            // 1. Save onboarding complete
            requireContext().getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("onboarding_completed", true).apply()
                
            // 2. LAUNCH MAIN DIRECTLY - DO NOT LAUNCH PREMIUM PAYWALL
            val intent = Intent(requireContext(), MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        binding.btnClose.setOnClickListener {
            (activity as? OnboardingActivity)?.finishOnboarding()
        }
    }

    private fun setupPricingText() {
        val tvPricing = binding.tvPricingText
        val fullText = "First 7 days are free then $1 a month."
        val targetText = "$1 a month."
        
        val spannable = SpannableString(fullText)
        val startIndex = fullText.indexOf(targetText)
        val endIndex = startIndex + targetText.length
        
        if (startIndex != -1) {
            // 1. Increase font size by 4dp
            val currentSizePx = tvPricing.textSize.toInt()
            val increasePx = (4 * resources.displayMetrics.density).toInt()
            spannable.setSpan(
                AbsoluteSizeSpan(currentSizePx + increasePx),
                startIndex,
                endIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // 2. Neon purple color
            val magenta = resources.getColor(R.color.neon_purple_premium, null)
            spannable.setSpan(
                ForegroundColorSpan(magenta),
                startIndex,
                endIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // 3. Keep it bold
            spannable.setSpan(
                StyleSpan(android.graphics.Typeface.BOLD),
                startIndex,
                endIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        tvPricing.text = spannable
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}