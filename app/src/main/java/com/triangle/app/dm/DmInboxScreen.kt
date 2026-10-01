package com.triangle.app.dm

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.triangle.app.data.models.DmThreadSummary
import com.triangle.app.ui.theme.TriangleBrandPurple
import com.triangle.app.ui.theme.triangleDarkTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AvatarSize = 42.dp
private val RowStart = 14.dp

/**
 * Messages list in a classic chat-list layout: a light top bar with the title centred and search / new-message
 * icons on the right, then plain full-width rows (square avatar, name, last message, time at the top right)
 * separated by thin lines that start under the text. Unread chats show a red count on the avatar's corner.
 */
@Composable
fun DmInboxScreen(
    viewModel: DmViewModel,
    onOpenThread: (String) -> Unit,
    onNewMessage: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.inbox.collectAsState()
    val dark = triangleDarkTheme()
    val barColor = if (dark) Color(0xFF1C1C1E) else Color(0xFFEDEDED)
    val pageColor = if (dark) Color(0xFF111111) else Color.White
    val textMain = MaterialTheme.colorScheme.onSurface
    val textMuted = if (dark) Color(0xFF8E8E93) else Color(0xFF9A9A9A)

    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val threads = remember(state.threads, q) {
        if (q.isEmpty()) state.threads
        else state.threads.filter { it.otherUserName.contains(q, ignoreCase = true) || it.lastMessage.contains(q, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().background(pageColor)) {
        // Top bar.
        Column(Modifier.fillMaxWidth().background(barColor).statusBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(56.dp)) {
                if (searching) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { searching = false; query = "" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    // Title centred on the whole bar (not just the space between the icons).
                    Text("Messages", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = textMain, modifier = Modifier.align(Alignment.Center))
                    IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Row(Modifier.align(Alignment.CenterEnd)) {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Search", modifier = Modifier.size(28.dp), tint = textMain)
                        }
                        IconButton(onClick = onNewMessage) {
                            Icon(Icons.Outlined.AddCircleOutline, contentDescription = "New message", modifier = Modifier.size(28.dp), tint = textMain)
                        }
                    }
                }
            }
        }

        if (threads.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (q.isNotEmpty()) "🔍" else "💬", fontSize = 44.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (q.isNotEmpty()) "No chats match “$q”" else "No messages yet",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textMain
                    )
                    if (q.isEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Tap the + at the top to message someone", fontSize = 12.5.sp, color = textMuted)
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(threads, key = { it.threadId }) { thread ->
                    ThreadRow(thread, textMain, textMuted, dark) { onOpenThread(thread.otherUserId) }
                }
            }
        }
    }
}

@Composable
private fun ThreadRow(thread: DmThreadSummary, textMain: Color, textMuted: Color, dark: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(start = RowStart, end = 14.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            // Square avatar with the unread count on its top-right corner.
            Box {
                SquareAvatar(thread.otherUserName, thread.otherUserPhotoUrl, dark)
                if (thread.unreadCount > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 0.dp)
                            .size(width = 20.dp, height = 20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFA5151)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (thread.unreadCount > 9) "9+" else thread.unreadCount.toString(),
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        thread.otherUserName.ifBlank { "..." },
                        fontSize = 16.sp, lineHeight = 19.sp, color = textMain, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(formatTime(thread.lastMessageAt), fontSize = 13.sp, color = textMuted, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(0.dp))
                Text(
                    thread.lastMessage.ifBlank { "No messages yet" },
                    fontSize = 15.sp, lineHeight = 18.sp, color = textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        // Thin line starting under the text, running to the right edge.
        HorizontalDivider(
            Modifier.padding(start = RowStart + AvatarSize + 12.dp),
            thickness = 0.5.dp,
            color = if (dark) Color(0xFF2C2C2E) else Color(0xFFE5E5E5)
        )
    }
}

/** Rounded-square profile picture, or the name's first letter on a tinted square when there is none. */
@Composable
private fun SquareAvatar(name: String, photoUrl: String?, dark: Boolean) {
    val shape = RoundedCornerShape(6.dp)
    if (!photoUrl.isNullOrBlank()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(AvatarSize).clip(shape)
        )
    } else {
        Box(
            Modifier.size(AvatarSize).clip(shape).background(TriangleBrandPurple.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text((name.firstOrNull() ?: '?').uppercase(), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TriangleBrandPurple)
        }
    }
}

private fun formatTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val now = Date()
    val date = Date(timestamp)
    val sameDay = SimpleDateFormat("yyyyMMdd", Locale.US).format(now) == SimpleDateFormat("yyyyMMdd", Locale.US).format(date)
    return if (sameDay) SimpleDateFormat("h:mm a", Locale.US).format(date) else SimpleDateFormat("MMM d", Locale.US).format(date)
}
