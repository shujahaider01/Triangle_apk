package com.triangle.app.dm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.SessionStore
import com.triangle.app.data.models.DmMessage
import com.triangle.app.ui.components.Avatar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Native port of renderDmThread() (script.js:1552-1624) — includes the real, working block/unblock feature (toggleDmBlock, script.js:1098-1109). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmThreadScreen(
    viewModel: DmViewModel,
    session: SessionStore.Session,
    otherUserId: String,
    onBack: () -> Unit
) {
    val state by viewModel.thread.collectAsState()
    val palette = dmPalette()
    var draft by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(otherUserId) { viewModel.openThread(otherUserId) }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    Scaffold(
        // The header lives in Scaffold's fixed topBar slot, never inside the
        // scrollable content below — imePadding() here just reserves space
        // for the keyboard within the CONTENT area (message list + input
        // row) so the keyboard pushes those up without the OS's default
        // whole-window pan (which would otherwise drag the header along
        // with it, since this app runs edge-to-edge with no
        // windowSoftInputMode declared to prevent that).
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(state.otherUserName, state.otherUserPhotoUrl, size = 32.dp, backgroundColor = DmColors.Accent.copy(alpha = 0.18f), textColor = DmColors.Accent, fontSize = 13.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(state.otherUserName.ifBlank { "..." }, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (state.blockedByMe) "Unblock" else "Block") },
                            onClick = { menuOpen = false; viewModel.toggleBlock() }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(message, isMine = message.senderId == session.uid, palette = palette)
                }
            }

            if (state.blocked) {
                Box(Modifier.fillMaxWidth().background(palette.surface2).padding(12.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.blockedByMe) "You blocked this conversation" else "You can't reply to this conversation",
                        fontSize = 12.5.sp,
                        color = palette.text3
                    )
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text("Message") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4
                    )
                    IconButton(
                        onClick = { if (draft.isNotBlank()) { viewModel.sendMessage(draft); draft = "" } },
                        enabled = draft.isNotBlank()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = if (draft.isNotBlank()) DmColors.Accent else palette.text3)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: DmMessage, isMine: Boolean, palette: DmPalette) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (isMine) 16.dp else 4.dp, bottomEnd = if (isMine) 4.dp else 16.dp))
                .background(if (isMine) DmColors.MyBubble else palette.theirBubble)
                .padding(12.dp, 8.dp)
        ) {
            Text(message.text, fontSize = 14.sp, color = if (isMine) Color.White else palette.text)
            Spacer(Modifier.height(2.dp))
            Text(
                formatTime(message.createdAt),
                fontSize = 9.5.sp,
                color = if (isMine) Color.White.copy(alpha = 0.7f) else palette.text3
            )
        }
    }
}

private fun formatTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return SimpleDateFormat("h:mm a", Locale.US).format(Date(timestamp))
}
