package com.example.appblocker.onboarding

import android.os.Bundle
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.appblocker.R
import com.example.appblocker.databinding.FragmentTakeBackLifeBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Screen 2: "Time to take back your life"
 */
@AndroidEntryPoint
class TakeBackLifeFragment : Fragment(R.layout.fragment_take_back_life) {

    private var _binding: FragmentTakeBackLifeBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTakeBackLifeBinding.bind(view)

        // Start stronger breathing animation
        val strongAnim = AnimationUtils.loadAnimation(requireContext(), R.anim.logo_breath_strong)
        binding.ivLogoScreen2.startAnimation(strongAnim)

        // Stronger glow effect
        binding.ivLogoScreen2.elevation = 20f
        binding.ivLogoScreen2.outlineProvider = ViewOutlineProvider.BOUNDS

        binding.btnContinueScreen2.setOnClickListener {
            navigateToSignIn()
        }
    }

    private fun navigateToSignIn() {
        findNavController().navigate(R.id.action_takeBackLifeFragment_to_signInFragment)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}