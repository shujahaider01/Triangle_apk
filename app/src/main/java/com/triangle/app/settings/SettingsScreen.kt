package com.triangle.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

/**
 * Native port of renderInternSettings() (script.js:6048-6151), trimmed to
 * the real, individual-relevant rows — see the Milestone 6 plan for what's
 * deliberately out (Avatar editing, Dark Mode/accent overrides, and the
 * confirmed-dead Privacy/Help/Feedback rows). Sign Out (`.isettings-signout`,
 * style.css:7446-7461) is genuinely the most important row here — it's the
 * only way to log out natively before this milestone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenNotifications: () -> Unit,
    onOpenChangePassword: () -> Unit,
    onSignedOut: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = isSystemInDarkTheme()
    val surface2 = if (dark) Color(0xFF1E2738) else Color(0xFFF3F4F6)
    val text2 = if (dark) Color(0xFFA1A1AA) else Color(0xFF6B7280)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxWidth().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(surface2)) {
                SettingsRow(Icons.Default.Notifications, "Notifications", text2, onClick = onOpenNotifications)
                SettingsRow(Icons.Default.Lock, "Change Password", text2, onClick = onOpenChangePassword)
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
