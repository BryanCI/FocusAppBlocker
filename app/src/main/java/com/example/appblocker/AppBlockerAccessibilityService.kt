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
import android.view.accessibility.AccessibilityNodeInfo
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.example.appblocker.settingsDataStore

class AppBlockerAccessibilityService : AccessibilityService() {

    private var overlayView: View? = null
    private var blockTimer: android.os.CountDownTimer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isBlockingActive = false
    private var lastBlockTime = 0L
    private val accPrefs by lazy { FocusSessionManager.getDeviceProtectedPrefs(this, "accessibility_block_prefs") }

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            isBlockingActive = false
            removeOverlay()
        }
    }
    private val startReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { isBlockingActive = true }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("ADV_BLOCK", "Service Connected")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, IntentFilter("com.example.appblocker.STOP_BLOCKING"), RECEIVER_NOT_EXPORTED)
            registerReceiver(startReceiver, IntentFilter("com.example.appblocker.START_BLOCKING"), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopReceiver, IntentFilter("com.example.appblocker.STOP_BLOCKING"))
            registerReceiver(startReceiver, IntentFilter("com.example.appblocker.START_BLOCKING"))
        }
    }

    private fun loadKeywords(): Set<String> = runBlocking(Dispatchers.IO) {
        try {
            val data = applicationContext.settingsDataStore.data.first()
            val kw = data[stringSetPreferencesKey("blocked_keywords")] ?: emptySet()
            kw.map { it.lowercase() }.toSet()
        } catch (e: Exception) { emptySet() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: ""
        
        // Always log pkg to see if we are receiving events
        if (pkg.contains("whatsapp") || pkg.contains("youtube") || pkg.contains("facebook")) {
            Log.v("ADV_BLOCK", "Event from $pkg type=${event.eventType}")
        }

        val rootNode = rootInActiveWindow
        if (rootNode == null) return

        // --- FIX 1: Advanced blocking FIRST, without session gate ---
        if (System.currentTimeMillis() - lastBlockTime > 800) {
            try {
                val blockWA = accPrefs.getBoolean(CustomizeBlockActivity.KEY_WHATSAPP_CHANNELS, false)
                val blockYT = accPrefs.getBoolean(CustomizeBlockActivity.KEY_YOUTUBE_SHORTS, false)
                val blockFB = accPrefs.getBoolean(CustomizeBlockActivity.KEY_FACEBOOK_REELS, false)

                if (pkg.contains("whatsapp") || pkg.contains("youtube") || pkg.contains("facebook")) {
                    Log.d("ADV_BLOCK", "CHECK pkg=$pkg wa=$blockWA yt=$blockYT fb=$blockFB")
                }

                if (blockWA && (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b")) {
                    if (isInsideWhatsAppChannel(rootNode, event)) {
                        Log.w("ADV_BLOCK", ">>> BLOCKING WHATSAPP CHANNEL <<<")
                        performBlockAction("WhatsApp Channels", "whatsapp_channels")
                        return
                    }
                }
                if (blockYT && pkg == "com.google.android.youtube") {
                    if (isInsideYouTubeShorts(rootNode, event)) {
                        Log.w("ADV_BLOCK", ">>> BLOCKING YOUTUBE SHORTS <<<")
                        performBlockAction("YouTube Shorts", "youtube_shorts")
                        return
                    }
                }
                if (blockFB && (pkg == "com.facebook.katana" || pkg == "com.facebook.lite")) {
                    if (isInsideFacebookReels(rootNode)) {
                        Log.w("ADV_BLOCK", ">>> BLOCKING FACEBOOK REELS <<<")
                        performBlockAction("Facebook Reels", "facebook_reels")
                        return
                    }
                }
            } catch (e: Exception) {
                Log.e("ADV_BLOCK", "Adv error ${e.message}")
            }
        }

        // --- Keyword blocking ONLY when session active ---
        isBlockingActive = FocusSessionManager.isSessionActive(this)
        if (!isBlockingActive) return

        if (!pkg.contains("chrome", true) && !pkg.contains("google", true)) return
        val text = (event.text.joinToString(" ")) + " " + (event.contentDescription?.toString() ?: "")
        if (text.isBlank()) return
        val keywords = loadKeywords()
        if (keywords.isEmpty()) return
        val lowerText = text.lowercase()
        val matched = keywords.firstOrNull { k ->
            val clean = k.lowercase().trim()
            clean.isNotEmpty() && !clean.startsWith("web:") && lowerText.contains(clean)
        }
        if (matched != null) {
            Log.d("ADV_BLOCK", "BLOCKING keyword=$matched")
            showOverlay(matched)
        }
    }

    private fun performBlockAction(label: String, featureKey: String) {
        lastBlockTime = System.currentTimeMillis()
        val remaining = FocusSessionManager.getRemainingMillis(this)
        showOverlay("", featureKey, if (remaining > 0 && remaining != Long.MAX_VALUE) remaining else 0L)
    }

    private fun isInsideWhatsAppChannel(root: AccessibilityNodeInfo, event: AccessibilityEvent? = null): Boolean {
        try {
            val texts = getAllTextFromNode(root)
            val joined = texts.joinToString(" ").lowercase()
            val isClick = event?.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
            val isWindow = event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

            Log.d("ADV_BLOCK", "WA_CHECK click=$isClick win=$isWindow joined=${joined.take(800)}")

            // --- ALWAYS ALLOW ---
            // 1. Normal chat
            if (joined.contains("type a message") && !joined.contains("channel info")) return false
            // 2. Status viewing - Status never has followers, has "view" or "status" or "tap to view"
            if (!joined.contains("followers") && (joined.contains("my status") || joined.contains("view status") || joined.contains("tap to view status") || joined.contains("status privacy"))) {
                return false
            }
            // 3. Allow scrolling Updates tab if only Status visible + Channels header but no interaction
            if (!isClick && !isWindow && joined.contains("status") && joined.contains("find channels to follow") && !joined.contains("unfollow")) {
                // This is Screen 1 or 2 static list, allow seeing it
                return false
            }

            val hasFollowers = joined.contains("followers")
            val noChatInput = !joined.contains("type a message")
            val isFindList = joined.contains("find channels to follow")

            // --- BLOCK CONDITIONS (Channels only) ---

            // Screen 3: Open channel preview
            if (hasFollowers && noChatInput && !isFindList) {
                if (joined.contains("follow channel") || joined.contains("follow") || joined.contains("unfollow")) {
                    Log.d("ADV_BLOCK", "BLOCK: Channel preview")
                    return true
                }
            }

            // Screen 1: User taps followed channel
            if (isClick && joined.contains("channels") && (joined.contains("followers") || joined.contains("12:09") || joined.contains("the class"))) {
                if (hasFollowers) {
                    Log.d("ADV_BLOCK", "BLOCK: Followed channel row")
                    return true
                }
            }

            // Screen 2: Taps on suggested channels
            if (isClick && isFindList && hasFollowers) {
                val clicked = (event?.text?.joinToString(" ") ?: "").lowercase()
                if (clicked.isNotEmpty() && clicked.length in 2..40 && !clicked.contains("find channels") && !clicked.contains("explore") && !clicked.contains("create")) {
                    Log.d("ADV_BLOCK", "BLOCK: Suggested channel row")
                    return true
                }
                // Empty click fallback for row taps
                if (clicked.isEmpty() || clicked.contains("follow")) {
                    Log.d("ADV_BLOCK", "BLOCK: Suggested channel row fallback")
                    return true
                }
            }

            // Block Explore more / Create channel
            if (isClick) {
                val clicked = (event?.text?.joinToString(" ") ?: "").lowercase()
                if (clicked.contains("explore more") || clicked.contains("create channel")) return true
            }
            if (isWindow && (joined.contains("explore") && hasFollowers || joined.contains("create a channel") || joined.contains("reach unlimited followers"))) {
                Log.d("ADV_BLOCK", "BLOCK: Explore/Create screen")
                return true
            }

            // Block channel info
            if (joined.contains("channel info") && !joined.contains("type a message")) return true

            return false
        } catch (e: Exception) { return false }
    }

    private fun isInsideYouTubeShorts(root: AccessibilityNodeInfo, event: AccessibilityEvent): Boolean {
        try {
            val ids = listOf(
                "com.google.android.youtube:id/reel_player_view_container",
                "com.google.android.youtube:id/reel_watch_fragment_container",
                "com.google.android.youtube:id/shorts_player_view",
                "com.google.android.youtube:id/shorts_main_container"
            )
            for (id in ids) { if (root.findAccessibilityNodeInfosByViewId(id)?.isNotEmpty() == true) return true }
            
            val texts = getAllTextFromNode(root)
            val joined = texts.joinToString(" ").lowercase()
            
            // Shorts player often has "Share", "Remix", and "Subscribe" buttons
            if (joined.contains("remix") && joined.contains("subscribe") && joined.contains("share")) return true
            
            val cn = event.className?.toString() ?: ""
            if (cn.contains("Reel") || cn.lowercase().contains("shorts")) return true
            
            return false
        } catch (e: Exception) { return false }
    }

    private fun isInsideFacebookReels(root: AccessibilityNodeInfo): Boolean {
        try {
            val ids = listOf(
                "com.facebook.katana:id/reels_viewer_container",
                "com.facebook.katana:id/reels_video_view",
                "com.facebook.katana:id/reels_share_sheet_container"
            )
            for (id in ids) { if (root.findAccessibilityNodeInfosByViewId(id)?.isNotEmpty() == true) return true }
            
            val texts = getAllTextFromNode(root)
            val joined = texts.joinToString(" ").lowercase()
            if (joined.contains("reels") && (joined.contains("follow") || joined.contains("share")) && joined.contains("like")) {
                // Heuristic for reels viewer
                return true
            }

            return false
        } catch (e: Exception) { return false }
    }

    private fun getAllTextFromNode(node: AccessibilityNodeInfo?, depth: Int = 0): List<String> {
        if (node == null || depth > 20) return emptyList()
        val list = mutableListOf<String>()
        try {
            node.text?.let { if (it.isNotBlank()) list.add(it.toString()) }
            node.contentDescription?.let { if (it.isNotBlank()) list.add(it.toString()) }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                list.addAll(getAllTextFromNode(child, depth + 1))
            }
        } catch (_: Exception) {}
        return list
    }

    private fun showOverlay(keyword: String, blockedFeature: String? = null, remaining: Long = 0L) {
        if (overlayView != null) removeOverlay()
        showOverlayInternal(keyword, blockedFeature, remaining)
    }

    private fun showOverlayInternal(keyword: String, blockedFeature: String? = null, remaining: Long = 0L) {
        if (overlayView != null) return
        handler.post {
            try {
                val view = LayoutInflater.from(this).inflate(R.layout.overlay_blocked, null)
                val btnHome = view.findViewById<android.widget.Button>(R.id.btnGoHome)
                val tvFriction = view.findViewById<android.widget.TextView>(R.id.tvFrictionTimer)
                val tvTimer = view.findViewById<android.widget.TextView>(R.id.tvUnblockTimer)

                // Setup text based on feature
                if (blockedFeature != null) {
                    val subtitle = view.findViewById<android.widget.TextView>(R.id.tvBlockedAppName)
                    when (blockedFeature) {
                        "whatsapp_channels" -> {
                            subtitle?.text = "You chose to block Channels.\nStatus is still allowed."
                        }
                        "youtube_shorts" -> {
                            subtitle?.text = if (remaining > 0) "You chose to block YouTube Shorts for ${remaining/60000} min." else "You chose to block YouTube Shorts."
                        }
                        "facebook_reels" -> {
                            subtitle?.text = if (remaining > 0) "You chose to block Facebook Reels for ${remaining/60000} min." else "You chose to block Facebook Reels."
                        }
                    }
                    if (remaining > 0 && tvTimer != null) {
                        tvTimer.visibility = android.view.View.VISIBLE
                        blockTimer?.cancel()
                        blockTimer = object : android.os.CountDownTimer(remaining, 1000) {
                            override fun onTick(m: Long) {
                                val mm = m / 60000
                                val ss = (m % 60000)/1000
                                tvTimer.text = String.format("Unblocks in %02d:%02d", mm, ss)
                            }
                            override fun onFinish() { tvTimer.text = "Unblocked - you can go back" }
                        }.start()
                    }
                } else if (keyword.isNotEmpty()) {
                    view.findViewById<android.widget.TextView>(R.id.tvBlockedAppName)?.text = "Keyword '$keyword' is blocked"
                }

                // --- PREMIUM FRICTION: 5 sec wait before button enabled ---
                val breathing = view.findViewById<android.view.View>(R.id.breathingCircle)
                val anim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.breathing_scale)
                breathing?.startAnimation(anim)

                var frictionSeconds = 5
                btnHome?.isEnabled = false
                btnHome?.alpha = 0.5f
                btnHome?.text = "Wait ${frictionSeconds}s..."
                tvFriction?.visibility = android.view.View.VISIBLE
                tvFriction?.text = "Breathe in..."

                val frictionTimer = object : android.os.CountDownTimer(5000, 1000) {
                    override fun onTick(millis: Long) {
                        frictionSeconds = (millis / 1000).toInt() + 1
                        btnHome?.text = "Wait ${frictionSeconds}s..."
                        tvFriction?.text = if (frictionSeconds % 2 == 0) "Breathe in..." else "Breathe out..."
                    }
                    override fun onFinish() {
                        breathing?.clearAnimation()
                        btnHome?.isEnabled = true
                        btnHome?.alpha = 1f
                        btnHome?.text = when(blockedFeature) {
                            "whatsapp_channels" -> "Return to Chats"
                            "youtube_shorts" -> "Return to YouTube"
                            "facebook_reels" -> "Return to Facebook"
                            else -> "Go Back"
                        }
                        btnHome?.setTextColor(android.graphics.Color.WHITE)
                        tvFriction?.text = "You got this 💜"
                        // Fade out friction text after 1 sec
                        handler.postDelayed({ tvFriction?.visibility = android.view.View.GONE }, 1200)
                    }
                }
                frictionTimer.start()

                // Keep reference to cancel if overlay removed early
                view.tag = frictionTimer

                view.findViewById<android.view.View>(R.id.btnGoHome)?.setOnClickListener {
                    if (btnHome?.isEnabled == false) return@setOnClickListener // Ignore taps during friction
                    removeOverlay()
                    lastBlockTime = System.currentTimeMillis() + 3000
                    try {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 250)
                    } catch (e: Exception) {
                        try { performGlobalAction(GLOBAL_ACTION_BACK) } catch(_: Exception) {}
                    }
                }

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

            } catch (e: Exception) { Log.e("FocusBlock", "overlay error $e") }
        }
    }

    private fun removeOverlay() {
        handler.post {
            try {
                blockTimer?.cancel()
                blockTimer = null
                overlayView?.let { v ->
                    (v.tag as? android.os.CountDownTimer)?.cancel()
                    val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    wm.removeView(v)
                }
                overlayView = null
            } catch (_: Exception) {}
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try { packageManager.getPackageInfo(packageName, 0); true } catch (e: Exception) { false }
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(stopReceiver) } catch(_: Exception) {}
        try { unregisterReceiver(startReceiver) } catch(_: Exception) {}
        removeOverlay()
    }
}
