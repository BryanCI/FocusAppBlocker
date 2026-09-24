package com.example.appblocker.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appblocker.R
import com.example.appblocker.databinding.BottomSheetLicensesBinding
import com.example.appblocker.databinding.ItemLicenseBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OpenSourceLicensesBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetLicensesBinding? = null
    private val binding get() = _binding!!

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetLicensesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.rvLicenses.layoutManager = LinearLayoutManager(context)
        binding.rvLicenses.adapter = LicensesAdapter(getLicenses())
        
        binding.btnClose.setOnClickListener { dismiss() }
    }

    private fun getLicenses(): List<LicenseItem> = listOf(
        LicenseItem("AndroidX Room", "Apache 2.0", "https://github.com/androidx/androidx"),
        LicenseItem("AndroidX WorkManager", "Apache 2.0", "https://github.com/androidx/androidx"),
        LicenseItem("AndroidX Biometric", "Apache 2.0", "https://github.com/androidx/androidx"),
        LicenseItem("Material Components", "Apache 2.0", "https://github.com/material-components/material-components-android"),
        LicenseItem("DataStore Preferences", "Apache 2.0", "https://github.com/androidx/androidx"),
        LicenseItem("Kotlin Coroutines", "Apache 2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
        LicenseItem("Hilt", "Apache 2.0", "https://github.com/google/dagger"),
        LicenseItem("Coil", "Apache 2.0", "https://github.com/coil-kt/coil")
    )

    data class LicenseItem(val name: String, val license: String, val url: String)

    inner class LicensesAdapter(private val items: List<LicenseItem>) :
        RecyclerView.Adapter<LicensesAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemLicenseBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemLicenseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.libName.text = item.name
            holder.binding.libLicense.text = item.license
            holder.binding.root.setOnClickListener {
                context?.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
            }
        }

        override fun getItemCount() = items.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
