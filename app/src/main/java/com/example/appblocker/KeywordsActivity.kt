package com.example.appblocker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appblocker.data.BlockedApp
import com.example.appblocker.databinding.ActivityKeywordsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class KeywordsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKeywordsBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: KeywordsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKeywordsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        adapter = KeywordsAdapter { keyword ->
            viewModel.removeKeywordBlock(keyword)
        }

        binding.rvKeywords.layoutManager = LinearLayoutManager(this)
        binding.rvKeywords.adapter = adapter

        binding.fabAddKeyword.setOnClickListener {
            showAddKeywordBottomSheet()
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.blockedApps.collect { blockedList ->
                    val keywords = blockedList.filter { it.isKeyword }.map { it.pattern }
                    adapter.submitList(keywords)
                    binding.tvEmptyState.visibility = if (keywords.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun showAddKeywordBottomSheet() {
        AddKeywordBottomSheet.newInstance().apply {
            setOnKeywordAddedListener { keyword ->
                viewModel.addKeywordBlock(keyword)
            }
        }.show(supportFragmentManager, "AddKeywordBottomSheet")
    }

    class KeywordsAdapter(private val onDelete: (String) -> Unit) : RecyclerView.Adapter<KeywordsAdapter.ViewHolder>() {
        private var keywords: List<String> = emptyList()

        fun submitList(newList: List<String>) {
            keywords = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_keyword, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val keyword = keywords[position]
            holder.tvKeyword.text = keyword
            holder.btnDelete.setOnClickListener { onDelete(keyword) }
            
            // Hide divider for last item
            holder.itemView.findViewById<View>(R.id.divider).visibility = 
                if (position == keywords.size - 1) View.GONE else View.VISIBLE
        }

        override fun getItemCount() = keywords.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvKeyword: TextView = view.findViewById(R.id.tvKeyword)
            val btnDelete: ImageView = view.findViewById(R.id.btnDelete)
        }
    }
}
