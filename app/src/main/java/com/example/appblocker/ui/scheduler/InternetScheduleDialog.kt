package com.example.appblocker.ui.scheduler

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.appblocker.databinding.DialogInternetScheduleBinding
import com.example.appblocker.data.InternetSchedule
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.Locale

class InternetScheduleDialog(
    private val schedule: InternetSchedule? = null,
    private val onSave: (InternetSchedule) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogInternetScheduleBinding? = null
    private val binding get() = _binding!!

    private var startH = 9
    private var startM = 0
    private var endH = 17
    private var endM = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogInternetScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        schedule?.let {
            binding.tvDialogTitle.text = "Edit Schedule"
            binding.etName.setText(it.name)
            startH = it.startHour
            startM = it.startMinute
            endH = it.endHour
            endM = it.endMinute
            binding.switchStrict.isChecked = it.isStrict
            setDayChips(it.days)
            updateTimeUI()
        }

        binding.btnStartTime.setOnClickListener {
            TimePickerDialog(requireContext(), { _, h, m ->
                startH = h
                startM = m
                updateTimeUI()
            }, startH, startM, true).show()
        }

        binding.btnEndTime.setOnClickListener {
            TimePickerDialog(requireContext(), { _, h, m ->
                endH = h
                endM = m
                updateTimeUI()
            }, endH, endM, true).show()
        }

        binding.btnSave.setOnClickListener {
            val name = binding.etName.text.toString().ifBlank { "New Schedule" }
            val days = getDayMask()
            val newSchedule = (schedule ?: InternetSchedule()).copy(
                name = name,
                startHour = startH,
                startMinute = startM,
                endHour = endH,
                endMinute = endM,
                days = days,
                isStrict = binding.switchStrict.isChecked
            )
            onSave(newSchedule)
            dismiss()
        }

        binding.btnCancel.setOnClickListener { dismiss() }
    }

    private fun updateTimeUI() {
        binding.tvStartTime.text = String.format(Locale.getDefault(), "%02d:%02d", startH, startM)
        binding.tvEndTime.text = String.format(Locale.getDefault(), "%02d:%02d", endH, endM)
    }

    private fun setDayChips(mask: Int) {
        binding.chipMon.isChecked = (mask and 2) != 0
        binding.chipTue.isChecked = (mask and 4) != 0
        binding.chipWed.isChecked = (mask and 8) != 0
        binding.chipThu.isChecked = (mask and 16) != 0
        binding.chipFri.isChecked = (mask and 32) != 0
        binding.chipSat.isChecked = (mask and 64) != 0
        binding.chipSun.isChecked = (mask and 1) != 0
    }

    private fun getDayMask(): Int {
        var mask = 0
        if (binding.chipSun.isChecked) mask = mask or 1
        if (binding.chipMon.isChecked) mask = mask or 2
        if (binding.chipTue.isChecked) mask = mask or 4
        if (binding.chipWed.isChecked) mask = mask or 8
        if (binding.chipThu.isChecked) mask = mask or 16
        if (binding.chipFri.isChecked) mask = mask or 32
        if (binding.chipSat.isChecked) mask = mask or 64
        return if (mask == 0) 127 else mask
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
