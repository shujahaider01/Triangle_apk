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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.models.DmThreadSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Native port of renderDmInbox() (script.js:1245-1302). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmInboxScreen(
    viewModel: DmViewModel,
    onOpenThread: (String) -> Unit,
    onNewMessage: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.inbox.collectAsState()
    val palette = dmPalette()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewMessage, containerColor = DmColors.Accent) {
                Icon(Icons.Default.Add, contentDescription = "New Message")
            }
        }
    ) { padding ->
        if (state.threads.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💬", fontSize = 44.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("No messages yet", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.text)
                    Spacer(Modifier.height(4.dp))
                    Text("Tap + to message someone", fontSize = 12.5.sp, color = palette.text3)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.threads, key = { it.threadId }) { thread ->
                    ThreadRow(thread, palette, onClick = { onOpenThread(thread.otherUserId) })
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
}

@Composable
private fun ThreadRow(thread: DmThreadSummary, palette: DmPalette, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface2)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(DmColors.Accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text((thread.otherUserName.firstOrNull() ?: '?').uppercase(), color = DmColors.Accent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(thread.otherUserName.ifBlank { "..." }, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = palette.text, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                thread.lastMessage.ifBlank { "No messages yet" },
                fontSize = 12.5.sp,
                color = if (thread.unreadCount > 0) palette.text else palette.text3,
                fontWeight = if (thread.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatTime(thread.lastMessageAt), fontSize = 10.5.sp, color = palette.text3)
            if (thread.unreadCount > 0) {
                Spacer(Modifier.height(4.dp))
                Box(Modifier.size(18.dp).clip(CircleShape).background(DmColors.Accent), contentAlignment = Alignment.Center) {
                    Text(thread.unreadCount.coerceAtMost(9).toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
                }
            }
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
