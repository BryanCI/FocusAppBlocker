package com.example.appblocker

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.TextView
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.example.appblocker.settingsDataStore

class AppBlockerAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ---- overlay state (all touched on the main thread only) ----
    private var overlayView: View? = null
    private var overlayShowing = false          // single source of truth: is an overlay up?
    private var overlayForPkg: String? = null   // package that triggered the overlay
    private var currentFeature: String? = null
    private var isFrictionActive = false
    private var frictionTimer: CountDownTimer? = null
    private var blockTimer: CountDownTimer? = null

    // ---- throttling / cooldown ----
    private var lastBlockTime = 0L   // events are ignored until this time (cooldown after dismiss)
    private var lastProcessed = 0L

    @Volatile
    private var cachedKeywords: Set<String> = emptySet()

    private val accPrefs by lazy {
        FocusSessionManager.getDeviceProtectedPrefs(this, "accessibility_block_prefs")
    }

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> removeOverlay("screen_off")
                "com.example.appblocker.STOP_BLOCKING" -> {
                    // Feature overlays (channels / shorts / reels) don't depend on a focus
                    // session, so a session-ended broadcast must not tear them down.
                    if (currentFeature == null) removeOverlay("stop_broadcast")
                    else Log.d("FocusBlock", "Ignoring STOP_BLOCKING while feature overlay is up")
                }
            }
        }
    }

    @Suppress("UnspecifiedRegisterReceiverFlag")
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("ADV_BLOCK", "Service Connected")

        val filter = IntentFilter().apply {
            addAction("com.example.appblocker.STOP_BLOCKING")
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(controlReceiver, filter)
        }

        // Keep keywords cached in memory instead of blocking the main thread on every event
        serviceScope.launch {
            try {
                applicationContext.settingsDataStore.data.collect { prefs ->
                    cachedKeywords = (prefs[stringSetPreferencesKey("blocked_keywords")] ?: emptySet())
                        .map { it.lowercase().trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("web:") }
                        .toSet()
                }
            } catch (e: Exception) {
                Log.e("ADV_BLOCK", "keyword collector failed: $e")
            }
        }
    }

    // ------------------------------------------------------------------
    // Event handling
    // ------------------------------------------------------------------

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        // Ignore events produced by our own overlay (timer ticks, animations, etc.)
        if (pkg == packageName) return

        // While an overlay is up: never re-trigger. If the user left the app that
        // caused it (home, dialer, another app...), remove the overlay immediately.
        if (overlayShowing) {
            if (pkg != overlayForPkg) {
                if (isRealAppSwitch(pkg, event)) {
                    removeOverlay("app_switch pkg=$pkg type=${event.eventType}")
                } else {
                    Log.d("FocusBlock", "Ignored transient event pkg=$pkg type=${event.eventType}")
                }
            }
            return
        }

        val now = System.currentTimeMillis()
        if (now < lastBlockTime) return   // cooldown after the user dismissed the overlay

        // Content-changed events fire constantly; throttle the expensive tree scans.
        // Window-state and click events are always processed because detection relies on them.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (now - lastProcessed < 300) return
            lastProcessed = now
        }

        if (isSystemCritical(pkg)) return

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return   // stale event from another window

        try {
            val blockWA = accPrefs.getBoolean(CustomizeBlockActivity.KEY_WHATSAPP_CHANNELS, false)
            val blockYT = accPrefs.getBoolean(CustomizeBlockActivity.KEY_YOUTUBE_SHORTS, false)
            val blockFB = accPrefs.getBoolean(CustomizeBlockActivity.KEY_FACEBOOK_REELS, false)

            if (blockYT && pkg == "com.google.android.youtube") {
                if (isInsideYouTubeShorts(root, event)) {
                    showOverlay(pkg, feature = "youtube_shorts")
                    return
                }
            }
            // NOTE: Instagram reels currently follow the Facebook reels setting.
            // If you add a dedicated Instagram key in CustomizeBlockActivity, use it here.
            if (blockFB && pkg == "com.instagram.android") {
                if (isInsideInstagramReels(root, event)) {
                    showOverlay(pkg, feature = "instagram_reels")
                    return
                }
            }
            if (blockFB && (pkg == "com.facebook.katana" || pkg == "com.facebook.lite")) {
                if (isInsideFacebookReels(root, event)) {
                    showOverlay(pkg, feature = "facebook_reels")
                    return
                }
            }
            if (blockWA && isWhatsApp(pkg)) {
                if (isInsideWhatsAppChannel(root, event)) {
                    showOverlay(pkg, feature = "whatsapp_channels")
                    return
                }
            }
        } catch (e: Exception) {
            Log.e("ADV_BLOCK", "Error $e")
        }

        // ---- Keyword blocking (only while a focus session is active) ----
        if (!FocusSessionManager.isSessionActive(this)) return
        if (!pkg.contains("chrome", true) && !pkg.contains("google", true)) return
        val text = event.text.joinToString(" ") + " " + (event.contentDescription?.toString() ?: "")
        if (text.isBlank()) return
        val keywords = cachedKeywords
        if (keywords.isEmpty()) return
        val lowerText = text.lowercase()
        val matched = keywords.firstOrNull { lowerText.contains(it) }
        if (matched != null) {
            Log.d("ADV_BLOCK", "BLOCKING keyword=$matched")
            showOverlay(pkg, keyword = matched)
        }
    }

    private fun isWhatsApp(pkg: String) = pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b"

    /**
     * True only when the user really moved to another app window (home, dialer, another app).
     * Status bar, keyboard and other transient system windows must not dismiss the overlay.
     */
    private fun isRealAppSwitch(pkg: String, event: AccessibilityEvent): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return false
        val p = pkg.lowercase()
        val transient = p == "com.android.systemui" || p == "android" ||
                p.contains("inputmethod") || p.contains("keyboard") ||
                p.contains("honeyboard") || p.contains("swiftkey")
        return !transient
    }

    private fun isSystemCritical(pkg: String): Boolean =
        pkg == "com.android.systemui" ||
                pkg == "com.android.server.telecom" ||
                pkg == "com.android.incallui" ||
                pkg == "com.google.android.dialer" ||
                pkg == "com.android.dialer" ||
                pkg == "com.google.android.apps.nbu.paisa.user" ||
                pkg.contains("launcher", true) ||
                pkg.contains("camera", true)

    // ------------------------------------------------------------------
    // Detection helpers
    // ------------------------------------------------------------------

    private fun isInsideWhatsAppChannel(root: AccessibilityNodeInfo, event: AccessibilityEvent? = null): Boolean {
        try {
            val texts = getAllTextFromNode(root)
            val joined = texts.joinToString(" ").lowercase()
            val isClick = event?.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
            val isWindow = event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            if (joined.contains("type a message") && !joined.contains("channel info")) return false
            if (!joined.contains("followers") && (joined.contains("my status") || joined.contains("view status") || joined.contains("tap to view status") || joined.contains("status privacy"))) return false
            if (!isClick && !isWindow && joined.contains("status") && joined.contains("find channels to follow") && !joined.contains("unfollow")) return false
            val hasFollowers = joined.contains("followers")
            val noChatInput = !joined.contains("type a message")
            val isFindList = joined.contains("find channels to follow")
            if (hasFollowers && noChatInput && !isFindList) {
                if (joined.contains("follow channel") || joined.contains("follow") || joined.contains("unfollow")) return true
            }
            if (isClick && joined.contains("channels") && hasFollowers) return true
            if (isClick && isFindList && hasFollowers) {
                val clicked = (event?.text?.joinToString(" ") ?: "").lowercase()
                if (clicked.isNotEmpty() && clicked.length in 2..40 && !clicked.contains("find channels") && !clicked.contains("explore") && !clicked.contains("create")) return true
                if (clicked.isEmpty() || clicked.contains("follow")) return true
            }
            if (isClick) {
                val clicked = (event?.text?.joinToString(" ") ?: "").lowercase()
                if (clicked.contains("explore more") || clicked.contains("create channel")) return true
            }
            if (isWindow && (joined.contains("explore") && hasFollowers || joined.contains("create a channel") || joined.contains("reach unlimited followers"))) return true
            if (joined.contains("channel info") && !joined.contains("type a message")) return true
            return false
        } catch (e: Exception) { return false }
    }

    private fun isInsideYouTubeShorts(root: AccessibilityNodeInfo, event: AccessibilityEvent?): Boolean {
        try {
            val texts = getAllTextFromNode(root)
            val joined = texts.joinToString(" ").lowercase()
            val isWindow = event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            val isShorts = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_player_view").isNotEmpty() ||
                    (joined.contains("shorts") && (joined.contains("subscribe") || joined.contains("dislike")) && !joined.contains("type a message")) ||
                    joined.contains("shorts player")
            if (isShorts && (isWindow || joined.contains("shorts"))) return true
            return false
        } catch (e: Exception) { return false }
    }

    private fun isInsideFacebookReels(root: AccessibilityNodeInfo, event: AccessibilityEvent?): Boolean {
        try {
            val texts = getAllTextFromNode(root)
            val joined = texts.joinToString(" ").lowercase()
            val hasReels = joined.contains("reels") && joined.contains("like") && !joined.contains("type a message")
            val hasReelsId = root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/clips_viewer").isNotEmpty()
            if (hasReelsId || (hasReels && event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)) return true
            return false
        } catch (e: Exception) { return false }
    }

    private fun isInsideInstagramReels(root: AccessibilityNodeInfo, event: AccessibilityEvent?): Boolean {
        try {
            val texts = getAllTextFromNode(root)
            val joined = texts.joinToString(" ").lowercase()
            val hasReelsId = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_viewer").isNotEmpty() ||
                    root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/reels_viewer").isNotEmpty()
            if (hasReelsId || (joined.contains("reels") && joined.contains("audio") && event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)) return true
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

    // ------------------------------------------------------------------
    // Overlay
    // ------------------------------------------------------------------

    /**
     * Shows the blocking overlay. Safe to call repeatedly: if an overlay is already up
     * this is a no-op, so the friction timer and animation are never reset.
     * Accessibility callbacks run on the main thread, so no handler hop is needed.
     */
    private fun showOverlay(pkg: String, keyword: String = "", feature: String? = null) {
        if (overlayShowing) return
        overlayShowing = true
        overlayForPkg = pkg
        currentFeature = feature
        isFrictionActive = true

        try {
            val view = LayoutInflater.from(this).inflate(R.layout.overlay_blocked, null)
            val btnHome = view.findViewById<Button>(R.id.btnGoHome)
            val tvFriction = view.findViewById<TextView>(R.id.tvFrictionTimer)
            val tvTitle = view.findViewById<TextView>(R.id.tvBlockedAppName)
            val breathing = view.findViewById<View>(R.id.breathingCircle)

            tvTitle?.text = when (feature) {
                "whatsapp_channels" -> "You chose to block Channels.\nWe're taking you back to Chats."
                "youtube_shorts" -> "You chose to block YouTube Shorts.\nStay focused!"
                "facebook_reels", "instagram_reels" -> "You chose to block Reels.\nStay focused!"
                else -> if (keyword.isNotEmpty()) "Keyword '$keyword' blocked" else "Stay focused!"
            }

            val readyText = when (feature) {
                "whatsapp_channels" -> "Return to Chats"
                null -> "Go Home"
                else -> "Go Back"
            }

            try {
                val anim = AnimationUtils.loadAnimation(this, R.anim.breathing_scale)
                breathing?.startAnimation(anim)
            } catch (_: Exception) {}

            // IMPORTANT: set the listener first. setOnClickListener() forces clickable=true,
            // so the "disabled" state must be applied afterwards.
            btnHome?.setOnClickListener {
                if (isFrictionActive) return@setOnClickListener
                dismissAndLeave(feature)
            }
            btnHome?.apply {
                isClickable = false
                alpha = 0.5f
                text = "Wait 5s..."
                setTextColor(Color.parseColor("#6A6A6E"))
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2A2A30"))
            }

            frictionTimer?.cancel()
            frictionTimer = object : CountDownTimer(5000, 1000) {
                override fun onTick(millis: Long) {
                    val seconds = (millis / 1000).toInt() + 1
                    btnHome?.text = "Wait ${seconds}s..."
                    tvFriction?.text = if (seconds % 2 == 0) "Breathe in..." else "Breathe out..."
                }

                override fun onFinish() {
                    isFrictionActive = false
                    Log.d("FocusBlock", "Friction finished, overlayShowing=$overlayShowing")
                    btnHome?.apply {
                        text = readyText
                        isClickable = true
                        alpha = 1f
                        setTextColor(Color.WHITE)
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#B24CFF"))
                    }
                    tvFriction?.text = "Ready ✓"
                    view.postDelayed({ tvFriction?.visibility = View.GONE }, 800)
                    breathing?.clearAnimation()
                }
            }.start()

            // No FLAG_LAYOUT_IN_SCREEN / NO_LIMITS: the status bar and navigation bar stay
            // reachable, so home, recents and calls always work.
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(view, params)
            overlayView = view
            Log.d("FocusBlock", "Overlay added for feature=$feature pkg=$pkg")

        } catch (e: Exception) {
            Log.e("FocusBlock", "overlay error $e")
            removeOverlay("show_failed")
        }
    }

    private fun dismissAndLeave(feature: String?) {
        removeOverlay("button_pressed")
        // Cooldown so we don't instantly re-block while navigating away
        lastBlockTime = System.currentTimeMillis() + 2500

        handler.postDelayed({
            when (feature) {
                "whatsapp_channels" -> {
                    if (!clickChatsTab()) performGlobalAction(GLOBAL_ACTION_BACK)
                }
                null -> performGlobalAction(GLOBAL_ACTION_HOME)       // keyword block
                else -> performGlobalAction(GLOBAL_ACTION_BACK)       // shorts / reels
            }
        }, 150)
    }

    /** Taps WhatsApp's bottom "Chats" tab. Returns true if a click was performed. */
    private fun clickChatsTab(): Boolean {
        return try {
            val root = rootInActiveWindow ?: return false
            val nodes = root.findAccessibilityNodeInfosByText("Chats") ?: return false
            for (n in nodes) {
                var target: AccessibilityNodeInfo? = n
                var hops = 0
                while (target != null && !target.isClickable && hops < 3) {
                    target = target.parent
                    hops++
                }
                if (target != null && target.isClickable &&
                    target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                ) return true
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun removeOverlay(reason: String = "unspecified") {
        Log.d("FocusBlock", "OVERLAY_REMOVED reason=$reason showing=$overlayShowing")
        overlayShowing = false
        isFrictionActive = false
        overlayForPkg = null
        currentFeature = null

        frictionTimer?.cancel()
        frictionTimer = null
        blockTimer?.cancel()
        blockTimer = null

        val v = overlayView
        overlayView = null
        if (v != null) {
            try { v.findViewById<View>(R.id.breathingCircle)?.clearAnimation() } catch (_: Exception) {}
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            try {
                wm.removeViewImmediate(v)
            } catch (_: Exception) {
                try { wm.removeView(v) } catch (_: Exception) {}
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(controlReceiver) } catch (_: Exception) {}
        removeOverlay("service_destroyed")
        serviceScope.cancel()
    }
}