package com.triangle.app.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Checks once per launch and shows an "Update available" dialog; renders nothing otherwise. */
@Composable
fun UpdatePrompt() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var dismissed by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(-1f) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { info = AppUpdater.checkForUpdate() }

    val update = info
    if (update == null || (dismissed && !update.force)) return

    AlertDialog(
        onDismissRequest = { if (!update.force && !downloading) dismissed = true },
        title = { Text("Update available (${update.versionName})") },
        text = {
            Column {
                Text(update.changelog.ifBlank { "A new version of Triangle is available." })
                if (downloading) {
                    Spacer(Modifier.height(12.dp))
                    if (progress >= 0f) LinearProgressIndicator(progress = { progress }) else LinearProgressIndicator()
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !downloading,
                onClick = {
                    if (!AppUpdater.canInstall(context)) {
                        error = "Allow \"Install unknown apps\" for Triangle, then tap Update again."
                        AppUpdater.openInstallPermissionSettings(context)
                        return@TextButton
                    }
                    error = null
                    downloading = true
                    scope.launch {
                        runCatching { AppUpdater.download(context, update) { progress = it } }
                            .onSuccess { AppUpdater.install(context, it) }
                            .onFailure { error = "Download failed. Check your connection and try again." }
                        downloading = false
                    }
                },
            ) { Text(if (error != null && !downloading) "Retry" else "Update") }
        },
        dismissButton = {
            if (!update.force) TextButton(enabled = !downloading, onClick = { dismissed = true }) { Text("Later") }
        },
    )
}
