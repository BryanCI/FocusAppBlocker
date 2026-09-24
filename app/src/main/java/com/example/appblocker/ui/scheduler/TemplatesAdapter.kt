package com.example.appblocker.ui.scheduler

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.appblocker.databinding.ItemTemplateCardBinding
import com.example.appblocker.data.InternetSchedule
import java.util.Locale

class TemplatesAdapter(
    private val onSelect: (InternetSchedule) -> Unit
) : ListAdapter<InternetSchedule, TemplatesAdapter.VH>(DiffCallback) {

    class VH(val binding: ItemTemplateCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(ItemTemplateCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position) ?: return
        try {
            holder.binding.title.text = item.name
            holder.binding.subtitle.text = item.tagline ?: "Proven routine"
            
            val timeRange = String.format(Locale.getDefault(), "%02d:%02d - %02d:%02d", 
                item.startHour, item.startMinute, item.endHour, item.endMinute)
            holder.binding.timeRange.text = timeRange
            
            // Set emoji or icon if possible
            // holder.binding.ivTemplateIcon ...

            holder.binding.root.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    holder.binding.playButton.animate().scaleX(1.2f).scaleY(1.2f).setDuration(120).withEndAction {
                        holder.binding.playButton.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                    }.start()
                    onSelect(getItem(pos))
                }
            }
        } catch (e: Exception) {
            Log.e("TEMPL", "bind crash at $position")
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<InternetSchedule>() {
        override fun areItemsTheSame(oldItem: InternetSchedule, newItem: InternetSchedule) = oldItem.name == newItem.name
        override fun areContentsTheSame(oldItem: InternetSchedule, newItem: InternetSchedule) = oldItem == newItem
    }
}
