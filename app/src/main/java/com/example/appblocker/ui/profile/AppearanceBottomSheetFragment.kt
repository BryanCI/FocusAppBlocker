package com.example.appblocker.ui.profile

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appblocker.MainViewModel
import com.example.appblocker.R
import com.example.appblocker.ThemeManager
import com.example.appblocker.databinding.BottomSheetAppearanceBinding
import com.example.appblocker.databinding.ItemAccentColorBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AppearanceBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAppearanceBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    private var currentTheme = "dark"
    private var currentAccent = "purple"
    private var isAmoled = true

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as com.google.android.material.bottomsheet.BottomSheetDialog
        dialog.setOnShowListener { dlg ->
            val bottomSheet = (dlg as com.google.android.material.bottomsheet.BottomSheetDialog).findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)
                behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isHideable = true
                behavior.peekHeight = resources.displayMetrics.heightPixels
            }
        }
        return dialog
    }

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetAppearanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            ThemeManager.getThemeModeFlow(requireContext()).collectLatest { 
                currentTheme = it
                updateThemeUI()
            }
        }

        lifecycleScope.launch {
            ThemeManager.getAccentFlow(requireContext()).collectLatest { 
                currentAccent = it
                updateAccentUI()
            }
        }

        lifecycleScope.launch {
            ThemeManager.getAmoledFlow(requireContext()).collectLatest { 
                isAmoled = it
                binding.switchOled.setOnCheckedChangeListener(null)
                binding.switchOled.isChecked = it
                setupOledListener()
            }
        }

        setupListeners()
        setupAccentList()
        startPulseAnimation()
    }

    private fun setupOledListener() {
        binding.switchOled.setOnCheckedChangeListener { _, isChecked ->
            if (isAmoled == isChecked) return@setOnCheckedChangeListener
            lifecycleScope.launch {
                ThemeManager.setAmoledEnabled(requireContext(), isChecked)
                restartActivity()
            }
        }
    }

    private fun startPulseAnimation() {
        val animation = AlphaAnimation(1f, 0.3f).apply {
            duration = 800
            interpolator = LinearInterpolator()
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
        }
        binding.dotPulse.startAnimation(animation)
    }

    private fun setupListeners() {
        binding.btnDark.setOnClickListener { setTheme("dark") }
        binding.btnLight.setOnClickListener { 
            Toast.makeText(requireContext(), "Light theme coming soon", Toast.LENGTH_SHORT).show()
        }
        binding.btnSystem.setOnClickListener { setTheme("system") }
        // Oled listener set via setupOledListener() to avoid initial trigger
    }

    private fun setTheme(mode: String) {
        if (currentTheme == mode) return
        lifecycleScope.launch {
            try {
                ThemeManager.setThemeMode(requireContext(), mode)
                // Force dark mode for both dark and system to keep premium theme
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                val amoled = ThemeManager.getAmoledFlow(requireContext()).first()
                activity?.let { ThemeManager.applyWindowColors(it, mode, amoled) }
                updateThemeUI()
                restartActivity()
            } catch (e: Exception) {}
        }
    }

    private fun restartActivity() {
        val activity = activity ?: return
        dismiss()
        Toast.makeText(requireContext(), "Applying theme...", Toast.LENGTH_SHORT).show()
        
        // Use a slight delay to allow the bottom sheet dismissal animation to finish
        Handler(Looper.getMainLooper()).postDelayed({
            activity.recreate()
            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, 400)
    }

    private fun updateThemeUI() {
        val allBtns = listOf(binding.btnDark, binding.btnLight, binding.btnSystem)
        val allContainers = listOf(binding.containerDark, binding.containerLight, binding.containerSystem)
        val isDarkMode = ThemeManager.isDark(requireContext(), currentTheme)
        
        allBtns.forEach { 
            it.setTextColor(Color.parseColor("#8A8A8E"))
            it.setTypeface(null, android.graphics.Typeface.NORMAL)
        }
        allContainers.forEach { it.setBackgroundResource(0) }

        val selectedBtn: android.widget.TextView
        val selectedContainer: android.widget.FrameLayout

        when(currentTheme) {
            "dark" -> {
                selectedBtn = binding.btnDark
                selectedContainer = binding.containerDark
            }
            "light" -> {
                selectedBtn = binding.btnLight
                selectedContainer = binding.containerLight
            }
            else -> {
                selectedBtn = binding.btnSystem
                selectedContainer = binding.containerSystem
            }
        }

        selectedContainer.setBackgroundResource(R.drawable.bg_segment_selected)
        selectedBtn.setTextColor(Color.WHITE)
        selectedBtn.setTypeface(null, android.graphics.Typeface.BOLD)
        
        binding.cardOled.visibility = if (currentTheme == "light") View.GONE else View.VISIBLE
    }

    private fun updateAccentUI() {
        val color = ThemeManager.getAccentColor(currentAccent)
        val glow = ThemeManager.getAccentGlow(currentAccent)
        val isDarkMode = ThemeManager.isDark(requireContext(), currentTheme)
        
        binding.previewRing.setAccentColor(color)
        binding.previewRing.setProgress(85)

        // Animate button gradient change
        val startColor = ThemeManager.getAccentColor(currentAccent)
        val endColor = ThemeManager.getAccentGradientEnd(currentAccent)
        val gradient = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(startColor, endColor)
        )
        gradient.cornerRadius = 22 * resources.displayMetrics.density
        binding.btnPreview.background = gradient
        
        binding.dotPulse.backgroundTintList = ColorStateList.valueOf(color)
        binding.switchOled.thumbTintList = ColorStateList.valueOf(color)
        binding.switchOled.trackTintList = ColorStateList.valueOf(glow)
        
        binding.labelTheme.setTextColor(color)
        binding.labelAccent.setTextColor(color)
        binding.labelDisplay.setTextColor(color)
        binding.txtPreviewLabel.setTextColor(color)

        // Update Pill Preview Live background tint
        binding.pillPreview.backgroundTintList = ColorStateList.valueOf(glow)
        (binding.pillPreview.getChildAt(1) as? android.widget.TextView)?.setTextColor(color)
    }

    private fun setupAccentList() {
        val accents = listOf(
            AccentItem("purple", "Purple", "#BF00FF", false),
            AccentItem("blue", "Blue", "#3B82F6", true),
            AccentItem("gold", "Gold", "#FFD700", true),
            AccentItem("green", "Green", "#10B981", true)
        )

        binding.rvAccents.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        binding.rvAccents.adapter = AccentAdapter(accents)
    }

    data class AccentItem(val id: String, val name: String, val color: String, val isPremium: Boolean)

    inner class AccentAdapter(private val items: List<AccentItem>) : RecyclerView.Adapter<AccentAdapter.ViewHolder>() {
        inner class ViewHolder(val binding: ItemAccentColorBinding) : RecyclerView.ViewHolder(binding.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(ItemAccentColorBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.txtAccentName.text = item.name
            holder.binding.viewColor.backgroundTintList = ColorStateList.valueOf(Color.parseColor(item.color))
            
            val isSelected = item.id == currentAccent
            holder.binding.txtCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
            holder.binding.circleOuter.visibility = if (isSelected) View.VISIBLE else View.GONE
            holder.binding.circleOuter.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            
            if (isSelected) {
                holder.binding.txtAccentName.setTextColor(Color.WHITE)
                holder.binding.txtAccentName.setTypeface(null, android.graphics.Typeface.BOLD)
                holder.binding.viewGlow.alpha = 0.25f
                holder.binding.viewGlow.backgroundTintList = ColorStateList.valueOf(Color.parseColor(item.color))
                
                // Glow scale animation
                holder.binding.viewGlow.scaleX = 0f
                holder.binding.viewGlow.scaleY = 0f
                holder.binding.viewGlow.animate().scaleX(1.1f).scaleY(1.1f).setDuration(300).start()
                
                // Checkmark scale animation
                holder.binding.txtCheck.scaleX = 0.5f
                holder.binding.txtCheck.scaleY = 0.5f
                holder.binding.txtCheck.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
            } else {
                holder.binding.txtAccentName.setTextColor(Color.parseColor("#8A8A8E"))
                holder.binding.txtAccentName.setTypeface(null, android.graphics.Typeface.NORMAL)
                holder.binding.viewGlow.alpha = 0f
            }
            
            holder.binding.txtAccentStatus.text = if (item.isPremium) "Premium" else "Free"
            holder.binding.txtAccentStatus.setTextColor(if (item.isPremium) Color.parseColor("#FFD700") else Color.parseColor("#6B7280"))

            lifecycleScope.launch {
                viewModel.isPremium.collectLatest { isPremium ->
                    holder.binding.badgeProContainer.visibility = if (item.isPremium && !isPremium) View.VISIBLE else View.GONE
                }
            }

            holder.binding.root.setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                lifecycleScope.launch {
                    val isPremiumActive = viewModel.isPremium.value
                    if (item.isPremium && !isPremiumActive) {
                        dismiss()
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                androidx.navigation.fragment.NavHostFragment.findNavController(this@AppearanceBottomSheetFragment)
                                    .navigate(R.id.navigation_paywall)
                            } catch (e: Exception) {}
                        }, 300)
                    } else {
                        ThemeManager.setAccentColor(requireContext(), item.id)
                        notifyDataSetChanged()
                        updateAccentUI()
                    }
                }
            }
        }
        override fun getItemCount() = items.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}