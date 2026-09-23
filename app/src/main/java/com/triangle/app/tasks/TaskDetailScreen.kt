package com.triangle.app.tasks

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import androidx.compose.runtime.LaunchedEffect
import com.triangle.app.data.HabitPalette
import com.triangle.app.data.SessionStore
import com.triangle.app.ui.components.rememberDriveImageUploader
import com.triangle.app.data.TaskRepository
import com.triangle.app.data.UserRepository
import com.triangle.app.data.models.Task
import com.triangle.app.data.models.TaskNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Native port of script.js's _openNewTaskDetail() — the full-bleed colored
 * hero with parallax fade + icon ring, sticky title on scroll, collapsible
 * General/Details/Checklist accordion, and a connected-line notes/photos
 * timeline. See DetailPageKit.kt for the shared pieces this and
 * HabitDetailScreen both build on (they're one shared page in the source,
 * class prefix "ntd").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    session: SessionStore.Session,
    viewModel: TasksHabitsViewModel,
    task: Task,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onAddNote: () -> Unit,
    onBack: () -> Unit,
    highlightOnOpen: Boolean = false
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showArchiveConfirm by remember { mutableStateOf(false) }
    val state by viewModel.uiState.collectAsState()
    val done = viewModel.isDone(task, state.completions)
    val color = runCatching { Color(android.graphics.Color.parseColor(task.iconColor ?: HabitPalette.DEFAULT_COLOR)) }
        .getOrDefault(MaterialTheme.colorScheme.primary)
    val svg = task.iconSvg ?: HabitPalette.ICONS.getValue(HabitPalette.DEFAULT_ICON_KEY)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Only resolved when this task was actually assigned by someone else
    // (Connect->Assign->Complete->Earn, Milestone 3) — a self-created task's
    // createdBy is the viewer's own uid, so there's nothing new to show.
    var assignerName by remember(task.id) { mutableStateOf<String?>(null) }
    var assignerPhotoUrl by remember(task.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(task.id, task.createdBy) {
        val creatorId = task.createdBy
        if (creatorId != null && creatorId != session.uid) {
            val record = UserRepository.fetchUserRecord(creatorId)
            assignerName = record?.name
            assignerPhotoUrl = record?.photoUrl
        } else {
            assignerName = null
            assignerPhotoUrl = null
        }
    }

    var showPhotoSheet by remember { mutableStateOf(false) }
    var rawPhotoForCrop by remember { mutableStateOf<Bitmap?>(null) }
    var lightboxUrl by remember { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { decodePickedUri(context, uri) }
                if (bmp != null) rawPhotoForCrop = bmp
            }
        }
    }
    val cameraLauncher = rememberCameraCaptureLauncher { bmp -> rawPhotoForCrop = bmp }

    val driveUploader = rememberDriveImageUploader()
    fun uploadPhotoNote(bmp: Bitmap) {
        scope.launch {
            runCatching {
                val url = driveUploader.upload(bmp, "TriangleTaskNote_${task.id}_${System.currentTimeMillis()}.jpg")
                TaskRepository.addNote(
                    session.orgId, task.id,
                    TaskNote(type = "photo", content = url, userId = session.uid, userName = session.name, role = session.role, timestamp = System.currentTimeMillis())
                )
            }
        }
    }

    val scrollState = rememberScrollState()
    var heroHeightPx by remember { mutableIntStateOf(0) }
    val scrollProgress = rememberHeroScrollProgress(scrollState.value, heroHeightPx)

    // One-time flash when arriving from a notification tap, so it's obvious
    // at a glance which task the notification was about — fades back to the
    // normal background over ~1s instead of staying tinted.
    val highlightAlpha = remember { Animatable(if (highlightOnOpen) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (highlightOnOpen) {
            delay(300)
            highlightAlpha.animateTo(0f, animationSpec = tween(1000))
        }
    }
    val screenBackground = MaterialTheme.colorScheme.background
    val cardBackground = androidx.compose.ui.graphics.lerp(screenBackground, color.copy(alpha = 0.25f), highlightAlpha.value)

    // Without an explicit background here, the raw window background (black,
    // since MainActivity runs edge-to-edge with transparent system bars)
    // showed through below the content whenever the scrollable Column ended
    // above the bottom of the screen — the "black area at the bottom" bug.
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // The hero is a normal in-flow child of the SAME scrollable Column as
        // the card, not a separate full-screen overlay behind a spacer — an
        // earlier version used a fillMaxSize() scrollable Column layered over
        // a fixed hero, which visually worked but silently ate every touch
        // meant for the hero's buttons (a later sibling in a Box always wins
        // hit-testing over its full bounds, even where its own content, like
        // a blank Spacer, is invisible). The card overlaps the hero's bottom
        // via a negative offset instead, so only its own actual (rounded,
        // opaque) area can intercept touches, never the hero's buttons above it.
        Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { heroHeightPx = it.size.height }
                    .graphicsLayer { alpha = (1f - scrollProgress * 1.3f).coerceIn(0f, 1f) }
            ) {
                DetailHero(
                    heroColor = color,
                    iconSvg = svg,
                    title = task.title,
                    pillIcon = Icons.Default.Star,
                    pillText = if (task.points > 0) "${task.points} XP" else "—",
                    actions = buildList {
                        if (com.triangle.app.data.FeatureFlags.isEnabled(com.triangle.app.data.FeatureFlag.TASK_HABIT_NOTES_ENABLED)) {
                            add(DetailHeroAction(Icons.Default.NoteAdd, "Add Note", enabled = !done, onClick = onAddNote))
                            add(DetailHeroAction(Icons.Default.AddAPhoto, "Add Photo", enabled = !done, onClick = { showPhotoSheet = true }))
                        }
                    },
                    onBack = onBack,
                    menuOptions = buildList {
                        if (canEdit) {
                            add(DetailMenuOption("Edit", onClick = onEdit))
                            add(DetailMenuOption("Delete", destructive = true, onClick = { showDeleteConfirm = true }))
                        } else {
                            add(DetailMenuOption("Archive", onClick = { showArchiveConfirm = true }))
                        }
                    }
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .offset(y = (-60).dp)
                    .defaultMinSize(minHeight = 500.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(cardBackground)
            ) {
                Spacer(Modifier.height(18.dp))
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AccordionSection(title = "General", initiallyOpen = task.description.isNotBlank()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("DESCRIPTION", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                            Spacer(Modifier.height(8.dp))
                            if (task.description.isNotBlank()) {
                                Text(task.description, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 22.sp)
                            } else {
                                Text("No description added.", fontSize = 15.sp, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            }
                        }
                    }

                    AccordionSection(title = "Details", initiallyOpen = true) {
                        Column {
                            assignerName?.let { DetailInfoRowPerson("Assigned By", it, assignerPhotoUrl) }
                            DetailInfoRowPerson("Assigned To", if (assignerName != null) "You" else session.name, session.photoUrl)
                            if (task.points > 0) DetailInfoRowChip("Points", "${task.points} XP", color)
                            DetailInfoRow("Task Created", task.createdDate ?: "—")
                            DetailInfoRow("Due Date", task.dueDate ?: "—", valueColor = if (task.dueDate != null) Color(0xFFEF4444) else null)
                            DetailInfoRow("Approval", if (task.approvalRequired) "Required" else "Not required", isLast = true)
                        }
                    }

                    if (task.checklist.isNotEmpty()) {
                        val doneCount = task.checklist.count { it.done }
                        AccordionSection(title = "Checklist", badge = "$doneCount/${task.checklist.size}", initiallyOpen = true) {
                            Column {
                                task.checklist.forEachIndexed { index, item ->
                                    CircularCheckItem(
                                        text = item.text,
                                        done = item.done,
                                        accentColor = color,
                                        onToggle = { viewModel.toggleChecklistItem(task, item.id, !item.done) },
                                        isLast = index == task.checklist.lastIndex
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Column(Modifier.padding(horizontal = 16.dp)) {
                    val notesOldestFirst = task.notes.reversed()
                    NotesTimeline(
                        entries = notesOldestFirst.mapIndexed { index, note ->
                            TimelineEntryData(
                                avatarInitial = note.userName.take(1).uppercase().ifBlank { "?" },
                                avatarColor = color,
                                username = note.userName,
                                timestampMillis = note.timestamp,
                                isLast = index == notesOldestFirst.lastIndex,
                                body = {
                                    if (note.type == "photo") {
                                        AsyncImage(
                                            model = note.content,
                                            contentDescription = "Note photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp)).clickable { lightboxUrl = note.content }
                                        )
                                    } else {
                                        Text(parseNoteMarkdown(note.content), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 20.sp)
                                    }
                                }
                            )
                        }
                    )
                }
                Spacer(Modifier.height(80.dp))
            }
        }

        DetailStickyHeader(title = task.title, visible = scrollProgress >= 1f, onBack = onBack)
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Delete this task?",
            body = "This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { showDeleteConfirm = false; onDelete(); onBack() },
            onDismiss = { showDeleteConfirm = false }
        )
    }
    if (showArchiveConfirm) {
        ConfirmDialog(
            title = "Archive this task?",
            body = "It'll be hidden from your lists. You can find it later under Settings > Archived Items.",
            confirmLabel = "Archive",
            onConfirm = { showArchiveConfirm = false; onArchive(); onBack() },
            onDismiss = { showArchiveConfirm = false }
        )
    }

    if (showPhotoSheet) {
        PhotoSourceSheet(
            onTakePhoto = { showPhotoSheet = false; cameraLauncher() },
            onFromGallery = { showPhotoSheet = false; galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onDismiss = { showPhotoSheet = false }
        )
    }

    rawPhotoForCrop?.let { raw ->
        PhotoCropView(
            sourceBitmap = raw,
            onConfirm = { cropped -> uploadPhotoNote(cropped); rawPhotoForCrop = null },
            onCancel = { rawPhotoForCrop = null }
        )
    }

    lightboxUrl?.let { url ->
        Dialog(onDismissRequest = { lightboxUrl = null }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(model = url, contentDescription = "Photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                IconButton(onClick = { lightboxUrl = null }, modifier = Modifier.padding(12.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

internal fun decodePickedUri(context: android.content.Context, uri: Uri): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ -> decoder.isMutableRequired = true }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
} catch (e: Exception) {
    null
}
