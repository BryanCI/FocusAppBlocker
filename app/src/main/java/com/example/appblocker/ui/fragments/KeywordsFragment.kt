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
import com.example.appblocker.databinding.FragmentKeywordsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

import android.content.Context
import android.view.inputmethod.InputMethodManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class KeywordsFragment : Fragment() {

    private var _binding: FragmentKeywordsBinding? = null
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
        _binding = FragmentKeywordsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = WebsAdapter { item ->
            viewModel.removeKeywordBlock(item.domain)
        }

        binding.recyclerKeywords.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerKeywords.adapter = adapter

        binding.etAddKeyword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addKeyword()
                true
            } else false
        }

        binding.btnAddKeyword.setOnClickListener {
            addKeyword()
            hideKeyboard()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.blockedApps.map { list ->
                list.filter { it.isKeyword && !it.pattern.startsWith("web:") }
                    .map { WebItem(it.pattern, true) }
            }.collectLatest {
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

    private fun addKeyword() {
        val keyword = binding.etAddKeyword.text.toString().trim().lowercase()
        if (keyword.length >= 2) {
            // Remove "web:" prefix if user accidentally added it in the Keywords tab
            val finalKeyword = if (keyword.startsWith("web:", ignoreCase = true)) {
                keyword.substring(4).trim()
            } else {
                keyword
            }
            
            if (finalKeyword.isNotEmpty()) {
                viewModel.addKeywordBlock(finalKeyword)
            }
            binding.etAddKeyword.text.clear()
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etAddKeyword.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}