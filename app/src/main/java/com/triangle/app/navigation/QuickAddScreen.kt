package com.triangle.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

/**
 * "Quick add": one page to start anything new — a task or habit (solo or shared), an announcement or a poll —
 * laid out as a rounded card of icon + label rows under small section headings.
 */
@Composable
fun QuickAddScreen(
    onBack: () -> Unit,
    onCreateTask: (solo: Boolean) -> Unit,
    onCreateHabit: (solo: Boolean) -> Unit,
    onAnnouncement: () -> Unit,
    onPoll: () -> Unit
) {
    val dark = triangleDarkTheme()
    val page = if (dark) Color.Black else Color(0xFFF2F2F7)
    val card = if (dark) Color(0xFF1C1C1E) else Color.White
    val text = if (dark) Color.White else Color(0xFF1C1C1E)
    val muted = if (dark) Color(0xFF9A9AA0) else Color(0xFF6B6B76)
    val chip = if (dark) Color(0xFF3A3A3C) else Color(0xFFE9E7FF)

    Column(Modifier.fillMaxSize().background(page).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = text) }
            Text("Quick add", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = text, modifier = Modifier.padding(start = 6.dp))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)) {
            Section("Create for myself", muted)
            QuickCard(card) {
                QuickRow(
                    QuickItem(Icons.Outlined.CheckCircleOutline, "Task") { onCreateTask(true) },
                    QuickItem(Icons.Outlined.Repeat, "Habit") { onCreateHabit(true) },
                    chip, text
                )
            }
            Spacer(Modifier.height(24.dp))
            Section("Create with others", muted)
            QuickCard(card) {
                QuickRow(
                    QuickItem(Icons.Outlined.Groups, "Shared task") { onCreateTask(false) },
                    QuickItem(Icons.Outlined.Groups, "Shared habit") { onCreateHabit(false) },
                    chip, text
                )
            }
            Spacer(Modifier.height(24.dp))
            Section("Send to people", muted)
            QuickCard(card) {
                QuickRow(
                    QuickItem(Icons.Outlined.Campaign, "Announcement", onAnnouncement),
                    QuickItem(Icons.Outlined.Poll, "Poll", onPoll),
                    chip, text
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

private class QuickItem(val icon: ImageVector, val label: String, val onClick: () -> Unit)

@Composable
private fun Section(label: String, color: Color) {
    Text(label, fontSize = 14.sp, color = color, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp))
}

@Composable
private fun QuickCard(color: Color, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(color).padding(horizontal = 12.dp, vertical = 10.dp)) { content() }
}

@Composable
private fun QuickRow(left: QuickItem, right: QuickItem, chip: Color, text: Color) {
    Row(Modifier.fillMaxWidth()) {
        listOf(left, right).forEach { item ->
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable(onClick = item.onClick).padding(horizontal = 10.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(chip), contentAlignment = Alignment.Center) {
                    Icon(item.icon, contentDescription = null, tint = TriangleBrandPurple, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(item.label, fontSize = 17.sp, color = text, maxLines = 1)
            }
        }
    }
}
