package com.triangle.app.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.ui.theme.TriangleBrandPurple
import com.triangle.app.ui.theme.triangleDarkTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Slide-in panel from the Home hamburger: today's day/date plus Connections, Settings and Archive. */
@Composable
fun SideMenu(
    open: Boolean,
    onClose: () -> Unit,
    onConnections: () -> Unit,
    onSettings: () -> Unit,
    onArchive: () -> Unit
) {
    BackHandler(enabled = open, onBack = onClose)
    val dark = triangleDarkTheme()
    val panelBg = if (dark) Color(0xFF000000) else Color(0xFFF6F4FB)
    val rowBg = if (dark) Color(0xFF1C1C1E) else Color.White
    val textMain = if (dark) Color.White else Color(0xFF1A1A2E)
    val textSub = if (dark) Color(0xFF9A9AA0) else Color(0xFF6B6B7B)

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = open, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = onClose)
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it }
        ) {
            val today = LocalDate.now()
            Column(
                Modifier
                    .fillMaxHeight()
                    .width(320.dp)
                    .background(panelBg)
                    .safeDrawingPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    // swallow taps so they don't fall through to the scrim
                    .clickable(interactionSource = MutableInteractionSource(), indication = null) {}
            ) {
                Spacer(Modifier.height(24.dp))
                Text(
                    today.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())),
                    color = textMain, fontSize = 32.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    today.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault())),
                    color = textSub, fontSize = 15.sp
                )
                Spacer(Modifier.height(32.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(rowBg)) {
                    MenuRow(Icons.Default.Group, "Connections", TriangleBrandPurple, textMain, textSub) { onClose(); onConnections() }
                }
                Spacer(Modifier.weight(1f))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(rowBg)) {
                    MenuRow(Icons.Default.Settings, "Settings", Color(0xFF8E8E93), textMain, textSub) { onClose(); onSettings() }
                }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(rowBg)) {
                    MenuRow(Icons.Default.Inventory2, "Archive", Color(0xFF2FA8E0), textMain, textSub) { onClose(); onArchive() }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    iconBg: Color,
    textMain: Color,
    textSub: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(iconBg),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(14.dp))
        Text(label, color = textMain, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = textSub)
    }
}
