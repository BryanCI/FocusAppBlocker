package com.example.appblocker.ui.scheduler

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.appblocker.AppInfo
import com.example.appblocker.data.InternetSchedule
import com.example.appblocker.data.Template
import com.example.appblocker.ui.components.SectionHeader
import java.util.Locale
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset

val PremiumPurple = Color(0xFFBF00FF)
val PremiumBackground = Color(0xFF0A0A0A)
val PremiumCard = Color(0xFF161616)
val PremiumSurface = Color(0xFF1E1E1E)
val PremiumBorder = Color(0xFF262626)
val PremiumTextPrimary = Color.White
val PremiumTextSecondary = Color(0xFF8E8E93)

val TimelineColors = listOf(
    Color(0xFFBF00FF), // Purple
    Color(0xFF007AFF), // Blue
    Color(0xFFFF9500), // Orange
    Color(0xFF34C759), // Green
    Color(0xFFFF2D55)  // Pink
)

@Composable
fun Timeline24hView(
    schedules: List<InternetSchedule>,
    modifier: Modifier = Modifier,
    showBackground: Boolean = true
) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    LaunchedEffect(Unit) {
        while(true) {
            currentTime = System.currentTimeMillis()
            delay(30000)
        }
    }

    val now = Calendar.getInstance().apply { timeInMillis = currentTime }
    val currentMinuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
    val dayMask = when(dayOfWeek) {
        Calendar.SUNDAY -> 1
        Calendar.MONDAY -> 2
        Calendar.TUESDAY -> 4
        Calendar.WEDNESDAY -> 8
        Calendar.THURSDAY -> 16
        Calendar.FRIDAY -> 32
        Calendar.SATURDAY -> 64
        else -> 0
    }

    val activeSchedules = schedules.filter { it.isEnabled && (it.days and dayMask) != 0 }
    val activeCount = activeSchedules.size

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (showBackground) {
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(PremiumCard)
                        .border(1.dp, PremiumBorder, RoundedCornerShape(24.dp))
                        .padding(16.dp)
                } else Modifier
            )
    ) {
        if (showBackground) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today",
                    style = MaterialTheme.typography.titleMedium,
                    color = PremiumTextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (activeCount > 0) Color(0xFF34C759) else Color(0xFFFFCC00))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (activeCount > 0) "$activeCount Active" else "No protection today",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF6B7280),
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackHeight = size.height
                val cornerRadius = trackHeight / 2
                
                if (activeCount == 0) {
                    drawRoundRect(
                        color = PremiumSurface.copy(alpha = pulseAlpha),
                        size = size,
                        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                    )
                    drawRoundRect(
                        color = Color(0xFF3A3A3A),
                        size = size,
                        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 4.dp.toPx()), 0f)
                        )
                    )
                } else {
                    drawRoundRect(
                        color = PremiumSurface,
                        size = size,
                        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                    )
                    
                    activeSchedules.forEachIndexed { index, schedule ->
                        val start = (schedule.startHour * 60 + schedule.startMinute).toFloat()
                        var end = (schedule.endHour * 60 + schedule.endMinute).toFloat()
                        if (end < start) end += 1440f

                        val left = (start / 1440f) * size.width
                        val width = ((end - start) / 1440f) * size.width
                        
                        val colorIdx = index % TimelineColors.size
                        val segmentColor = TimelineColors[colorIdx]

                        // Draw segment with glow effect
                        drawRoundRect(
                            color = segmentColor.copy(alpha = 0.3f),
                            topLeft = Offset(left - 2.dp.toPx(), -2.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(width.coerceAtLeast(trackHeight) + 4.dp.toPx(), trackHeight + 4.dp.toPx()),
                            cornerRadius = CornerRadius(cornerRadius + 2.dp.toPx(), cornerRadius + 2.dp.toPx())
                        )
                        drawRoundRect(
                            color = segmentColor,
                            topLeft = Offset(left, 0f),
                            size = androidx.compose.ui.geometry.Size(width.coerceAtLeast(trackHeight), trackHeight),
                            cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                        )
                    }
                }
                
                // Now Indicator - Only when active
                if (activeCount > 0) {
                    val nowX = (currentMinuteOfDay.toFloat() / 1440f) * size.width
                    drawLine(
                        color = Color.White,
                        start = Offset(nowX, -4.dp.toPx()),
                        end = Offset(nowX, trackHeight + 4.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = Offset(nowX, -4.dp.toPx())
                    )
                }
            }
        }
        
        // Empty state text below track
        if (activeCount == 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🌙", fontSize = 10.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "No active periods",
                    color = Color(0xFF6B7280),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val markers = listOf("00:00", "06:00", "12:00", "18:00", "00:00")
            markers.forEach { time ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(4.dp)
                            .background(Color(0xFF4A4A4A))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4A4A4A),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun ProtectionScorePill(score: Int, hasActive: Boolean) {
    val bgColor = if (hasActive) Color(0xFF0F1F1A) else Color(0xFF2A2210)
    val borderColor = if (hasActive) Color(0xFF1A3A2A) else Color(0xFF3A2F0B)
    val contentColor = if (hasActive) Color(0xFF10B981) else Color(0xFFEAB308)
    
    Surface(
        color = bgColor,
        shape = CircleShape,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(contentColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Score: $score",
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun PremiumHeroDashboard(
    schedules: List<InternetSchedule>,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sdf = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
    val currentDate = sdf.format(Date())
    
    val now = Calendar.getInstance()
    val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
    val dayMask = when(dayOfWeek) {
        Calendar.SUNDAY -> 1
        Calendar.MONDAY -> 2
        Calendar.TUESDAY -> 4
        Calendar.WEDNESDAY -> 8
        Calendar.THURSDAY -> 16
        Calendar.FRIDAY -> 32
        Calendar.SATURDAY -> 64
        else -> 0
    }

    val enabledSchedules = schedules.filter { it.isEnabled && (it.days and dayMask) != 0 }
    val totalProtectedMinutes = enabledSchedules.sumOf { schedule ->
        val start = schedule.startHour * 60 + schedule.startMinute
        var end = schedule.endHour * 60 + schedule.endMinute
        if (end < start) end += 1440
        (end - start).toLong()
    }
    
    val displayScore = (totalProtectedMinutes.toDouble() / 1440.0 * 100).toInt().coerceAtMost(100)
    val hasActive = enabledSchedules.isNotEmpty()
    val activeSchedules = schedules.filter { it.isEnabled }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(PremiumCard)
            .border(1.dp, PremiumBorder, RoundedCornerShape(32.dp))
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(PremiumPurple.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width * 0.8f, 0f),
                    radius = size.width
                )
            )
        }

        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = currentDate.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6B7280),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Focus Shield",
                        style = MaterialTheme.typography.headlineSmall,
                        color = PremiumTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        letterSpacing = (-0.5).sp
                    )
                }
                ProtectionScorePill(score = displayScore, hasActive = hasActive)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Timeline24hView(schedules = schedules, showBackground = false)

            if (schedules.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PremiumSurface)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(PremiumPurple.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = PremiumPurple, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "System Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = PremiumTextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${activeSchedules.size} Rules Running",
                                style = MaterialTheme.typography.bodySmall,
                                color = PremiumTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Surface(
                        onClick = onAddClick,
                        color = PremiumPurple,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "+ NEW",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MiniStatItem(
                        icon = Icons.AutoMirrored.Filled.TrendingDown,
                        label = "Distractions",
                        value = "-24%",
                        color = Color(0xFF34C759),
                        modifier = Modifier.weight(1f)
                    )
                    MiniStatItem(
                        icon = Icons.Default.Timer,
                        label = "Focus Time",
                        value = "4.2h",
                        color = PremiumPurple,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun MiniStatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(PremiumSurface)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(label, fontSize = 9.sp, color = PremiumTextSecondary, fontWeight = FontWeight.Medium)
            Text(value, fontSize = 12.sp, color = PremiumTextPrimary, fontWeight = FontWeight.Bold)
        }
    }
}





@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedTimePickerDialog(
    title: String = "Select Time",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    toggle: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .height(IntrinsicSize.Min),
            color = PremiumCard
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = PremiumPurple,
                    fontWeight = FontWeight.Bold
                )
                content()
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .fillMaxWidth()
                ) {
                    toggle()
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel", color = PremiumTextSecondary) }
                    TextButton(onClick = onConfirm) { Text("OK", color = PremiumPurple, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SchedulerScreen(
    schedules: List<InternetSchedule>,
    templates: List<Template>,
    isStrictActive: Boolean,
    allApps: List<AppInfo>,
    onSaveSchedule: (InternetSchedule) -> Unit,
    onToggleSchedule: (InternetSchedule, Boolean) -> Unit,
    onDeleteSchedule: (InternetSchedule) -> Unit,
    onTemplateSelect: (Template) -> Unit,
    onUpdateTemplate: (Template) -> Unit,
    onDeleteTemplate: (Template) -> Unit,
    onRestoreDefaults: () -> Unit,
    onPauseSchedule: (Int, Long) -> Unit,
    onResumeSchedule: (Int) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var scheduleToEdit by remember { mutableStateOf<InternetSchedule?>(null) }
    var templateToEdit by remember { mutableStateOf<Template?>(null) }
    var showDeleteTemplateConfirm by remember { mutableStateOf<Template?>(null) }
    var scheduleToPause by remember { mutableStateOf<InternetSchedule?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = PremiumBackground,
        floatingActionButton = {
            if (schedules.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = PremiumPurple,
                    contentColor = Color.White,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Schedule", modifier = Modifier.size(24.dp))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp, start = 16.dp, end = 16.dp, top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                PremiumHeroDashboard(
                    schedules = schedules,
                    onAddClick = { showCreateDialog = true }
                )
            }

            item {
                val haptic = LocalHapticFeedback.current
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader(
                        title = "My Schedules",
                        subtitle = "Rules for focused internet use"
                    )
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showCreateDialog = true
                        },
                        color = PremiumPurple.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, PremiumPurple.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = PremiumPurple, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("NEW", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = PremiumPurple)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (schedules.isEmpty()) {
                item {
                    val animatedAlpha = remember { Animatable(0f) }
                    LaunchedEffect(Unit) {
                        animatedAlpha.animateTo(1f, tween(600))
                    }
                    Box(modifier = Modifier.graphicsLayer { alpha = animatedAlpha.value }) {
                        EmptySchedulesCard(onClick = { showCreateDialog = true })
                    }
                }
            } else {
                schedules.forEachIndexed { index, schedule ->
                    item(key = schedule.id) {
                        val animatedAlpha = remember { Animatable(0f) }
                        val animatedSlide = remember { Animatable(50f) }
                        
                        LaunchedEffect(Unit) {
                            delay(index * 50L)
                            launch { animatedAlpha.animateTo(1f, tween(400)) }
                            launch { animatedSlide.animateTo(0f, tween(400, easing = LinearOutSlowInEasing)) }
                        }

                        Box(modifier = Modifier
                            .graphicsLayer {
                                alpha = animatedAlpha.value
                                translationY = animatedSlide.value
                            }
                        ) {
                            ScheduleCard(
                                schedule = schedule,
                                isStrictActive = isStrictActive,
                                allApps = allApps,
                                onEdit = { scheduleToEdit = it },
                                onToggle = { enabled -> onToggleSchedule(schedule, enabled) },
                                onLongPress = { scheduleToPause = schedule },
                                onResume = { onResumeSchedule(schedule.id) }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader(
                        title = "Templates", 
                        subtitle = "Quick start with proven routines"
                    )
                    IconButton(onClick = {
                        templateToEdit = Template(
                            name = "",
                            subtitle = "",
                            icon = "📚",
                            timeLabel = "",
                            startHour = 9,
                            startMin = 0,
                            endHour = 17,
                            endMin = 0,
                            daysMask = 127,
                            defaultApps = "",
                            isCustom = true,
                            sortOrder = templates.size + 1
                        )
                    }) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Create Template", tint = PremiumPurple)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (templates.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No templates", style = MaterialTheme.typography.bodyMedium, color = PremiumTextSecondary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onRestoreDefaults,
                            colors = ButtonDefaults.buttonColors(containerColor = PremiumPurple),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Restore default templates", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                templates.forEachIndexed { index, template ->
                    item(key = template.id) {
                        val animatedAlpha = remember { Animatable(0f) }
                        val animatedSlide = remember { Animatable(50f) }
                        
                        LaunchedEffect(Unit) {
                            delay((schedules.size + index) * 50L)
                            launch { animatedAlpha.animateTo(1f, tween(400)) }
                            launch { animatedSlide.animateTo(0f, tween(400, easing = LinearOutSlowInEasing)) }
                        }

                        Box(modifier = Modifier
                            .graphicsLayer {
                                alpha = animatedAlpha.value
                                translationY = animatedSlide.value
                            }
                        ) {
                            TemplateCard(
                                template = template,
                                allApps = allApps,
                                onUseClick = { onTemplateSelect(template) },
                                onEditClick = { templateToEdit = template },
                                onDeleteClick = { showDeleteTemplateConfirm = template }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        InternetSchedulerDialog(
            schedule = InternetSchedule(startHour = 9, startMinute = 0, endHour = 17, endMinute = 0),
            allApps = allApps,
            allSchedules = schedules,
            onDismiss = { showCreateDialog = false },
            onSave = {
                onSaveSchedule(it)
                showCreateDialog = false
            }
        )
    }

    scheduleToEdit?.let { schedule ->
        InternetSchedulerDialog(
            schedule = schedule,
            allApps = allApps,
            allSchedules = schedules,
            onDismiss = { scheduleToEdit = null },
            onSave = {
                onToggleSchedule(it, it.isEnabled) // Using toggle to update since it's already implemented in VM
                scheduleToEdit = null
            }
        )
    }

    templateToEdit?.let { template ->
        EditTemplateBottomSheet(
            template = template,
            allApps = allApps,
            onDismiss = { templateToEdit = null },
            onSave = {
                if (it.id == 0) {
                    onUpdateTemplate(it.copy(isCustom = true))
                } else {
                    onUpdateTemplate(it)
                }
                templateToEdit = null
            }
        )
    }

    showDeleteTemplateConfirm?.let { template ->
        AlertDialog(
            onDismissRequest = { showDeleteTemplateConfirm = null },
            title = { Text("Delete Template?") },
            text = { Text("Delete '${template.name}'? You can restore defaults later.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTemplate(template)
                        showDeleteTemplateConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteTemplateConfirm = null }) {
                    Text("Cancel", color = PremiumTextSecondary)
                }
            }
        )
    }
    
    if (scheduleToPause != null) {
        PauseScheduleBottomSheet(
            schedule = scheduleToPause!!,
            onDismiss = { scheduleToPause = null },
            onPause = { durationMillis ->
                onPauseSchedule(scheduleToPause!!.id, durationMillis)
                scheduleToPause = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScheduleCard(
    schedule: InternetSchedule,
    isStrictActive: Boolean,
    allApps: List<AppInfo>,
    onEdit: (InternetSchedule) -> Unit,
    onToggle: (Boolean) -> Unit,
    onLongPress: () -> Unit,
    onResume: () -> Unit
) {
    val isActive = schedule.isActive()
    val isPaused = schedule.pauseUntil > System.currentTimeMillis()
    val isLocked = isStrictActive && isActive && !isPaused
    val canModify = !isLocked
    val haptic = LocalHapticFeedback.current

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.98f else 1f, label = "scale")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .combinedClickable(
                onClick = { if (canModify) onEdit(schedule) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                },
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = PremiumCard),
        border = BorderStroke(1.dp, PremiumBorder)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Left accent border
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(
                        if (schedule.isEnabled && !isPaused) PremiumPurple else Color(0xFF3A3A3C)
                    )
            )

            Column(modifier = Modifier.padding(16.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = if (isActive && !isPaused) PremiumPurple.copy(alpha = 0.1f) else Color(0xFF1C1C1E)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isPaused) Icons.Default.PauseCircle else Icons.Default.Timer,
                                contentDescription = null,
                                tint = if (isActive && !isPaused) PremiumPurple else PremiumTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = schedule.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PremiumTextPrimary,
                            fontSize = 16.sp
                        )
                        Text(
                            text = if (isPaused) {
                                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(schedule.pauseUntil))
                                "Paused until $time"
                            } else getDaysLabel(schedule.days),
                            style = MaterialTheme.typography.labelSmall,
                            color = PremiumTextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    if (isPaused) {
                        TextButton(onClick = onResume) {
                            Text("RESUME", fontWeight = FontWeight.Bold, color = PremiumPurple, fontSize = 12.sp)
                        }
                    } else {
                        Switch(
                            checked = schedule.isEnabled,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggle(it)
                            },
                            enabled = canModify,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PremiumPurple,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFF3A3A3C),
                                uncheckedBorderColor = Color.Transparent
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = PremiumTextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d - %02d:%02d", 
                            schedule.startHour, schedule.startMinute, schedule.endHour, schedule.endMinute),
                        style = MaterialTheme.typography.labelMedium,
                        color = PremiumTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    if (isActive && !isPaused) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = Color(0xFF34C759).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "ACTIVE",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF34C759),
                                fontWeight = FontWeight.Black,
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val appPackages = schedule.apps.split(",").filter { it.isNotBlank() }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.CenterStart) {
                        appPackages.take(5).forEachIndexed { index, pkg ->
                            val app = allApps.find { it.packageName == pkg }
                            val icon = app?.icon
                            if (icon != null) {
                                Image(
                                    bitmap = icon.toBitmap().asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(start = (index * 18).dp)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, PremiumCard, CircleShape)
                                )
                            }
                        }
                        if (appPackages.size > 5) {
                            Surface(
                                modifier = Modifier.padding(start = (5 * 18).dp),
                                shape = CircleShape,
                                color = PremiumSurface,
                                border = BorderStroke(2.dp, PremiumCard)
                            ) {
                                Text(
                                    "+${appPackages.size - 5}",
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PremiumTextSecondary
                                )
                            }
                        }
                    }
                    
                    if (schedule.streakDays > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔥", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "${schedule.streakDays}d Streak",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFF9500),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PauseScheduleBottomSheet(
    schedule: InternetSchedule,
    onDismiss: () -> Unit,
    onPause: (Long) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PremiumCard,
        contentColor = PremiumTextPrimary
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            Text("Pause \"${schedule.name}\"", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Temporary bypass strict rules", style = MaterialTheme.typography.bodyMedium, color = PremiumTextSecondary)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            val options = listOf(
                "1 Hour" to 3600000L,
                "Until Midnight" to (getMillisUntilMidnight()),
                "For 1 Week" to 7 * 24 * 3600000L
            )
            
            options.forEach { (label, duration) ->
                Button(
                    onClick = { onPause(duration) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(label)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = PremiumTextSecondary)
            }
        }
    }
}

fun getMillisUntilMidnight(): Long {
    val now = Calendar.getInstance()
    val midnight = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 24)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return midnight.timeInMillis - now.timeInMillis
}

@Composable
fun TemplateCard(
    template: Template,
    allApps: List<AppInfo>,
    onUseClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.98f else 1f, label = "scale")
    
    val accentColor = remember(template) {
        when {
            template.name.contains("Work", true) || template.icon == "💼" -> Color(0xFFBF00FF)
            template.name.contains("Study", true) || template.icon == "📚" -> Color(0xFF3B82F6)
            template.name.contains("Focus", true) || template.icon == "🎯" -> Color(0xFFF59E0B)
            template.name.contains("Zen", true) || template.icon == "🧘" -> Color(0xFF10B981)
            template.name.contains("Morning", true) || template.icon == "🌅" -> Color(0xFFEC4899)
            else -> PremiumPurple
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onUseClick()
                }
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PremiumCard),
        border = BorderStroke(1.dp, PremiumBorder)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Left accent bar
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(accentColor)
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon circle
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(template.icon, fontSize = 24.sp)
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = template.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PremiumTextPrimary,
                        fontSize = 15.sp
                    )
                    Text(
                        text = template.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8A8A8E),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = PremiumPurple, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = template.timeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF6B7280),
                            fontSize = 11.sp
                        )
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        val appPackages = template.defaultApps.split(",").filter { it.isNotBlank() }
                        if (appPackages.isNotEmpty()) {
                            Box(modifier = Modifier.height(16.dp), contentAlignment = Alignment.CenterStart) {
                                appPackages.take(3).forEachIndexed { index, pkg ->
                                    val app = allApps.find { it.packageName == pkg }
                                    val icon = app?.icon
                                    if (icon != null) {
                                        Image(
                                            bitmap = icon.toBitmap().asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .padding(start = (index * 12).dp)
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .border(1.dp, PremiumCard, CircleShape)
                                        )
                                    }
                                }
                            }
                        } else {
                            Surface(
                                color = Color(0xFF2A2A2A),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "All apps",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    fontSize = 9.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = Color(0xFF6B7280),
                        modifier = Modifier.size(18.dp).clickable { onEditClick() }
                    )
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp).clickable { onDeleteClick() }
                    )
                }
                
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF4A4A4A),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}



fun getDaysLabel(mask: Int): String {
    return when (mask) {
        127 -> "Daily"
        62 -> "Mon-Fri"
        65 -> "Weekends"
        else -> "Custom"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTemplateBottomSheet(
    template: Template,
    allApps: List<AppInfo>,
    onDismiss: () -> Unit,
    onSave: (Template) -> Unit
) {
    var name by remember { mutableStateOf(template.name) }
    var subtitle by remember { mutableStateOf(template.subtitle) }
    var selectedIcon by remember { mutableStateOf(template.icon) }
    var startHour by remember { mutableIntStateOf(template.startHour) }
    var startMin by remember { mutableIntStateOf(template.startMin) }
    var endHour by remember { mutableIntStateOf(template.endHour) }
    var endMin by remember { mutableIntStateOf(template.endMin) }
    var daysMask by remember { mutableIntStateOf(template.daysMask) }
    var selectedApps by remember { mutableStateOf(template.defaultApps.split(",").filter { it.isNotBlank() }.toSet()) }

    var showAppSelector by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val icons = listOf("📚","🎯","💼","🌙","🏠","🧘","🌅","🚫","🌴","⏱️","💡","🎧")

    if (showStartTimePicker) {
        val startTimeState = rememberTimePickerState(initialHour = startHour, initialMinute = startMin, is24Hour = true)
        AdvancedTimePickerDialog(
            onDismiss = { showStartTimePicker = false },
            onConfirm = {
                startHour = startTimeState.hour
                startMin = startTimeState.minute
                showStartTimePicker = false
            }
        ) {
            TimePicker(
                state = startTimeState,
                colors = TimePickerDefaults.colors(
                    clockDialColor = Color.DarkGray.copy(alpha = 0.3f),
                    selectorColor = PremiumPurple,
                    containerColor = PremiumCard,
                    periodSelectorSelectedContainerColor = PremiumPurple.copy(alpha = 0.2f),
                    periodSelectorSelectedContentColor = PremiumPurple,
                    timeSelectorSelectedContainerColor = PremiumPurple.copy(alpha = 0.2f),
                    timeSelectorSelectedContentColor = PremiumPurple
                )
            )
        }
    }

    if (showEndTimePicker) {
        val endTimeState = rememberTimePickerState(initialHour = endHour, initialMinute = endMin, is24Hour = true)
        AdvancedTimePickerDialog(
            onDismiss = { showEndTimePicker = false },
            onConfirm = {
                endHour = endTimeState.hour
                endMin = endTimeState.minute
                showEndTimePicker = false
            }
        ) {
            TimePicker(
                state = endTimeState,
                colors = TimePickerDefaults.colors(
                    clockDialColor = Color.DarkGray.copy(alpha = 0.3f),
                    selectorColor = PremiumPurple,
                    containerColor = PremiumCard,
                    periodSelectorSelectedContainerColor = PremiumPurple.copy(alpha = 0.2f),
                    periodSelectorSelectedContentColor = PremiumPurple,
                    timeSelectorSelectedContainerColor = PremiumPurple.copy(alpha = 0.2f),
                    timeSelectorSelectedContentColor = PremiumPurple
                )
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PremiumCard,
        contentColor = PremiumTextPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.DarkGray) }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                if (template.id == 0) "Create Template" else "Edit Template",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = PremiumPurple
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Template Name", style = MaterialTheme.typography.labelMedium, color = PremiumTextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = PremiumTextPrimary,
                    unfocusedTextColor = PremiumTextPrimary,
                    focusedBorderColor = PremiumPurple,
                    unfocusedBorderColor = Color.DarkGray,
                    cursorColor = PremiumPurple
                )
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text("Subtitle", style = MaterialTheme.typography.labelMedium, color = PremiumTextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = subtitle,
                onValueChange = { subtitle = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = PremiumTextPrimary,
                    unfocusedTextColor = PremiumTextPrimary,
                    focusedBorderColor = PremiumPurple,
                    unfocusedBorderColor = Color.DarkGray,
                    cursorColor = PremiumPurple
                )
            )
            Spacer(modifier = Modifier.height(24.dp))

            Text("Icon", fontWeight = FontWeight.Bold, color = PremiumTextPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(icons) { icon ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (selectedIcon == icon) PremiumPurple.copy(alpha = 0.2f) else Color.DarkGray.copy(alpha = 0.3f))
                            .border(2.dp, if (selectedIcon == icon) PremiumPurple else Color.Transparent, CircleShape)
                            .clickable { selectedIcon = icon },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(icon, fontSize = 24.sp)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Time Interval", fontWeight = FontWeight.Bold, color = PremiumTextPrimary)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = { showStartTimePicker = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PremiumTextPrimary),
                    border = BorderStroke(1.dp, Color.DarkGray)
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = PremiumPurple)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(String.format(Locale.getDefault(), "%02d:%02d", startHour, startMin))
                }
                OutlinedButton(
                    onClick = { showEndTimePicker = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PremiumTextPrimary),
                    border = BorderStroke(1.dp, Color.DarkGray)
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = PremiumPurple)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(String.format(Locale.getDefault(), "%02d:%02d", endHour, endMin))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Days", fontWeight = FontWeight.Bold, color = PremiumTextPrimary)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val dayLabels = listOf("S", "M", "T", "W", "T", "F", "S")
                val days = listOf(1, 2, 4, 8, 16, 32, 64)
                dayLabels.forEachIndexed { index, label ->
                    val mask = days[index]
                    val isSelected = (daysMask and mask) != 0
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) PremiumPurple else Color.DarkGray.copy(alpha = 0.5f))
                            .clickable {
                                daysMask = if (isSelected) daysMask and mask.inv() else daysMask or mask
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (isSelected) Color.White else PremiumTextSecondary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { showAppSelector = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray.copy(alpha = 0.5f), contentColor = PremiumTextPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(20.dp), tint = PremiumPurple)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Default Apps (${selectedApps.size})")
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { 
                    Text("Cancel", color = PremiumTextSecondary) 
                }
                Button(
                    onClick = {
                        onSave(template.copy(
                            name = name,
                            subtitle = subtitle,
                            icon = selectedIcon,
                            startHour = startHour,
                            startMin = startMin,
                            endHour = endHour,
                            endMin = endMin,
                            daysMask = daysMask,
                            defaultApps = selectedApps.joinToString(","),
                            timeLabel = String.format(Locale.getDefault(), "%02d:%02d - %02d:%02d", startHour, startMin, endHour, endMin)
                        ))
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PremiumPurple),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showAppSelector) {
        MultiAppSelectorDialog(
            allApps = allApps,
            initialSelected = selectedApps,
            onDismiss = { showAppSelector = false },
            onSave = {
                selectedApps = it
                showAppSelector = false
            }
        )
    }
}

@Composable
fun EmptySchedulesCard(onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val infiniteTransition = rememberInfiniteTransition(label = "float")
    val translateY by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(PremiumCard)
            .border(1.dp, PremiumBorder, RoundedCornerShape(24.dp))
            .padding(vertical = 40.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Radial glow
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(200.dp)
                .graphicsLayer { alpha = 0.06f }
                .background(Brush.radialGradient(colors = listOf(PremiumPurple, Color.Transparent)))
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer { translationY = translateY.dp.toPx() }
                    .size(96.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background circle with glow
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = Color.Transparent,
                    shadowElevation = 24.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(PremiumPurple, Color(0xFF7C3AED))))
                    )
                }
                Icon(
                    Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp).align(Alignment.BottomEnd).padding(8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "No active schedules",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = PremiumTextPrimary,
                letterSpacing = (-0.5).sp,
                fontSize = 20.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "Tap + to create your first rule",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF8A8A8E),
                fontSize = 14.sp
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val btnScale by animateFloatAsState(if (isPressed) 0.97f else 1f, label = "btnScale")

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .scale(btnScale),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PremiumPurple),
                interactionSource = interactionSource,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Create Schedule",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                "or browse templates ↓",
                color = PremiumPurple,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .graphicsLayer { translationY = translateY.coerceAtLeast(0f) }
                    .clickable { /* Scroll to templates */ }
            )
        }
    }
}



@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InternetSchedulerDialog(
    schedule: InternetSchedule,
    allApps: List<AppInfo>,
    allSchedules: List<InternetSchedule>,
    onDismiss: () -> Unit,
    onSave: (InternetSchedule) -> Unit
) {
    var name by remember { mutableStateOf(schedule.name) }
    var startHour by remember { mutableIntStateOf(schedule.startHour) }
    var startMinute by remember { mutableIntStateOf(schedule.startMinute) }
    var endHour by remember { mutableIntStateOf(schedule.endHour) }
    var endMinute by remember { mutableIntStateOf(schedule.endMinute) }
    var days by remember { mutableIntStateOf(schedule.days) }
    var isStrict by remember { mutableStateOf(schedule.isStrict) }
    var strictnessLevel by remember { mutableIntStateOf(schedule.strictnessLevel) }
    var usageTriggerMinutes by remember { mutableIntStateOf(schedule.usageTriggerMinutes) }
    var linkToBedtime by remember { mutableStateOf(schedule.linkToBedtime) }
    var selectedApps by remember { mutableStateOf(schedule.apps.split(",").filter { it.isNotBlank() }.toSet()) }
    var keywords by remember { mutableStateOf(schedule.keywords) }

    val conflicts = remember(startHour, startMinute, endHour, endMinute, days, allSchedules) {
        val newSchedule = schedule.copy(
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute,
            days = days
        )
        allSchedules.filter { existing ->
            existing.id != newSchedule.id &&
                    (existing.days and newSchedule.days) != 0 &&
                    run {
                        val s1 = existing.startHour * 60 + existing.startMinute
                        val e1 = if (existing.endHour * 60 + existing.endMinute < s1) (existing.endHour * 60 + existing.endMinute) + 1440 else existing.endHour * 60 + existing.endMinute
                        val s2 = newSchedule.startHour * 60 + newSchedule.startMinute
                        val e2 = if (newSchedule.endHour * 60 + newSchedule.endMinute < s2) (newSchedule.endHour * 60 + newSchedule.endMinute) + 1440 else newSchedule.endHour * 60 + newSchedule.endMinute
                        s1 < e2 && s2 < e1
                    }
        }
    }

    var showAppSelector by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        containerColor = PremiumCard,
        titleContentColor = PremiumPurple,
        textContentColor = PremiumTextPrimary,
        title = { Text(if (schedule.id == 0) "Create Schedule" else "Edit Schedule", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                if (conflicts.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFCC00).copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, Color(0xFFFFCC00))
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFCC00))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Conflict detected with '${conflicts.first().name}'",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFFCC00),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Schedule Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PremiumPurple,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedLabelColor = PremiumPurple,
                            unfocusedLabelColor = PremiumTextSecondary
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    Text("Days", fontWeight = FontWeight.Bold, color = PremiumTextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val dayNames = listOf("S", "M", "T", "W", "T", "F", "S")
                        // Sun=1, Mon=2, Tue=4, Wed=8, Thu=16, Fri=32, Sat=64
                        val dayMasks = listOf(1, 2, 4, 8, 16, 32, 64)
                        dayNames.forEachIndexed { index, day ->
                            val mask = dayMasks[index]
                            val isSelected = (days and mask) != 0
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) PremiumPurple else Color.DarkGray)
                                    .clickable {
                                        days = if (isSelected) days and mask.inv() else days or mask
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = day,
                                    color = if (isSelected) Color.White else Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                item {
                    Text("Strictness Level", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val levels = listOf("Gentle", "Firm", "Strict")
                        levels.forEachIndexed { index, label ->
                            val isSelected = strictnessLevel == index
                            Card(
                                onClick = { strictnessLevel = index },
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = if (isSelected) PremiumPurple.copy(alpha = 0.2f) else Color.DarkGray),
                                border = if (isSelected) BorderStroke(2.dp, PremiumPurple) else null
                            ) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(
                                        when(index) {
                                            0 -> "1 bypass"
                                            1 -> "5m delay"
                                            else -> "No bypass"
                                        },
                                        fontSize = 10.sp,
                                        color = PremiumTextSecondary
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    Text("Smart Triggers", fontWeight = FontWeight.Bold, color = PremiumTextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth().background(Color.DarkGray.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = usageTriggerMinutes > 0, 
                                onCheckedChange = { usageTriggerMinutes = if (it) 30 else 0 },
                                colors = CheckboxDefaults.colors(checkedColor = PremiumPurple)
                            )
                            Text("Usage limit trigger", style = MaterialTheme.typography.bodyMedium, color = PremiumTextPrimary)
                        }
                        if (usageTriggerMinutes > 0) {
                            Slider(
                                value = usageTriggerMinutes.toFloat(),
                                onValueChange = { usageTriggerMinutes = it.toInt() },
                                valueRange = 5f..120f,
                                steps = 23,
                                colors = SliderDefaults.colors(thumbColor = PremiumPurple, activeTrackColor = PremiumPurple)
                            )
                            Text("$usageTriggerMinutes minutes of use", style = MaterialTheme.typography.labelSmall, color = PremiumPurple)
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = linkToBedtime, 
                                onCheckedChange = { linkToBedtime = it },
                                colors = CheckboxDefaults.colors(checkedColor = PremiumPurple)
                            )
                            Text("Link to Bedtime mode", style = MaterialTheme.typography.bodyMedium, color = PremiumTextPrimary)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    var showStartTimePicker by remember { mutableStateOf(false) }
                    var showEndTimePicker by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Time Interval", fontWeight = FontWeight.Bold, color = PremiumTextPrimary)
                            Text(
                                text = String.format(Locale.getDefault(), "%02d:%02d – %02d:%02d", startHour, startMinute, endHour, endMinute),
                                fontSize = 14.sp,
                                color = PremiumTextSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { showStartTimePicker = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PremiumTextPrimary),
                            border = BorderStroke(1.dp, Color.DarkGray)
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp), tint = PremiumPurple)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(String.format(Locale.getDefault(), "Start: %02d:%02d", startHour, startMinute))
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.padding(horizontal = 8.dp), tint = Color.DarkGray)
                        OutlinedButton(
                            onClick = { showEndTimePicker = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PremiumTextPrimary),
                            border = BorderStroke(1.dp, Color.DarkGray)
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp), tint = PremiumPurple)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(String.format(Locale.getDefault(), "End: %02d:%02d", endHour, endMinute))
                        }
                    }

                    if (showStartTimePicker) {
                        val state = rememberTimePickerState(initialHour = startHour, initialMinute = startMinute, is24Hour = true)
                        AdvancedTimePickerDialog(
                            title = "Set Start Time",
                            onDismiss = { showStartTimePicker = false },
                            onConfirm = { 
                                startHour = state.hour
                                startMinute = state.minute
                                showStartTimePicker = false 
                            },
                            content = {
                                TimePicker(
                                    state = state,
                                    colors = TimePickerDefaults.colors(
                                        clockDialColor = Color.DarkGray.copy(alpha = 0.3f),
                                        selectorColor = PremiumPurple,
                                        containerColor = PremiumCard,
                                        periodSelectorSelectedContainerColor = PremiumPurple.copy(alpha = 0.2f),
                                        periodSelectorSelectedContentColor = PremiumPurple,
                                        timeSelectorSelectedContainerColor = PremiumPurple.copy(alpha = 0.2f),
                                        timeSelectorSelectedContentColor = PremiumPurple
                                    )
                                )
                            }
                        )
                    }

                    if (showEndTimePicker) {
                        val state = rememberTimePickerState(initialHour = endHour, initialMinute = endMinute, is24Hour = true)
                        AdvancedTimePickerDialog(
                            title = "Set End Time",
                            onDismiss = { showEndTimePicker = false },
                            onConfirm = { 
                                endHour = state.hour
                                endMinute = state.minute
                                showEndTimePicker = false 
                            },
                            content = {
                                TimePicker(
                                    state = state,
                                    colors = TimePickerDefaults.colors(
                                        clockDialColor = Color.DarkGray.copy(alpha = 0.3f),
                                        selectorColor = PremiumPurple,
                                        containerColor = PremiumCard,
                                        periodSelectorSelectedContainerColor = PremiumPurple.copy(alpha = 0.2f),
                                        periodSelectorSelectedContentColor = PremiumPurple,
                                        timeSelectorSelectedContainerColor = PremiumPurple.copy(alpha = 0.2f),
                                        timeSelectorSelectedContentColor = PremiumPurple
                                    )
                                )
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Blocked Apps", fontWeight = FontWeight.Bold, color = PremiumTextPrimary)
                                TextButton(onClick = { showAppSelector = true }) {
                                    Text(if (selectedApps.isEmpty()) "Select" else "${selectedApps.size} Selected", color = PremiumPurple)
                                }
                            }
                            if (selectedApps.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    selectedApps.take(5).forEach { pkg ->
                                        val appName = allApps.find { it.packageName == pkg }?.name ?: pkg.split(".").last()
                                        SuggestionChip(
                                            onClick = { },
                                            label = { Text(appName, fontSize = 10.sp, color = PremiumTextPrimary) },
                                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = PremiumPurple.copy(alpha = 0.1f)),
                                            border = BorderStroke(1.dp, PremiumPurple.copy(alpha = 0.3f))
                                        )
                                    }
                                    if (selectedApps.size > 5) {
                                        Text("+${selectedApps.size - 5} more", fontSize = 10.sp, color = PremiumTextSecondary, modifier = Modifier.padding(top = 8.dp))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    OutlinedTextField(
                        value = keywords,
                        onValueChange = { keywords = it },
                        label = { Text("Keywords (comma separated)") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. reels, shorts, fyp", color = PremiumTextSecondary) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PremiumPurple,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = PremiumTextPrimary,
                            unfocusedTextColor = PremiumTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Strict Mode", fontWeight = FontWeight.Bold, color = PremiumTextPrimary)
                            Text("Cannot disable during active time", fontSize = 12.sp, color = PremiumTextSecondary)
                        }
                        Switch(
                            checked = isStrict,
                            onCheckedChange = { isStrict = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = PremiumPurple, checkedTrackColor = PremiumPurple.copy(alpha = 0.5f))
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(schedule.copy(
                        name = name,
                        startHour = startHour,
                        startMinute = startMinute,
                        endHour = endHour,
                        endMinute = endMinute,
                        days = days,
                        isStrict = isStrict,
                        apps = selectedApps.joinToString(","),
                        keywords = keywords,
                        isEnabled = true,
                        strictnessLevel = strictnessLevel,
                        usageTriggerMinutes = usageTriggerMinutes,
                        linkToBedtime = linkToBedtime
                    ))
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PremiumPurple)
            ) {
                Text("Save Schedule", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = PremiumTextSecondary) }
        }
    )

    if (showAppSelector) {
        MultiAppSelectorDialog(
            allApps = allApps,
            initialSelected = selectedApps,
            onDismiss = { showAppSelector = false },
            onSave = {
                selectedApps = it
                showAppSelector = false
            }
        )
    }
}

@Composable
fun MultiAppSelectorDialog(
    allApps: List<AppInfo>,
    initialSelected: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    var selected by remember { mutableStateOf(initialSelected) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = allApps.filter {
        it.name.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PremiumCard,
        titleContentColor = PremiumPurple,
        textContentColor = PremiumTextPrimary,
        title = { Text("Select Apps", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.heightIn(max = 450.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps...", color = PremiumTextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PremiumPurple) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PremiumPurple,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = PremiumTextPrimary,
                        unfocusedTextColor = PremiumTextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredApps) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected.contains(app.packageName)) PremiumPurple.copy(alpha = 0.1f) else Color.Transparent)
                                .clickable {
                                    selected = if (selected.contains(app.packageName)) {
                                        selected - app.packageName
                                    } else {
                                        selected + app.packageName
                                    }
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icon = app.icon
                            if (icon != null) {
                                Image(
                                    bitmap = icon.toBitmap().asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Android,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = PremiumTextSecondary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(app.name, modifier = Modifier.weight(1f), color = PremiumTextPrimary)
                            Checkbox(
                                checked = selected.contains(app.packageName),
                                onCheckedChange = {
                                    selected = if (it) selected + app.packageName else selected - app.packageName
                                },
                                colors = CheckboxDefaults.colors(checkedColor = PremiumPurple)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selected) },
                colors = ButtonDefaults.buttonColors(containerColor = PremiumPurple),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Done", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = PremiumTextSecondary) }
        }
    )
}

