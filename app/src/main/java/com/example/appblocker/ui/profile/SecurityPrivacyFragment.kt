package com.example.appblocker.ui.profile

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.appblocker.MainActivity
import com.example.appblocker.R
import com.example.appblocker.databinding.FragmentSecurityPrivacyBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SecurityPrivacyFragment : Fragment() {
    private var _binding: FragmentSecurityPrivacyBinding? = null
    private val binding get() = _binding!!
    
    private val prefs by lazy { requireContext().getSharedPreferences("security_prefs", Context.MODE_PRIVATE) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSecurityPrivacyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refreshAll()
        binding.itemAppLock.setOnClickListener { showAppLockDialog() }
        binding.itemStealth.setOnClickListener { showStealthDialog() }
    }

    private fun refreshAll() {
        val count = getRealCount()
        binding.tvPermissionsStatus.text = "$count/5 granted • Tap to manage"
    }

    private fun getRealCount(): Int {
        var c = 0
        val ctx = requireContext()
        try {
            val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            if (appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName) == android.app.AppOpsManager.MODE_ALLOWED) c++
            val en = android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            if (en.contains(ctx.packageName)) c++
            if (android.provider.Settings.canDrawOverlays(ctx)) c++
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (pm.isIgnoringBatteryOptimizations(ctx.packageName)) c++
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                val am = ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                if (am.canScheduleExactAlarms()) c++
            } else c++
        } catch (e: Exception) {}
        return c.coerceIn(0, 5)
    }

    private fun showAppLockDialog() {
        val isEnabled = prefs.getBoolean("app_lock_enabled", false)
        val dv = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_switch_setting, null)
        val sw = dv.findViewById<SwitchCompat>(R.id.switchSetting)
        val td = dv.findViewById<android.widget.TextView>(R.id.tvDesc)
        sw.isChecked = isEnabled
        td.text = "Require phone PIN / fingerprint / pattern every time app opens. Uses phone's own lock."
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("App Lock")
            .setView(dv)
            .setPositiveButton("Save") { _, _ ->
                val nv = sw.isChecked
                if (nv && !isEnabled) {
                    authenticate { ok ->
                        if (ok) {
                            prefs.edit().putBoolean("app_lock_enabled", true).apply()
                            refreshAll()
                            Toast.makeText(requireContext(), "Enabled", Toast.LENGTH_SHORT).show()
                            // Removed forceLockNextTime call
                        } else Toast.makeText(requireContext(), "Auth failed", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    prefs.edit().putBoolean("app_lock_enabled", nv).apply()
                    refreshAll()
                    if (!nv) {
                        try {
                            val act = requireActivity()
                            val f = act.javaClass.getDeclaredField("isAppUnlocked")
                            f.isAccessible = true
                            f.set(act, true)
                        } catch (e: Exception) {}
                    }
                    Toast.makeText(requireContext(), if (nv) "Enabled" else "Disabled", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showStealthDialog() {
        val isEnabled = prefs.getBoolean("stealth_enabled", false)
        val dv = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_switch_setting, null)
        val sw = dv.findViewById<SwitchCompat>(R.id.switchSetting)
        val td = dv.findViewById<android.widget.TextView>(R.id.tvDesc)
        sw.isChecked = isEnabled
        td.text = "Hide from recent apps and block screenshots."
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Stealth Mode")
            .setView(dv)
            .setPositiveButton("Save") { _, _ ->
                val nv = sw.isChecked
                prefs.edit().putBoolean("stealth_enabled", nv).apply()
                refreshAll()
                Toast.makeText(requireContext(), if (nv) "Enabled" else "Disabled", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun authenticate(cb: (Boolean) -> Unit) {
        val ex = ContextCompat.getMainExecutor(requireContext())
        val p = BiometricPrompt(this, ex, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) { cb(true) }
            override fun onAuthenticationError(c: Int, e: CharSequence) { cb(false) }
            override fun onAuthenticationFailed() { cb(false) }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Enable App Lock")
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        p.authenticate(info)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
