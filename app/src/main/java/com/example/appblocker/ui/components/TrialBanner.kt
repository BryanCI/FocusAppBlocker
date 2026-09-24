package com.example.appblocker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appblocker.ui.theme.PrimaryPurple
import com.example.appblocker.ui.theme.FocusVibrantLightPurple

@Composable
fun TrialBanner(
    daysLeft: Int?,
    isTrial: Boolean,
    isPremium: Boolean,
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isPremium && !isTrial) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isTrial) FocusVibrantLightPurple else PrimaryPurple
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isTrial) "Trial Active" else "Go Premium",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isTrial) PrimaryPurple else Color.White
                )
                Text(
                    text = if (isTrial) {
                        "$daysLeft days remaining in your free trial"
                    } else {
                        "Get a 7-day free trial and unlimited schedules"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isTrial) PrimaryPurple.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.8f)
                )
            }
            
            Button(
                onClick = onUpgradeClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTrial) PrimaryPurple else Color.White,
                    contentColor = if (isTrial) Color.White else PrimaryPurple
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (isTrial) "Keep Premium" else "Try Free",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
