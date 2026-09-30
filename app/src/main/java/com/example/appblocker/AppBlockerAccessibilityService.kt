package com.example.appblocker

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

// Import existing settingsDataStore
import com.example.appblocker.settingsDataStore

class AppBlockerAccessibilityService : AccessibilityService() {

    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isBlockingActive = false

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            isBlockingActive = false
            removeOverlay()
        }
    }

    private val startReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            isBlockingActive = true
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.e("FocusBlock", "Service Connected")
        // Fix warning line 50 - remove redundant qualifier if needed
        // Fix error line 52 & 53 - add Android 14 flag

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, android.content.IntentFilter("com.example.appblocker.STOP_BLOCKING"), android.content.Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(startReceiver, android.content.IntentFilter("com.example.appblocker.START_BLOCKING"), android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopReceiver, android.content.IntentFilter("com.example.appblocker.STOP_BLOCKING"))
            registerReceiver(startReceiver, android.content.IntentFilter("com.example.appblocker.START_BLOCKING"))
        }
    }

    private fun loadKeywords(): Set<String> = runBlocking(Dispatchers.IO) {
        try {
            val data = applicationContext.settingsDataStore.data.first()
            val kw = data[stringSetPreferencesKey("blocked_keywords")] ?: emptySet()
            Log.e("FocusBlock", "FINAL keywords=$kw")
            kw.map { it.lowercase() }.toSet()
        } catch (e: Exception) {
            Log.e("FocusBlock", "ERROR load $e", e)
            emptySet()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isBlockingActive) return
        val pkg = event.packageName?.toString() ?: ""
        if (!pkg.contains("chrome", true) && !pkg.contains("google", true)) return
        val text = (event.text?.joinToString(" ") ?: "") + " " + (event.contentDescription?.toString() ?: "")
        if (text.isBlank()) return
        val keywords = loadKeywords()
        if (keywords.isEmpty()) return
        val lowerText = text.lowercase()
        val matched = keywords.firstOrNull { k ->
            val clean = k.lowercase().trim()
            clean.isNotEmpty() && !clean.startsWith("web:") && lowerText.contains(clean)
        }
        if (matched != null) {
            Log.e("FocusBlock", "BLOCKING matched=$matched pkg=$pkg")
            showOverlay(matched)
        }
    }

    private fun showOverlay(keyword: String) {
        if (overlayView != null) return
        handler.post {
            try {
                val view = LayoutInflater.from(this).inflate(R.layout.overlay_blocked, null)
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    android.graphics.PixelFormat.TRANSLUCENT
                )
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.addView(view, params)
                overlayView = view
                view.setOnClickListener { removeOverlay() }
                handler.postDelayed({ removeOverlay() }, 3000)
            } catch (e: Exception) {
                Log.e("FocusBlock", "overlay error $e")
            }
        }
    }

    private fun removeOverlay() {
        handler.post {
            try {
                overlayView?.let {
                    val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    wm.removeView(it)
                }
                overlayView = null
            } catch (_: Exception) {}
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(stopReceiver) } catch(_: Exception) {}
        try { unregisterReceiver(startReceiver) } catch(_: Exception) {}
        removeOverlay()
    }
}
