package com.triangle.app.tasks

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.triangle.app.data.HabitPalette
import com.triangle.app.data.HabitRepository
import com.triangle.app.data.HabitStats
import com.triangle.app.data.SessionStore
import com.triangle.app.data.TaskNoteRepository
import com.triangle.app.data.UserRepository
import com.triangle.app.data.models.Habit
import com.triangle.app.data.models.TaskNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Native port of script.js's openHabitDetail() — shares the "ntd" hero/
 * accordion/timeline shell with TaskDetailScreen (see DetailPageKit.kt),
 * but with Habit's own 5-button action row (Analytics/Achievements/Note/
 * Photo/Complete) and Details fields (no due date, no checklist).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(
    session: SessionStore.Session,
    viewModel: TasksHabitsViewModel,
    habit: Habit,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onBack: () -> Unit,
    highlightOnOpen: Boolean = false
) {
    val streak = viewModel.streakFor(habit)
    val today = LocalDate.now().toString()

    val color = runCatching { Color(android.graphics.Color.parseColor(habit.color)) }.getOrDefault(MaterialTheme.colorScheme.primary)
    val svg = habit.iconSvg ?: HabitPalette.ICONS.getValue(HabitPalette.DEFAULT_ICON_KEY)
    val freqLabel = remember(habit.frequency) { frequencyLabel(habit) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showPhotoSheet by remember { mutableStateOf(false) }
    var rawPhotoForCrop by remember { mutableStateOf<Bitmap?>(null) }
    var lightboxUrl by remember { mutableStateOf<String?>(null) }
    var showAchievements by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var todaysNote by remember { mutableStateOf("") }

    LaunchedEffect(habit.id) {
        todaysNote = HabitRepository.getHabitNote(session.orgId, session.uid, habit.id, today)
    }

    // Only resolved when this habit was actually assigned by someone else
    // (Connect->Assign->Complete->Earn, Milestone 3) — a self-created habit's
    // createdBy is the viewer's own uid, so there's nothing new to show.
    var assignerName by remember(habit.id) { mutableStateOf<String?>(null) }
    var assignerPhotoUrl by remember(habit.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(habit.id, habit.createdBy) {
        val creatorId = habit.createdBy
        if (creatorId != null && creatorId != session.uid) {
            val record = UserRepository.fetchUserRecord(creatorId)
            assignerName = record?.name
            assignerPhotoUrl = record?.photoUrl
        } else {
            assignerName = null
            assignerPhotoUrl = null
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { decodePickedUri(context, uri) }
                if (bmp != null) rawPhotoForCrop = bmp
            }
        }
    }
    val cameraLauncher = rememberCameraCaptureLauncher { bmp -> rawPhotoForCrop = bmp }

    fun uploadPhoto(bmp: Bitmap) {
        scope.launch {
            runCatching {
                val url = TaskNoteRepository.uploadHabitPhoto(session.orgId, session.uid, habit.id, bmp)
                HabitRepository.addHabitPhoto(
                    session.orgId, habit.id,
                    TaskNote(type = "photo", content = url, userId = session.uid, userName = session.name, role = session.role, timestamp = System.currentTimeMillis())
                )
            }
        }
    }

    val scrollState = rememberScrollState()
    var heroHeightPx by remember { mutableIntStateOf(0) }
    val scrollProgress = rememberHeroScrollProgress(scrollState.value, heroHeightPx)

    // One-time flash when arriving from a notification tap — see
    // TaskDetailScreen's identical highlightOnOpen for why.
    val highlightAlpha = remember { Animatable(if (highlightOnOpen) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (highlightOnOpen) {
            delay(300)
            highlightAlpha.animateTo(0f, animationSpec = tween(1000))
        }
    }
    val screenBackground = MaterialTheme.colorScheme.background
    val cardBackground = androidx.compose.ui.graphics.lerp(screenBackground, color.copy(alpha = 0.25f), highlightAlpha.value)

    // Same "black area at the bottom" fix as TaskDetailScreen — without this,
    // the raw window background showed through below short content.
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // See TaskDetailScreen's identical structure for why the hero lives
        // inside the same scrollable Column as the card (offset upward to
        // overlap it) instead of as a separate full-screen layer behind it —
        // the latter silently blocked every touch meant for the hero buttons.
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
                    title = habit.name,
                    pillIcon = Icons.Default.Autorenew,
                    pillText = freqLabel,
                    actions = buildList {
                        add(DetailHeroAction(Icons.Default.BarChart, "Analytics", onClick = onOpenAnalytics))
                        add(DetailHeroAction(Icons.Default.EmojiEvents, "Streak", onClick = { showAchievements = true }))
                        if (com.triangle.app.data.FeatureFlags.isEnabled(com.triangle.app.data.FeatureFlag.TASK_HABIT_NOTES_ENABLED)) {
                            add(DetailHeroAction(Icons.Default.NoteAdd, "Add Note", onClick = { showNoteDialog = true }))
                            add(DetailHeroAction(Icons.Default.AddAPhoto, "Add Photo", onClick = { showPhotoSheet = true }))
                        }
                    },
                    onBack = onBack,
                    onEdit = if (canEdit) onEdit else null
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
                    AccordionSection(title = "General", initiallyOpen = habit.description.isNotBlank()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("DESCRIPTION", fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                            Spacer(Modifier.height(8.dp))
                            if (habit.description.isNotBlank()) {
                                Text(habit.description, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 22.sp)
                            } else {
                                Text("No description added.", fontSize = 15.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            }
                        }
                    }

                    AccordionSection(title = "Details", initiallyOpen = true) {
                        // No Category row: unlike Task, the native Habit model has no
                        // category field at all (never ported in Milestone 2) — the source's
                        // `habit.category || 'Personal'` fallback would be fabricated data
                        // here, so this row is dropped rather than showing a fake constant.
                        Column {
                            assignerName?.let { DetailInfoRowPerson("Assigned By", it, assignerPhotoUrl) }
                            DetailInfoRowPerson("Assigned To", if (assignerName != null) "You" else session.name, session.photoUrl)
                            DetailInfoRow("Frequency", freqLabel)
                            DetailInfoRow("Current Streak", "${streak.current} day${if (streak.current == 1) "" else "s"}")
                            DetailInfoRow("Habit Created", habit.startDate, isLast = true)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Column(Modifier.padding(horizontal = 16.dp)) {
                    val entries = mutableListOf<TimelineEntryData>()
                    if (todaysNote.isNotBlank()) {
                        entries.add(
                            TimelineEntryData(
                                avatarInitial = session.name.take(1).uppercase().ifBlank { "?" },
                                avatarColor = color,
                                username = session.name,
                                timestampMillis = System.currentTimeMillis(),
                                isLast = habit.photos.isEmpty(),
                                body = { Text(todaysNote, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 20.sp) }
                            )
                        )
                    }
                    val photosOldestFirst = habit.photos.reversed()
                    photosOldestFirst.forEachIndexed { index, note ->
                        entries.add(
                            TimelineEntryData(
                                avatarInitial = note.userName.take(1).uppercase().ifBlank { "?" },
                                avatarColor = color,
                                username = note.userName,
                                timestampMillis = note.timestamp,
                                isLast = index == photosOldestFirst.lastIndex,
                                body = {
                                    AsyncImage(
                                        model = note.content,
                                        contentDescription = "Habit photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp)).clickable { lightboxUrl = note.content }
                                    )
                                }
                            )
                        )
                    }
                    NotesTimeline(entries = entries)
                }
                Spacer(Modifier.height(80.dp))
            }
        }

        DetailStickyHeader(title = habit.name, visible = scrollProgress >= 1f, onBack = onBack)
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
            onConfirm = { cropped -> uploadPhoto(cropped); rawPhotoForCrop = null },
            onCancel = { rawPhotoForCrop = null }
        )
    }

    lightboxUrl?.let { url ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { lightboxUrl = null }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(model = url, contentDescription = "Photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                IconButton(onClick = { lightboxUrl = null }, modifier = Modifier.padding(12.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }

    if (showAchievements) {
        HabitAchievementsSheet(habit = habit, streak = streak, onDismiss = { showAchievements = false })
    }

    if (showNoteDialog) {
        val sheetState = rememberModalBottomSheetState()
        var draft by remember(todaysNote) { mutableStateOf(todaysNote) }
        ModalBottomSheet(onDismissRequest = { showNoteDialog = false }, sheetState = sheetState) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("📝 Add Note", fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text("Note for today", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { showNoteDialog = false }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            scope.launch {
                                runCatching { HabitRepository.addHabitNote(session.orgId, session.uid, habit.id, today, draft.trim()) }
                                todaysNote = draft.trim()
                                showNoteDialog = false
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Save Note") }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** Matches _habitFreqLabel() — human-readable label for a habit's frequency config. */
fun frequencyLabel(habit: Habit): String = when (habit.frequency.type) {
    "everyday" -> "Everyday"
    "daysOfWeek" -> {
        val names = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        habit.frequency.days.sorted().joinToString(", ") { names.getOrElse(it) { "?" } }
    }
    "daysOfMonth" -> habit.frequency.dates.sorted().joinToString(", ") { "${it}${daySuffix(it)}" }
    "perPeriod" -> "${habit.frequency.count ?: 1}x per ${habit.frequency.unit ?: "week"}"
    else -> "Everyday"
}

private fun daySuffix(day: Int): String = if (day in 11..13) "th" else when (day % 10) {
    1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th"
}
