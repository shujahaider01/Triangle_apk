package com.triangle.app.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.ui.theme.TriangleBrandPurple
import com.triangle.app.ui.theme.triangleDarkTheme
import java.text.DateFormat
import java.util.Date

/** Native rewrite of Settings > Backup & Restore — see BackupRestoreViewModel for the OAuth/backup logic. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(viewModel: BackupRestoreViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val dark = triangleDarkTheme()
    val surface2 = if (dark) Color(0xFF1E2738) else Color(0xFFF3F4F6)
    val text2 = if (dark) Color(0xFFA1A1AA) else Color(0xFF6B7280)
    var showRestoreConfirm by remember { mutableStateOf(false) }

    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onAuthorizationIntentResult(context, result.data)
    }
    fun launchIntent(request: IntentSenderRequest) = authLauncher.launch(request)

    LaunchedEffect(Unit) { viewModel.refreshLocalState(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxWidth().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(surface2).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (state.connected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = if (state.connected) TriangleBrandPurple else text2,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.connected) "Google Drive connected" else "Google Drive not connected",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (state.connected && state.lastBackupTime > 0) {
                        Text(
                            "Last backup: ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(state.lastBackupTime))}",
                            fontSize = 12.sp,
                            color = text2
                        )
                    } else if (!state.connected) {
                        Text("Connect to back up or restore your data", fontSize = 12.sp, color = text2)
                    }
                }
            }

            if (state.connected) {
                Button(
                    onClick = { viewModel.backupNow(context, ::launchIntent) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Back Up Now")
                }
                OutlinedButton(
                    onClick = { showRestoreConfirm = true },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Restore from Backup")
                }
                TextButton(onClick = { viewModel.disconnect(context) }, enabled = !state.busy) {
                    Text("Disconnect Google Drive", color = MaterialTheme.colorScheme.error)
                }
            } else {
                Button(
                    onClick = { viewModel.connect(context, ::launchIntent) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect Google Drive")
                }
            }

            if (state.busy) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                    Text("Working…", fontSize = 13.sp, color = text2)
                }
            }
            state.message?.let { msg ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(msg, fontSize = 13.sp, color = Color(0xFF22C55E))
                }
            }
            state.error?.let { err ->
                Text(err, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Restore from backup?") },
            text = { Text("This replaces all of your current tasks, habits, and other data with whatever was in your most recent Google Drive backup. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    viewModel.restore(context, ::launchIntent)
                }) { Text("Restore", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text("Cancel") } }
        )
    }
}
