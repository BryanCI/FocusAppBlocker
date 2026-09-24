package com.example.appblocker.onboarding

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.appblocker.AppInfo
import com.example.appblocker.databinding.ItemAppSelectionBinding

class AppSelectionAdapter(private val onToggleBlock: (AppInfo) -> Unit) :
    ListAdapter<AppInfo, AppSelectionAdapter.ViewHolder>(AppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppSelectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemAppSelectionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(appInfo: AppInfo) {
            binding.ivAppIcon.setImageDrawable(appInfo.icon)
            binding.tvAppName.text = appInfo.name
            binding.cbBlock.setOnCheckedChangeListener(null)
            binding.cbBlock.isChecked = appInfo.isBlocked

            binding.root.setOnClickListener {
                onToggleBlock(appInfo)
            }
            binding.cbBlock.setOnClickListener {
                onToggleBlock(appInfo)
            }
        }
    }

    private class AppDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.isBlocked == newItem.isBlocked && oldItem.name == newItem.name
        }
    }
}