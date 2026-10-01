package com.triangle.app.reviews

import android.widget.Toast
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.HabitPalette
import com.triangle.app.data.ReviewRepository
import com.triangle.app.data.SessionStore
import com.triangle.app.data.UserRepository
import com.triangle.app.data.models.Review
import com.triangle.app.data.models.Task
import com.triangle.app.ui.SvgPathIcon
import com.triangle.app.ui.components.Avatar
import com.triangle.app.ui.theme.TriangleBrandPurple
import com.triangle.app.ui.theme.triangleDarkTheme
import com.triangle.app.util.capFirst
import kotlinx.coroutines.launch

private val StarGold = Color(0xFFF5A623)

/**
 * The review form the assigner sees after someone completes a task they assigned:
 * who did it, which task, "How was the task done?", 1..5 stars, an optional
 * comment, and Send. A review cannot be edited or deleted afterwards.
 */
@Composable
fun ReviewSubmitScreen(
    session: SessionStore.Session,
    taskId: String,
    recipientUid: String,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = triangleDarkTheme()
    val page = if (dark) MaterialTheme.colorScheme.background else Color.White
    val fieldBg = if (dark) Color(0xFF26262C) else Color(0xFFF1F1F3)

    var recipientName by remember { mutableStateOf("") }
    var recipientPhoto by remember { mutableStateOf<String?>(null) }
    var task by remember { mutableStateOf<Task?>(null) }
    var alreadyReviewed by remember { mutableStateOf(false) }
    var stars by remember { mutableStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    LaunchedEffect(taskId, recipientUid) {
        UserRepository.fetchUserRecord(recipientUid)?.let { recipientName = it.name; recipientPhoto = it.photoUrl }
        task = runCatching { ReviewRepository.loadTask(recipientUid, taskId) }.getOrNull()
        alreadyReviewed = runCatching { ReviewRepository.exists(recipientUid, taskId) }.getOrDefault(false)
    }

    fun send() {
        if (stars == 0 || sending) return
        sending = true
        scope.launch {
            val result = ReviewRepository.submit(
                Review(
                    taskId = taskId,
                    recipientUid = recipientUid,
                    reviewerUid = session.uid,
                    reviewerName = session.name,
                    reviewerPhotoUrl = session.photoUrl,
                    taskTitle = task?.title ?: "",
                    stars = stars,
                    comment = comment.trim()
                )
            )
            sending = false
            when (result) {
                is ReviewRepository.Result.Sent -> {
                    Toast.makeText(context, "Review sent", Toast.LENGTH_SHORT).show()
                    onDone()
                }
                is ReviewRepository.Result.AlreadyReviewed -> { alreadyReviewed = true }
                is ReviewRepository.Result.NotAllowed ->
                    Toast.makeText(context, "You can only review tasks you assigned that have been completed", Toast.LENGTH_LONG).show()
                is ReviewRepository.Result.Failed -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(page).statusBarsPadding().navigationBarsPadding().imePadding()) {
        // Close.
        Box(
            Modifier.padding(16.dp).size(36.dp).clip(CircleShape).background(fieldBg).clickable(onClick = onDone),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
        }

        // Middle: who, which task, the question and the stars.
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Avatar(recipientName.ifBlank { "?" }, recipientPhoto, size = 84.dp)
            Spacer(Modifier.height(12.dp))
            Text(recipientName, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            task?.let { t ->
                val tint = runCatching { Color(android.graphics.Color.parseColor(t.iconColor ?: HabitPalette.DEFAULT_COLOR)) }.getOrDefault(TriangleBrandPurple)
                Row(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(fieldBg).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SvgPathIcon(t.iconSvg ?: HabitPalette.ICONS.getValue(HabitPalette.DEFAULT_ICON_KEY), tint = tint, size = 22.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(t.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                }
            }
            Spacer(Modifier.height(40.dp))
            Text("How was the task done?", fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            if (alreadyReviewed) {
                Text("You have already reviewed this task.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(Review.MAX_STARS) { i ->
                        Icon(
                            if (i < stars) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "${i + 1} stars",
                            tint = if (i < stars) StarGold else Color(0xFFB9B9C0),
                            modifier = Modifier.size(46.dp).clickable { stars = i + 1 }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (stars == 0) Review.label(0) else "${Review.label(stars)} · $stars/5",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Bottom: grey comment field, then Send.
        if (!alreadyReviewed) {
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it.take(300).capFirst() },
                placeholder = { Text("Add a comment (optional)") },
                minLines = 2,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = fieldBg,
                    unfocusedContainerColor = fieldBg,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = ::send,
                enabled = stars > 0 && !sending,
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TriangleBrandPurple, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).height(52.dp)
            ) {
                if (sending) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Send", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
