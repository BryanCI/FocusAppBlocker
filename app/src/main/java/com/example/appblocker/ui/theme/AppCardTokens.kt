package com.example.appblocker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object AppCardTokens {
    val Shape = RoundedCornerShape(20.dp)
    val Elevation = 0.dp
    val PressedElevation = 0.dp
    val Spacing = 12.dp
    
    val ContainerColor @Composable get() = MaterialTheme.colorScheme.surfaceVariant
}
