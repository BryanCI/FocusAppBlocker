package com.example.appblocker.onboarding

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.appblocker.R
import com.example.appblocker.databinding.FragmentTakeBackLifeBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WelcomeFragment : Fragment(R.layout.fragment_take_back_life) {
    private var _binding: FragmentTakeBackLifeBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTakeBackLifeBinding.bind(view)

        binding.btnContinueScreen2.setOnClickListener {
            findNavController().navigate(R.id.action_welcomeFragment_to_takeBackLifeFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}