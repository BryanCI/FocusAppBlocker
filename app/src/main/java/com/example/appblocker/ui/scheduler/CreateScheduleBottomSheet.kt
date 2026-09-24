package com.example.appblocker.ui.scheduler

import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appblocker.R
import com.example.appblocker.databinding.BottomSheetCreateScheduleBinding
import com.example.appblocker.databinding.DialogSelectAppsBinding
import com.example.appblocker.databinding.ItemAppSelectBinding
import com.example.appblocker.data.InternetSchedule
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import java.util.*

@AndroidEntryPoint
class CreateScheduleBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCreateScheduleBinding? = null
    private val binding get() = _binding!!

    private var selectedDaysMask = 0
    private var selectedStrictness = "Firm"
    private var startH = 9
    private var startM = 0
    private var endH = 17
    private var endM = 0
    private var blockedApps = mutableSetOf<String>()
    
    var onSave: ((InternetSchedule) -> Unit)? = null
    private var existingSchedule: InternetSchedule? = null

    companion object {
        fun newInstance(schedule: InternetSchedule? = null): CreateScheduleBottomSheet {
            val fragment = CreateScheduleBottomSheet()
            fragment.existingSchedule = schedule
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetCreateScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        if (existingSchedule != null) {
            populateExistingData(existingSchedule!!)
        }
        animateEntrance()
    }

    private fun setupUI() {
        val dayViews = listOf(
            binding.dayS1 to 0, binding.dayM to 1, binding.dayT to 2,
            binding.dayW to 3, binding.dayT2 to 4, binding.dayF to 5, binding.dayS2 to 6
        )

        dayViews.forEach { (view, dayIndex) ->
            view.setOnClickListener {
                toggleDay(view, dayIndex)
                checkConflict()
            }
        }

        binding.strictGentle.setOnClickListener { selectStrictness("Gentle") }
        binding.strictFirm.setOnClickListener { selectStrictness("Firm") }
        binding.strictStrict.setOnClickListener { selectStrictness("Strict") }

        binding.startTimeCard.setOnClickListener {
            showTimePicker(true)
        }
        binding.endTimeCard.setOnClickListener {
            showTimePicker(false)
        }

        binding.selectAppsButton.setOnClickListener {
            showAppSelectionDialog()
        }

        binding.cancelButton.setOnClickListener { dismiss() }
        binding.saveButton.setOnClickListener { saveSchedule() }
    }

    private fun populateExistingData(s: InternetSchedule) {
        binding.bottomSheetTitle.text = "Edit Schedule"
        binding.scheduleNameInput.setText(s.name)
        
        for (i in 0..6) {
            val mask = 1 shl i
            if ((s.days and mask) != 0) {
                val view = when(i) {
                    0 -> binding.dayS1
                    1 -> binding.dayM
                    2 -> binding.dayT
                    3 -> binding.dayW
                    4 -> binding.dayT2
                    5 -> binding.dayF
                    6 -> binding.dayS2
                    else -> null
                }
                view?.let { toggleDay(it, i, forceSelected = true) }
            }
        }
        
        val strictnessStr = when(s.strictnessLevel) {
            0 -> "Gentle"
            2 -> "Strict"
            else -> "Firm"
        }
        selectStrictness(strictnessStr)
        
        binding.usageLimitTrigger.isChecked = (s.usageTriggerMinutes > 0)
        binding.bedtimeModeTrigger.isChecked = s.linkToBedtime
        
        startH = s.startHour
        startM = s.startMinute
        endH = s.endHour
        endM = s.endMinute
        
        updateTimeLabels()
        
        blockedApps.clear()
        if (s.apps.isNotEmpty()) {
            blockedApps.addAll(s.apps.split(",").map { it.trim() })
        }
        binding.selectAppsButton.text = if (blockedApps.isEmpty()) "Select" else "${blockedApps.size} Selected"
        binding.keywordsInput.setText(s.keywords)
        binding.strictModeSwitch.isChecked = s.isStrict
    }

    private fun toggleDay(view: FrameLayout, dayIndex: Int, forceSelected: Boolean? = null) {
        val bgView = view.getChildAt(0)
        val mask = 1 shl dayIndex
        val isNowSelected = forceSelected ?: ((selectedDaysMask and mask) == 0)
        
        if (isNowSelected) {
            selectedDaysMask = selectedDaysMask or mask
            bgView.setBackgroundResource(R.drawable.bg_day_selected)
            view.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).setInterpolator(OvershootInterpolator()).start()
        } else {
            selectedDaysMask = selectedDaysMask and mask.inv()
            bgView.setBackgroundResource(R.drawable.bg_day_unselected)
            view.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
        }
    }

    private fun selectStrictness(level: String) {
        selectedStrictness = level
        val cards = listOf(binding.strictGentle, binding.strictFirm, binding.strictStrict)
        cards.forEach { card ->
            val isSelected = (level == "Gentle" && card == binding.strictGentle) ||
                             (level == "Firm" && card == binding.strictFirm) ||
                             (level == "Strict" && card == binding.strictStrict)
            
            if (isSelected) {
                card.setCardBackgroundColor(Color.parseColor("#2E1A4A"))
                card.strokeColor = Color.parseColor("#A855F7")
                card.strokeWidth = (1.5f * resources.displayMetrics.density).toInt()
            } else {
                card.setCardBackgroundColor(Color.parseColor("#3A3A4A"))
                card.strokeColor = Color.parseColor("#4A4A5A")
                card.strokeWidth = (1f * resources.displayMetrics.density).toInt()
            }
        }
    }

    private fun showTimePicker(isStart: Boolean) {
        val h = if (isStart) startH else endH
        val m = if (isStart) startM else endM
        
        TimePickerDialog(requireContext(), R.style.TimePickerTheme, { _, hour, minute ->
            if (isStart) {
                startH = hour
                startM = minute
            } else {
                endH = hour
                endM = minute
            }
            updateTimeLabels()
            checkConflict()
        }, h, m, true).show()
    }

    private fun updateTimeLabels() {
        val startStr = String.format(Locale.getDefault(), "%02d:%02d", startH, startM)
        val endStr = String.format(Locale.getDefault(), "%02d:%02d", endH, endM)
        binding.startTimeText.text = startStr
        binding.endTimeText.text = endStr
        binding.timeIntervalSummary.text = "$startStr - $endStr"
    }

    private fun showAppSelectionDialog() {
        val dialog = BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme)
        val dialogBinding = DialogSelectAppsBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val pm = requireContext().packageManager
        val allApps = pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { it.loadLabel(pm).toString() }

        val adapter = AppsAdapter(allApps, blockedApps)
        dialogBinding.appsRecyclerView.layoutManager = LinearLayoutManager(context)
        dialogBinding.appsRecyclerView.adapter = adapter

        dialogBinding.searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString(), pm)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        dialogBinding.dialogDoneButton.setOnClickListener {
            blockedApps.clear()
            blockedApps.addAll(adapter.selectedPackages)
            binding.selectAppsButton.text = if (blockedApps.isEmpty()) "Select" else "${blockedApps.size} Selected"
            dialog.dismiss()
        }
        dialogBinding.dialogCancelButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun checkConflict() {
        // Simple conflict detection logic for demo
        val hasConflict = selectedDaysMask != 0 && startH == 9 && startM == 0 && existingSchedule == null
        if (hasConflict) {
            binding.conflictBanner.visibility = View.VISIBLE
            binding.conflictBanner.translationY = -20f
            binding.conflictBanner.alpha = 0f
            binding.conflictBanner.animate().translationY(0f).alpha(1f).setDuration(350).start()
        } else {
            binding.conflictBanner.visibility = View.GONE
        }
    }

    private fun saveSchedule() {
        binding.saveButton.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        val name = binding.scheduleNameInput.text.toString().ifEmpty { "My Schedule" }
        if (selectedDaysMask == 0) {
            Toast.makeText(context, "Select at least one day", Toast.LENGTH_SHORT).show()
            return
        }
        
        val strictnessLvl = when(selectedStrictness) {
            "Gentle" -> 0
            "Strict" -> 2
            else -> 1
        }

        val schedule = InternetSchedule(
            id = existingSchedule?.id ?: 0,
            name = name,
            days = selectedDaysMask,
            startHour = startH,
            startMinute = startM,
            endHour = endH,
            endMinute = endM,
            isEnabled = true,
            isStrict = binding.strictModeSwitch.isChecked,
            apps = blockedApps.joinToString(","),
            keywords = binding.keywordsInput.text.toString(),
            strictnessLevel = strictnessLvl,
            linkToBedtime = binding.bedtimeModeTrigger.isChecked,
            usageTriggerMinutes = if (binding.usageLimitTrigger.isChecked) 60 else 0
        )
        
        onSave?.invoke(schedule)
        dismiss()
    }

    private fun animateEntrance() {
        binding.bottomSheetTitle.alpha = 0f
        binding.bottomSheetTitle.translationY = 20f
        binding.bottomSheetTitle.animate().alpha(1f).translationY(0f).setDuration(400).start()
        
        binding.daysContainer.translationY = 20f
        binding.daysContainer.alpha = 0f
        binding.daysContainer.animate().translationY(0f).alpha(1f).setStartDelay(150).setDuration(400).start()

        binding.saveButton.scaleX = 0.8f
        binding.saveButton.scaleY = 0.8f
        binding.saveButton.animate().scaleX(1f).scaleY(1f).setStartDelay(350).setDuration(450).setInterpolator(OvershootInterpolator(2.6f)).start()
    }

    class AppsAdapter(val allApps: List<android.content.pm.ApplicationInfo>, initialSelected: Set<String>) : RecyclerView.Adapter<AppsAdapter.VH>() {
        private var filteredApps = allApps
        val selectedPackages = initialSelected.toMutableSet()
        
        fun filter(query: String, pm: android.content.pm.PackageManager) {
            filteredApps = if (query.isEmpty()) {
                allApps
            } else {
                allApps.filter { 
                    it.loadLabel(pm).toString().contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
                }
            }
            notifyDataSetChanged()
        }

        inner class VH(val b: ItemAppSelectBinding) : RecyclerView.ViewHolder(b.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(ItemAppSelectBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            val app = filteredApps[position]
            val pm = holder.itemView.context.packageManager
            holder.b.appName.text = app.loadLabel(pm)
            holder.b.appIcon.setImageDrawable(app.loadIcon(pm))
            holder.b.appCheckBox.setOnCheckedChangeListener(null)
            holder.b.appCheckBox.isChecked = selectedPackages.contains(app.packageName)
            holder.b.appCheckBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedPackages.add(app.packageName) else selectedPackages.remove(app.packageName)
            }
        }
        override fun getItemCount() = filteredApps.size
    }
}
