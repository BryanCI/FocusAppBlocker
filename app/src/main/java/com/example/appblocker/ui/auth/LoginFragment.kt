package com.example.appblocker.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.appblocker.R
import com.example.appblocker.ui.theme.AppBlockerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private val authViewModel: AuthViewModel by activityViewModels()

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
