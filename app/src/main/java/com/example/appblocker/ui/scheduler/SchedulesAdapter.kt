package com.example.appblocker.ui.scheduler

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.appblocker.databinding.ItemScheduleCardBinding
import com.example.appblocker.data.InternetSchedule
import java.util.Locale

class SchedulesAdapter(
    private val onEdit: (InternetSchedule) -> Unit,
    private val onDelete: (InternetSchedule) -> Unit
) : ListAdapter<InternetSchedule, SchedulesAdapter.VH>(DiffCallback) {

    class VH(val binding: ItemScheduleCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(ItemScheduleCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = getItem(position) ?: return
        try {
            h.binding.title.text = item.name ?: "Untitled"
            h.binding.subtitle.text = item.tagline ?: (if (item.isStrict) "Strict Mode Active" else "Standard Blocking")
            
            val timeRange = String.format(Locale.getDefault(), "%02d:%02d - %02d:%02d", 
                item.startHour, item.startMinute, item.endHour, item.endMinute)
            h.binding.timeRange.text = timeRange
            
            h.binding.emoji.text = item.iconName ?: "🧘"
            
            h.binding.editIcon.setOnClickListener { 
                val pos = h.adapterPosition
                if (pos != RecyclerView.NO_POSITION) onEdit(getItem(pos)) 
            }
            h.binding.closeIcon.setOnClickListener { 
                val pos = h.adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    h.binding.closeIcon.animate().scaleX(1.2f).scaleY(1.2f).setDuration(100).withEndAction {
                        h.binding.closeIcon.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    }.start()
                    onDelete(getItem(pos)) 
                }
            }
            h.binding.root.setOnClickListener {
                val pos = h.adapterPosition
                if (pos != RecyclerView.NO_POSITION) onEdit(getItem(pos))
            }
        } catch (e: Exception) {
            Log.e("SCHED", "bind crash at $position: ${e.message}")
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<InternetSchedule>() {
        override fun areItemsTheSame(oldItem: InternetSchedule, newItem: InternetSchedule) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: InternetSchedule, newItem: InternetSchedule) = oldItem == newItem
    }
}
