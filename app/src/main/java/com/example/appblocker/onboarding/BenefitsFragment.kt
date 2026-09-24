package com.example.appblocker.onboarding

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.appblocker.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BenefitsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_benefits, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val tvTitle = view.findViewById<TextView>(R.id.tv_title)
        val fullText = "Less scrolling.\nMore time.\nMore life!"
        val spannable = SpannableString(fullText)
        
        // Color "time" and "life" neon purple
        val timeStart = fullText.indexOf("time")
        val timeEnd = timeStart + "time".length
        val lifeStart = fullText.indexOf("life")
        val lifeEnd = lifeStart + "life".length
        
        val magenta = ContextCompat.getColor(requireContext(), R.color.premium_purple)
        spannable.setSpan(ForegroundColorSpan(magenta), timeStart, timeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(ForegroundColorSpan(magenta), lifeStart, lifeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        tvTitle.text = spannable

        view.findViewById<Button>(R.id.btn_next).setOnClickListener {
            findNavController().navigate(R.id.action_benefitsFragment_to_setupPermissionFragment)
        }
    }
}