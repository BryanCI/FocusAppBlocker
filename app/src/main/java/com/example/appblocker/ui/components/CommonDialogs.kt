package com.example.appblocker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appblocker.MainViewModel
import com.example.appblocker.AppInfo
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Android
import androidx.compose.runtime.collectAsState

@Composable
fun PasswordDialog(correctPassword: String, onDismiss: () -> Unit, onCorrectPassword: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Password") },
        text = {
            Column {
                OutlinedTextField(
                    value = password,
                    onValueChange = { 
                        password = it
                        error = false
                    },
                    label = { Text("Password") },
                    isError = error,
                    supportingText = { if (error) Text("Incorrect password") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (password == correctPassword) {
                    onCorrectPassword()
                } else {
                    error = true
                }
            }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectorDialog(viewModel: MainViewModel, isStrictActive: Boolean, onDismiss: () -> Unit) {
    val apps by viewModel.uiState.collectAsState()
    val blockedApps by viewModel.blockedApps.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredApps = remember(apps, searchQuery) {
        apps.filter { it.name.contains(searchQuery, ignoreCase = true) }
            .sortedBy { it.name }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Apps to Block") },
        text = {
            Column(modifier = Modifier.fillMaxHeight(0.8f)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps...") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredApps) { app ->
                        val isBlocked = blockedApps.any { it.pattern == app.packageName && !it.isKeyword }
                        AppRow(
                            app = app,
                            isBlocked = isBlocked,
                            enabled = !isStrictActive || !isBlocked,
                            onBlockedChange = { _ ->
                                viewModel.toggleBlock(app)
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun AllowedAppsSelectorDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val apps by viewModel.uiState.collectAsState()
    val allowedApps by viewModel.allowedApps.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredApps = remember(apps, searchQuery) {
        apps.filter { it.name.contains(searchQuery, ignoreCase = true) }
            .sortedBy { it.name }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Always Allowed Apps") },
        text = {
            Column(modifier = Modifier.fillMaxHeight(0.8f)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps...") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredApps) { app ->
                        val isAllowed = allowedApps.any { it.packageName == app.packageName }
                        AppRow(
                            app = app,
                            isBlocked = isAllowed,
                            onBlockedChange = { _ ->
                                viewModel.toggleAllowApp(app)
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun KeywordSelectorDialog(viewModel: MainViewModel, isStrictActive: Boolean, onDismiss: () -> Unit) {
    val blockedApps by viewModel.blockedApps.collectAsState()
    val keywords = blockedApps.filter { it.isKeyword }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block Keywords") },
        text = {
            Column(modifier = Modifier.fillMaxHeight(0.8f)) {
                Text("Apps containing these words in their title or content will be blocked.", 
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                KeywordAddRow(onAdd = { viewModel.addKeywordBlock(it) })
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(keywords) { keyword ->
                        KeywordRow(
                            keyword = keyword.pattern,
                            enabled = !isStrictActive,
                            onRemove = { viewModel.removeKeywordBlock(keyword.pattern) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun AppRow(
    app: AppInfo,
    isBlocked: Boolean,
    enabled: Boolean = true,
    onBlockedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = app.icon
        if (icon != null) {
            androidx.compose.foundation.Image(
                bitmap = icon.toBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        } else {
            androidx.compose.material3.Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Android,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = app.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        Checkbox(
            checked = isBlocked,
            onCheckedChange = onBlockedChange,
            enabled = enabled
        )
    }
}

@Composable
fun KeywordAddRow(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Add keyword...") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = {
                if (text.isNotBlank()) {
                    onAdd(text)
                    text = ""
                }
            },
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Add")
        }
    }
}

@Composable
fun KeywordRow(keyword: String, enabled: Boolean, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(keyword)
        IconButton(onClick = onRemove, enabled = enabled) {
            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}
