package com.example.appblocker.onboarding

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.appblocker.BillingManager
import com.example.appblocker.R
import com.example.appblocker.databinding.FragmentOnboardingAccountBinding
import com.example.appblocker.ui.auth.AuthUiState
import com.example.appblocker.ui.auth.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SignInFragment"

@AndroidEntryPoint
class SignInFragment : Fragment(R.layout.fragment_onboarding_account) {

    @Inject
    lateinit var billingManager: BillingManager

    private val authViewModel: AuthViewModel by viewModels()
    private var _binding: FragmentOnboardingAccountBinding? = null
    private val binding get() = _binding!!

    private var isNavigating = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentOnboardingAccountBinding.bind(view)

        setupListeners()
        observeUiState()
        checkExistingUser()
    }

    private fun checkExistingUser() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.currentUser.collect { user ->
                    if (user != null) {
                        Log.d(TAG, "User already signed in (from Flow): ${user.id}")
                        navigateToScreen4()
                    }
                }
            }
        }
    }

    private fun navigateToScreen4() {
        if (isNavigating) {
            Log.d(TAG, "Already navigating, skipping request")
            return
        }

        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.signInFragment) {
            isNavigating = true
            Log.d(TAG, "Successfully triggered navigation to Screen 4")
            try {
                navController.navigate(R.id.action_signInFragment_to_setupPermissionFragment)
                
                // Launch trial flow after navigation attempt
                binding.root.postDelayed({
                    if (isAdded && !billingManager.hasPremiumAccess.value) {
                        Log.d(TAG, "Launching trial flow from SignInFragment")
                        billingManager.launchTrial(requireActivity())
                    }
                }, 500)
            } catch (e: Exception) {
                Log.e(TAG, "Navigation failed", e)
                isNavigating = false // Reset so they can try again if it failed
            }
        } else {
            Log.d(TAG, "Navigation skipped: Not in SignInFragment. Current: ${navController.currentDestination?.label}")
        }
    }

    private fun setupListeners() {
        binding.btnCreateAccount.setOnClickListener {
            val name = binding.etFullName.text.toString()
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                authViewModel.signUpWithEmail(email, password, name)
            } else {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnContinueGoogle.setOnClickListener {
            Log.d(TAG, "Google button clicked")
            authViewModel.signInWithGoogle(requireContext())
        }

        binding.btnContinueGuest.setOnClickListener {
            Log.d(TAG, "Guest button clicked")
            authViewModel.signInAnonymously()
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.uiState.collectLatest { state ->
                    Log.d(TAG, "Auth State changed: $state")
                    when (state) {
                        is AuthUiState.Loading -> {
                            binding.progressBar.visibility = View.VISIBLE
                            setButtonsEnabled(false)
                        }
                        is AuthUiState.Success -> {
                            Log.d(TAG, "Auth Success state received for user: ${state.user.id}")
                            binding.progressBar.visibility = View.GONE
                            setButtonsEnabled(true)
                            navigateToScreen4()
                        }
                        is AuthUiState.Error -> {
                            Log.e(TAG, "Auth Error: ${state.message}")
                            binding.progressBar.visibility = View.GONE
                            setButtonsEnabled(true)
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        }
                        is AuthUiState.Idle -> {
                            binding.progressBar.visibility = View.GONE
                            setButtonsEnabled(true)
                        }
                    }
                }
            }
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.btnCreateAccount.isEnabled = enabled
        binding.btnContinueGoogle.isEnabled = enabled
        binding.btnContinueGuest.isEnabled = enabled
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
