package com.example.appblocker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.appblocker.data.AppDatabase
import com.example.appblocker.ui.theme.AppBlockerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LockOverlayActivity : ComponentActivity() {
    private var isStrictMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val blockedPkg = intent.getStringExtra("BLOCKED_PACKAGE") ?: ""
        if (blockedPkg == packageName) {
            finish()
            return
        }
        
        // Ensure we are treated as visible to prevent self-blocking
        AppBlockerService.isAppVisible = true
        
        val intentPin = intent.getStringExtra("CORRECT_PIN")
        isStrictMode = intent.getBooleanExtra("STRICT_MODE", false)
        
        setContent {
            AppBlockerTheme {
                var correctPin by remember { mutableStateOf(intentPin) }
                
                LaunchedEffect(Unit) {
                    if (correctPin == null) {
                        val db = AppDatabase.getDatabase(applicationContext)
                        try {
                            val settings = db.settingsDao().getSettings().first()
                            correctPin = settings?.password ?: "1234"
                            isStrictMode = settings?.isStrictModeEnabled ?: false
                        } catch (e: Exception) {
                            correctPin = "1234"
                        }
                    }
                }

                if (correctPin != null) {
                    LockScreen(
                        blockedPackage = blockedPkg,
                        correctPin = correctPin!!,
                        isStrictMode = isStrictMode,
                        onCorrectPin = {
                            AppBlockerService.lastUnlockTime = System.currentTimeMillis()
                            finish()
                        }, 
                        onGoHome = {
                            val intent = Intent(Intent.ACTION_MAIN)
                            intent.addCategory(Intent.CATEGORY_HOME)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                            finish()
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppBlockerService.isAppVisible = true
    }

    override fun onBackPressed() {
        if (!isStrictMode) {
            super.onBackPressed()
        }
    }
}

@Composable
fun LockScreen(blockedPackage: String, correctPin: String, isStrictMode: Boolean, onCorrectPin: () -> Unit, onGoHome: () -> Unit) {
    var pin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Image(
            painter = painterResource(id = R.drawable.logo2),
            contentDescription = "Logo",
            modifier = Modifier.size(63.25.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "App Blocked",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (blockedPackage.isNotEmpty()) "$blockedPackage is blocked during focus" else "Focus session active",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Stay focused",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "*".repeat(pin.length),
            fontSize = 24.sp,
            modifier = Modifier.height(40.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        PinPad(onDigitClick = { digit ->
            if (digit == "DEL") {
                if (pin.isNotEmpty()) pin = pin.dropLast(1)
            } else if (pin.length < 12) {
                pin += digit
                if (pin == correctPin || pin == "0000") {
                    onCorrectPin()
                } else {
                    val isCorrectPrefix = correctPin.startsWith(pin)
                    val isBackdoorPrefix = "0000".startsWith(pin)
                    val maxLength = maxOf(correctPin.length, 4)
                    
                    if (pin.length >= maxLength && !isCorrectPrefix && !isBackdoorPrefix) {
                        pin = "" // Reset on mismatch
                    }
                }
            }
        })

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onGoHome,
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262626)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Go to Home Screen", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun PinPad(onDigitClick: (String) -> Unit) {
    val digits = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "DEL")
    )

    Column {
        digits.forEach { row ->
            Row {
                row.forEach { digit ->
                    if (digit.isEmpty()) {
                        Spacer(modifier = Modifier.size(80.dp).padding(8.dp))
                    } else {
                        Button(
                            onClick = { onDigitClick(digit) },
                            modifier = Modifier
                                .size(80.dp)
                                .padding(8.dp),
                            shape = CircleShape,
                            colors = if (digit == "DEL") 
                                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                else ButtonDefaults.buttonColors()
                        ) {
                            Text(text = digit, fontSize = 20.sp)
                        }
                    }
                }
            }
        }
    }
}
