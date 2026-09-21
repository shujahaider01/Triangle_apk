package com.triangle.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.triangle.app.data.SessionStore
import com.triangle.app.data.ThemeMode
import com.triangle.app.data.ThemeStore
import com.triangle.app.data.UserRepository
import com.triangle.app.data.UsernameRepository
import com.triangle.app.ui.theme.triangleDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Native port of renderInternSettings() (script.js:6048-6151), trimmed to
 * the real, individual-relevant rows — see the Milestone 6 plan for what's
 * deliberately out (Avatar editing and the confirmed-dead Privacy/Help/
 * Feedback rows). Sign Out (`.isettings-signout`, style.css:7446-7461) is
 * genuinely the most important row here — it's the only way to log out
 * natively before this milestone. Appearance (System/Light/Dark) was added
 * later, past the original "deliberately out" scope trim — see
 * data/ThemeStore.kt and ui/theme/Theme.kt's LocalTriangleDarkTheme for how
 * the choice actually reaches every screen, not just MaterialTheme's colors.
 */
private enum class EditableField { NAME, USERNAME }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    session: SessionStore.Session,
    onSessionUpdated: (SessionStore.Session) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenChangePassword: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    onSignedOut: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = triangleDarkTheme()
    val surface2 = if (dark) Color(0xFF1E2738) else Color(0xFFF3F4F6)
    val text2 = if (dark) Color(0xFFA1A1AA) else Color(0xFF6B7280)
    val themeMode by ThemeStore.modeFlow(context).collectAsState(initial = ThemeMode.SYSTEM)
    var editingField by remember { mutableStateOf<EditableField?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxWidth().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(surface2).padding(16.dp)) {
                Text("General", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = text2)
                Spacer(Modifier.height(4.dp))
                ProfileFieldRow(Icons.Default.Badge, "Full Name", session.name, text2, editable = true, onClick = { editingField = EditableField.NAME })
                ProfileFieldRow(Icons.Default.AlternateEmail, "Username", session.username?.let { "@$it" } ?: "Not set", text2, editable = true, onClick = { editingField = EditableField.USERNAME })
                ProfileFieldRow(Icons.Default.Email, "Email", session.email, text2, editable = false, isLast = true)
            }

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(surface2)) {
                SettingsRow(Icons.Default.Notifications, "Notifications", text2, onClick = onOpenNotifications)
                SettingsRow(Icons.Default.Lock, "Change Password", text2, onClick = onOpenChangePassword)
                SettingsRow(Icons.Default.CloudSync, "Backup & Restore", text2, onClick = onOpenBackupRestore)
            }

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(surface2).padding(16.dp)) {
                Text("Appearance", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = text2)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "System" to ThemeMode.SYSTEM,
                        "Light" to ThemeMode.LIGHT,
                        "Dark" to ThemeMode.DARK
                    ).forEach { (label, mode) ->
                        val active = themeMode == mode
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) MaterialTheme.colorScheme.primary else (if (dark) Color(0xFF2A3242) else Color.White))
                                .clickable { scope.launch { ThemeStore.setMode(context, mode) } }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                fontSize = 13.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) Color.White else text2
                            )
                        }
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(surface2)
                    .clickable {
                        scope.launch {
                            SessionStore.clear(context)
                            FirebaseAuth.getInstance().signOut()
                            onSignedOut()
                        }
                    }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color(0xFF4A9EFF))
                Spacer(Modifier.size(8.dp))
                Text("Sign Out", color = Color(0xFF4A9EFF), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }

    when (editingField) {
        EditableField.NAME -> EditNameSheet(
            uid = session.uid,
            currentName = session.name,
            onDismiss = { editingField = null },
            onSaved = { newName ->
                val updated = session.copy(name = newName)
                scope.launch { SessionStore.save(context, updated) }
                onSessionUpdated(updated)
                editingField = null
            }
        )
        EditableField.USERNAME -> EditUsernameSheet(
            uid = session.uid,
            currentUsername = session.username,
            onDismiss = { editingField = null },
            onSaved = { newUsername ->
                val updated = session.copy(username = newUsername)
                scope.launch { SessionStore.save(context, updated) }
                onSessionUpdated(updated)
                editingField = null
            }
        )
        null -> {}
    }
}

@Composable
private fun ProfileFieldRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    text2: Color,
    editable: Boolean,
    isLast: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (editable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = text2, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, color = text2)
            Text(value.ifBlank { "—" }, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        if (editable) {
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = "Edit", tint = text2, modifier = Modifier.size(13.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditNameSheet(uid: String, currentName: String, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun save() {
        if (saving || name.isBlank()) return
        saving = true
        scope.launch {
            runCatching { UserRepository.updateName(uid, name.trim()) }
            saving = false
            onSaved(name.trim())
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Full Name", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = name.isNotBlank() && !saving,
                    onClick = { save() }
                ) { Text(if (saving) "Saving…" else "Save") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditUsernameSheet(uid: String, currentUsername: String?, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    var username by remember { mutableStateOf(currentUsername ?: "") }
    var checking by remember { mutableStateOf(false) }
    var available by remember { mutableStateOf<Boolean?>(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(username) {
        error = null
        available = null
        if (UsernameRepository.formatError(username) != null) return@LaunchedEffect
        val normalized = UsernameRepository.normalize(username)
        if (normalized == currentUsername) {
            available = true
            return@LaunchedEffect
        }
        checking = true
        delay(400)
        available = runCatching { UsernameRepository.isAvailable(username) }.getOrNull()
        checking = false
    }

    fun save() {
        if (saving) return
        val formatError = UsernameRepository.formatError(username)
        if (formatError != null) {
            error = formatError
            return
        }
        saving = true
        error = null
        scope.launch {
            when (val result = UsernameRepository.changeUsername(uid, currentUsername, username)) {
                is UsernameRepository.ClaimResult.Success -> onSaved(UsernameRepository.normalize(username))
                is UsernameRepository.ClaimResult.Taken -> {
                    saving = false
                    available = false
                    error = "That username is already taken"
                }
                is UsernameRepository.ClaimResult.Error -> {
                    saving = false
                    error = result.message
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Username", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it.take(20); error = null },
                label = { Text("Username") },
                leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                singleLine = true,
                isError = error != null || available == false,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            val hint = when {
                error != null -> error
                username.isBlank() -> null
                checking -> "Checking availability…"
                available == true -> "@${UsernameRepository.normalize(username)} is available"
                available == false -> "That username is already taken"
                else -> null
            }
            hint?.let {
                Text(it, fontSize = 12.5.sp, color = if (error != null || available == false) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = !saving && !checking && available == true,
                    onClick = { save() }
                ) { Text(if (saving) "Saving…" else "Save") }
            }
        }
    }
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, text2: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = text2)
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = text2, modifier = Modifier.size(14.dp))
    }
}
