package com.example.appblocker.util

import com.example.appblocker.R

object AppGroupMapper {

    fun getGroupForPackage(pkg: String, label: String = ""): String {
        val p = pkg.lowercase()
        val l = label.lowercase()
        return when {
            // Social media - pink
            p.contains("facebook") || p.contains("instagram") || p.contains("whatsapp") || p.contains("snapchat") || p.contains("twitter") || p.contains("tiktok") || p.contains("telegram") || p.contains("discord") || p.contains("reddit") -> "Social media"
            // Games - gray
            l.contains("game") || p.contains("game") || p.contains("play.games") -> "Games"
            // Entertainment - green
            p.contains("youtube") || p.contains("netflix") || p.contains("spotify") || p.contains("music") || p.contains("video") || p.contains("player") -> "Entertainment"
            // Creativity - yellow
            p.contains("camera") || p.contains("photo") || p.contains("picsart") || p.contains("canva") || p.contains("editor") || p.contains("gallery") -> "Creativity"
            // Education - purple
            p.contains("classroom") || p.contains("duolingo") || p.contains("education") || p.contains("learn") || p.contains("book") || p.contains("udemy") || p.contains("wikipedia") -> "Education"
            // Health - red
            p.contains("health") || p.contains("fit") || p.contains("fitness") || p.contains("step") || p.contains("medical") -> "Health & Fitness"
            // Productivity - blue
            p.contains("gmail") || p.contains("drive") || p.contains("docs") || p.contains("office") || p.contains("calendar") || p.contains("note") || p.contains("chrome") || p.contains("browser") -> "Productivity"
            // News - light purple
            p.contains("news") || p.contains("medium") || p.contains("times") || p.contains("cnn") || p.contains("bbc") -> "News & Books"
            // Shopping & Food - orange + blue
            p.contains("shop") || p.contains("amazon") || p.contains("uber") || p.contains("food") || p.contains("travel") || p.contains("booking") || p.contains("airbnb") || p.contains("maps") -> "Shopping & Food"
            else -> "Other"
        }
    }

    val orderedNames = listOf(
        "Social media",
        "Games",
        "Entertainment",
        "Creativity",
        "Education",
        "Health & Fitness",
        "Productivity",
        "News & Books",
        "Shopping & Food",
        "Other"
    )

    fun getIconForGroup(groupName: String): Int {
        return when (groupName) {
            "Social media", "Social" -> R.drawable.ic_group_social
            "Games" -> R.drawable.ic_group_games
            "Entertainment" -> R.drawable.ic_group_entertainment
            "Creativity" -> R.drawable.ic_group_creativity
            "Education" -> R.drawable.ic_group_education
            "Health & Fitness", "Health" -> R.drawable.ic_group_health
            "Productivity" -> R.drawable.ic_group_productivity
            "News & Books", "News" -> R.drawable.ic_group_news
            "Shopping & Food", "Shopping", "Food & Drink" -> R.drawable.ic_group_shopping
            else -> R.drawable.ic_group_other
        }
    }

    fun buildGroups(apps: List<com.example.appblocker.model.AppInfo>): List<com.example.appblocker.model.AppGroup> {
        val grouped = apps.groupBy { app ->
            getGroupForPackage(app.packageName, app.appName)
        }

        return orderedNames.map { groupName ->
            val appList = grouped[groupName] ?: emptyList()
            com.example.appblocker.model.AppGroup(
                id = groupName,
                name = groupName,
                iconRes = getIconForGroup(groupName),
                apps = appList.toMutableList()
            )
        }
    }
}
