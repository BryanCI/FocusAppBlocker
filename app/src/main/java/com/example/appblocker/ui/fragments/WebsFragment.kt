package com.example.appblocker.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.appblocker.MainViewModel
import com.example.appblocker.adapter.WebItem
import com.example.appblocker.adapter.WebsAdapter
import com.example.appblocker.databinding.FragmentWebsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import android.content.Context
import android.view.inputmethod.InputMethodManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WebsFragment : Fragment() {

    private var _binding: FragmentWebsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: WebsAdapter
    private var currentQuery = ""
    private var fullList = emptyList<WebItem>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWebsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = WebsAdapter { item ->
            viewModel.toggleWeb(item.domain)
        }

        binding.recyclerWebs.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerWebs.adapter = adapter

        binding.etAddWebsite.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addWebsite()
                true
            } else false
        }

        binding.btnAddWebsite.setOnClickListener {
            addWebsite()
            hideKeyboard()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.webs.collectLatest {
                fullList = it
                updateList()
            }
        }
    }

    private fun updateList() {
        val filtered = if (currentQuery.isEmpty()) {
            fullList
        } else {
            fullList.filter { it.domain.contains(currentQuery, ignoreCase = true) }
        }
        adapter.submitList(filtered)
    }

    fun onSearch(query: String) {
        currentQuery = query
        updateList()
    }

    private fun addWebsite() {
        val domain = binding.etAddWebsite.text.toString().trim().lowercase()
        if (domain.length >= 2) {
            viewModel.addWebsite(domain)
            binding.etAddWebsite.text.clear()
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etAddWebsite.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}