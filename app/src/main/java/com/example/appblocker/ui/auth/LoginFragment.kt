package com.example.appblocker.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.appblocker.R
import com.example.appblocker.ui.theme.AppBlockerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private val authViewModel: AuthViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeUiState()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.uiState.collect { state ->
                    if (state is AuthUiState.Success) {
                        findNavController().navigate(R.id.navigation_block)
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                AppBlockerTheme {
                    LoginScreen(
                        authViewModel = authViewModel,
                        onLoginSuccess = {
                            findNavController().navigate(R.id.navigation_block)
                        },
                        onSkip = {
                            findNavController().navigate(R.id.navigation_block)
                        },
                        onCreateAccount = {
                            findNavController().navigate(R.id.navigation_register)
                        }
                    )
                }
            }
        }
    }
}
