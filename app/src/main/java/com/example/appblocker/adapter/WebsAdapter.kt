package com.example.appblocker.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.appblocker.R
import com.example.appblocker.databinding.ItemWebSelectBinding

data class WebItem(val domain: String, val isBlocked: Boolean)

class WebsAdapter(private val onToggle: (WebItem) -> Unit) :
    ListAdapter<WebItem, WebsAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWebSelectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemWebSelectBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: WebItem) {
            binding.tvDomain.text = item.domain
            binding.cbWeb.isChecked = item.isBlocked

            val faviconUrl = "https://www.google.com/s2/favicons?domain=${item.domain}&sz=64"
            binding.ivFavicon.load(faviconUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_globe)
                error(R.drawable.ic_globe)
                transformations(CircleCropTransformation())
            }

            binding.root.setOnClickListener {
                onToggle(item)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<WebItem>() {
        override fun areItemsTheSame(oldItem: WebItem, newItem: WebItem): Boolean =
            oldItem.domain == newItem.domain

        override fun areContentsTheSame(oldItem: WebItem, newItem: WebItem): Boolean =
            oldItem == newItem
    }
}