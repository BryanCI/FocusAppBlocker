package com.example.appblocker.onboarding

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.appblocker.MainViewModel
import com.example.appblocker.R
import com.example.appblocker.databinding.FragmentChooseAppsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChooseAppsFragment : Fragment(R.layout.fragment_choose_apps) {

    private var _binding: FragmentChooseAppsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: AppSelectionAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentChooseAppsBinding.bind(view)

        setupRecyclerView()
        setupSearch()

        binding.btnFinish.setOnClickListener {
            (activity as? OnboardingActivity)?.finishOnboarding()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { apps ->
                adapter.submitList(apps)
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = AppSelectionAdapter { appInfo ->
            viewModel.toggleBlock(appInfo)
        }
        binding.rvApps.layoutManager = LinearLayoutManager(context)
        binding.rvApps.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setSearchQuery(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}