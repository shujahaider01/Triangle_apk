package com.triangle.app.announcements

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import com.triangle.app.data.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.models.Announcement
import com.triangle.app.data.models.AnnouncementAttachment
import com.triangle.app.tasks.CompactMenuPopup
import com.triangle.app.tasks.DetailMenuOption
import com.triangle.app.ui.components.Avatar
import com.triangle.app.ui.components.deepLinkHighlight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Person(val name: String, val photoUrl: String?)

/**
 * An announcement rendered inline in the Notifications list — no tap-through screen: a thin
 * colored bar carrying the title, then sender avatar/name with an "Announcement" tag, the
 * attachments as compact file chips (opened on tap, never shown inline) with "Save attachments",
 * and the full description. The sender sees the identical card plus a three-dot Edit/Delete menu.
 */
@Composable
fun AnnouncementCard(
    announcement: Announcement,
    isMine: Boolean,
    unread: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    onOpenAttachment: (AnnouncementAttachment) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showRecipients by remember { mutableStateOf(false) }
    // Recipients with their current names and profile pictures, looked up live from their uids (so older
    // announcements, sent before names/photos were stored, work too); the stored names are the fallback.
    val people by produceState(
        announcement.recipientNames.values.sortedBy { it.lowercase() }.map { Person(it, null) },
        announcement.id, announcement.recipients.keys
    ) {
        value = coroutineScope {
            announcement.recipients.keys.map { uid ->
                async {
                    val record = runCatching { UserRepository.fetchUserRecord(uid) }.getOrNull()
                    Person(record?.name?.takeIf { it.isNotBlank() } ?: announcement.recipientNames[uid] ?: "Unknown", record?.photoUrl)
                }
            }.awaitAll().sortedBy { it.name.lowercase() }
        }
    }
    val names = people.map { it.name }
    // The sender's current profile picture (falls back to the one stored when it was sent).
    val senderPhoto by produceState(announcement.senderPhotoUrl, announcement.senderUid) {
        value = runCatching { UserRepository.fetchUserRecord(announcement.senderUid)?.photoUrl }.getOrNull() ?: announcement.senderPhotoUrl
    }
    val accent = parseAnnouncementColor(announcement.color)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        Modifier
            .fillMaxWidth()
            .deepLinkHighlight(highlighted)
            .clip(RoundedCornerShape(14.dp))
            .background(if (unread) accent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(bottom = 12.dp)
    ) {
        // Thin colored title bar — flush to both card edges (the card's own rounded clip shapes its top corners).
        Box(
            Modifier.fillMaxWidth().background(accent).padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(announcement.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
            Avatar(announcement.senderName, senderPhoto, size = 44.dp, fontSize = 16.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(announcement.senderName.ifBlank { "Someone" }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                if (names.isNotEmpty()) {
                    // "To A, B, +N" — the names shrink with an ellipsis if long, but "+N" always stays visible. The "⋯" below opens the full list.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("To ${names.take(2).joinToString(", ")}", fontSize = 13.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (names.size > 2) Text(", +${names.size - 2}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = muted, maxLines = 1)
                    }
                }
            }
            // Announcement icon (top-right); the sender's Edit/Delete menu sits right beside it.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Campaign, contentDescription = "Announcement", tint = accent, modifier = Modifier.padding(top = 4.dp).size(26.dp))
                if (isMine) {
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.padding(top = 4.dp).size(32.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = muted)
                        }
                        if (menuOpen) {
                            CompactMenuPopup(
                                listOf(
                                    DetailMenuOption("Edit", onClick = onEdit),
                                    DetailMenuOption("Delete", destructive = true, onClick = onDelete)
                                ),
                                onDismiss = { menuOpen = false }
                            )
                        }
                    }
                }
            }
        }

        if (announcement.attachments.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                announcement.attachments.forEach { a ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { onOpenAttachment(a) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            when { a.isVideo -> Icons.Default.Videocam; a.isPdf -> Icons.Default.PictureAsPdf; else -> Icons.Default.Image },
                            contentDescription = null, tint = accent, modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.widthIn(max = 150.dp)) {
                            // Explicit tight line heights so the name and size sit close together.
                            Text(a.name, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, color = onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val type = a.name.substringAfterLast('.', "").uppercase().ifBlank { if (a.isVideo) "VIDEO" else if (a.isPdf) "PDF" else "FILE" }
                            Text("$type · ${formatSize(a.sizeBytes)}", fontSize = 11.sp, lineHeight = 13.sp, color = muted)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AttachFile, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                val total = announcement.attachments.sumOf { it.sizeBytes }
                val count = announcement.attachments.size
                Text("$count attachment${if (count == 1) "" else "s"} (${formatSize(total)})", fontSize = 12.5.sp, color = muted, modifier = Modifier.weight(1f))
                Row(Modifier.clickable { saveAttachments(context, announcement.attachments) }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save attachments", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Text(
            announcement.body,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = onSurface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        )

        // Bottom row: "⋯" (bottom-left) opens the full recipients list; date · time (and "edited") on the same line at the right.
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (names.isNotEmpty()) {
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable { showRecipients = true }.padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = "Show recipients", tint = muted, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            val stamp = SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(announcement.createdAt))
            Text(if (announcement.editedAt != null) "$stamp · edited" else stamp, fontSize = 12.sp, color = muted)
        }
    }

    if (showRecipients) {
        AlertDialog(
            onDismissRequest = { showRecipients = false },
            title = { Text("Sent to ${names.size} ${if (names.size == 1) "person" else "people"}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    people.forEach { person ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(person.name, person.photoUrl, size = 36.dp, fontSize = 13.sp)
                            Spacer(Modifier.width(12.dp))
                            Text(person.name, fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRecipients = false }) { Text("Close") } }
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

/** Hands each attachment to the system DownloadManager (lands in the Downloads folder with its progress notification). */
private fun saveAttachments(context: Context, attachments: List<AnnouncementAttachment>) {
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val queued = attachments.count { a ->
        runCatching {
            manager.enqueue(
                DownloadManager.Request(Uri.parse(a.downloadUrl))
                    .setTitle(a.name)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Triangle_${System.currentTimeMillis()}_${a.name}")
            )
        }.isSuccess
    }
    Toast.makeText(
        context,
        if (queued > 0) "Saving $queued attachment${if (queued == 1) "" else "s"} to Downloads" else "Couldn't save the attachments",
        Toast.LENGTH_SHORT
    ).show()
}
