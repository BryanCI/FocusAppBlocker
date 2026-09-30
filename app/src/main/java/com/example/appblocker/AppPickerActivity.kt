package com.example.appblocker

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.appblocker.databinding.ActivityAppPickerBinding
import com.example.appblocker.ui.fragments.AppsFragment
import com.example.appblocker.ui.fragments.KeywordsFragment
import com.example.appblocker.ui.fragments.WebsFragment
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.graphics.drawable.ColorDrawable
import android.util.Log

@AndroidEntryPoint
class AppPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppPickerBinding
    private val viewModel: MainViewModel by viewModels()
    private var pickerType: String = TYPE_BLOCKED

    companion object {
        const val EXTRA_PICKER_TYPE = "extra_picker_type"
        const val TYPE_BLOCKED = "type_blocked"
        const val TYPE_ALLOWED = "type_allowed"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pickerType = intent.getStringExtra(EXTRA_PICKER_TYPE) ?: TYPE_BLOCKED

        setupViewPager()
        setupSearch()

        binding.btnSaveApps.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
            saveSelection()
        }
    }

    private fun setupViewPager() {
        val adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 3
            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> AppsFragment()
                    1 -> WebsFragment()
                    2 -> KeywordsFragment()
                    else -> AppsFragment()
                }
            }
        }
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Apps"
                1 -> "Webs"
                2 -> "Keywords"
                else -> null
            }
        }.attach()
        
        binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.etSearch.hint = when (position) {
                    0 -> "Search apps..."
                    1 -> "Search websites..."
                    2 -> "Search keywords..."
                    else -> "Search..."
                }
            }
        })
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                
                // Fragments are stored by the PagerAdapter with tags "f" + position
                val f0 = supportFragmentManager.findFragmentByTag("f0") as? AppsFragment
                val f1 = supportFragmentManager.findFragmentByTag("f1") as? WebsFragment
                val f2 = supportFragmentManager.findFragmentByTag("f2") as? KeywordsFragment

                when (binding.viewPager.currentItem) {
                    0 -> f0?.onSearch(query)
                    1 -> f1?.onSearch(query)
                    2 -> f2?.onSearch(query)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    fun syncSelectedPackages() {
        // Logic for updating global selected state if needed
    }

    fun updateSaveButton() {
        binding.btnSaveApps.isEnabled = true
        binding.btnSaveApps.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3B82F6"))
        binding.btnSaveApps.setTextColor(Color.WHITE)
    }

    private fun saveSelection() {
        lifecycleScope.launch {
            val appsFragment = supportFragmentManager.findFragmentByTag("f0") as? AppsFragment
            val allApps = appsFragment?.getSelectedApps() ?: emptyList()
            
            if (pickerType == TYPE_BLOCKED) {
                // Perform clear and save in one transaction logic
                lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val dao = com.example.appblocker.data.AppDatabase.getDatabase(applicationContext).blockedAppDao()
                    
                    // 1. Get existing keywords so we don't delete them
                    val currentList = dao.getBlockedAppsList()
                    val keywords = currentList.filter { it.isKeyword }
                    
                    // 2. Delete all patterns (non-keywords)
                    currentList.filter { !it.isKeyword }.forEach {
                        dao.unblockApp(it.pattern)
                    }
                    
                    // 3. Insert newly selected apps + WhatsApp variants
                    allApps.filter { it.isChecked }.forEach { app ->
                        dao.blockApp(com.example.appblocker.data.BlockedApp(pattern = app.packageName, isKeyword = false))
                        
                        // Fix WhatsApp: ensure business version is also blocked if regular is selected, and vice versa
                        if (app.packageName == "com.whatsapp") {
                            dao.blockApp(com.example.appblocker.data.BlockedApp(pattern = "com.whatsapp.w4b", isKeyword = false))
                        } else if (app.packageName == "com.whatsapp.w4b") {
                            dao.blockApp(com.example.appblocker.data.BlockedApp(pattern = "com.whatsapp", isKeyword = false))
                        }
                    }
                    
                    // Sync to SharedPreferences for service quick access
                    val newList = dao.getBlockedAppsList()
                    val packages = newList.filter { !it.isKeyword }.map { it.pattern }.toSet()
                    getSharedPreferences("block_prefs", Context.MODE_PRIVATE)
                        .edit().putStringSet("blocked_apps", packages).apply()
                }
            } else {
                val currentAllowed = viewModel.allowedApps.first().map { it.packageName }.toSet()
                val selectedPackages = allApps.filter { it.isChecked }.map { it.packageName }.toSet()

                // 1. Disallow apps that were allowed but are now unchecked
                currentAllowed.forEach { pkg ->
                    if (!selectedPackages.contains(pkg)) {
                        android.util.Log.d("BlocklistSave", "Disallowing $pkg")
                        viewModel.toggleAllowApp(com.example.appblocker.AppInfo(name = "", packageName = pkg, icon = null, isBlocked = false, isAllowed = true))
                    }
                }

                // 2. Allow apps that were not allowed but are now checked
                allApps.filter { it.isChecked }.forEach { app ->
                    if (!currentAllowed.contains(app.packageName)) {
                        android.util.Log.d("BlocklistSave", "Allowing ${app.packageName}")
                        viewModel.toggleAllowApp(com.example.appblocker.AppInfo(name = app.appName, packageName = app.packageName, icon = null, isBlocked = false, isAllowed = false))
                    }
                }
            }
            android.util.Log.d("BlocklistSave", "Save complete for $pickerType")
            finish()
        }
    }
}