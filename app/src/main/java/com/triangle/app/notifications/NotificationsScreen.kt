package com.triangle.app.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.models.AppNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class NotifMeta(val emoji: String, val label: String, val color: Color)

private val NotifTypes = mapOf(
    "task_assigned" to NotifMeta("📋", "Task Assigned", Color(0xFF3B82F6)),
    "task_completed" to NotifMeta("✅", "Task Completed", Color(0xFF22C55E)),
    "habit_assigned" to NotifMeta("🔁", "Habit Assigned", Color(0xFFF59E0B)),
    "habit_completed" to NotifMeta("🔁", "Habit Completed", Color(0xFFF59E0B)),
    "reward_created" to NotifMeta("🎁", "New Reward", Color(0xFF8B5CF6)),
    "reward_redeemed" to NotifMeta("🎁", "Reward Redeemed", Color(0xFFEF4444)),
    "reward_approved" to NotifMeta("✅", "Reward Approved", Color(0xFF22C55E)),
    "reward_rejected" to NotifMeta("❌", "Reward Rejected", Color(0xFFEF4444)),
    "reward_delivered" to NotifMeta("📦", "Reward Delivered", Color(0xFF6C5CE7)),
    "post_created" to NotifMeta("📰", "New Post", Color(0xFF6366F1)),
    "chat_message" to NotifMeta("💬", "New Message", Color(0xFF14B8A6)),
    "task_shared" to NotifMeta("🔗", "Task Shared", Color(0xFFE85D26))
)
private val DefaultNotifMeta = NotifMeta("🔔", "Notification", Color(0xFF6C5CE7))
private fun notifMeta(type: String) = NotifTypes[type] ?: DefaultNotifMeta

/**
 * Native port of renderInternNotifs()/_renderNotifList() (script.js:4739-
 * 4798). Tap-routing mirrors onNotifTap() (script.js:4808-4832): chat_message
 * opens the DM thread; task_shared/task_completed navigate to Tasks (a task
 * shared via _deliverTaskCopyToUser lands as a real row in the recipient's
 * own tasks list, so this is a real destination even though nothing creates
 * these two types today — SHARED_TASK_MODE_ENABLED is false in the source,
 * see the Milestone 5 plan); everything else just marks read, matching
 * source's own default no-op fallthrough.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    onOpenDmThread: (otherUserId: String) -> Unit,
    onOpenTasks: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val palette = notificationsPalette()
    val unreadCount = state.notifications.count { !it.read }

    fun handleTap(n: AppNotification) {
        viewModel.markRead(n.id)
        when (n.type) {
            "chat_message" -> n.otherUserId?.let(onOpenDmThread)
            "task_shared", "task_completed" -> onOpenTasks()
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (unreadCount > 0) {
                        TextButton(onClick = { viewModel.markAllRead() }) { Text("Mark all Read") }
                    }
                }
            )
        }
    ) { padding ->
        if (state.notifications.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔔", fontSize = 44.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("No notifications yet", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.text)
                }
            }
        } else {
            val groups = groupByDate(state.notifications)
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                groups.forEach { (label, items) ->
                    item {
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.text3, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                    }
                    items(items, key = { it.id }) { n ->
                        NotifRow(n, palette, onClick = { handleTap(n) })
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun NotifRow(n: AppNotification, palette: NotificationsPalette, onClick: () -> Unit) {
    val meta = notifMeta(n.type)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (!n.read) meta.color.copy(alpha = 0.06f) else palette.surface2)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(meta.color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Text(meta.emoji, fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(n.title.ifBlank { meta.label }, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = palette.text, maxLines = 1)
            if (n.body.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(n.body, fontSize = 12.sp, color = palette.text2, maxLines = 2)
            }
            Spacer(Modifier.height(2.dp))
            Text(formatTime(n.createdAt), fontSize = 10.5.sp, color = palette.text3)
        }
        if (!n.read) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(8.dp).clip(CircleShape).background(meta.color))
        }
    }
}

private fun groupByDate(notifications: List<AppNotification>): List<Pair<String, List<AppNotification>>> {
    val sorted = notifications.sortedByDescending { it.createdAt }
    val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    val yesterday = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(System.currentTimeMillis() - 86_400_000L))
    val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.US)
    val labelFmt = SimpleDateFormat("MMMM d, yyyy", Locale.US)
    val groups = LinkedHashMap<String, MutableList<AppNotification>>()
    sorted.forEach { n ->
        val day = dayFmt.format(Date(n.createdAt))
        val label = when (day) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> labelFmt.format(Date(n.createdAt))
        }
        groups.getOrPut(label) { mutableListOf() }.add(n)
    }
    return groups.map { it.key to it.value }
}

private fun formatTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return SimpleDateFormat("h:mm a", Locale.US).format(Date(timestamp))
}
