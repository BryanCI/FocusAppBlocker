package com.example.appblocker.ui.statistics

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.appblocker.databinding.ItemChartBarBinding

class ChartBarAdapter : RecyclerView.Adapter<ChartBarAdapter.ViewHolder>() {

    private var items: List<ChartItem> = emptyList()
    private var maxVal: Long = 1L
    private var itemWidth: Int = 0

    data class ChartItem(
        val value: Long,
        val label: String,
        val isCurrent: Boolean,
        val isPeak: Boolean,
        val isFuture: Boolean
    )

    fun submitData(newItems: List<ChartItem>, newMaxVal: Long, width: Int = 0) {
        val oldItems = items
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldItems.size
            override fun getNewListSize(): Int = newItems.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldItems[oldItemPosition].label == newItems[newItemPosition].label
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldItems[oldItemPosition] == newItems[newItemPosition] && 
                       maxVal == newMaxVal && itemWidth == width
            }
        })
        
        items = newItems
        maxVal = if (newMaxVal > 0) newMaxVal else 1L
        itemWidth = width
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChartBarBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemChartBarBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ChartItem) {
            val context = binding.root.context
            
            val layoutParams = binding.root.layoutParams ?: ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            if (itemWidth > 0 && layoutParams.width != itemWidth) {
                layoutParams.width = itemWidth
                binding.root.layoutParams = layoutParams
            }

            val heightPercent = (item.value.toFloat() / maxVal).coerceIn(0f, 1f)
            val heightPx = if (item.value == 0L) dpToPx(4) else (heightPercent * dpToPx(100)).toInt().coerceAtLeast(dpToPx(8))
            
            val params = binding.viewBar.layoutParams
            params.height = heightPx
            binding.viewBar.layoutParams = params

            val colorStr = when {
                item.isFuture -> "#1C1C1E"
                item.isCurrent -> "#BF00FF"
                item.isPeak -> "#3B82F6"
                else -> "#2A2A2E"
            }
            
            binding.viewBar.background = GradientDrawable().apply {
                setColor(Color.parseColor(colorStr))
                if (heightPx > dpToPx(4)) {
                    val radius = dpToPx(4).toFloat()
                    cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
                } else {
                    cornerRadius = dpToPx(2).toFloat()
                }
            }
            
            binding.viewBar.alpha = if (item.isFuture) 0.4f else 1.0f
            binding.textLabel.text = item.label
            binding.textLabel.setTextColor(Color.parseColor(if (item.isCurrent) "#FFFFFF" else "#8A8A8E"))
            binding.textLabel.alpha = if (item.isFuture) 0.4f else 1.0f
            
            binding.viewDot.visibility = if (item.isCurrent) View.VISIBLE else View.INVISIBLE
        }

        private fun dpToPx(dp: Int): Int {
            return (dp * binding.root.resources.displayMetrics.density).toInt()
        }
    }
}