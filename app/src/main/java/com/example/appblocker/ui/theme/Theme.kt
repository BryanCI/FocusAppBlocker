package com.example.appblocker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.appblocker.ThemeManager

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    secondary = DarkSecondary,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF8A8A8E),
    outline = Color(0xFF2A2A2E),
    outlineVariant = Color(0xFF2A2A2E),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryPurple,
    onPrimary = TextButton,
    secondary = AccentPurple,
    onSecondary = TextButton,
    tertiary = PrimaryPurpleDark,
    background = AppBackground,
    surface = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = AppSurface,
    onSurfaceVariant = TextSecondary,
    outlineVariant = Color(0xFFEEEEEE),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

@Composable
fun AppBlockerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val amoled by ThemeManager.getAmoledFlow(context).collectAsState(initial = true)
    val themeMode by ThemeManager.getThemeModeFlow(context).collectAsState(initial = "dark")

    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> darkTheme
    }

    val backgroundColor = if (isDark) {
        if (amoled) Color(0xFF0A0A0A) else Color(0xFF121212)
    } else {
        Color(0xFFFFFFFF)
    }

    val colorScheme = if (isDark) {
        DarkColorScheme.copy(
            background = backgroundColor,
            surface = if (amoled) Color(0xFF161616) else Color(0xFF1C1C1E)
        )
    } else {
        LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            var activityContext = view.context
            while (activityContext is android.content.ContextWrapper) {
                if (activityContext is Activity) break
                activityContext = activityContext.baseContext
            }
            val window = (activityContext as? Activity)?.window
            if (window != null) {
                window.statusBarColor = backgroundColor.toArgb()
                window.navigationBarColor = backgroundColor.toArgb()
                
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
