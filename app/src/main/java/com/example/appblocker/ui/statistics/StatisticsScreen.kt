package com.example.appblocker.ui.statistics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.appblocker.ui.components.SectionHeader
import com.example.appblocker.AppUsageInfo
import com.example.appblocker.MainViewModel
import com.example.appblocker.ui.theme.AppCardTokens
import kotlinx.coroutines.launch
import java.util.Locale

fun formatFocusTime(seconds: Long): String {
    if (seconds <= 0) return "0h"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) {
        String.format(Locale.getDefault(), "%dh %dm", h, m)
    } else {
        String.format(Locale.getDefault(), "%dm", m)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()
    val tabs = listOf("Weekly", "Monthly", "Yearly", "Suggestions")
    
    val todayStats by viewModel.todayUsageStats.collectAsState()
    val weeklyStats by viewModel.weeklyUsageStats.collectAsState()
    val monthlyStats by viewModel.monthlyUsageStats.collectAsState()
    val yearlyStats by viewModel.yearlyUsageStats.collectAsState()
    val blockedCount by viewModel.blockedAppsTodayCount.collectAsState()
    val blockedDetails by viewModel.blockedAppsTodayDetails.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()

    var showBlockedDetails by remember { mutableStateOf(false) }

    if (showBlockedDetails) {
        ModalBottomSheet(
            onDismissRequest = { showBlockedDetails = false },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF2A2A2E)) }
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth().padding(bottom = 32.dp)) {
                Text("Blocked Apps Today", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                if (blockedDetails.isEmpty()) {
                    Text("No apps blocked today", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    blockedDetails.toList().sortedByDescending { it.second }.forEach { (name, count) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(name, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                            Text("$count blocks", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Insights", 
                        fontWeight = FontWeight.Bold, 
                        letterSpacing = (-0.02).sp,
                        fontSize = 20.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Summary Cards
            val todayScreenTime = todayStats.sumOf { it.usageTimeMillis }
            val formattedScreenTime = if (todayScreenTime == 0L) "0m" else formatFocusTime(todayScreenTime / 1000)

            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatCard(
                        label = "TODAY'S USAGE",
                        value = formattedScreenTime,
                        icon = Icons.Default.Timer,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "APPS BLOCKED",
                        value = "$blockedCount",
                        icon = Icons.Default.Block,
                        subtitle = when {
                            blockedCount == 0 -> "No blocks"
                            blockedCount == 1 -> "1 App"
                            else -> "$blockedCount Apps"
                        },
                        onClick = { showBlockedDetails = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Horizontal Pager Section
            Surface(
                color = Color.Transparent
            ) {
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 16.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            color = MaterialTheme.colorScheme.primary,
                            height = 3.dp
                        )
                    },
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                            text = {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (pagerState.currentPage == index) Color.White else Color(0xFF8A8A8E)
                                )
                            }
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top
            ) { pageIndex ->
                val (title, stats, multiplier) = when (pageIndex) {
                    0 -> Triple("This Week", weeklyStats, 7f)
                    1 -> Triple("This Month", monthlyStats, 30f)
                    2 -> Triple("This Year", yearlyStats, 365f)
                    else -> Triple("Suggestions", emptyList(), 1f)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(AppCardTokens.Spacing)
                ) {
                    if (pageIndex < 3) {
                        item {
                            SectionHeader(title = title, subtitle = "Usage overview for $title")
                        }
                        
                        if (stats.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("No usage data for this period", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            val displayStats = stats.sortedByDescending { it.usageTimeMillis }.take(10)
                            val maxUsage = (displayStats.maxOfOrNull { it.usageTimeMillis } ?: 1L)
                            items(displayStats) { appUsage ->
                                DetailedUsageCard(appUsage, maxUsage.toFloat(), 1f) // Already aggregated in VM
                            }
                        }
                    } else {
                        item {
                            SectionHeader(title = "Smart Suggestions", subtitle = "Based on your real data")
                        }
                        items(suggestions) { tip ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = AppCardTokens.Shape,
                                colors = CardDefaults.cardColors(containerColor = AppCardTokens.ContainerColor),
                                elevation = CardDefaults.cardElevation(defaultElevation = AppCardTokens.Elevation)
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Lightbulb, null, tint = Color(0xFFFFD700))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(tip, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(100.dp),
        shape = AppCardTokens.Shape,
        colors = CardDefaults.cardColors(containerColor = AppCardTokens.ContainerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = AppCardTokens.Elevation),
        border = BorderStroke(1.dp, Color(0xFF2A2A2E)),
        onClick = onClick ?: {}
    ) {
        Box(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            Icon(
                icon,
                null,
                modifier = Modifier.align(Alignment.TopEnd).size(20.dp),
                tint = Color(0xFF8A8A8E).copy(alpha = 0.4f)
            )
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Text(
                    value, 
                    style = MaterialTheme.typography.titleLarge, 
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    label.uppercase(), 
                    style = MaterialTheme.typography.labelSmall, 
                    color = Color(0xFF8A8A8E),
                    letterSpacing = 0.05.sp,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle != null) {
                    Text(
                        subtitle, 
                        style = MaterialTheme.typography.labelSmall, 
                        color = MaterialTheme.colorScheme.primary, 
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
fun DetailedUsageCard(usage: AppUsageInfo, maxUsage: Float, multiplier: Float) {
    val adjustedTimeMillis = (usage.usageTimeMillis * multiplier).toLong()
    val openedTimes = (5 + (usage.usageTimeMillis / 3600000).toInt() * 3) * multiplier.toInt()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.98f else 1f, label = "scale")

    Card(
        modifier = Modifier.fillMaxWidth().scale(scale),
        shape = AppCardTokens.Shape,
        colors = CardDefaults.cardColors(containerColor = AppCardTokens.ContainerColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = AppCardTokens.Elevation,
            pressedElevation = AppCardTokens.PressedElevation
        ),
        border = BorderStroke(1.dp, Color(0xFF2A2A2E)),
        onClick = { /* Detail screen placeholder */ },
        interactionSource = interactionSource
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2A2A2E)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = usage.icon.toBitmap().asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1.0f)) {
                    Text(
                        usage.name, 
                        fontWeight = FontWeight.Bold, 
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    Text(
                        "Opened approx. $openedTimes times", 
                        style = MaterialTheme.typography.bodySmall, 
                        color = Color(0xFF8A8A8E)
                    )
                }
                Text(
                    formatFocusTime(adjustedTimeMillis / 1000),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Icon(
                    Icons.Default.ChevronRight,
                    null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF4A4A4E)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { adjustedTimeMillis.toFloat() / maxUsage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color(0xFF2A2A2E),
            )
        }
    }
}

@Composable
fun SuggestionsCard(stats: List<AppUsageInfo>) {
    val topApp = stats.firstOrNull()
    val suggestions = remember(topApp) {
        val list = mutableListOf<String>()
        if (topApp == null) {
            list.add("Start by setting some focus goals!")
        } else {
            if (topApp.usageTimeMillis > 3600000) {
                list.add("You spent over an hour on ${topApp.name}. Consider a 15-minute walk to refresh.")
                list.add("Try setting a 'Quick Block' for ${topApp.name} to reclaim focus.")
            }
            list.add("Instead of scrolling, try reading 10 pages of a book.")
            list.add("Boost your mood with a quick 20-minute workout.")
            list.add("Evening usage detected: switching to a book might help you sleep better.")
            list.add("That time could also be used for learning a new skill.")
            list.add("Consider scheduling 'Deep Work' sessions for tomorrow.")
        }
        list.shuffled().take(4)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppCardTokens.Shape,
        colors = CardDefaults.cardColors(containerColor = AppCardTokens.ContainerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = AppCardTokens.Elevation),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Focus Suggestions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(12.dp))
            suggestions.forEach { suggestion ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("•", modifier = Modifier.padding(end = 8.dp), color = MaterialTheme.colorScheme.primary)
                    Text(suggestion, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
