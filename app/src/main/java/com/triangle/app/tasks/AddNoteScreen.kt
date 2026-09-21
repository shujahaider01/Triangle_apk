package com.triangle.app.tasks

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.SessionStore
import com.triangle.app.data.TaskNoteRepository
import com.triangle.app.data.TaskRepository
import com.triangle.app.data.models.TaskNote
import com.triangle.app.ui.theme.TriangleBrandPurple
import com.triangle.app.util.decodeImageUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Add a note" composer for Task Detail's Notes timeline — a text field
 * with a minimal Bold/Italic toggle (see NoteMarkdown.kt) and an optional
 * photo (native Photo Picker -> PhotoCropScreen -> Firebase Storage). Saves
 * as one or two TaskNote entries (text and/or photo) via TaskRepository.
 * Deliberately NOT built on the shared TasksHabitsViewModel, matching
 * CreateEditTaskScreen's own convention of talking to the repository
 * directly for a single self-contained create action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNoteScreen(
    session: SessionStore.Session,
    taskId: String,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var textValue by remember { mutableStateOf(TextFieldValue("")) }
    // Tapping Bold/Italic (outside the field) blurs it first — the IME's
    // final update on blur can collapse textValue.selection before the
    // button's onClick runs, so the toggle would see "nothing selected."
    // Track the last real (non-collapsed) selection separately and use that.
    var lastRealSelection by remember { mutableStateOf(TextRange.Zero) }
    var pickedRawBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var croppedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var decoding by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        decoding = true
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { decodeImageUri(context, uri) }
            decoding = false
            if (bmp == null) {
                error = "Couldn't open that image"
            } else {
                pickedRawBitmap = bmp
            }
        }
    }

    fun toggleMark(marker: String) {
        val sel = if (!textValue.selection.collapsed) textValue.selection else lastRealSelection
        val (newText, newSelRange) = toggleMarkAtSelection(
            textValue.text, sel.start, sel.end, marker
        ) ?: return
        textValue = TextFieldValue(newText, TextRange(newSelRange.first, newSelRange.last + 1))
    }

    fun save() {
        if (saving) return
        val text = textValue.text.trim()
        if (text.isBlank() && croppedBitmap == null) return
        saving = true
        scope.launch {
            try {
                if (text.isNotBlank()) {
                    TaskRepository.addNote(
                        session.orgId, taskId,
                        TaskNote(type = "text", content = text, userId = session.uid, userName = session.name, role = session.role, timestamp = System.currentTimeMillis())
                    )
                }
                croppedBitmap?.let { bmp ->
                    val url = TaskNoteRepository.uploadNotePhoto(session.orgId, session.uid, taskId, bmp)
                    TaskRepository.addNote(
                        session.orgId, taskId,
                        TaskNote(type = "photo", content = url, userId = session.uid, userName = session.name, role = session.role, timestamp = System.currentTimeMillis())
                    )
                }
                onSaved()
            } catch (e: Exception) {
                saving = false
                error = e.message ?: "Couldn't save this note"
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Note", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    TextButton(onClick = { save() }, enabled = !saving && (textValue.text.isNotBlank() || croppedBitmap != null)) {
                        if (saving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.weight(1f).padding(20.dp)) {
                BasicTextField(
                    value = textValue,
                    onValueChange = { new ->
                        if (!new.selection.collapsed) lastRealSelection = new.selection
                        textValue = new
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth().height(160.dp).padding(4.dp)
                )

                croppedBitmap?.let { bmp ->
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.size(140.dp).clip(RoundedCornerShape(14.dp))) {
                        Image(bitmap = bmp.asImageBitmap(), contentDescription = "Attached photo", modifier = Modifier.fillMaxSize())
                        Box(
                            Modifier.padding(6.dp).size(26.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface)
                                .clickable { croppedBitmap = null },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove photo", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }

            // Formatting toolbar lives just above the keyboard (like a chat
            // app's input accessory bar) rather than above the text field —
            // Android's own floating selection toolbar (Cut/Copy/Paste)
            // appears right above whatever text is selected, which would
            // otherwise sit on top of and block these buttons.
            Row(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = { toggleMark("**") }) { Icon(Icons.Default.FormatBold, contentDescription = "Bold") }
                IconButton(onClick = { toggleMark("*") }) { Icon(Icons.Default.FormatItalic, contentDescription = "Italic") }
                Spacer(Modifier.size(8.dp))
                IconButton(onClick = { pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add photo")
                }
                if (decoding) {
                    Spacer(Modifier.size(8.dp))
                    CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(top = 10.dp), strokeWidth = 2.dp)
                }
            }
        }
    }

    pickedRawBitmap?.let { raw ->
        PhotoCropView(
            sourceBitmap = raw,
            onConfirm = { cropped ->
                croppedBitmap = cropped
                pickedRawBitmap = null
            },
            onCancel = { pickedRawBitmap = null }
        )
    }
    }
}
