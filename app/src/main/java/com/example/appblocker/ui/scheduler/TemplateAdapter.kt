package com.example.appblocker.ui.scheduler

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import android.animation.ObjectAnimator
import android.view.HapticFeedbackConstants
import com.example.appblocker.databinding.ItemTemplateCardBinding

data class TemplateItem(
    val id: String,
    val title: String,
    val desc: String,
    val time: String,
    val emoji: String,
    val color: String
)

class TemplateAdapter(
    private val items: List<TemplateItem>,
    private val onClick: (TemplateItem) -> Unit
) : RecyclerView.Adapter<TemplateAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemTemplateCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTemplateCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.title.text = item.title
        holder.binding.subtitle.text = item.desc
        holder.binding.timeRange.text = item.time
        holder.binding.emoji.text = item.emoji
        
        holder.binding.root.setOnClickListener { v ->
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                // Scale animation: 100ms to 0.98x then 150ms back
                v.animate()
                    .scaleX(0.98f)
                    .scaleY(0.98f)
                    .setDuration(100L)
                    .withEndAction {
                        v.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(150L)
                            .start()
                        
                        // existing template apply logic
                        onClick(items[pos])
                    }
                    .start()

                // Play icon pulse (1.1x)
                holder.binding.playButton.animate()
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(200)
                    .withEndAction {
                        holder.binding.playButton.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(200)
                            .start()
                    }
                    .start()
            }
        }
    }

    override fun getItemCount(): Int = items.size
}