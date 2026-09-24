package com.example.appblocker.ui.profile

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appblocker.AppIconManager
import com.example.appblocker.MainViewModel
import com.example.appblocker.R
import com.example.appblocker.databinding.BottomSheetAppIconBinding
import com.example.appblocker.databinding.ItemAppIconBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AppIconBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAppIconBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetAppIconBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentIcon = AppIconManager.getCurrentIcon(requireContext())
        
        lifecycleScope.launch {
            viewModel.isPremium.collectLatest { isPremium ->
                binding.txtCurrentIndicator.text = "Current: ${currentIcon.displayName}"
                setupRecyclerView(currentIcon, isPremium)
            }
        }
    }

    private fun setupRecyclerView(currentIcon: AppIconManager.AppIcon, isPremium: Boolean) {
        binding.rvIcons.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvIcons.adapter = AppIconAdapter(AppIconManager.AppIcon.values().toList(), currentIcon, isPremium) { selectedIcon ->
            handleIconSelection(selectedIcon, currentIcon, isPremium)
        }
    }

    private fun handleIconSelection(selectedIcon: AppIconManager.AppIcon, currentIcon: AppIconManager.AppIcon, isPremium: Boolean) {
        if (selectedIcon.isPremium && !isPremium) {
            dismiss()
            try {
                androidx.navigation.fragment.NavHostFragment.findNavController(this)
                    .navigate(R.id.navigation_paywall)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Premium feature", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (selectedIcon == currentIcon) {
            Toast.makeText(requireContext(), "Already selected", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(requireContext(), R.style.LuxuryAlertDialog)
            .setTitle("Change App Icon")
            .setMessage("The app will close to apply the new icon. You can reopen it immediately.")
            .setPositiveButton("Change") { _, _ ->
                AppIconManager.setIcon(requireContext(), selectedIcon)
                Toast.makeText(requireContext(), "Icon updated ✨", Toast.LENGTH_LONG).show()
                Handler(Looper.getMainLooper()).postDelayed({
                    Process.killProcess(Process.myPid())
                }, 800)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class AppIconAdapter(
        private val icons: List<AppIconManager.AppIcon>,
        private val currentIcon: AppIconManager.AppIcon,
        private val isPremium: Boolean,
        private val onIconSelected: (AppIconManager.AppIcon) -> Unit
    ) : RecyclerView.Adapter<AppIconAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemAppIconBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemAppIconBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val icon = icons[position]
            holder.binding.txtIconName.text = icon.displayName
            holder.binding.imgIconPreview.setImageResource(icon.preview)
            holder.binding.txtIconSubtitle.text = if (icon.isPremium) "Premium" else "Free"
            holder.binding.txtIconSubtitle.setTextColor(
                if (icon.isPremium) android.graphics.Color.parseColor("#FFD700")
                else android.graphics.Color.parseColor("#6B7280")
            )

            val isSelected = icon == currentIcon
            holder.binding.imgChecked.visibility = if (isSelected) View.VISIBLE else View.GONE
            
            if (isSelected) {
                holder.binding.cardIcon.setBackgroundResource(R.drawable.bg_luxury_card_selected_purple)
                holder.binding.cardIcon.strokeWidth = 0
                holder.binding.cardIcon.scaleX = 1.02f
                holder.binding.cardIcon.scaleY = 1.02f
            } else {
                holder.binding.cardIcon.setBackgroundResource(0)
                holder.binding.cardIcon.setCardBackgroundColor(android.graphics.Color.parseColor("#161616"))
                holder.binding.cardIcon.strokeColor = android.graphics.Color.parseColor("#262626")
                holder.binding.cardIcon.strokeWidth = 2
                holder.binding.cardIcon.scaleX = 1f
                holder.binding.cardIcon.scaleY = 1f
            }

            if (icon.isPremium && !isPremium) {
                holder.binding.badgePro.visibility = View.VISIBLE
                holder.binding.scrimLocked.visibility = View.VISIBLE
                holder.binding.imgLockOverlay.visibility = View.VISIBLE
                holder.binding.imgIconPreview.alpha = 0.4f
            } else {
                holder.binding.badgePro.visibility = View.GONE
                holder.binding.scrimLocked.visibility = View.GONE
                holder.binding.imgLockOverlay.visibility = View.GONE
                holder.binding.imgIconPreview.alpha = 1.0f
            }

            holder.binding.root.setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                it.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).withEndAction {
                    it.animate().scaleX(1.02f).scaleY(1.02f).setDuration(150).start()
                }.start()
                onIconSelected(icon)
            }
        }

        override fun getItemCount() = icons.size
    }
}