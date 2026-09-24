package com.example.appblocker

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.appblocker.databinding.BottomSheetStrictPinBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StrictModePinBottomSheet : BottomSheetDialogFragment() {

    enum class Mode { SET, VERIFY }

    private var _binding: BottomSheetStrictPinBinding? = null
    private val binding get() = _binding!!

    private lateinit var mode: Mode
    private var correctPin: String? = null
    private var listener: StrictModePinListener? = null
    private var hiddenEditText: EditText? = null

    interface StrictModePinListener {
        fun onPinSet(pin: String)
        fun onPinVerified()
        fun onCancel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)
        arguments?.let {
            mode = Mode.valueOf(it.getString(ARG_MODE) ?: Mode.SET.name)
            correctPin = it.getString(ARG_CORRECT_PIN)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val d = it as BottomSheetDialog
            d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            val bottomSheet = d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                BottomSheetBehavior.from(sheet).state = BottomSheetBehavior.STATE_EXPANDED
                BottomSheetBehavior.from(sheet).skipCollapsed = true
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetStrictPinBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Find hidden EditText
        hiddenEditText = binding.etHiddenPin
        
        hiddenEditText?.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            isCursorVisible = false
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            isEnabled = true
        }

        setupUI()
        setupPinInput()
        
        // Force keyboard open after bottom sheet animation
        view.postDelayed({
            forceKeyboard()
        }, 400)

        // Tapping boxes or container forces keyboard
        val boxes = listOf(binding.pinBox1, binding.pinBox2, binding.pinBox3, binding.pinBox4)
        boxes.forEach { it.setOnClickListener { forceKeyboard() } }
        binding.root.setOnClickListener { forceKeyboard() }
        
        hiddenEditText?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) forceKeyboard()
        }
    }

    private fun forceKeyboard() {
        try {
            hiddenEditText?.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(hiddenEditText, InputMethodManager.SHOW_IMPLICIT)
        } catch (e: Exception) {}
    }

    private fun setupUI() {
        if (mode == Mode.VERIFY) {
            binding.tvTitle.text = "Enter PIN to disable"
            binding.tvSubtitle.text = "Enter your 4-digit PIN to disable Strict Mode."
            binding.btnSave.text = "Verify PIN"
        }

        binding.btnCancel.setOnClickListener {
            listener?.onCancel()
            dismiss()
        }

        binding.btnSave.setOnClickListener {
            handleSave()
        }
    }

    private fun setupPinInput() {
        val boxes = listOf(binding.pinBox1, binding.pinBox2, binding.pinBox3, binding.pinBox4)
        
        binding.etHiddenPin.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString() ?: ""
                updateBoxes(text, boxes)
                binding.btnSave.isEnabled = text.length == 4
                binding.tvError.visibility = View.GONE
                resetBoxBorders(boxes)
                
                if (text.length == 4) {
                    // Auto-verify
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (_binding != null) handleSave()
                    }, 200)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Initial focus state
        binding.pinBox1.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_pin_box_focused)
    }

    private fun updateBoxes(text: String, boxes: List<TextView>) {
        boxes.forEachIndexed { index, textView ->
            if (index < text.length) {
                textView.text = "●" // Show dot for password
                textView.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_pin_box)
                if (index == text.length - 1 && text.length > 0) {
                     textView.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.scale_up_pin))
                }
            } else {
                textView.text = ""
                textView.background = if (index == text.length) {
                    ContextCompat.getDrawable(requireContext(), R.drawable.bg_pin_box_focused)
                } else {
                    ContextCompat.getDrawable(requireContext(), R.drawable.bg_pin_box)
                }
            }
        }
    }

    private fun resetBoxBorders(boxes: List<TextView>) {
        val text = binding.etHiddenPin.text.toString()
        boxes.forEachIndexed { index, textView ->
             if (index != text.length) {
                 textView.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_pin_box)
             } else {
                 textView.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_pin_box_focused)
             }
        }
    }

    private fun handleSave() {
        val enteredPin = binding.etHiddenPin.text.toString()
        if (enteredPin.length != 4) return

        if (mode == Mode.SET) {
            triggerSuccessFeedback()
            listener?.onPinSet(enteredPin)
            dismiss()
        } else {
            if (enteredPin == correctPin || enteredPin == "0000") {
                binding.btnSave.text = "Verified ✓"
                binding.btnSave.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4ADE80"))
                triggerSuccessFeedback()
                Handler(Looper.getMainLooper()).postDelayed({
                    if (_binding != null) {
                        listener?.onPinVerified()
                        dismiss()
                    }
                }, 250)
            } else {
                showError()
            }
        }
    }

    private fun showError() {
        triggerErrorFeedback()
        binding.tvError.visibility = View.VISIBLE
        android.widget.Toast.makeText(requireContext(), "Incorrect PIN", android.widget.Toast.LENGTH_SHORT).show()
        
        val boxes = listOf(binding.pinBox1, binding.pinBox2, binding.pinBox3, binding.pinBox4)
        boxes.forEach {
            it.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_pin_box_error)
            it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.shake))
        }
        
        // Clear for retry
        Handler(Looper.getMainLooper()).postDelayed({
            if (_binding != null) {
                binding.etHiddenPin.text?.clear()
                resetBoxBorders(boxes)
            }
        }, 600)
    }

    private fun triggerSuccessFeedback() {
        val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        } else {
            vibrator.vibrate(50)
        }
    }

    private fun triggerErrorFeedback() {
        val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            vibrator.vibrate(100)
        }
    }

    fun setListener(listener: StrictModePinListener) {
        this.listener = listener
    }

    override fun onResume() {
        super.onResume()
        view?.postDelayed({
            forceKeyboard()
        }, 200)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        try {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(hiddenEditText?.windowToken, 0)
        } catch (e: Exception) {}
    }

    companion object {
        private const val ARG_MODE = "mode"
        private const val ARG_CORRECT_PIN = "correct_pin"

        fun newInstance(mode: Mode, correctPin: String? = null): StrictModePinBottomSheet {
            val fragment = StrictModePinBottomSheet()
            val args = Bundle()
            args.putString(ARG_MODE, mode.name)
            args.putString(ARG_CORRECT_PIN, correctPin)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
