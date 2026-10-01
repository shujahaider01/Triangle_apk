package com.triangle.app.inbox

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.triangle.app.announcements.AnnouncementCard
import com.triangle.app.announcements.TitleColorPicker
import com.triangle.app.data.AnnouncementRepository
import com.triangle.app.data.FeatureFlag
import com.triangle.app.data.FeatureFlags
import com.triangle.app.data.HighlightBus
import com.triangle.app.data.HighlightKind
import com.triangle.app.data.models.Announcement
import com.triangle.app.data.models.Poll
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import com.triangle.app.data.models.AppNotification
import com.triangle.app.notifications.NotificationsViewModel
import com.triangle.app.notifications.notificationsPalette
import com.triangle.app.tasks.ConfirmDialog
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One announcement in the Inbox: either received (with its notification, for read state) or sent by me. */
private data class InboxItem(val a: Announcement, val mine: Boolean, val notification: AppNotification?) {
    val key get() = "a-${a.id}-${if (mine) "sent" else "received"}"
}

/**
 * Inbox: where announcements live now. The Notifications list only shows a one-line
 * "New Inbox: “Title”" row for each; the full cards (text, photos, attachments, recipients),
 * and the + button to send a new announcement, are here. Sent announcements show too, with
 * their Edit / Delete menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    viewModel: NotificationsViewModel,
    myUid: String,
    onCompose: () -> Unit,
    onComposePoll: () -> Unit,
    onOpenPollDetails: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val announcements by viewModel.announcements.collectAsState()
    val sentIds by viewModel.sentIds.collectAsState()
    val polls by viewModel.polls.collectAsState()
    val sentPollIds by viewModel.sentPollIds.collectAsState()
    var showCreateSheet by remember { mutableStateOf(false) }
    var deletingPoll by remember { mutableStateOf<Poll?>(null) }
    val palette = notificationsPalette()
    val enabled = FeatureFlags.isEnabled(FeatureFlag.ANNOUNCEMENTS_ENABLED)

    var editing by remember { mutableStateOf<Announcement?>(null) }
    var deleting by remember { mutableStateOf<Announcement?>(null) }
    var lightboxUrl by remember { mutableStateOf<String?>(null) }
    var highlightedId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // Newest first: received (from their notification row) plus sent by me. A still-loading or recalled announcement is hidden.
    val feed = remember(state.notifications, announcements, sentIds, polls, sentPollIds, enabled) {
        if (!enabled) emptyList<Any>() else buildList<Any> {
            state.notifications.filter { it.type == "announcement" }.forEach { n ->
                n.itemId?.let { announcements[it] }?.let { add(InboxItem(it, mine = false, notification = n)) }
            }
            sentIds.forEach { id -> announcements[id]?.let { add(InboxItem(it, mine = true, notification = null)) } }
            state.notifications.filter { it.type == "poll" }.forEach { n ->
                n.itemId?.let { polls[it] }?.let { add(PollEntry(it, mine = false, notification = n)) }
            }
            sentPollIds.forEach { id -> polls[id]?.let { add(PollEntry(it, mine = true, notification = null)) } }
        }.sortedByDescending { entryTime(it) }
    }
    val rows = remember(feed) { groupByDate(feed) }
    val flat = remember(rows) { rows.flatMap { (label, items) -> listOf<Any>(label) + items } }
    val paging = com.triangle.app.ui.components.rememberLazyPaging(listState, flat.size, 12)

    // Cards load a moment after the list, so keep the list pinned to the top until the user scrolls.
    var userScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(listState) { snapshotFlow { listState.isScrollInProgress }.collect { if (it) userScrolled = true } }
    LaunchedEffect(flat) { if (!userScrolled && flat.isNotEmpty()) listState.scrollToItem(0) }

    // A tapped announcement notification asks to scroll to and highlight its card.
    val highlightReq by HighlightBus.request.collectAsState()
    val req = highlightReq?.takeIf { it.kind == HighlightKind.ANNOUNCEMENT }
    LaunchedEffect(req, flat) {
        val r = req ?: return@LaunchedEffect
        val index = flat.indexOfFirst { (it is InboxItem && it.a.id == r.itemId) || (it is PollEntry && it.p.id == r.itemId) }
        if (index < 0) return@LaunchedEffect
        paging.ensure(index + 1)
        listState.animateScrollToItem(index)
        highlightedId = r.itemId
        delay(3000)
        highlightedId = null
        HighlightBus.clear(r)
    }
    LaunchedEffect(req) {
        val r = req ?: return@LaunchedEffect
        delay(8000)
        HighlightBus.clear(r) // never appeared (recalled) — stop waiting
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inbox", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        },
        floatingActionButton = {
            if (enabled) {
                FloatingActionButton(onClick = { showCreateSheet = true }) { Icon(Icons.Default.Add, contentDescription = "New announcement or poll") }
            }
        }
    ) { padding ->
        if (flat.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Inbox, contentDescription = null, tint = palette.text3, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Your inbox is empty", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.text)
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(flat.take(paging.visible), key = { when (it) { is InboxItem -> it.key; is PollEntry -> it.key; else -> "h-$it" } }) { row ->
                    when (row) {
                        is String -> Text(row, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.text3, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                        is PollEntry -> PollCard(
                            poll = row.p,
                            myUid = myUid,
                            mine = row.mine,
                            unread = row.notification?.read == false,
                            highlighted = highlightedId == row.p.id,
                            onVote = { viewModel.vote(row.p.id, it) },
                            onClose = { viewModel.closePoll(row.p.id) },
                            onDelete = { deletingPoll = row.p },
                            onSeen = { row.notification?.let { if (!it.read) viewModel.markRead(it.id) } },
                            onViewVotes = { onOpenPollDetails(row.p.id) }
                        )
                        is InboxItem -> AnnouncementCard(
                            announcement = row.a,
                            isMine = row.mine,
                            unread = row.notification?.read == false,
                            highlighted = highlightedId == row.a.id,
                            onClick = { row.notification?.let { if (!it.read) viewModel.markRead(it.id) } },
                            onOpenAttachment = { att ->
                                // Photos open in the in-app viewer; videos and PDFs open in Drive's viewer/player.
                                if (att.isImage) lightboxUrl = att.url
                                else runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(att.viewUrl))) }
                                    .onFailure { Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show() }
                            },
                            onEdit = { editing = row.a },
                            onDelete = { deleting = row.a }
                        )
                    }
                }
                if (paging.hasMore) item(key = "load-more") { com.triangle.app.ui.components.LoadMoreFooter() }
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

    if (showCreateSheet) {
        ModalBottomSheet(onDismissRequest = { showCreateSheet = false }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text("Create", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                CreateOption(Icons.Filled.Campaign, "Announcement", "Share a message, photos or files", Color(0xFFE85D26)) {
                    showCreateSheet = false; onCompose()
                }
                Spacer(Modifier.height(8.dp))
                CreateOption(Icons.Outlined.BarChart, "Poll", "Ask a question and collect votes", Color(0xFF6C5CE7)) {
                    showCreateSheet = false; onComposePoll()
                }
            }
        }
    }

    deletingPoll?.let { p ->
        ConfirmDialog(
            title = "Delete this poll?",
            body = "It will be removed for you and everyone it was sent to, along with all votes. This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { viewModel.deletePoll(p.id); deletingPoll = null },
            onDismiss = { deletingPoll = null }
        )
    }

    lightboxUrl?.let { url ->
        Dialog(onDismissRequest = { lightboxUrl = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                com.triangle.app.ui.components.ZoomableImage(model = url, contentDescription = "Photo")
                IconButton(onClick = { lightboxUrl = null }, modifier = Modifier.padding(12.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

/** One poll in the Inbox: received (with its notification, for read state) or created by me. */
private data class PollEntry(val p: Poll, val mine: Boolean, val notification: AppNotification?) {
    val key get() = "p-${p.id}-${if (mine) "sent" else "received"}"
}

private fun entryTime(item: Any): Long = when (item) {
    is InboxItem -> item.a.createdAt
    is PollEntry -> item.p.createdAt
    else -> 0L
}

private fun groupByDate(feed: List<Any>): List<Pair<String, List<Any>>> {
    val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    val yesterday = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(System.currentTimeMillis() - 86_400_000L))
    val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.US)
    val labelFmt = SimpleDateFormat("MMMM d, yyyy", Locale.US)
    val groups = LinkedHashMap<String, MutableList<Any>>()
    feed.forEach { item ->
        val ms = entryTime(item)
        val label = when (dayFmt.format(Date(ms))) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> labelFmt.format(Date(ms))
        }
        groups.getOrPut(label) { mutableListOf() }.add(item)
    }
    return groups.map { it.key to it.value }
}

@Composable
private fun CreateOption(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = tint) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
