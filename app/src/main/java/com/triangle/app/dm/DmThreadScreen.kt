package com.triangle.app.dm

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.WavingHand
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.SentimentSatisfiedAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.triangle.app.data.HighlightBus
import com.triangle.app.data.HighlightKind
import com.triangle.app.data.SessionStore
import com.triangle.app.data.models.DmMessage
import com.triangle.app.ui.components.deepLinkHighlight
import com.triangle.app.ui.theme.TriangleBrandPurple
import com.triangle.app.ui.theme.triangleDarkTheme
import com.triangle.app.util.capFirst
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ChatAvatar = 42.dp
private const val SEPARATOR_GAP_MS = 5 * 60 * 1000L

/**
 * Chat screen in a classic messenger layout: a grey top bar with a back chevron, the name centred and a
 * "…" menu; time stamps centred between groups of messages; each message with its sender's rounded-square
 * picture and a small pointer on the bubble; and a bottom bar with a voice button at the left, the message
 * box, an emoji button, and Send once there is text.
 */
@Composable
fun DmThreadScreen(
    viewModel: DmViewModel,
    session: SessionStore.Session,
    otherUserId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.thread.collectAsState()
    val dark = triangleDarkTheme()
    val pageColor = if (dark) Color(0xFF111111) else Color(0xFFEDEDED)
    val barColor = if (dark) Color(0xFF1C1C1E) else Color(0xFFEDEDED)
    val inputBarColor = if (dark) Color(0xFF1C1C1E) else Color(0xFFF7F7F7)
    val lineColor = if (dark) Color(0xFF2C2C2E) else Color(0xFFDCDCDC)
    val textMain = MaterialTheme.colorScheme.onSurface
    val textMuted = if (dark) Color(0xFF8E8E93) else Color(0xFF9A9A9A)

    var draft by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var emojiPanel by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<DmMessage?>(null) }
    var actionBounds by remember { mutableStateOf(Rect.Zero) }
    var replyingTo by remember { mutableStateOf<DmMessage?>(null) }
    var deleteTarget by remember { mutableStateOf<DmMessage?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val inputFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(replyingTo) {
        if (replyingTo != null) { delay(150); runCatching { inputFocus.requestFocus() }; keyboard?.show() }
    }
    val listState = rememberLazyListState()

    // A tapped chat notification asked to highlight one specific message (see HighlightBus);
    // until it's found and shown, the usual "jump to newest" scroll must not win over it.
    val highlightReq by HighlightBus.request.collectAsState()
    val messageReq = highlightReq?.takeIf { it.kind == HighlightKind.MESSAGE }
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }

    // Messages with a centred time stamp inserted before the first message and after every 5+ minute pause.
    val rows = remember(state.messages) {
        buildList<Any> {
            var prev = 0L
            state.messages.forEach { m ->
                if (prev == 0L || m.createdAt - prev > SEPARATOR_GAP_MS) add(separatorLabel(m.createdAt))
                add(m)
                prev = m.createdAt
            }
        }
    }

    var panelOpen by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var viewerImage by remember { mutableStateOf<String?>(null) }
    var viewerVideo by remember { mutableStateOf<String?>(null) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val driveUploader = com.triangle.app.ui.components.rememberDriveImageUploader()

    // Prepares and uploads photos/videos one by one; a toast reports a failure.
    fun sendMedia(prepare: suspend () -> PreparedMedia?) {
        if (uploading) return
        scope.launch {
            uploading = true
            try {
                val token = driveUploader.accessToken()
                val media = prepare()
                if (media == null) Toast.makeText(context, "Couldn't open that file", Toast.LENGTH_SHORT).show()
                else if (!viewModel.sendMedia(media, token)) Toast.makeText(context, "Couldn't send. Please try again.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Couldn't send. Please try again.", Toast.LENGTH_SHORT).show()
            } finally {
                uploading = false
            }
        }
    }
    fun saveMedia(url: String, isVideo: Boolean) {
        scope.launch {
            Toast.makeText(context, "Saving…", Toast.LENGTH_SHORT).show()
            val ok = saveMediaToGallery(context, url, isVideo)
            Toast.makeText(context, if (ok) "Saved to ${if (isVideo) "Movies" else "Pictures"}/Triangle" else "Couldn't save", Toast.LENGTH_SHORT).show()
        }
    }
    // ── Voice messages ──
    var voiceMode by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var voiceAction by remember { mutableStateOf(0) } // 0 = send, 1 = cancel, 2 = convert to text
    val levels = remember { androidx.compose.runtime.mutableStateListOf<Float>() }
    val recorder = remember { VoiceRecorder(context) }
    var playingId by remember { mutableStateOf<String?>(null) }
    val player = remember { arrayOfNulls<android.media.MediaPlayer>(1) }
    val micPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, "Microphone permission is needed for voice messages", Toast.LENGTH_SHORT).show()
    }
    fun stopPlayback() {
        player[0]?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player[0] = null
        playingId = null
    }
    fun playVoice(msg: DmMessage) {
        if (playingId == msg.id) { stopPlayback(); return }
        stopPlayback()
        val url = msg.mediaUrl ?: return
        playingId = msg.id
        scope.launch {
            val file = cachedChatFile(context, url, "m4a")
            if (file == null) { playingId = null; Toast.makeText(context, "Couldn't load this voice message", Toast.LENGTH_SHORT).show(); return@launch }
            if (playingId != msg.id) return@launch // tapped again / left while it downloaded
            runCatching {
                val mp = android.media.MediaPlayer()
                mp.setDataSource(file.absolutePath)
                mp.setOnCompletionListener { runCatching { it.release() }; if (player[0] === it) player[0] = null; if (playingId == msg.id) playingId = null }
                mp.prepare()
                mp.start()
                player[0] = mp
            }.onFailure { playingId = null }
        }
    }
    fun finishRecording(action: Int) {
        val media = recorder.stop(discard = action != 0)
        recording = false
        when {
            action == 2 -> Toast.makeText(context, "Convert to text is coming soon", Toast.LENGTH_SHORT).show()
            action == 0 && media != null -> {
                if ((media.durationMs ?: 0L) < 1000L) Toast.makeText(context, "Message too short", Toast.LENGTH_SHORT).show()
                else scope.launch { while (uploading) delay(100); sendMedia { media } }
            }
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { recorder.stop(discard = true); stopPlayback() }
    }
    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        val startedAt = System.currentTimeMillis()
        levels.clear()
        while (recording) {
            levels.add(recorder.level())
            if (levels.size > 30) levels.removeAt(0)
            if (System.currentTimeMillis() - startedAt >= 60_000L) { finishRecording(voiceAction); break } // 60 s cap
            delay(70)
        }
    }
    val tooBig = { android.os.Handler(android.os.Looper.getMainLooper()).post { Toast.makeText(context, "Videos can be up to 25 MB", Toast.LENGTH_LONG).show() }; Unit }
    val galleryPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(9)
    ) { uris ->
        if (uris.isNotEmpty()) {
            panelOpen = false
            scope.launch {
                for (u in uris) { while (uploading) delay(200); sendMedia { prepareUri(context, u, tooBig) }; delay(50) }
            }
        }
    }
    val openCamera = com.triangle.app.tasks.rememberCameraCaptureLauncher { bmp ->
        panelOpen = false
        sendMedia { withContext(kotlinx.coroutines.Dispatchers.IO) { prepareBitmap(bmp) } }
    }
    androidx.activity.compose.BackHandler(enabled = panelOpen) { panelOpen = false }
    LaunchedEffect(panelOpen) { if (panelOpen && rows.isNotEmpty()) listState.scrollToItem(rows.lastIndex) }
    // When the keyboard opens (or closes) the list area resizes; keep the newest message in view by following
    // the keyboard's height as it animates, instead of leaving the list where it was.
    val imeBottom = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)
    // The panel takes the keyboard's place: it is sized to (last keyboard height - keyboard height right now), so
    // the keyboard sliding away and the panel growing add up to a constant and nothing jumps. Going back to the
    // keyboard keeps the panel until the keyboard is fully up again.
    val density = androidx.compose.ui.platform.LocalDensity.current
    var keyboardPx by remember { mutableStateOf(0) }
    var keyboardWanted by remember { mutableStateOf(false) }
    // Remember the keyboard's height once it has settled, and drop the panel only after the keyboard has finished
    // rising (the panel shrinks in step with it, so the swap is continuous).
    LaunchedEffect(imeBottom, keyboardWanted, panelOpen) {
        if (imeBottom > 0) {
            delay(150)
            if (!panelOpen) keyboardPx = imeBottom
            if (keyboardWanted) { panelOpen = false; keyboardWanted = false }
        }
    }
    LaunchedEffect(keyboardWanted) { if (keyboardWanted) { delay(2000); if (keyboardWanted) { panelOpen = false; keyboardWanted = false } } }
    val panelHeight = with(density) { ((if (keyboardPx > 0) keyboardPx else 300.dp.roundToPx()) - imeBottom).coerceAtLeast(0).toDp() }
    LaunchedEffect(imeBottom) {
        if (rows.isNotEmpty() && messageReq == null) listState.scrollToItem(rows.lastIndex)
    }
    LaunchedEffect(otherUserId) { viewModel.openThread(otherUserId) }
    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty() && messageReq == null) listState.animateScrollToItem(rows.size - 1)
    }
    LaunchedEffect(messageReq, rows) {
        val req = messageReq ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it is DmMessage && it.id == req.itemId }
        if (index < 0) return@LaunchedEffect
        listState.animateScrollToItem(index)
        highlightedMessageId = req.itemId
        delay(3000)
        highlightedMessageId = null
        HighlightBus.clear(req)
    }
    LaunchedEffect(messageReq) {
        val req = messageReq ?: return@LaunchedEffect
        delay(8000)
        HighlightBus.clear(req) // message never appeared in this thread — fall back to normal behavior
    }

    // imePadding() reserves space for the keyboard inside the content (messages + input bar) so the
    // keyboard pushes those up without dragging the top bar along.
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(pageColor).imePadding()) {
        // Top bar.
        Column(Modifier.fillMaxWidth().background(barColor).statusBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(56.dp)) {
                Text(
                    state.otherUserName.ifBlank { "..." },
                    fontSize = 18.sp, fontWeight = FontWeight.Medium, color = textMain, maxLines = 1,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 64.dp)
                )
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Back", modifier = Modifier.size(34.dp))
                }
                Box(Modifier.align(Alignment.CenterEnd)) {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreHoriz, contentDescription = "More", modifier = Modifier.size(28.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (state.blockedByMe) "Unblock" else "Block") },
                            onClick = { menuOpen = false; viewModel.toggleBlock() }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear chat") },
                            onClick = { menuOpen = false; confirmClear = true }
                        )
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = lineColor)
        }

        // Messages.
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
        ) {
            items(rows, key = { if (it is DmMessage) it.id else "t-$it-${rows.indexOf(it)}" }) { row ->
                if (row is DmMessage) {
                    val mine = row.senderId == session.uid
                    MessageRow(
                        text = row.text,
                        isMine = mine,
                        name = if (mine) session.name else state.otherUserName,
                        photoUrl = if (mine) session.photoUrl else state.otherUserPhotoUrl,
                        dark = dark,
                        highlighted = highlightedMessageId == row.id,
                        pressed = actionTarget?.id == row.id,
                        replyToName = row.replyToName,
                        replyToText = row.replyToText,
                        onLongPress = { rect -> actionBounds = rect; actionTarget = row },
                        mediaUrl = row.mediaUrl, mediaType = row.mediaType, thumbUrl = row.thumbUrl,
                        durationMs = row.durationMs, playing = playingId == row.id, onVoiceClick = { playVoice(row) },
                        onMediaClick = { if (row.mediaType == "video") viewerVideo = row.mediaUrl else viewerImage = row.mediaUrl },
                        onQuoteClick = {
                            val idx = rows.indexOfFirst { it is DmMessage && (if (row.replyToId != null) it.id == row.replyToId else it.text == row.replyToText && it.id != row.id) }
                            if (idx >= 0) scope.launch {
                                listState.animateScrollToItem(idx)
                                highlightedMessageId = (rows[idx] as DmMessage).id
                                delay(1800)
                                highlightedMessageId = null
                            }
                        }
                    )
                } else {
                    Text(
                        row as String,
                        fontSize = 13.sp, color = textMuted,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        viewerImage?.let { url ->
            androidx.compose.ui.window.Dialog(onDismissRequest = { viewerImage = null }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    com.triangle.app.ui.components.ZoomableImage(model = url, contentDescription = "Photo")
                    IconButton(onClick = { saveMedia(url, false) }, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)) {
                        Icon(Icons.Outlined.Download, contentDescription = "Save", tint = Color.White)
                    }
                    IconButton(onClick = { viewerImage = null }, modifier = Modifier.statusBarsPadding().padding(12.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }
        viewerVideo?.let { url -> VideoDialog(url, onSave = { saveMedia(url, true) }) { viewerVideo = null } }

        actionTarget?.let { msg ->
            MessageActionPopup(
                bubble = actionBounds,
                canCopy = msg.mediaUrl == null,
                canSave = msg.mediaUrl != null && msg.mediaType != "voice",
                canDelete = msg.senderId == session.uid,
                dark = dark,
                onReply = { replyingTo = msg; actionTarget = null },
                onCopy = {
                    clipboard.setText(AnnotatedString(msg.text))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    actionTarget = null
                },
                onSave = { saveMedia(msg.mediaUrl!!, msg.mediaType == "video"); actionTarget = null },
                onDelete = { deleteTarget = msg; actionTarget = null },
                onDismiss = { actionTarget = null }
            )
        }
        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text("Clear chat?") },
                text = { Text("This clears the conversation on your side only. ${state.otherUserName.ifBlank { "The other person" }} will still see it.") },
                confirmButton = { TextButton(onClick = { viewModel.clearChat(); confirmClear = false }) { Text("Clear", color = Color(0xFFE53935)) } },
                dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
            )
        }
        deleteTarget?.let { msg ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("Delete this message?") },
                confirmButton = { TextButton(onClick = { viewModel.deleteMessage(msg.id); deleteTarget = null }) { Text("Delete", color = Color(0xFFE53935)) } },
                dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
            )
        }

        // Bottom bar.
        if (state.blocked) {
            Box(Modifier.fillMaxWidth().background(inputBarColor).navigationBarsPadding().padding(14.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (state.blockedByMe) "You blocked this conversation" else "You can't reply to this conversation",
                    fontSize = 13.sp, color = textMuted
                )
            }
        } else {
            Column(Modifier.fillMaxWidth().background(inputBarColor).navigationBarsPadding()) {
                HorizontalDivider(thickness = 0.5.dp, color = lineColor)
                replyingTo?.let { target ->
                    val targetName = if (target.senderId == session.uid) session.name else state.otherUserName
                    Row(
                        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                                .background(if (dark) Color(0xFF2C2C2E) else Color(0xFFE9E9E9)).padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(targetName, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TriangleBrandPurple, maxLines = 1)
                            Text(target.text, fontSize = 12.sp, color = textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { replyingTo = null }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Cancel reply", tint = textMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Voice button, to the left of the text box.
                    RoundIconButton(if (voiceMode) Icons.Outlined.Keyboard else Icons.Outlined.GraphicEq, "Voice message", textMain) {
                        if (voiceMode) {
                            voiceMode = false
                            scope.launch { delay(60); runCatching { inputFocus.requestFocus() }; keyboard?.show() }
                        } else {
                            voiceMode = true
                            panelOpen = false
                            keyboardWanted = false
                            focusManager.clearFocus()
                            keyboard?.hide()
                        }
                    }
                    // Compact message box (the stock Material TextField is 56dp tall, too big for a chat bar).
                    if (voiceMode && draft.isBlank()) {
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (recording) (if (dark) Color(0xFF3A3A3C) else Color(0xFFC9C9C9)) else if (dark) Color(0xFF2C2C2E) else Color.White)
                                .heightIn(min = 36.dp)
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        if (!granted) { micPermission.launch(android.Manifest.permission.RECORD_AUDIO); waitForUpOrCancellation(); return@awaitEachGesture }
                                        stopPlayback()
                                        if (!recorder.start()) {
                                            Toast.makeText(context, "Couldn't start recording", Toast.LENGTH_SHORT).show()
                                            waitForUpOrCancellation(); return@awaitEachGesture
                                        }
                                        voiceAction = 0
                                        recording = true
                                        val threshold = -70.dp.toPx()
                                        val half = size.width / 2f
                                        try {
                                            while (true) {
                                                val change = awaitPointerEvent().changes.first()
                                                voiceAction = if (change.position.y < threshold) (if (change.position.x < half) 1 else 2) else 0
                                                if (!change.pressed) break
                                                change.consume()
                                            }
                                        } finally {
                                            finishRecording(voiceAction)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (recording) "Release to send" else "Hold to Talk", fontSize = 15.sp, color = textMain)
                        }
                    } else
                    androidx.compose.foundation.text.BasicTextField(
                        value = draft,
                        onValueChange = { draft = it.capFirst() },
                        maxLines = 4,
                        textStyle = androidx.compose.ui.text.TextStyle(color = textMain, fontSize = 15.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(TriangleBrandPurple),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(inputFocus)
                            .onFocusChanged { if (it.isFocused && panelOpen) keyboardWanted = true }
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (dark) Color(0xFF2C2C2E) else Color.White)
                            .heightIn(min = 36.dp)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { inner() } }
                    )
                    RoundIconButton(if (panelOpen && emojiPanel) Icons.Outlined.Keyboard else Icons.Outlined.SentimentSatisfiedAlt, "Emoji", textMain) {
                        if (panelOpen && emojiPanel) {
                            keyboardWanted = true
                            runCatching { inputFocus.requestFocus() }
                            keyboard?.show()
                        } else if (panelOpen) {
                            emojiPanel = true // the keyboard is already hidden — just swap the panel
                        } else {
                            emojiPanel = true
                            panelOpen = true
                            focusManager.clearFocus()
                            keyboard?.hide()
                        }
                    }
                    if (draft.isNotBlank()) {
                        Text(
                            "Send", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(TriangleBrandPurple)
                                .clickable {
                                    val r = replyingTo
                                    val rName = r?.let { if (it.senderId == session.uid) session.name else state.otherUserName }
                                    viewModel.sendMessage(draft, rName, r?.text, r?.id)
                                    draft = ""
                                    replyingTo = null
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    } else {
                        // Attachments aren't built yet; the + is in place for them (replaced by Send while typing).
                        RoundIconButton(if (panelOpen && !emojiPanel) Icons.Outlined.Close else Icons.Outlined.Add, "More", textMain) {
                            if (panelOpen && !emojiPanel) {
                                keyboardWanted = true
                                runCatching { inputFocus.requestFocus() }
                                keyboard?.show()
                            } else if (panelOpen) {
                                emojiPanel = false
                            } else {
                                emojiPanel = false
                                panelOpen = true
                                focusManager.clearFocus()
                                keyboard?.hide()
                            }
                        }
                    }
                }
                if (uploading) {
                    Text("Sending…", fontSize = 12.sp, color = textMuted, modifier = Modifier.padding(start = 14.dp, bottom = 6.dp))
                }
                if (panelOpen) {
                    HorizontalDivider(thickness = 0.5.dp, color = lineColor)
                    if (emojiPanel) EmojiPanel(
                        height = panelHeight,
                        dark = dark,
                        onPick = { draft += it },
                        onBackspace = { draft = removeLastEmojiOrChar(draft) },
                        onSearch = { Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show() }
                    ) else AttachPanel(
                        height = panelHeight,
                        dark = dark,
                        onGallery = {
                            galleryPicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        },
                        onCamera = { openCamera() },
                        onOther = { Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show() }
                    )
                }
            }
        }
    }
        if (recording) VoiceRecordingOverlay(action = voiceAction, levels = levels, dark = dark)
    }
}

/** A 30dp circle with a thin outline and a line icon, like the voice / emoji buttons of a classic messenger. */
@Composable
private fun RoundIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).border(1.5.dp, tint, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(17.dp))
    }
}

/** One message: the sender's square picture on their side, and the bubble with a small pointer towards it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    text: String,
    isMine: Boolean,
    name: String,
    photoUrl: String?,
    dark: Boolean,
    highlighted: Boolean,
    pressed: Boolean,
    replyToName: String?,
    replyToText: String?,
    onLongPress: (Rect) -> Unit,
    onQuoteClick: () -> Unit,
    mediaUrl: String? = null,
    mediaType: String? = null,
    thumbUrl: String? = null,
    onMediaClick: () -> Unit = {},
    durationMs: Long? = null,
    playing: Boolean = false,
    onVoiceClick: () -> Unit = {}
) {
    val base = if (isMine) DmColors.MyBubble else if (dark) Color(0xFF2C2C2E) else Color.White
    val bubbleColor = if (pressed) (if (dark) Color(0xFF48484A) else Color(0xFFC9C9C9)).let { if (isMine) Color(0xFFB7B0F2) else it } else base
    val textColor = MaterialTheme.colorScheme.onSurface
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val visual = mediaUrl != null && mediaType != "voice"
    val isVoice = mediaType == "voice"
    val voiceSeconds = ((durationMs ?: 0L) / 1000f).toInt().coerceAtLeast(1)
    val voiceWidth = (72 + voiceSeconds * 5).coerceAtMost(210).dp
    Row(
        Modifier.fillMaxWidth().deepLinkHighlight(highlighted),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isMine) {
            ChatPicture(name, photoUrl, dark)
            Spacer(Modifier.width(6.dp))
            if (!visual) Pointer(bubbleColor, pointsRight = false)
        }
        if (visual) {
            val isVideo = mediaType == "video"
            Box(
                Modifier
                    .width(200.dp)
                    .defaultMinSize(minHeight = 110.dp)
                    .heightIn(max = 300.dp)
                    .onGloballyPositioned { bounds = it.boundsInWindow() }
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (dark) Color(0xFF2C2C2E) else Color(0xFFDDDDDD))
                    .combinedClickable(onClick = onMediaClick, onLongClick = { onLongPress(bounds) }),
                contentAlignment = Alignment.Center
            ) {
                val preview = if (isVideo) thumbUrl else mediaUrl
                if (preview != null) {
                    AsyncImage(model = preview, contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
                }
                if (isVideo) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play video", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
                if (pressed) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.3f)))
            }
        } else
        Column(
            Modifier
                .then(if (isVoice) Modifier.width(voiceWidth) else Modifier.widthIn(max = 250.dp))
                .onGloballyPositioned { bounds = it.boundsInWindow() }
                .clip(RoundedCornerShape(6.dp))
                .background(bubbleColor)
                .combinedClickable(onClick = { if (isVoice) onVoiceClick() }, onLongClick = { onLongPress(bounds) })
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (replyToText != null) {
                Row(
                    Modifier.padding(bottom = 6.dp).height(IntrinsicSize.Min).clickable(onClick = onQuoteClick),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(TriangleBrandPurple.copy(alpha = 0.6f)))
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(replyToName.orEmpty(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TriangleBrandPurple, maxLines = 1)
                        Text(replyToText, fontSize = 12.sp, color = textColor.copy(alpha = 0.65f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (isVoice) {
                val wave = @Composable {
                    VoiceWaveIcon(playing = playing, facingLeft = isMine, tint = if (playing) TriangleBrandPurple else textColor)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (isMine) {
                        Spacer(Modifier.weight(1f))
                        Text("$voiceSeconds\"", fontSize = 14.sp, color = textColor)
                        Spacer(Modifier.width(6.dp))
                        wave()
                    } else {
                        wave()
                        Spacer(Modifier.width(6.dp))
                        Text("$voiceSeconds\"", fontSize = 14.sp, color = textColor)
                    }
                }
            } else
            Text(text, fontSize = 14.sp, lineHeight = 20.sp, color = textColor)
        }
        if (isMine) {
            if (!visual) Pointer(bubbleColor, pointsRight = true)
            Spacer(Modifier.width(6.dp))
            ChatPicture(name, photoUrl, dark)
        }
    }
}

private val ActionItemWidth = 64.dp
private val ActionPanelHeight = 66.dp
private val ArrowHeight = 7.dp

private class ActionPos(val x: Int, val y: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset(x, y)
}

/** Dark rounded panel of icon-over-label actions with a small arrow aimed at the pressed bubble. */
@Composable
private fun MessageActionPopup(bubble: Rect, canCopy: Boolean, canSave: Boolean, canDelete: Boolean, dark: Boolean, onReply: () -> Unit, onCopy: () -> Unit, onSave: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val windowW = LocalConfiguration.current.screenWidthDp.dp
    val itemCount = 1 + (if (canCopy) 1 else 0) + (if (canSave) 1 else 0) + (if (canDelete) 1 else 0)
    val panelW = ActionItemWidth * itemCount
    val (wPx, panelHPx, arrowPx, margin) = with(density) { listOf(panelW.toPx(), ActionPanelHeight.toPx(), ArrowHeight.toPx(), 8.dp.toPx()) }
    val windowWPx = with(density) { windowW.toPx() }
    val above = bubble.top > with(density) { 140.dp.toPx() }
    val x = (bubble.center.x - wPx / 2).coerceIn(margin, windowWPx - wPx - margin)
    val y = if (above) bubble.top - panelHPx - arrowPx - with(density) { 2.dp.toPx() } else bubble.bottom + with(density) { 2.dp.toPx() }
    val arrowCenter = (bubble.center.x - x).coerceIn(with(density) { 14.dp.toPx() }, wPx - with(density) { 14.dp.toPx() })
    val panelColor = if (dark) Color(0xFF3A3A3C) else Color(0xFF4C4C4C)

    Popup(popupPositionProvider = ActionPos(x.toInt(), y.toInt()), onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        @Composable
        fun Arrow(down: Boolean) {
            Canvas(Modifier.width(panelW).height(ArrowHeight)) {
                val p = Path().apply {
                    if (down) { moveTo(arrowCenter - 7.dp.toPx(), 0f); lineTo(arrowCenter + 7.dp.toPx(), 0f); lineTo(arrowCenter, size.height) }
                    else { moveTo(arrowCenter - 7.dp.toPx(), size.height); lineTo(arrowCenter + 7.dp.toPx(), size.height); lineTo(arrowCenter, 0f) }
                    close()
                }
                drawPath(p, panelColor)
            }
        }
        Column {
            if (!above) Arrow(down = false)
            Row(Modifier.width(panelW).height(ActionPanelHeight).clip(RoundedCornerShape(6.dp)).background(panelColor)) {
                val items = buildList {
                    add(Triple(Icons.AutoMirrored.Outlined.Reply, "Reply", onReply))
                    if (canCopy) add(Triple(Icons.Outlined.ContentCopy, "Copy", onCopy))
                    if (canSave) add(Triple(Icons.Outlined.Download, "Save", onSave))
                    if (canDelete) add(Triple(Icons.Outlined.Delete, "Delete", onDelete))
                }
                items.forEachIndexed { i, (icon, label, action) ->
                    if (i > 0) Box(Modifier.padding(vertical = 14.dp).width(0.5.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.2f)))
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clickable(onClick = action),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(label, fontSize = 12.sp, color = Color.White)
                    }
                }
            }
            if (above) Arrow(down = true)
        }
    }
}

/** The small triangle on the side of a bubble nearest the sender's picture. */
@Composable
private fun Pointer(color: Color, pointsRight: Boolean) {
    Canvas(Modifier.padding(top = 13.dp).size(width = 7.dp, height = 14.dp)) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            if (pointsRight) { moveTo(0f, 0f); lineTo(w, h / 2); lineTo(0f, h) }
            else { moveTo(w, 0f); lineTo(0f, h / 2); lineTo(w, h) }
            close()
        }
        drawPath(p, color)
    }
}

/** Rounded-square profile picture, or the name's first letter on a tinted square. */
@Composable
private fun ChatPicture(name: String, photoUrl: String?, dark: Boolean) {
    val shape = RoundedCornerShape(5.dp)
    if (!photoUrl.isNullOrBlank()) {
        AsyncImage(model = photoUrl, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.size(ChatAvatar).clip(shape))
    } else {
        Box(
            Modifier.size(ChatAvatar).clip(shape).background(TriangleBrandPurple.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text((name.firstOrNull() ?: '?').uppercase(), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TriangleBrandPurple)
        }
    }
}

/** "2:25 AM" today, "Yesterday 1:16 PM", otherwise "Oct 1, 1:16 PM". */
private fun separatorLabel(timestamp: Long): String {
    val time = SimpleDateFormat("h:mm a", Locale.US).format(Date(timestamp))
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return when {
        same(cal, today) -> time
        same(cal, yesterday) -> "Yesterday $time"
        else -> SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(timestamp))
    }
}

/** The "+" panel: a grid of attachment shortcuts on the bar's colour. Gallery and Camera work; the rest are placeholders. */
@Composable
private fun AttachPanel(height: androidx.compose.ui.unit.Dp, dark: Boolean, onGallery: () -> Unit, onCamera: () -> Unit, onOther: () -> Unit) {
    val items = listOf(
        Triple(Icons.Filled.Image, "Gallery", onGallery),
        Triple(Icons.Filled.PhotoCamera, "Camera", onCamera),
        Triple(Icons.Filled.LocationOn, "Location", onOther),
        Triple(Icons.Filled.Mic, "Voice Input", onOther),
        Triple(Icons.Filled.Inventory2, "Favorites", onOther),
        Triple(Icons.Filled.Person, "Contact Card", onOther),
        Triple(Icons.Filled.Folder, "Files", onOther),
        Triple(Icons.Filled.MusicNote, "Music", onOther)
    )
    val tile = if (dark) Color(0xFF2C2C2E) else Color.White
    val label = if (dark) Color(0xFFB0B0B5) else Color(0xFF6B6B6B)
    Column(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(0.dp)).padding(horizontal = 12.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        items.chunked(4).forEach { rowItems ->
            Row(Modifier.fillMaxWidth()) {
                rowItems.forEach { (icon, name, action) ->
                    Column(Modifier.weight(1f).clickable(onClick = action, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)).background(tile), contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = name, tint = if (dark) Color.White else Color(0xFF1C1C1E), modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(name, fontSize = 12.sp, color = label, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Drive's view links aren't streamable, so the video is downloaded to the cache once (reused afterwards), then played locally. */
@Composable
private fun VideoDialog(url: String, onSave: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var file by remember { mutableStateOf<java.io.File?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(url) {
        val id = Regex("[?&]id=([^&]+)").find(url)?.groupValues?.get(1) ?: url.hashCode().toString()
        val target = java.io.File(context.cacheDir, "chat_videos").apply { mkdirs() }.let { java.io.File(it, "$id.mp4") }
        if (target.exists() && target.length() > 0) { file = target; return@LaunchedEffect }
        val ok = withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val dl = if (id.length > 10) "https://drive.usercontent.google.com/download?id=$id&export=download&confirm=t" else url
                val conn = java.net.URL(dl).openConnection() as java.net.HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 15000; conn.readTimeout = 30000
                if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                val tmp = java.io.File(target.path + ".part")
                conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                conn.disconnect()
                tmp.renameTo(target)
            }.getOrDefault(false)
        }
        if (ok) file = target else failed = true
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            val f = file
            when {
                f != null -> androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        android.widget.VideoView(ctx).apply {
                            val controller = android.widget.MediaController(ctx)
                            controller.setAnchorView(this)
                            setMediaController(controller)
                            setOnErrorListener { _, _, _ -> Toast.makeText(ctx, "Couldn't play this video", Toast.LENGTH_SHORT).show(); true }
                            setVideoPath(f.absolutePath)
                            setOnPreparedListener { start() }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                failed -> Text("Couldn't load this video", color = Color.White, fontSize = 14.sp)
                else -> androidx.compose.material3.CircularProgressIndicator(color = Color.White)
            }
            IconButton(onClick = onSave, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)) {
                Icon(Icons.Outlined.Download, contentDescription = "Save", tint = Color.White)
            }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

private fun graphemes(s: String): List<String> {
    val it = java.text.BreakIterator.getCharacterInstance()
    it.setText(s)
    val out = ArrayList<String>()
    var start = it.first()
    var end = it.next()
    while (end != java.text.BreakIterator.DONE) { out.add(s.substring(start, end)); start = end; end = it.next() }
    return out
}

/** Backspace for the emoji panel: removes the last whole character, so a multi-part emoji goes in one tap. */
private fun removeLastEmojiOrChar(s: String): String {
    if (s.isEmpty()) return s
    val g = graphemes(s)
    return g.dropLast(1).joinToString("")
}

private val EmojiSmileys = graphemes("😀😃😄😁😆😅😂🤣😊😇🙂🙃😉😌😍🥰😘😗😙😚😋😛😝😜🤪🤨🧐🤓😎🤩🥳😏😒😞😔😟😕🙁☹️😣😖😫😩🥺😢😭😤😠😡🤬🤯😳🥵🥶😱😨😰😥😓🤗🤔🤭🤫🤥😶😐😑😬🙄😯😦😧😮😲🥱😴🤤😪😵🤐🥴🤢🤮🤧😷🤒🤕🤑🤠😈👿👹👺🤡💩👻💀☠️👽🤖🎃")
private val EmojiHearts = graphemes("❤️🧡💛💚💙💜🖤🤍🤎💔❣️💕💞💓💗💖💘💝💟♥️😍🥰😘😻💑💏🌹🥀🌷🌸💐🎁🎉🎊✨🌟⭐🔥💯")
private val EmojiHands = graphemes("👍👎👌✌️🤞🤟🤘🤙👈👉👆👇☝️✋🤚🖐️🖖👋🤝🙏✍️💪👏🙌👐🤲🤜🤛✊👊🫶🫰🤌🤏🙋‍♂️🤷‍♂️🤦‍♂️🙇‍♂️💁‍♂️🙆‍♂️🙅‍♂️")

/** Emoji panel: a tab row (search, faces, hearts, hands), a handle, a heading and an 8-column grid with a backspace key floating at the bottom right. */
@Composable
private fun EmojiPanel(height: androidx.compose.ui.unit.Dp, dark: Boolean, onPick: (String) -> Unit, onBackspace: () -> Unit, onSearch: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("😀" to EmojiSmileys, "♡" to EmojiHearts, "✌" to EmojiHands)
    val panel = if (dark) Color(0xFF1C1C1E) else Color(0xFFF7F7F7)
    val tile = if (dark) Color(0xFF2C2C2E) else Color.White
    val tint = if (dark) Color.White else Color(0xFF1C1C1E)
    val heading = listOf("All Stickers", "Hearts & Celebration", "Hands & Gestures")[tab]
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(0.dp)).background(panel)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 52.dp, height = 44.dp).clickable(onClick = onSearch), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search emoji", tint = tint, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(10.dp))
                listOf(Icons.Outlined.SentimentSatisfiedAlt, Icons.Outlined.FavoriteBorder, Icons.Outlined.WavingHand).forEachIndexed { i, icon ->
                    Box(
                        Modifier.padding(end = 10.dp).size(width = 52.dp, height = 44.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (tab == i) tile else Color.Transparent).clickable { tab = i },
                        contentAlignment = Alignment.Center
                    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp)) }
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = if (dark) Color(0xFF2C2C2E) else Color(0xFFDCDCDC))
            Box(Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally).size(width = 44.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFCFCFCF)))
            Text(heading, fontSize = 15.sp, color = if (dark) Color(0xFF8E8E93) else Color(0xFF7A7A7A), modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp))
            // Only ~100 emojis per tab, so every cell is composed up front in a plain scrolling column: nothing is
            // created or measured while scrolling (a lazy grid builds each new row mid-scroll, which stutters).
            val noRipple = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val rows = remember(tab) { tabs[tab].second.chunked(8) }
            Column(Modifier.fillMaxSize().padding(horizontal = 8.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                rows.forEach { line ->
                    Row(Modifier.fillMaxWidth()) {
                        line.forEach { e ->
                            Box(Modifier.weight(1f).height(44.dp).clickable(indication = null, interactionSource = noRipple) { onPick(e) }, contentAlignment = Alignment.Center) {
                                Text(e, fontSize = 26.sp)
                            }
                        }
                        repeat(8 - line.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(70.dp))
            }
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 14.dp).size(width = 66.dp, height = 46.dp)
                .clip(RoundedCornerShape(10.dp)).background(tile).clickable(onClick = onBackspace),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "Delete", tint = tint, modifier = Modifier.size(26.dp)) }
    }
}

/** Dimmed screen shown while holding to talk: a live waveform bubble, Cancel / Convert-to-text pills to slide up to, and the "Release to send" arc. */
@Composable
private fun VoiceRecordingOverlay(action: Int, levels: List<Float>, dark: Boolean) {
    val bubble = if (action == 1) Color(0xFFFA5151) else TriangleBrandPurple
    val arc = if (dark) Color(0xFF48484A) else Color(0xFFBEBEBE)
    val pillOff = Color(0xFF5A5A5A)
    val pillOn = Color(0xFFE9E9E9)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f))) {
        Column(Modifier.align(Alignment.Center).padding(bottom = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(width = 170.dp, height = 72.dp).clip(RoundedCornerShape(14.dp)).background(bubble), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(width = 120.dp, height = 48.dp)) {
                    val slots = 30
                    val step = size.width / slots
                    val padded = List(slots - levels.size.coerceAtMost(slots)) { 0f } + levels.takeLast(slots)
                    padded.forEachIndexed { i, l ->
                        val h = 4.dp.toPx() + l * (size.height - 4.dp.toPx())
                        val x = i * step + step / 2
                        drawLine(Color.White.copy(alpha = 0.9f), Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), strokeWidth = 2.5.dp.toPx())
                    }
                }
            }
            Canvas(Modifier.size(width = 16.dp, height = 8.dp)) {
                drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height); close() }, bubble)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(260.dp)) {
            Box(
                Modifier.align(Alignment.TopStart).offset(x = (-24).dp, y = 20.dp).size(width = 160.dp, height = 66.dp)
                    .rotate(-8f).clip(RoundedCornerShape(33.dp)).background(if (action == 1) pillOn else pillOff),
                contentAlignment = Alignment.Center
            ) { Text("Cancel", fontSize = 16.sp, color = if (action == 1) Color(0xFF1C1C1E) else Color.White) }
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 24.dp, y = 20.dp).size(width = 180.dp, height = 66.dp)
                    .rotate(8f).clip(RoundedCornerShape(33.dp)).background(if (action == 2) pillOn else pillOff),
                contentAlignment = Alignment.Center
            ) { Text("Convert to Text", fontSize = 16.sp, color = if (action == 2) Color(0xFF1C1C1E) else Color.White) }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(160.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val peak = 50.dp.toPx()
                    drawPath(
                        Path().apply {
                            moveTo(0f, peak); quadraticBezierTo(size.width / 2, -peak, size.width, peak)
                            lineTo(size.width, size.height); lineTo(0f, size.height); close()
                        },
                        arc
                    )
                }
                Text(
                    when (action) { 1 -> "Release to cancel"; 2 -> "Release to convert to text"; else -> "Release to send" },
                    fontSize = 16.sp, color = Color(0xFF1C1C1E), modifier = Modifier.align(Alignment.TopCenter).padding(top = 46.dp)
                )
            }
        }
    }
}

/** The speaker "sound waves" icon of a voice bubble; while [playing] its arcs light up one after another so it visibly pulses. */
@Composable
private fun VoiceWaveIcon(playing: Boolean, facingLeft: Boolean, tint: Color) {
    val phase = if (playing) {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "voiceWave").animateFloat(
            initialValue = 0f, targetValue = 4f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.LinearEasing)
            ),
            label = "voicePhase"
        ).value
    } else 4f
    val visible = phase.toInt() // 0..3 arcs shown while playing (a brief empty beat), all three when idle
    Canvas(Modifier.size(22.dp, 20.dp)) {
        val cy = size.height / 2
        val cx = if (facingLeft) size.width - 3.dp.toPx() else 3.dp.toPx()
        val stroke = 2.dp.toPx()
        drawCircle(tint, radius = 2.2.dp.toPx(), center = Offset(cx, cy))
        for (i in 0 until 3) {
            if (playing && i >= visible) continue
            val r = (6 + i * 4.5f).dp.toPx()
            drawArc(
                tint, startAngle = if (facingLeft) 135f else -45f, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(cx - r, cy - r), size = androidx.compose.ui.geometry.Size(2 * r, 2 * r),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
    }
}
