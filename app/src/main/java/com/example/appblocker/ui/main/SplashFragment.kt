package com.example.appblocker.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.appblocker.ui.auth.AuthViewModel
import com.example.appblocker.MainViewModel
import com.example.appblocker.R
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import com.example.appblocker.ui.theme.AppBlockerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SplashFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private val authViewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                AppBlockerTheme {
                    SplashContent()
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            // Wait for both settings and auth state to be available
            combine(
                viewModel.settings.filterNotNull(),
                authViewModel.currentUser,
                viewModel.isPremium
            ) { settings, user, isPremium ->
                Triple(settings, user, isPremium)
            }.first { (settings, user, isPremium) ->
                // Ensure we have enough data to make a decision
                true 
            }.let { (settings, user, isPremium) ->
                val isPremiumOrTrial = isPremium || settings.isPremium == true ||
                    (settings.isTrialPeriod == true && (settings.premiumExpiryDate == null || System.currentTimeMillis() < (settings.premiumExpiryDate ?: 0L)))

                if (user == null) {
                    findNavController().navigate(R.id.navigation_login)
                } else {
                    if (isPremiumOrTrial) {
                        findNavController().navigate(R.id.navigation_block)
                    } else {
                        findNavController().navigate(R.id.navigation_paywall)
                    }
                }
            }
        }
    }
}

@Composable
fun SplashContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("FocusAppBlocker")
    }
}
