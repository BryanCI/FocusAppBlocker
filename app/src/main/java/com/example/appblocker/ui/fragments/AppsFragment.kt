package com.example.appblocker.ui.fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.appblocker.AppPickerActivity
import com.example.appblocker.MainViewModel
import com.example.appblocker.StrictModePinBottomSheet
import com.example.appblocker.adapter.HybridAppsAdapter
import com.example.appblocker.databinding.FragmentAppsBinding
import com.example.appblocker.model.AppGroup
import com.example.appblocker.model.AppInfo as ModelAppInfo
import com.example.appblocker.util.AppGroupMapper
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class AppsFragment : Fragment() {

    private var _binding: FragmentAppsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: HybridAppsAdapter
    private var pickerType: String = AppPickerActivity.TYPE_BLOCKED

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        pickerType = activity?.intent?.getStringExtra(AppPickerActivity.EXTRA_PICKER_TYPE) ?: AppPickerActivity.TYPE_BLOCKED

        val instantGroups = AppGroupMapper.orderedNames.map { groupName ->
            AppGroup(
                id = groupName,
                name = groupName,
                iconRes = AppGroupMapper.getIconForGroup(groupName),
                apps = mutableListOf()
            )
        }

        adapter = HybridAppsAdapter(instantGroups) { item ->
            handleToggle(item)
        }

        binding.rvApps.layoutManager = LinearLayoutManager(requireContext())
        binding.rvApps.adapter = adapter

        binding.chipGroups.setOnClickListener { adapter.setMode(HybridAppsAdapter.Mode.GROUPS) }
        binding.chipAZ.setOnClickListener { adapter.setMode(HybridAppsAdapter.Mode.AZ) }
        binding.chipSelected.setOnClickListener { adapter.setMode(HybridAppsAdapter.Mode.SELECTED) }

        loadApps()
    }

    private fun handleToggle(item: Any) {
        val isStrict = viewModel.isStrictBlockActive.value
        val isBlockingPicker = pickerType == AppPickerActivity.TYPE_BLOCKED

        val isRemoval = when (item) {
            is ModelAppInfo -> !item.isChecked
            is AppGroup -> item.apps.any { !it.isChecked }
            else -> false
        }

        if (isStrict && isBlockingPicker && isRemoval) {
            val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val currentPin = prefs.getString("strict_pin", null)
            val sheet = StrictModePinBottomSheet.newInstance(StrictModePinBottomSheet.Mode.VERIFY, currentPin)
            sheet.setListener(object : StrictModePinBottomSheet.StrictModePinListener {
                override fun onPinSet(pin: String) {}
                override fun onPinVerified() {
                    (activity as? AppPickerActivity)?.syncSelectedPackages()
                    (activity as? AppPickerActivity)?.updateSaveButton()
                    updateSelectedCount()
                }
                override fun onCancel() {
                    loadApps()
                }
            })
            sheet.show(parentFragmentManager, "StrictModePinBottomSheet")
        } else {
            (activity as? AppPickerActivity)?.syncSelectedPackages()
            (activity as? AppPickerActivity)?.updateSaveButton()
            updateSelectedCount()
        }
    }

    private fun loadApps() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val pm = requireContext().packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveInfos = try {
                pm.queryIntentActivities(intent, 0)
            } catch (e: Exception) {
                emptyList()
            }
            
            val blockedApps = viewModel.blockedApps.first().filter { !it.isKeyword }.map { it.pattern }.toSet()
            val allowedApps = viewModel.allowedApps.first().map { it.packageName }.toSet()

            val appInfos = resolveInfos.mapNotNull {
                val activityInfo = it.activityInfo ?: return@mapNotNull null
                if (activityInfo.packageName == requireContext().packageName) return@mapNotNull null
                
                ModelAppInfo(
                    packageName = activityInfo.packageName,
                    appName = it.loadLabel(pm).toString(),
                    isChecked = if (pickerType == AppPickerActivity.TYPE_BLOCKED) {
                        blockedApps.contains(activityInfo.packageName)
                    } else {
                        allowedApps.contains(activityInfo.packageName)
                    },
                    icon = null
                )
            }.distinctBy { it.packageName }.sortedBy { it.appName.lowercase() }

            val realGroups = AppGroupMapper.buildGroups(appInfos)

            withContext(Dispatchers.Main) {
                adapter.setGroups(realGroups)
                updateSelectedCount()
            }
        }
    }

    private fun updateSelectedCount() {
        val count = adapter.getAllApps().count { it.isChecked }
        binding.chipSelected.text = "Selected ($count)"
    }
    
    fun onSearch(query: String) {
        adapter.search(query)
    }
    
    fun getSelectedApps(): List<ModelAppInfo> = adapter.getAllApps()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}