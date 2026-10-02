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
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.SwapVert
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.triangle.app.tasks.formCardBorder
import com.triangle.app.tasks.formCardColor
import com.triangle.app.tasks.formPageColor
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
    onOpenArchivedItems: () -> Unit,
    onOpenHabitSorting: () -> Unit,
    onOpenCategories: () -> Unit,
    onSignedOut: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = triangleDarkTheme()
    val surface2 = if (dark) Color(0xFF1E2738) else Color(0xFFF3F4F6)
    val text2 = if (dark) Color(0xFFA1A1AA) else Color(0xFF6B7280)
    val themeMode by ThemeStore.modeFlow(context).collectAsState(initial = ThemeMode.LIGHT)
    var editingField by remember { mutableStateOf<EditableField?>(null) }

    // Reference look: a back arrow, the app icon + name + version, then white rounded cards of icon / label / chevron rows.
    val pageBg = formPageColor()
    val rowText = MaterialTheme.colorScheme.onSurface
    val iconTint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    val appIcon = remember { androidx.core.content.ContextCompat.getDrawable(context, com.triangle.app.R.mipmap.ic_launcher)?.toBitmap(192, 192)?.asImageBitmap() }
    var showThemeDialog by remember { mutableStateOf(false) }
    // Hidden shortcut: tap the app icon / name / version three times (within 2 seconds) to open the latest APK release on GitHub.
    var versionTaps by remember { mutableStateOf(0) }
    var lastVersionTap by remember { mutableStateOf(0L) }
    val releaseTap = {
        val now = System.currentTimeMillis()
        versionTaps = if (now - lastVersionTap <= 2000L) versionTaps + 1 else 1
        lastVersionTap = now
        if (versionTaps >= 3) {
            versionTaps = 0
            runCatching {
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/shujahaider01/Triangle_apk/releases/latest")))
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(pageBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }

        // App icon, name and version.
        Row(
            Modifier.fillMaxWidth().clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { releaseTap() }.padding(top = 22.dp, bottom = 34.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            appIcon?.let {
                Image(it, contentDescription = null, modifier = Modifier.size(68.dp).clip(RoundedCornerShape(18.dp)))
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text("Triangle", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = rowText)
                Text("Version ${com.triangle.app.BuildConfig.VERSION_NAME}", fontSize = 15.sp, color = text2)
            }
        }

        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // Account details (tap Full Name / Username to edit).
            SettingsCard {
                ProfileFieldRow(Icons.Default.Badge, "Full Name", session.name, text2, iconTint, editable = true, onClick = { editingField = EditableField.NAME })
                SettingsDivider()
                ProfileFieldRow(Icons.Default.AlternateEmail, "Username", session.username?.let { "@$it" } ?: "Not set", text2, iconTint, editable = true, onClick = { editingField = EditableField.USERNAME })
                SettingsDivider()
                ProfileFieldRow(Icons.Default.Email, "Email", session.email, text2, iconTint, editable = false)
            }

            SettingsCard {
                SettingsRow(Icons.Default.Palette, "Theme", iconTint, trailing = themeLabel(themeMode)) { showThemeDialog = true }
                SettingsDivider()
                SettingsRow(Icons.Default.Notifications, "Notifications", iconTint, onClick = onOpenNotifications)
            }

            SettingsCard {
                SettingsRow(Icons.Default.SwapVert, "Sorting", iconTint, onClick = onOpenHabitSorting)
                SettingsDivider()
                SettingsRow(Icons.Default.Category, "Categories", iconTint, onClick = onOpenCategories)
                SettingsDivider()
                SettingsRow(Icons.Default.Inventory2, "Archived Items", iconTint, onClick = onOpenArchivedItems)
            }

            SettingsCard {
                SettingsRow(Icons.Default.CloudUpload, "Backup & Restore", iconTint, onClick = onOpenBackupRestore)
                SettingsDivider()
                SettingsRow(Icons.Default.Lock, "Change Password", iconTint, onClick = onOpenChangePassword)
            }

            SettingsCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                SessionStore.clear(context)
                                FirebaseAuth.getInstance().signOut()
                                onSignedOut()
                            }
                        }
                        .padding(vertical = 18.dp, horizontal = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color(0xFF4A9EFF))
                    Spacer(Modifier.size(10.dp))
                    Text("Sign Out", color = Color(0xFF4A9EFF), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showThemeDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Theme") },
            text = {
                Column {
                    listOf("System default" to ThemeMode.SYSTEM, "Light" to ThemeMode.LIGHT, "Dark" to ThemeMode.DARK).forEach { (label, mode) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { scope.launch { ThemeStore.setMode(context, mode) }; showThemeDialog = false }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = themeMode == mode,
                                onClick = { scope.launch { ThemeStore.setMode(context, mode) }; showThemeDialog = false }
                            )
                            Text(label, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("Close") } }
        )
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
    iconTint: Color,
    editable: Boolean,
    onClick: () -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (editable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, color = text2)
            Text(value.ifBlank { "—" }, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
        if (editable) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Edit", tint = text2, modifier = Modifier.size(28.dp))
        }
    }
}

/** White rounded card with a hairline border, holding a group of rows. */
@Composable
private fun SettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(22.dp),
        color = formCardColor(),
        border = androidx.compose.foundation.BorderStroke(1.dp, formCardBorder()),
        modifier = Modifier.fillMaxWidth()
    ) { Column(content = content) }
}

@Composable
private fun SettingsDivider() {
    androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 24.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

private fun themeLabel(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
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
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    iconTint: Color,
    trailing: String? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, fontSize = 15.sp, color = iconTint) }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
    }
}
