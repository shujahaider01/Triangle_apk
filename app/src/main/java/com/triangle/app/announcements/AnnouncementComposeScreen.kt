package com.triangle.app.announcements

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import com.triangle.app.data.AnnouncementRepository
import com.triangle.app.data.models.Announcement
import com.triangle.app.tasks.AssignMembersScreen
import com.triangle.app.tasks.decodePickedUri
import com.triangle.app.tasks.rememberCameraCaptureLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Composer opened from the Notifications tab's + button: title, text, up to
 * [AnnouncementRepository.MAX_ATTACHMENTS] attachments (photos, videos, PDFs), and recipients
 * picked from the sender's Circle.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AnnouncementComposeScreen(
    viewModel: AnnouncementViewModel,
    onSent: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()

    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var colorHex by remember { mutableStateOf(Announcement.DEFAULT_COLOR) }
    val attachments = remember { mutableStateListOf<AnnouncementRepository.PendingAttachment>() }
    var chosenUids by remember { mutableStateOf(emptySet<String>()) }
    var picking by remember { mutableStateOf(false) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }

    // Photos (multiple), videos and PDFs from one system picker. Photos are decoded up front (they get
    // downscaled at send time); videos/PDFs are only referenced by Uri so big files aren't held in memory.
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch {
            for (uri in uris) {
                if (attachments.size >= AnnouncementRepository.MAX_ATTACHMENTS) {
                    Toast.makeText(context, "You can attach up to ${AnnouncementRepository.MAX_ATTACHMENTS} files", Toast.LENGTH_SHORT).show()
                    break
                }
                val meta = withContext(Dispatchers.IO) { queryFileMeta(context, uri) }
                when {
                    meta.mimeType.startsWith("image/") -> {
                        val bmp = withContext(Dispatchers.IO) { decodePickedUri(context, uri) }
                        if (bmp != null) attachments.add(AnnouncementRepository.PendingAttachment(meta.name, meta.mimeType, meta.sizeBytes, bitmap = bmp))
                        else Toast.makeText(context, "Couldn't open ${meta.name}", Toast.LENGTH_SHORT).show()
                    }
                    meta.mimeType.startsWith("video/") || meta.mimeType == "application/pdf" -> {
                        if (meta.sizeBytes > AnnouncementRepository.MAX_FILE_BYTES) {
                            Toast.makeText(context, "${meta.name} is larger than ${AnnouncementRepository.MAX_FILE_BYTES / 1024 / 1024} MB", Toast.LENGTH_LONG).show()
                        } else attachments.add(AnnouncementRepository.PendingAttachment(meta.name, meta.mimeType, meta.sizeBytes, uri = uri))
                    }
                    else -> Toast.makeText(context, "${meta.name}: only photos, videos and PDFs can be attached", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    // Google's Drive consent screen — only shown the first time (or when access has lapsed) and only when sending attachments.
    val driveAuthLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onDriveAuthorizationResult(context, result.data)
    }
    val cameraLauncher = rememberCameraCaptureLauncher { bmp ->
        if (attachments.size < AnnouncementRepository.MAX_ATTACHMENTS) {
            attachments.add(AnnouncementRepository.PendingAttachment("Photo ${System.currentTimeMillis()}.jpg", "image/jpeg", 0L, bitmap = bmp))
        }
    }

    val recipients: Set<String> = chosenUids.intersect(state.members.map { it.uid }.toSet())
    val recipientNames = state.members.filter { it.uid in recipients }.map { it.name.substringBefore(' ').ifBlank { it.name } }

    BackHandler(enabled = picking) { picking = false }
    if (picking) {
        AssignMembersScreen(
            itemLabel = "",
            members = state.members,
            recentUids = emptyList(),
            initiallySelected = chosenUids,
            onClose = { picking = false },
            onDone = { chosenUids = it; picking = false },
            title = "Choose members",
            heading = "Send to",
            showRecentTab = false
        )
        return
    }

    fun send() {
        if (sending) return
        sending = true
        viewModel.send(context, title, body, colorHex, attachments.toList(), recipients, launchIntent = { driveAuthLauncher.launch(it) }) { result ->
            sending = false
            result.onSuccess { r ->
                val msg = when {
                    r.sent == 0 && r.skippedBlocked > 0 -> "Not sent — everyone selected has blocked announcements from you"
                    r.skippedBlocked > 0 -> "Sent to ${r.sent}, ${r.skippedBlocked} skipped (blocked announcements)"
                    else -> "Announcement sent to ${r.sent} ${if (r.sent == 1) "person" else "people"}"
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                if (r.sent > 0) onSent()
            }.onFailure {
                Toast.makeText(context, "Couldn't send the announcement. Please try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Announcement", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "Close") } },
                actions = {
                    TextButton(onClick = { send() }, enabled = title.isNotBlank() && body.isNotBlank() && recipients.isNotEmpty() && !sending) {
                        if (sending) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Send")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(AnnouncementRepository.TITLE_MAX) },
                label = { Text("Title") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            Column {
                Text("Title color", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                TitleColorPicker(selected = colorHex, onSelect = { colorHex = it })
            }
            OutlinedTextField(
                value = body,
                onValueChange = { body = it.take(AnnouncementRepository.BODY_MAX) },
                label = { Text("Announcement") },
                supportingText = { Text("${body.length}/${AnnouncementRepository.BODY_MAX}") },
                minLines = 5,
                maxLines = 10,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )

            Column {
                Text(
                    "Attachments (${attachments.size}/${AnnouncementRepository.MAX_ATTACHMENTS})",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    attachments.forEachIndexed { index, a ->
                        Box(Modifier.size(72.dp)) {
                            if (a.bitmap != null) {
                                Image(
                                    a.bitmap.asImageBitmap(), contentDescription = a.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                // Video / PDF: no preview, so a labelled tile.
                                Column(
                                    Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        if (a.isVideo) Icons.Default.Videocam else Icons.Default.PictureAsPdf,
                                        contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)
                                    )
                                    Text(a.name, fontSize = 9.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, lineHeight = 11.sp)
                                }
                            }
                            Box(
                                Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
                                    .clickable { attachments.removeAt(index) },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Default.Close, contentDescription = "Remove ${a.name}", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(14.dp)) }
                        }
                    }
                    if (attachments.size < AnnouncementRepository.MAX_ATTACHMENTS) {
                        Box(
                            Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { showAttachSheet = true },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.AttachFile, contentDescription = "Add attachment", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Photos, videos and PDFs · videos/PDFs up to ${AnnouncementRepository.MAX_FILE_BYTES / 1024 / 1024} MB each",
                    fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column {
                Text("Send to", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (!state.isLoading && state.members.isEmpty()) {
                    Text("Connect with people in your Circle first — announcements go to Circle members.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { picking = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Choose members", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            if (recipientNames.isNotEmpty()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    recipientNames.take(3).joinToString(", ") + if (recipientNames.size > 3) ", + ${recipientNames.size - 3}" else "",
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(if (recipients.isEmpty()) "Select" else "${recipients.size} selected", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (showAttachSheet) {
        AttachSourceSheet(
            onTakePhoto = { showAttachSheet = false; cameraLauncher() },
            onChooseFiles = { showAttachSheet = false; filePicker.launch(arrayOf("image/*", "video/*", "application/pdf")) },
            onDismiss = { showAttachSheet = false }
        )
    }
}

private data class FileMeta(val name: String, val mimeType: String, val sizeBytes: Long)

private fun queryFileMeta(context: Context, uri: Uri): FileMeta {
    val resolver = context.contentResolver
    var name = "Attachment"
    var size = 0L
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
            c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
        }
    }
    return FileMeta(name, resolver.getType(uri) ?: "application/octet-stream", size)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachSourceSheet(onTakePhoto: () -> Unit, onChooseFiles: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text("Add attachment", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp, start = 4.dp))
            AttachOption(Icons.Default.AddAPhoto, "Take a photo", onTakePhoto)
            Spacer(Modifier.height(8.dp))
            AttachOption(Icons.Default.AttachFile, "Choose photos, videos or PDFs", onChooseFiles)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AttachOption(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Swatches for the announcement card's thin title bar — shared by the composer and the edit dialog. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TitleColorPicker(selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Announcement.PALETTE.forEach { hex ->
            val color = parseAnnouncementColor(hex)
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(if (hex == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .clickable { onSelect(hex) }
            )
        }
    }
}

fun parseAnnouncementColor(hex: String): androidx.compose.ui.graphics.Color =
    runCatching { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex)) }
        .getOrDefault(androidx.compose.ui.graphics.Color(0xFFB8962E))

