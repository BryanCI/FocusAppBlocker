package com.example.appblocker.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.appblocker.R
import com.example.appblocker.model.AppGroup
import com.example.appblocker.model.AppInfo
import com.example.appblocker.model.CheckState

class HybridAppsAdapter(
    private var groups: List<AppGroup>,
    private val onAppToggled: (Any) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    enum class Mode { GROUPS, AZ, SELECTED, SEARCH }

    private var currentMode = Mode.GROUPS
    private var flatList = mutableListOf<Any>()
    private var allApps = mutableListOf<AppInfo>()
    private var searchQuery = ""

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_APP = 1
        private const val TYPE_APP_FLAT = 2
    }

    init {
        updateAllApps()
        rebuildFlatList()
    }

    fun setGroups(newGroups: List<AppGroup>) {
        android.util.Log.d("HybridAppsAdapter", "setGroups: ${newGroups.size}")
        if (newGroups.isEmpty() && allApps.isNotEmpty()) {
            val otherGroup = AppGroup("other", "Other", R.drawable.ic_shield, allApps.toMutableList())
            this.groups = listOf(otherGroup)
        } else {
            this.groups = newGroups
        }
        updateAllApps()
        rebuildFlatList()
    }

    fun getGroups(): List<AppGroup> = groups

    fun getAllApps(): List<AppInfo> = allApps

    fun setMode(mode: Mode) {
        currentMode = mode
        rebuildFlatList()
    }

    fun search(query: String) {
        searchQuery = query
        currentMode = if (query.isEmpty()) Mode.GROUPS else Mode.SEARCH
        rebuildFlatList()
    }

    private fun updateAllApps() {
        allApps.clear()
        groups.forEach { allApps.addAll(it.apps) }
    }

    private fun rebuildFlatList() {
        flatList.clear()
        when (currentMode) {
            Mode.GROUPS -> {
                groups.forEach { group ->
                    flatList.add(group)
                    if (group.isExpanded) {
                        flatList.addAll(group.apps)
                    }
                }
            }
            Mode.AZ -> {
                flatList.addAll(allApps.sortedBy { it.appName.lowercase() })
            }
            Mode.SELECTED -> {
                flatList.addAll(allApps.filter { it.isChecked })
            }
            Mode.SEARCH -> {
                flatList.addAll(allApps.filter { it.appName.contains(searchQuery, true) })
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = flatList[position]) {
            is AppGroup -> TYPE_HEADER
            is AppInfo -> if (currentMode == Mode.GROUPS) TYPE_APP else TYPE_APP_FLAT
            else -> throw IllegalArgumentException("Unknown type")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.item_group_header, parent, false))
            TYPE_APP -> AppViewHolder(inflater.inflate(R.layout.item_app_child, parent, false))
            TYPE_APP_FLAT -> AppViewHolder(inflater.inflate(R.layout.item_app_flat, parent, false))
            else -> throw IllegalArgumentException("Unknown type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = flatList[position]) {
            is AppGroup -> (holder as HeaderViewHolder).bind(item)
            is AppInfo -> (holder as AppViewHolder).bind(item)
        }
    }

    override fun getItemCount(): Int = flatList.size

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivArrow = view.findViewById<ImageView>(R.id.ivArrow)
        private val ivGroupIcon = view.findViewById<ImageView>(R.id.ivGroupIcon)
        private val tvGroupName = view.findViewById<TextView>(R.id.tvGroupName)
        private val cbGroup = view.findViewById<CheckBox>(R.id.cbGroup)

        fun bind(group: AppGroup) {
            tvGroupName?.text = group.name
            try {
                ivGroupIcon?.setImageResource(group.iconRes)
            } catch (e: Exception) {
                ivGroupIcon?.setImageResource(R.drawable.ic_shield)
            }
            ivArrow?.rotation = if (group.isExpanded) 180f else 0f

            val state = group.getState()
            cbGroup?.isChecked = state == CheckState.CHECKED

            itemView.setOnClickListener {
                group.isExpanded = !group.isExpanded
                rebuildFlatList()
            }

            cbGroup?.setOnClickListener {
                val newState = !group.apps.all { it.isChecked }
                group.apps.forEach { it.isChecked = newState }
                notifyDataSetChanged()
                onAppToggled(group)
            }
        }
    }

    inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivAppIcon = view.findViewById<ImageView>(R.id.ivAppIcon)
        private val tvAppName = view.findViewById<TextView>(R.id.tvAppName)
        private val cbApp = view.findViewById<CheckBox>(R.id.cbApp)

        fun bind(app: AppInfo) {
            tvAppName?.text = app.appName
            cbApp?.isChecked = app.isChecked

            ivAppIcon?.setImageResource(R.drawable.ic_shield) // Placeholder
            val context = itemView.context
            val pkgName = app.packageName
            val currentIcon = app.icon
            if (currentIcon != null) {
                ivAppIcon?.setImageDrawable(currentIcon)
            } else {
                itemView.post {
                    try {
                        val icon = context.packageManager.getApplicationIcon(pkgName)
                        ivAppIcon?.setImageDrawable(icon)
                    } catch (e: Exception) {
                        ivAppIcon?.setImageResource(R.drawable.ic_shield)
                    }
                }
            }

            cbApp?.setOnClickListener {
                app.isChecked = cbApp.isChecked
                notifyDataSetChanged()
                onAppToggled(app)
            }

            itemView.setOnClickListener {
                app.isChecked = !app.isChecked
                cbApp?.isChecked = app.isChecked
                notifyDataSetChanged() // To update header state if in GROUPS mode
                onAppToggled(app)
            }
        }
    }
}
