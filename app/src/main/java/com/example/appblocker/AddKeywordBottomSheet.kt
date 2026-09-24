package com.example.appblocker

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import com.example.appblocker.databinding.BottomSheetAddKeywordBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AddKeywordBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddKeywordBinding? = null
    private val binding get() = _binding!!

    private var onKeywordAdded: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAddKeywordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.etKeyword.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etKeyword, InputMethodManager.SHOW_IMPLICIT)

        binding.btnAdd.setOnClickListener {
            val keyword = binding.etKeyword.text.toString().trim()
            if (keyword.isNotEmpty()) {
                onKeywordAdded?.invoke(keyword)
                dismiss()
            } else {
                binding.tilKeyword.error = "Please enter a keyword"
            }
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    fun setOnKeywordAddedListener(listener: (String) -> Unit) {
        onKeywordAdded = listener
    }

    companion object {
        fun newInstance() = AddKeywordBottomSheet()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
