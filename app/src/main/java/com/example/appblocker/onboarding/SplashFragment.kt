package com.example.appblocker.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.appblocker.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import android.os.Build
import android.widget.ImageView
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest

class SplashFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_splash, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val imageView = view.findViewById<ImageView>(R.id.ivSplashAnim)
        val imageLoader = ImageLoader.Builder(requireContext())
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()

        val request = ImageRequest.Builder(requireContext())
            .data(R.drawable.focussapp)
            .target(imageView)
            .build()
        imageLoader.enqueue(request)

        var tapCount = 0
        imageView.setOnLongClickListener {
            tapCount++
            if (tapCount >= 5) {
                val appPrefs = requireContext().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                appPrefs.edit().clear().apply()
                android.widget.Toast.makeText(requireContext(), "Developer Reset: Preferences Cleared", android.widget.Toast.LENGTH_SHORT).show()
                tapCount = 0
            }
            true
        }

        lifecycleScope.launch {
            delay(500) // Reduced to half a second
            if (findNavController().currentDestination?.id == R.id.splashFragment) {
                val appPrefs = requireContext().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                val onboardingDone = appPrefs.getBoolean("onboarding_done", false)
                val isFirstLaunch = appPrefs.getBoolean("is_first_launch", true)
                
                if (!onboardingDone || isFirstLaunch) {
                    findNavController().navigate(R.id.action_splashFragment_to_welcomeFragment)
                } else {
                    val intent = android.content.Intent(requireContext(), com.example.appblocker.MainActivity::class.java)
                    startActivity(intent)
                    activity?.finish()
                }
            }
        }
    }
}