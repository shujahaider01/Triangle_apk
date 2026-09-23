package com.triangle.app.notifications

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.triangle.app.announcements.AnnouncementCard
import com.triangle.app.announcements.TitleColorPicker
import com.triangle.app.data.AnnouncementRepository
import com.triangle.app.data.HighlightBus
import com.triangle.app.data.HighlightKind
import com.triangle.app.data.models.Announcement
import com.triangle.app.tasks.ConfirmDialog
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.triangle.app.data.FeatureFlag
import com.triangle.app.data.FeatureFlags
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.models.AppNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class NotifMeta(val icon: ImageVector, val label: String, val color: Color)

private val NotifTypes = mapOf(
    "task_assigned" to NotifMeta(Icons.AutoMirrored.Filled.Assignment, "Task Assigned", Color(0xFF3B82F6)),
    "task_completed" to NotifMeta(Icons.Filled.CheckCircle, "Task Completed", Color(0xFF22C55E)),
    "habit_assigned" to NotifMeta(Icons.Filled.Repeat, "Habit Assigned", Color(0xFFF59E0B)),
    "habit_completed" to NotifMeta(Icons.Filled.Repeat, "Habit Completed", Color(0xFFF59E0B)),
    "post_created" to NotifMeta(Icons.AutoMirrored.Filled.Article, "New Post", Color(0xFF6366F1)),
    "chat_message" to NotifMeta(Icons.AutoMirrored.Filled.Chat, "New Message", Color(0xFF14B8A6)),
    "task_shared" to NotifMeta(Icons.Filled.Share, "Task Shared", Color(0xFFE85D26)),
    "connection_request" to NotifMeta(Icons.Filled.PersonAddAlt1, "Connection Request", Color(0xFF8B5CF6)),
    "connection_accepted" to NotifMeta(Icons.Filled.Groups, "Connection Accepted", Color(0xFF22C55E)),
    "announcement" to NotifMeta(Icons.Filled.Campaign, "Announcement", Color(0xFFE85D26))
)
private val DefaultNotifMeta = NotifMeta(Icons.Filled.Notifications, "Notification", Color(0xFF6C5CE7))
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
    onOpenDmThread: (otherUserId: String, messageId: String?) -> Unit,
    onOpenTaskDetail: (taskId: String?, body: String) -> Unit,
    onOpenHabitDetail: (habitId: String?, body: String) -> Unit,
    onOpenTasks: () -> Unit,
    onOpenCircle: () -> Unit,
    onComposeAnnouncement: () -> Unit,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val announcements by viewModel.announcements.collectAsState()
    val sentIds by viewModel.sentIds.collectAsState()
    val announcementsEnabled = FeatureFlags.isEnabled(FeatureFlag.ANNOUNCEMENTS_ENABLED)
    val palette = notificationsPalette()
    val unreadCount = state.notifications.count { !it.read }

    var editing by remember { mutableStateOf<Announcement?>(null) }
    var deleting by remember { mutableStateOf<Announcement?>(null) }
    var lightboxUrl by remember { mutableStateOf<String?>(null) }
    var highlightedAnnouncementId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    fun handleTap(n: AppNotification) {
        viewModel.markRead(n.id)
        when (n.type) {
            "chat_message" -> n.otherUserId?.let { onOpenDmThread(it, n.itemId) }
            // Jump straight to the specific task/habit (highlighted in the list) instead of just
            // landing on the generic Tasks tab — falls back to matching by the name in the text
            // for notifications pushed before itemId existed.
            "task_assigned" -> onOpenTaskDetail(n.itemId, n.body)
            "habit_assigned" -> onOpenHabitDetail(n.itemId, n.body)
            "task_shared", "task_completed", "habit_completed" -> onOpenTasks()
            // connection_request no longer navigates away — it gets inline
            // Accept/Decline buttons right on the row (see NotifRow) instead
            // of hiding them behind a tap into ConnectionRequestsScreen.
            "connection_accepted" -> onOpenCircle()
            else -> Unit
        }
    }

    // One chronological feed: ordinary notification rows, plus announcements rendered inline as
    // full cards — received ones (from their notification row) and ones I sent (from my Sent
    // index), so the sender sees the same card. An announcement still loading, or recalled, is hidden.
    val feed = remember(state.notifications, announcements, sentIds, announcementsEnabled) {
        buildList<FeedItem> {
            state.notifications.forEach { n ->
                if (n.type == "announcement") {
                    val a = n.itemId?.let { announcements[it] }
                    if (a != null && announcementsEnabled) add(FeedItem.Card(a, mine = false, notification = n))
                } else add(FeedItem.Note(n))
            }
            if (announcementsEnabled) sentIds.forEach { id -> announcements[id]?.let { add(FeedItem.Card(it, mine = true, notification = null)) } }
        }
    }
    val rows = remember(feed) { groupByDate(feed).flatMap { (label, items) -> listOf<FeedRow>(FeedRow.Header(label)) + items.map { FeedRow.Entry(it) } } }

    // Announcement cards load a moment after the plain rows, and a LazyColumn keeps whatever row
    // was first on screen anchored — so cards landing above it would sit off-screen. Until the
    // user scrolls, keep the list pinned to the top.
    var userScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(listState) { snapshotFlow { listState.isScrollInProgress }.collect { if (it) userScrolled = true } }
    LaunchedEffect(rows) { if (!userScrolled && rows.isNotEmpty()) listState.scrollToItem(0) }

    // A tapped announcement push asks to scroll to and highlight its card (see HighlightBus).
    val highlightReq by HighlightBus.request.collectAsState()
    val announcementReq = highlightReq?.takeIf { it.kind == HighlightKind.ANNOUNCEMENT }
    LaunchedEffect(announcementReq, rows) {
        val req = announcementReq ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it is FeedRow.Entry && it.item is FeedItem.Card && it.item.a.id == req.itemId }
        if (index < 0) return@LaunchedEffect
        listState.animateScrollToItem(index)
        highlightedAnnouncementId = req.itemId
        delay(3000)
        highlightedAnnouncementId = null
        HighlightBus.clear(req)
    }
    LaunchedEffect(announcementReq) {
        val req = announcementReq ?: return@LaunchedEffect
        delay(8000)
        HighlightBus.clear(req) // never appeared (recalled) — stop waiting
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
        },
        floatingActionButton = {
            if (announcementsEnabled) {
                FloatingActionButton(onClick = onComposeAnnouncement) {
                    Icon(Icons.Default.Add, contentDescription = "New announcement")
                }
            }
        }
    ) { padding ->
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Notifications, contentDescription = null, tint = palette.text3, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("No notifications yet", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.text)
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(rows, key = { row -> when (row) { is FeedRow.Header -> "h-${row.label}"; is FeedRow.Entry -> row.item.key } }) { row ->
                    when (row) {
                        is FeedRow.Header -> Text(row.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.text3, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                        is FeedRow.Entry -> when (val item = row.item) {
                            is FeedItem.Note -> {
                                val n = item.n
                                val isPendingConnection = n.type == "connection_request" && n.otherUserId in state.pendingConnectionUids
                                NotifRow(
                                    n,
                                    palette,
                                    onClick = { handleTap(n) },
                                    isPendingConnection = isPendingConnection,
                                    onAccept = {
                                        n.otherUserId?.let(viewModel::acceptConnectionRequest)
                                        viewModel.markRead(n.id)
                                    },
                                    onDecline = {
                                        n.otherUserId?.let(viewModel::declineConnectionRequest)
                                        viewModel.markRead(n.id)
                                    }
                                )
                            }
                            is FeedItem.Card -> AnnouncementCard(
                                announcement = item.a,
                                isMine = item.mine,
                                unread = item.notification?.read == false,
                                highlighted = highlightedAnnouncementId == item.a.id,
                                onClick = { item.notification?.let { if (!it.read) viewModel.markRead(it.id) } },
                                onOpenAttachment = { a ->
                                    // Photos open in the in-app viewer; videos and PDFs open in Drive's viewer/player.
                                    if (a.isImage) lightboxUrl = a.url
                                    else runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.viewUrl))) }
                                        .onFailure { Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show() }
                                },
                                onEdit = { editing = item.a },
                                onDelete = { deleting = item.a }
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) } // clear the + button
            }
        }
    }

    editing?.let { a ->
        var title by remember(a.id) { mutableStateOf(a.title) }
        var body by remember(a.id) { mutableStateOf(a.body) }
        var color by remember(a.id) { mutableStateOf(a.color) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit announcement") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(title, { title = it.take(AnnouncementRepository.TITLE_MAX) }, label = { Text("Title") }, singleLine = true)
                    OutlinedTextField(body, { body = it.take(AnnouncementRepository.BODY_MAX) }, label = { Text("Announcement") }, minLines = 4, maxLines = 8)
                    TitleColorPicker(selected = color, onSelect = { color = it })
                }
            },
            confirmButton = {
                TextButton(
                    enabled = title.isNotBlank() && body.isNotBlank(),
                    onClick = { viewModel.editAnnouncement(a.id, title, body, color); editing = null }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }

    deleting?.let { a ->
        ConfirmDialog(
            title = "Delete this announcement?",
            body = "It will be removed for you and every recipient. This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { viewModel.deleteAnnouncement(a.id); deleting = null },
            onDismiss = { deleting = null }
        )
    }

    lightboxUrl?.let { url ->
        Dialog(onDismissRequest = { lightboxUrl = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(model = url, contentDescription = "Photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                IconButton(onClick = { lightboxUrl = null }, modifier = Modifier.padding(12.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

private sealed interface FeedItem {
    val key: String
    val createdAt: Long

    data class Note(val n: AppNotification) : FeedItem {
        override val key get() = "n-${n.id}"
        override val createdAt get() = n.createdAt
    }

    data class Card(val a: Announcement, val mine: Boolean, val notification: AppNotification?) : FeedItem {
        override val key get() = "a-${a.id}-${if (mine) "sent" else "received"}"
        override val createdAt get() = a.createdAt
    }
}

private sealed interface FeedRow {
    data class Header(val label: String) : FeedRow
    data class Entry(val item: FeedItem) : FeedRow
}

@Composable
private fun NotifRow(
    n: AppNotification,
    palette: NotificationsPalette,
    onClick: () -> Unit,
    isPendingConnection: Boolean = false,
    onAccept: () -> Unit = {},
    onDecline: () -> Unit = {}
) {
    val meta = notifMeta(n.type)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (!n.read) meta.color.copy(alpha = 0.06f) else palette.surface2)
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(meta.color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(meta.icon, contentDescription = null, tint = meta.color, modifier = Modifier.size(20.dp))
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
        // Shown directly on the row instead of behind a tap-through to a
        // separate requests screen — see NotificationsScreen's doc comment.
        if (isPendingConnection) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAccept, modifier = Modifier.weight(1f)) { Text("Accept") }
                OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f)) { Text("Decline") }
            }
        }
    }
}

private fun groupByDate(feed: List<FeedItem>): List<Pair<String, List<FeedItem>>> {
    val sorted = feed.sortedByDescending { it.createdAt }
    val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    val yesterday = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(System.currentTimeMillis() - 86_400_000L))
    val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.US)
    val labelFmt = SimpleDateFormat("MMMM d, yyyy", Locale.US)
    val groups = LinkedHashMap<String, MutableList<FeedItem>>()
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
