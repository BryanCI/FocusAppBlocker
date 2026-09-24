package com.example.appblocker.model

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val appName: String,
    var isChecked: Boolean,
    val icon: Drawable? = null
)

data class AppGroup(
    val id: String,
    val name: String,
    val iconRes: Int,
    val apps: MutableList<AppInfo>,
    var isExpanded: Boolean = false
) {
    fun getState(): CheckState {
        if (apps.isEmpty()) return CheckState.UNCHECKED
        if (apps.all { it.isChecked }) return CheckState.CHECKED
        if (apps.none { it.isChecked }) return CheckState.UNCHECKED
        return CheckState.PARTIAL
    }
}

enum class CheckState { CHECKED, UNCHECKED, PARTIAL }
