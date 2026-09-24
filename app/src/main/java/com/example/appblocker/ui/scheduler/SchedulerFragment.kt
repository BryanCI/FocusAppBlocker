package com.example.appblocker.ui.scheduler

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.appblocker.AppBlockerService
import com.example.appblocker.R
import com.example.appblocker.data.AppDatabase
import com.example.appblocker.data.InternetSchedule
import com.example.appblocker.databinding.ItemScheduleBinding
import com.example.appblocker.databinding.ItemTemplateBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.*

@AndroidEntryPoint
class SchedulerFragment : Fragment() {

    private lateinit var mySchedulesContainer: LinearLayout
    private lateinit var templatesContainer: LinearLayout
    private var hasAnimated = false

    private lateinit var greenDot: View
    private lateinit var greenDotOuter: View
    private lateinit var scoreGlowOuter: View
    private lateinit var progressGlow: View
    private lateinit var shimmerView: View
    private lateinit var moonIcon: View
    private lateinit var moonOuterGlow: View
    private lateinit var moonInnerGlow: View
    private lateinit var createCircle: View
    private lateinit var createGlow: View
    private lateinit var progressContainer: FrameLayout

    data class Template(val title: String, val time: String, val icon: String, val subtitle: String, val accent: String)

    private var activeTimer: CountDownTimer? = null
    private var schedulesJob: kotlinx.coroutines.Job? = null

    private lateinit var liveBadge: View
    private lateinit var liveDot: View
    private lateinit var liveDotOuter: View
    private lateinit var activeInfoContainer: View
    private lateinit var activeScheduleName: TextView
    private lateinit var activeScheduleCountdown: TextView

    private lateinit var progressFill: View
    private lateinit var scorePill: View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_scheduler, container, false)
        
        mySchedulesContainer = root.findViewById(R.id.mySchedulesContainer)
        templatesContainer = root.findViewById(R.id.templatesContainer)
        
        greenDot = root.findViewById(R.id.greenDot)
        greenDotOuter = root.findViewById(R.id.greenDotOuter)
        scoreGlowOuter = root.findViewById(R.id.scoreGlowOuter)
        progressGlow = root.findViewById(R.id.progressGlow)
        shimmerView = root.findViewById(R.id.shimmerView)
        moonIcon = root.findViewById(R.id.moonIcon)
        moonOuterGlow = root.findViewById(R.id.moonOuterGlow)
        moonInnerGlow = root.findViewById(R.id.moonInnerGlow)
        createCircle = root.findViewById(R.id.createCircle)
        createGlow = root.findViewById(R.id.createGlow)
        progressContainer = root.findViewById(R.id.progressContainer)
        progressFill = root.findViewById(R.id.progressFill)
        scorePill = root.findViewById(R.id.scorePill)
        
        liveBadge = root.findViewById(R.id.liveBadge)
        liveDot = root.findViewById(R.id.liveDot)
        liveDotOuter = root.findViewById(R.id.liveDotOuter)
        activeInfoContainer = root.findViewById(R.id.activeInfoContainer)
        activeScheduleName = root.findViewById(R.id.activeScheduleName)
        activeScheduleCountdown = root.findViewById(R.id.activeScheduleCountdown)

        val createCard = root.findViewById<View>(R.id.createScheduleCard)
        val createButton = root.findViewById<View>(R.id.createScheduleButton)

        val createAction = { v: View ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(110L).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(240L).setInterpolator(OvershootInterpolator(2.3f)).start()
                showCreateBottomSheet(null)
            }.start()
        }
        createCard.setOnClickListener(createAction)
        createButton.setOnClickListener(createAction)

        observeSchedules()
        populateTemplates()
        startConstantFocusShieldAnimations()
        
        return root
    }

    override fun onResume() {
        super.onResume()
        refreshUI()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!hasAnimated) {
            animateOnSchedulerTap()
            hasAnimated = true
        }
    }

    private fun observeSchedules() {
        schedulesJob?.cancel()
        schedulesJob = lifecycleScope.launch {
            val database = AppDatabase.getDatabase(requireContext())
            database.internetScheduleDao().getAllSchedules().collect { schedules ->
                populateMySchedules(schedules)
            }
        }
    }

    private fun refreshUI() {
        // Triggered on resume to ensure time-based states are fresh
        val database = AppDatabase.getDatabase(requireContext())
        CoroutineScope(Dispatchers.IO).launch {
            val schedules = database.internetScheduleDao().getAllSchedulesList()
            withContext(Dispatchers.Main) {
                populateMySchedules(schedules)
            }
        }
    }

    private fun populateMySchedules(schedules: List<InternetSchedule>) {
        mySchedulesContainer.removeAllViews()
        
        val map = LinkedHashMap<String, InternetSchedule>()
        for (s in schedules) {
            val key = s.name.trim().lowercase()
            if (!map.containsKey(key)) map[key] = s
        }
        val deduped = map.values.toList()

        try {
            val isEmpty = deduped.isEmpty()
            val emptyLayout = view?.findViewById<View>(R.id.layoutEmptyMySchedules)
            if (isEmpty) {
                mySchedulesContainer.visibility = View.GONE
                emptyLayout?.visibility = View.VISIBLE
            } else {
                mySchedulesContainer.visibility = View.VISIBLE
                emptyLayout?.visibility = View.GONE
            }
        } catch (e: Exception) { e.printStackTrace() }

        val prefs = requireContext().getSharedPreferences("schedules_prefs", Context.MODE_PRIVATE)
        val activeIdStr = prefs.getString("active_schedule_id", null)

        var hasActive = false
        deduped.forEach { schedule ->
            val b = ItemScheduleBinding.inflate(layoutInflater, mySchedulesContainer, false)
            b.root.tag = schedule.id
            b.title.text = schedule.name
            
            val isActive = schedule.id.toString() == activeIdStr
            
            b.timeChip.text = String.format(Locale.getDefault(), "%02d:%02d - %02d:%02d", 
                schedule.startHour, schedule.startMinute, schedule.endHour, schedule.endMinute)
            
            b.iconEmoji.text = getCorrectIcon(schedule.name)
            
            val accentColorString = getAccentColor(schedule.name)
            val accentColor = Color.parseColor(accentColorString)
            
            // FIX TINT - 14% alpha = 36 out of 255 (glassy red)
            try {
                val bg = ContextCompat.getDrawable(requireContext(), R.drawable.bg_icon_oval)
                b.iconContainer.background = bg
                val tintColor = Color.argb(36, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
                b.iconContainer.backgroundTintList = ColorStateList.valueOf(tintColor)
                b.iconContainer.backgroundTintMode = PorterDuff.Mode.SRC_IN
            } catch (e: Exception) {}

            if (isActive) {
                hasActive = true
                b.scheduleCard.minimumHeight = (136 * resources.displayMetrics.density).toInt()
                b.scheduleCard.strokeWidth = (1.8f * resources.displayMetrics.density).toInt()
                b.scheduleCard.strokeColor = Color.parseColor("#B440FF")
                (b.scheduleCard as? com.google.android.material.card.MaterialCardView)?.setCardBackgroundColor(Color.parseColor("#2E1A4A"))
                
                b.activeOuterGlow.alpha = 0.32f
                b.activeBorderGlow.alpha = 1.0f
                b.leftGlow.alpha = 0.38f
                
                b.leftBar.layoutParams.width = (6 * resources.displayMetrics.density).toInt()
                b.leftBar.layoutParams.height = (88 * resources.displayMetrics.density).toInt()
                b.leftBar.setBackgroundColor(Color.parseColor("#B440FF"))
                
                b.iconContainer.layoutParams.width = (70 * resources.displayMetrics.density).toInt()
                b.iconContainer.layoutParams.height = (70 * resources.displayMetrics.density).toInt()
                b.iconGlow.alpha = 0.32f
                b.iconEmoji.textSize = 30f
                
                b.title.textSize = 18f
                b.title.setTypeface(null, android.graphics.Typeface.BOLD)
                
                b.subtitle.text = "🔒 Blocking Active"
                b.subtitle.setTextColor(Color.parseColor("#4ADE80"))
                b.subtitle.setTypeface(null, android.graphics.Typeface.BOLD)
                
                b.activeBadgeContainer.layoutParams.height = (36 * resources.displayMetrics.density).toInt()
                b.activeBadgeContainer.setBackgroundResource(R.drawable.bg_badge_active_premium)
                b.activeBadgeContainer.elevation = 8f * resources.displayMetrics.density
                
                b.scheduledLayout.visibility = View.GONE
                b.activeLayout.visibility = View.VISIBLE
                
                b.activeProgress.visibility = View.VISIBLE
                b.activeProgressFill.visibility = View.VISIBLE
                b.activeProgressGlow.visibility = View.VISIBLE
                
                startActiveAnimations(b)
                startCountdownTimer(b, schedule)
            } else {
                b.scheduleCard.minimumHeight = (116 * resources.displayMetrics.density).toInt()
                b.scheduleCard.strokeWidth = (1f * resources.displayMetrics.density).toInt()
                b.scheduleCard.strokeColor = Color.parseColor("#3A3A50")
                (b.scheduleCard as? com.google.android.material.card.MaterialCardView)?.setCardBackgroundColor(Color.parseColor("#2D2D42"))
                
                b.activeOuterGlow.alpha = 0f
                b.activeBorderGlow.alpha = 0f
                b.leftGlow.alpha = 0f
                
                b.leftBar.layoutParams.width = (4 * resources.displayMetrics.density).toInt()
                b.leftBar.layoutParams.height = (72 * resources.displayMetrics.density).toInt()
                b.leftBar.setBackgroundColor(accentColor)
                
                b.iconContainer.layoutParams.width = (62 * resources.displayMetrics.density).toInt()
                b.iconContainer.layoutParams.height = (62 * resources.displayMetrics.density).toInt()
                b.iconGlow.alpha = 0f
                b.iconEmoji.textSize = 28f
                
                b.title.textSize = 17f
                b.title.setTypeface(null, android.graphics.Typeface.NORMAL)
                
                b.subtitle.text = if (schedule.isStrict) "Strict Mode Enabled" else "Standard Protection"
                b.subtitle.setTextColor(Color.parseColor("#9A9AAF"))
                b.subtitle.setTypeface(null, android.graphics.Typeface.NORMAL)
                
                b.activeBadgeContainer.layoutParams.height = (28 * resources.displayMetrics.density).toInt()
                b.activeBadgeContainer.setBackgroundResource(R.drawable.bg_badge_scheduled_grey)
                b.activeBadgeContainer.elevation = 0f
                
                b.scheduledLayout.visibility = View.VISIBLE
                b.activeLayout.visibility = View.GONE
                b.activeProgress.visibility = View.GONE
                b.activeProgressFill.visibility = View.GONE
                b.activeProgressGlow.visibility = View.GONE
                
                scheduleRefreshIfUpcoming(schedule)
            }

            b.editIcon.isClickable = true
            b.editIcon.isFocusable = true
            b.editIcon.elevation = 12f
            b.editIcon.setOnClickListener { v ->
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                v.animate().scaleX(0.90f).scaleY(0.90f).setDuration(100L).withEndAction {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(200L).setInterpolator(OvershootInterpolator(2.5f)).start()
                    showCreateBottomSheet(schedule)
                }.start()
            }

            b.closeIcon.isClickable = true
            b.closeIcon.isFocusable = true
            b.closeIcon.elevation = 12f
            b.closeIcon.setOnClickListener(null)
            b.closeIcon.setOnClickListener { v ->
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(100L).withEndAction {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(200L).setInterpolator(OvershootInterpolator(2.5f)).start()
                }.start()

                try {
                    val prefs2 = requireContext().getSharedPreferences("schedules_prefs", Context.MODE_PRIVATE)
                    val activeId2 = prefs2.getString("active_schedule_id", null)
                    
                    if (activeId2 == schedule.id.toString()) {
                        activeTimer?.cancel()
                        activeTimer = null
                        prefs2.edit().remove("active_schedule_id").remove("active_start").apply()
                        try {
                            val nm = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            nm.cancel(1001)
                        } catch (e: Exception) {}
                        try {
                            requireContext().stopService(Intent(requireContext(), AppBlockerService::class.java))
                        } catch (e: Exception) {}
                        
                        liveBadge.visibility = View.GONE
                        activeInfoContainer.visibility = View.GONE
                    }

                    b.root.animate().scaleX(0.92f).scaleY(0.92f).alpha(0f).setDuration(280L).setInterpolator(DecelerateInterpolator()).withEndAction {
                        CoroutineScope(Dispatchers.IO).launch {
                            val database = AppDatabase.getDatabase(requireContext())
                            database.internetScheduleDao().deleteSchedule(schedule.id)
                            withContext(Dispatchers.Main) {
                                refreshUI()
                            }
                        }
                    }.start()
                    
                    Toast.makeText(requireContext(), "${schedule.name} deleted", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("DELETE", "delete failed ${e.message}", e)
                }
            }

            b.root.isClickable = false
            b.root.isFocusable = false
            setupScheduleClick(b.iconContainer, schedule)
            setupScheduleClick(b.title, schedule)
            setupScheduleClick(b.subtitle, schedule)

            mySchedulesContainer.addView(b.root)
        }
        
        if (!hasActive) {
            liveBadge.visibility = View.GONE
            activeInfoContainer.visibility = View.GONE
        }
    }

    private fun setupScheduleClick(view: View, schedule: InternetSchedule) {
        view.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(110L).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(240L).setInterpolator(OvershootInterpolator(2.3f)).start()
                activateSchedule(schedule)
            }.start()
        }
    }

    private fun scheduleRefreshIfUpcoming(schedule: InternetSchedule) {
        val now = Calendar.getInstance()
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, schedule.startHour)
            set(Calendar.MINUTE, schedule.startMinute)
            set(Calendar.SECOND, 0)
        }
        if (start.after(now)) {
            val diff = start.timeInMillis - now.timeInMillis
            if (diff < 3600000) { // Only if starts in next hour
                mySchedulesContainer.postDelayed({ refreshUI() }, diff + 500)
            }
        }
    }

    private fun startActiveAnimations(b: ItemScheduleBinding) {
        // Subtitle breathing
        ObjectAnimator.ofFloat(b.subtitle, "alpha", 0.7f, 1.0f, 0.7f).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        // Left Glow Pulse
        ObjectAnimator.ofFloat(b.leftGlow, "alpha", 0.38f, 0.50f, 0.38f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            start()
        }
        
        // Active Glow Pulse
        ObjectAnimator.ofFloat(b.activeOuterGlow, "alpha", 0.32f, 0.42f, 0.32f).apply {
            duration = 1400
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        // Icon Glow Pulse
        val iconGlowScaleX = ObjectAnimator.ofFloat(b.iconGlow, "scaleX", 1.0f, 1.30f, 1.0f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
        }
        val iconGlowScaleY = ObjectAnimator.ofFloat(b.iconGlow, "scaleY", 1.0f, 1.30f, 1.0f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
        }
        val iconGlowAlpha = ObjectAnimator.ofFloat(b.iconGlow, "alpha", 0.22f, 0.42f, 0.22f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
        }
        AnimatorSet().apply {
            playTogether(iconGlowScaleX, iconGlowScaleY, iconGlowAlpha)
            start()
        }
        
        // Active Dot Pulse
        val dotOuterScaleX = ObjectAnimator.ofFloat(b.activeDotOuter, "scaleX", 1.0f, 1.8f, 1.0f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
        }
        val dotOuterScaleY = ObjectAnimator.ofFloat(b.activeDotOuter, "scaleY", 1.0f, 1.8f, 1.0f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
        }
        val dotOuterAlpha = ObjectAnimator.ofFloat(b.activeDotOuter, "alpha", 0.28f, 0f, 0.28f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
        }
        
        val dotInnerScaleX = ObjectAnimator.ofFloat(b.activeDot, "scaleX", 1.0f, 1.5f, 1.0f).apply {
            duration = 600
            repeatCount = ValueAnimator.INFINITE
        }
        val dotInnerScaleY = ObjectAnimator.ofFloat(b.activeDot, "scaleY", 1.0f, 1.5f, 1.0f).apply {
            duration = 600
            repeatCount = ValueAnimator.INFINITE
        }

        AnimatorSet().apply {
            playTogether(dotOuterScaleX, dotOuterScaleY, dotOuterAlpha, dotInnerScaleX, dotInnerScaleY)
            start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activeTimer?.cancel()
    }

    private fun showPremiumToast(scheduleName: String, endTime: String? = null) {
        val root = requireActivity().findViewById<ViewGroup>(android.R.id.content)
        val toastView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_premium_success, root, false)
        
        toastView.findViewById<TextView>(R.id.btnDone)?.visibility = View.GONE
        
        val container = toastView.findViewById<View>(R.id.btnDone).parent as ViewGroup
        val titleText = container.getChildAt(2) as TextView 
        val descText = container.getChildAt(3) as TextView
        
        titleText.text = "Schedule Active"
        descText.text = if (endTime != null) {
            "$scheduleName started • until $endTime"
        } else {
            "$scheduleName is now shielding your focus."
        }
        
        toastView.alpha = 0f
        toastView.translationY = 100f
        root.addView(toastView)
        
        toastView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setInterpolator(OvershootInterpolator())
            .withEndAction {
                spawnConfetti(root)
                toastView.postDelayed({
                    toastView.animate()
                        .alpha(0f)
                        .translationY(-100f)
                        .setDuration(400)
                        .withEndAction { root.removeView(toastView) }
                        .start()
                }, 3000)
            }
            .start()
    }

    private fun spawnConfetti(root: ViewGroup) {
        val colors = intArrayOf(Color.parseColor("#B440FF"), Color.parseColor("#0A84FF"), Color.parseColor("#4ADE80"), Color.parseColor("#FFD700"))
        val random = Random()
        
        for (i in 0 until 8) {
            val particle = View(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(24, 24)
                setBackgroundResource(R.drawable.bg_glow_white_oval)
                backgroundTintList = ColorStateList.valueOf(colors[random.nextInt(colors.size)])
                x = root.width / 2f
                y = root.height / 2f
            }
            root.addView(particle)
            
            val angle = random.nextDouble() * 2 * Math.PI
            val distance = 200f + random.nextFloat() * 300f
            val destX = particle.x + (Math.cos(angle) * distance).toFloat()
            val destY = particle.y + (Math.sin(angle) * distance).toFloat()
            
            particle.animate()
                .translationX(destX)
                .translationY(destY)
                .alpha(0f)
                .scaleX(0f)
                .scaleY(0f)
                .setDuration(1000 + random.nextInt(500).toLong())
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { root.removeView(particle) }
                .start()
        }
    }

    private fun startCountdownTimer(b: ItemScheduleBinding, schedule: InternetSchedule) {
        val now = Calendar.getInstance()
        val startMillis = parseTimeToTodayMillis(schedule.startHour, schedule.startMinute)
        val endMillis = parseTimeToTodayMillis(schedule.endHour, schedule.endMinute)
        
        val totalMillis: Long
        val diff: Long
        
        if (schedule.startHour * 60 + schedule.startMinute <= schedule.endHour * 60 + schedule.endMinute) {
            // Standard
            totalMillis = endMillis - startMillis
            diff = endMillis - now.timeInMillis
        } else {
            // Over-midnight
            totalMillis = (endMillis + 86400000L) - startMillis
            
            val currentTime = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            val scheduleEndTime = schedule.endHour * 60 + schedule.endMinute
            
            diff = if (currentTime <= scheduleEndTime) {
                endMillis - now.timeInMillis
            } else {
                (endMillis + 86400000L) - now.timeInMillis
            }
        }
        
        if (diff <= 0) return

        activeTimer?.cancel()
        activeTimer = object : CountDownTimer(diff, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val h = millisUntilFinished / 3600000
                val m = (millisUntilFinished % 3600000) / 60000
                val s = (millisUntilFinished % 60000) / 1000
                val timeText = String.format("%02d:%02d:%02d", h, m, s)
                b.activeCountdown.text = "ACTIVE $timeText"
                
                val elapsed = totalMillis - millisUntilFinished
                val pct = (elapsed.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
                
                b.activeProgress.post {
                    val trackWidth = b.activeProgress.width
                    if (trackWidth > 0) {
                        b.activeProgressFill.layoutParams.width = (trackWidth * (1f - pct)).toInt()
                        b.activeProgressFill.requestLayout()
                    }
                }

                if (isAdded) {
                    liveBadge.visibility = View.VISIBLE
                    activeInfoContainer.visibility = View.VISIBLE
                    activeScheduleName.text = "🔒 ${schedule.name}"
                    activeScheduleCountdown.text = "ACTIVE $timeText • until ${String.format("%02d:%02d", schedule.endHour, schedule.endMinute)}"
                    
                    showActiveNotification(schedule, millisUntilFinished)
                    updateFocusShieldLiveState()
                    
                    // Update Focus Shield progress elapsed
                    val focusTrackW = progressContainer.width
                    if (focusTrackW > 0) {
                        progressFill.layoutParams.width = (focusTrackW * pct).toInt()
                        progressFill.requestLayout()
                    }
                }
            }
            override fun onFinish() {
                val nm = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(1001)
                val prefs = requireContext().getSharedPreferences("schedules_prefs", Context.MODE_PRIVATE)
                prefs.edit().remove("active_schedule_id").apply()
                liveBadge.visibility = View.GONE
                activeInfoContainer.visibility = View.GONE
                refreshUI()
            }
        }.start()
    }

    private fun updateFocusShieldLiveState() {
        liveDot.animate().scaleX(1.6f).scaleY(1.6f).setDuration(700L).withEndAction {
            liveDot.animate().scaleX(1f).scaleY(1f).setDuration(700L).start()
        }.start()
        liveDotOuter.animate().scaleX(1.9f).scaleY(1.9f).alpha(0f).setDuration(1100L).withEndAction {
            liveDotOuter.scaleX = 1f
            liveDotOuter.scaleY = 1f
            liveDotOuter.alpha = 0.28f
        }.start()
        
        scoreGlowOuter.animate().alpha(0.40f).setDuration(1000L).withEndAction {
            scoreGlowOuter.animate().alpha(0.20f).setDuration(1000L).start()
        }.start()
        
        greenDot.animate().scaleX(1.5f).scaleY(1.5f).setDuration(700L).withEndAction {
            greenDot.animate().scaleX(1f).scaleY(1f).setDuration(700L).start()
        }.start()
        
        scorePill.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1F2E1A"))
    }

    private fun activateSchedule(schedule: InternetSchedule) {
        val prefs = requireContext().getSharedPreferences("schedules_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("active_schedule_id", schedule.id.toString()).apply()
        
        refreshUI()
        startActiveStateWithCountdown(schedule)
    }

    private fun startActiveStateWithCountdown(schedule: InternetSchedule) {
        mySchedulesContainer.post {
            val cardView = mySchedulesContainer.findViewWithTag<View>(schedule.id)
            if (cardView != null) {
                val b = ItemScheduleBinding.bind(cardView)
                
                // Pop + confetti
                cardView.animate().scaleX(1.04f).scaleY(1.04f).setDuration(250L).setInterpolator(OvershootInterpolator(1.5f)).withEndAction {
                    cardView.animate().scaleX(1f).scaleY(1f).setDuration(250L).setInterpolator(DecelerateInterpolator()).start()
                }.start()
                cardView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                
                val root = requireActivity().findViewById<ViewGroup>(android.R.id.content)
                val parent = mySchedulesContainer as ViewGroup
                val colors = listOf("#A855F7", "#06B6D4", "#FACC15", "#4ADE80")
                for (i in 0..7) {
                    val conf = View(requireContext())
                    conf.layoutParams = ViewGroup.LayoutParams((8 * resources.displayMetrics.density).toInt(), (8 * resources.displayMetrics.density).toInt())
                    val d = GradientDrawable()
                    d.shape = GradientDrawable.OVAL
                    d.setColor(Color.parseColor(colors[i % 4]))
                    conf.background = d
                    parent.addView(conf)
                    conf.x = cardView.x + cardView.width / 2f
                    conf.y = cardView.y
                    conf.animate()
                        .translationY(cardView.y - 120f * resources.displayMetrics.density - i * 15f)
                        .translationXBy((i - 4) * 20f * resources.displayMetrics.density)
                        .alpha(0f)
                        .scaleX(1.4f)
                        .scaleY(1.4f)
                        .setDuration(900L + i * 80L)
                        .setStartDelay(i * 40L)
                        .withEndAction { parent.removeView(conf) }
                        .start()
                }

                val endTimeStr = String.format("%02d:%02d", schedule.endHour, schedule.endMinute)
                showPremiumToast(schedule.name, endTimeStr)
                startCountdownTimer(b, schedule)

                // Update active info 2 lines
                liveBadge.visibility = View.VISIBLE
                activeInfoContainer.visibility = View.VISIBLE
                activeScheduleName.text = "🔒 ${schedule.name}"
                // activeScheduleCountdown text will be updated by startCountdownTimer immediately
            }
        }
    }

    private fun showActiveNotification(schedule: InternetSchedule, remainingMillis: Long) {
        val channelId = "focus_active"
        val nm = context?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(channelId) == null) {
            val ch = NotificationChannel(channelId, "Focus Active", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }

        val h = remainingMillis / 3600000
        val m = (remainingMillis % 3600000) / 60000
        val s = (remainingMillis % 60000) / 1000
        val timeText = String.format("%02d:%02d:%02d", h, m, s)

        val notification = NotificationCompat.Builder(requireContext(), channelId)
            .setSmallIcon(R.drawable.focussapp)
            .setContentTitle("${schedule.name} • ACTIVE $timeText")
            .setContentText("Blocking until ${String.format("%02d:%02d", schedule.endHour, schedule.endMinute)}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(Color.parseColor("#B440FF"))
            .setColorized(true)
            .build()
        
        nm.notify(1001, notification)
    }

    private fun getAccentColor(name: String): String {
        return when {
            name.contains("Sabbath", true) -> "#FF3B30"
            name.contains("Night", true) || name.contains("Sleep", true) -> "#0A84FF"
            name.contains("Study", true) || name.contains("Learn", true) -> "#0A84FF"
            name.contains("Deep Focus", true) -> "#A855F7"
            name.contains("Flow", true) -> "#10B981"
            name.contains("Wind Down", true) -> "#8B5CF6"
            name.contains("Morning", true) -> "#FF9500"
            name.contains("House", true) || name.contains("Chore", true) -> "#FFCC00"
            name.contains("Exercise", true) || name.contains("Workout", true) -> "#FF2D55"
            name.contains("Social", true) || name.contains("Detox", true) -> "#AF52DE"
            name.contains("Gaming", true) || name.contains("Game", true) -> "#5856D6"
            name.contains("Work", true) -> "#007AFF"
            else -> "#0A84FF"
        }
    }

    private fun parseTimeToTodayMillis(h: Int, m: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, h)
        calendar.set(Calendar.MINUTE, m)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun populateTemplates() {
        templatesContainer.removeAllViews()
        val templates = listOf(
            Template("Digital Sabbath", "00:00 - 23:59", "🚫", "Full disconnection for mental clarity", "#FF3B30"),
            Template("Night Shield", "22:00 - 07:00", "🌙", "Block all distractions for sleep", "#0A84FF"),
            Template("Rise & Grind", "06:00 - 09:00", "🌅", "Early morning productivity session", "#FF9500"),
            Template("Morning Clarity", "06:00 - 08:00", "🌅", "Start your day right without phones", "#FF9500"),
            Template("Study Time", "16:00 - 20:00", "📚", "Deep focus for exam prep & learning", "#0A84FF"),
            Template("Deep Focus", "16:00 - 20:00", "📚", "Extreme concentration for deep work", "#A855F7"),
            Template("Focus Hour", "14:00 - 15:00", "🎯", "Short deep work session", "#0A84FF"),
            Template("Flow State", "09:00 - 12:00", "⚡", "Uninterrupted creative flow session", "#10B981"),
            Template("Quick Refresh", "11:00 - 11:20", "⏱️", "Brief digital detox break", "#0A84FF"),
            Template("Wind Down", "21:00 - 23:00", "🕯️", "Gentle disconnection before bed", "#8B5CF6"),
            Template("Weekend Detox", "09:00 - 18:00", "🌴", "Unplug and recharge your mind", "#10B981"),
            Template("House Chores", "18:00 - 19:00", "🏠", "Stay focused while cleaning up", "#FFCC00")
        )

        templates.forEach { template ->
            val b = ItemTemplateBinding.inflate(layoutInflater, templatesContainer, false)
            b.title.text = template.title
            b.subtitle.text = template.subtitle
            b.timeChip.text = template.time
            b.iconEmoji.text = getCorrectIcon(template.title)
            
            val accentColor = Color.parseColor(template.accent)
            b.accentLine.backgroundTintList = ColorStateList.valueOf(accentColor)
            b.glowView.backgroundTintList = ColorStateList.valueOf(accentColor).withAlpha(41)
            
            // FIX TINT - 14% alpha = 36 out of 255
            val tintColor = Color.argb(36, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            b.iconContainer.backgroundTintList = ColorStateList.valueOf(tintColor)
            b.iconContainer.backgroundTintMode = PorterDuff.Mode.SRC_IN
            b.iconContainer.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_icon_oval)
            b.iconContainer.backgroundTintList = ColorStateList.valueOf(tintColor)
            
            // FIX PLAY CLICK
            b.playButton.isClickable = true
            b.playButton.isFocusable = true
            b.playButton.elevation = 10f * resources.displayMetrics.density
            b.triangle.isClickable = false
            b.triangle.isFocusable = false
            b.triangle.isEnabled = false
            b.root.isClickable = false
            b.root.isFocusable = false

            b.playButton.setOnClickListener { v ->
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(110L).withEndAction {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(240L).setInterpolator(OvershootInterpolator(2.8f)).start()
                }.start()
                
                val times = template.time.split(" - ")
                val startParts = times[0].split(":")
                val endParts = times[1].split(":")
                
                val schedule = InternetSchedule(
                    name = template.title,
                    days = 127, 
                    startHour = startParts[0].toInt(),
                    startMinute = startParts[1].toInt(),
                    endHour = endParts[0].toInt(),
                    endMinute = endParts[1].toInt(),
                    isEnabled = true,
                    isStrict = false,
                    apps = "",
                    keywords = "",
                    strictnessLevel = 1,
                    linkToBedtime = false
                )
                
                CoroutineScope(Dispatchers.IO).launch {
                    val database = AppDatabase.getDatabase(requireContext())
                    val id = database.internetScheduleDao().insertSchedule(schedule).toInt()
                    val saved = schedule.copy(id = id)
                    AppBlockerService.scheduleNextAlarm(requireContext())
                    withContext(Dispatchers.Main) {
                        activateSchedule(saved)
                    }
                }
            }

            templatesContainer.addView(b.root)
        }
    }

    private fun showCreateBottomSheet(existing: InternetSchedule?) {
        val sheet = CreateScheduleBottomSheet.newInstance(existing)
        sheet.onSave = { newSchedule ->
            CoroutineScope(Dispatchers.IO).launch {
                val database = AppDatabase.getDatabase(requireContext())
                val id = database.internetScheduleDao().insertSchedule(newSchedule).toInt()
                val saved = newSchedule.copy(id = id)
                AppBlockerService.scheduleNextAlarm(requireContext())
                withContext(Dispatchers.Main) {
                    if (saved.isActive()) {
                        activateSchedule(saved)
                    } else {
                        refreshUI()
                    }
                }
            }
        }
        sheet.show(childFragmentManager, "create_schedule")
    }

    private fun setupClickMicroInteraction(view: View) {
        view.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(110L).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(240L).setInterpolator(OvershootInterpolator(2.3f)).start()
            }.start()
        }
    }

    private fun startConstantFocusShieldAnimations() {
        loopGreenDot()
        loopOuterDot()
        loopScoreGlow()
        loopProgressGlow()
        startShimmerLoop()
        loopMoonFloat()
        loopMoonGlows()
        loopCreateCircle()
    }

    private fun loopGreenDot() {
        greenDot.animate().scaleX(1.4f).scaleY(1.4f).setDuration(1600L).setInterpolator(AccelerateDecelerateInterpolator()).withEndAction {
            greenDot.animate().scaleX(1f).scaleY(1f).setDuration(1600L).withEndAction {
                if (isAdded && isVisible) loopGreenDot()
            }.start()
        }.start()
    }

    private fun loopOuterDot() {
        greenDotOuter.animate().alpha(0.12f).scaleX(1.6f).scaleY(1.6f).setDuration(1600L).withEndAction {
            greenDotOuter.alpha = 0.28f
            greenDotOuter.scaleX = 1f
            greenDotOuter.scaleY = 1f
            if (isAdded && isVisible) loopOuterDot()
        }.start()
    }

    private fun loopScoreGlow() {
        scoreGlowOuter.animate().alpha(0.34f).setDuration(2200L).setInterpolator(AccelerateDecelerateInterpolator()).withEndAction {
            scoreGlowOuter.animate().alpha(0.18f).setDuration(2200L).withEndAction {
                if (isAdded && isVisible) loopScoreGlow()
            }.start()
        }.start()
    }

    private fun loopProgressGlow() {
        progressGlow.animate().alpha(0.48f).setDuration(2200L).withEndAction {
            progressGlow.animate().alpha(0.28f).setDuration(2200L).withEndAction {
                if (isAdded && isVisible) loopProgressGlow()
            }.start()
        }.start()
    }

    private fun startShimmerLoop() {
        shimmerView.translationX = -90f
        shimmerView.alpha = 0.65f
        shimmerView.animate().translationX(progressContainer.width.toFloat() + 90f).setDuration(1800L).setInterpolator(DecelerateInterpolator()).setStartDelay(800L).withEndAction {
            shimmerView.alpha = 0f
            shimmerView.postDelayed({ if (isAdded && isVisible) startShimmerLoop() }, 1200L)
        }.start()
    }

    private fun loopMoonFloat() {
        moonIcon.animate().translationY(-8f).setDuration(1600L).setInterpolator(AccelerateDecelerateInterpolator()).withEndAction {
            moonIcon.animate().translationY(0f).setDuration(1600L).withEndAction {
                if (isAdded && isVisible) loopMoonFloat()
            }.start()
        }.start()
    }

    private fun loopMoonGlows() {
        moonOuterGlow.animate().alpha(0.36f).setDuration(2600L).withEndAction {
            moonOuterGlow.animate().alpha(0.20f).setDuration(2600L).withEndAction {
                if (isAdded && isVisible) loopMoonGlows()
            }.start()
        }.start()
        moonInnerGlow.animate().alpha(0.30f).setDuration(2600L).withEndAction {
            moonInnerGlow.animate().alpha(0.16f).setDuration(2600L).start()
        }.start()
    }

    private fun loopCreateCircle() {
        createCircle.animate().scaleX(1.07f).scaleY(1.07f).setDuration(1500L).withEndAction {
            createCircle.animate().scaleX(1f).scaleY(1f).setDuration(1500L).withEndAction {
                if (isAdded && isVisible) loopCreateCircle()
            }.start()
        }.start()
        createGlow.animate().alpha(0.38f).setDuration(2000L).withEndAction {
            createGlow.animate().alpha(0.22f).setDuration(2000L).start()
        }.start()
    }

    private fun getCorrectIcon(name: String): String {
        return when {
            name.contains("Sabbath", true) -> "🚫"
            name.contains("Night", true) || name.contains("Sleep", true) -> "🌙"
            name.contains("Study", true) || name.contains("Learn", true) -> "📚"
            name.contains("Deep Focus", true) -> "💼"
            name.contains("Flow", true) -> "⚡"
            name.contains("Wind Down", true) -> "🕯️"
            name.contains("Morning", true) -> "🌅"
            name.contains("House", true) || name.contains("Chore", true) -> "🏠"
            name.contains("Exercise", true) || name.contains("Workout", true) -> "💪"
            name.contains("Social", true) || name.contains("Detox", true) -> "📱"
            name.contains("Gaming", true) || name.contains("Game", true) -> "🎮"
            name.contains("Work", true) -> "💼"
            else -> "🛡️"
        }
    }

    private fun animateOnSchedulerTap() {
        templatesContainer.post {
            for (i in 0 until templatesContainer.childCount) {
                val card = templatesContainer.getChildAt(i)
                card.alpha = 0f
                card.translationY = 90f
                card.scaleX = 0.84f
                card.scaleY = 0.84f
                card.rotationX = 12f
                card.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).rotationX(0f).setStartDelay(80L + i * 135L).setDuration(700L).setInterpolator(OvershootInterpolator(1.35f)).withEndAction {
                    val chip = card.findViewById<TextView>(R.id.timeChip)
                    chip?.let { 
                        it.alpha = 0f; it.scaleX = 0.6f; it.scaleY = 0.6f; it.translationY = 10f
                        it.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(380L).setInterpolator(OvershootInterpolator(2.4f)).setStartDelay(60L).start() 
                    }
                    val play = card.findViewById<View>(R.id.playButton)
                    play?.let { 
                        it.scaleX = 0f; it.scaleY = 0f
                        it.animate().scaleX(1f).scaleY(1f).setDuration(420L).setInterpolator(OvershootInterpolator(3.2f)).setStartDelay(110L).withEndAction {
                            it.animate().scaleX(1.08f).scaleY(1.08f).setDuration(2000L).setStartDelay(500L + i * 100L).withEndAction {
                                it.animate().scaleX(1f).scaleY(1f).setDuration(2000L).start()
                            }.start()
                        }.start() 
                    }
                    val accent = card.findViewById<View>(R.id.accentLine)
                    accent?.let { it.scaleY = 0f; it.animate().scaleY(1f).setDuration(550L).setInterpolator(DecelerateInterpolator()).setStartDelay(40L).start() }
                }.start()
            }
        }
        mySchedulesContainer.post {
            for (i in 0 until mySchedulesContainer.childCount) {
                val c = mySchedulesContainer.getChildAt(i)
                c.alpha = 0f; c.translationY = 70f; c.scaleX = 0.86f; c.scaleY = 0.86f
                c.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setStartDelay(200L + i * 115L).setDuration(600L).setInterpolator(OvershootInterpolator(1.28f)).start()
            }
        }
        val focusCard = view?.findViewById<View>(R.id.focusShieldCard)
        focusCard?.let {
            it.alpha = 0f; it.translationY = 60f; it.scaleX = 0.92f; it.scaleY = 0.92f
            it.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(750L).setInterpolator(DecelerateInterpolator()).start()
        }
        val createCard = view?.findViewById<View>(R.id.createScheduleCard)
        createCard?.let {
            it.alpha = 0f; it.translationY = 50f; it.scaleX = 0.90f; it.scaleY = 0.90f
            it.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setStartDelay(180L).setDuration(650L).setInterpolator(OvershootInterpolator(1.25f)).start()
        }
    }
}