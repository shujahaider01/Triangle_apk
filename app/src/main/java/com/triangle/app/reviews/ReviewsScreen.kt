package com.triangle.app.reviews

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.ReviewRepository
import com.triangle.app.data.SessionStore
import com.triangle.app.data.models.Review
import com.triangle.app.ui.components.Avatar
import com.triangle.app.ui.theme.triangleDarkTheme

private val StarGold = Color(0xFFF5B301) // also used by the task sheet below
private val EmptyStar = Color(0xFFD9D9DE)

/** Row of [count] stars filled up to [filled], at [size] each. */
@Composable
fun StarsRow(filled: Int, size: Dp, count: Int = Review.MAX_STARS, gap: Dp = 2.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        repeat(count) { i ->
            Icon(
                if (i < filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                tint = if (i < filled) StarGold else EmptyStar,
                modifier = Modifier.size(size)
            )
        }
    }
}

fun timeAgo(ms: Long): String {
    val diff = System.currentTimeMillis() - ms
    val min = diff / 60_000
    val hr = min / 60
    val day = hr / 24
    return when {
        min < 1 -> "just now"
        min < 60 -> "$min min ago"
        hr < 24 -> "$hr hour${if (hr == 1L) "" else "s"} ago"
        day < 30 -> "$day day${if (day == 1L) "" else "s"} ago"
        else -> "${day / 30} month${if (day / 30 == 1L) "" else "s"} ago"
    }
}

/**
 * Reviews home (opened from the Home shortcut), laid out like a clean ratings page:
 * back arrow and centred title, the big average with stars, the five-band breakdown
 * bars, a divider, then the reviews I received.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReviewsScreen(
    session: SessionStore.Session,
    onBack: () -> Unit
) {
    val dark = triangleDarkTheme()
    val reviews by ReviewRepository.receivedFlow(session.uid).collectAsState(initial = emptyList())
    val summary = remember(reviews) { ReviewRepository.summarize(reviews) }
    var taskFor by remember { mutableStateOf<Review?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Plain page background (no gradient); same top bar as Leaderboard / Notifications.
    val pageBg = Modifier.background(if (dark) MaterialTheme.colorScheme.background else Color.White)
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.then(pageBg),
        topBar = {
            TopAppBar(
                title = { Text("Reviews", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
    val scroll = rememberScrollState()
    val paging = com.triangle.app.ui.components.rememberScrollPaging(scroll, reviews.size, 8)
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(scroll)) {
        // Overall rating.
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (summary.count == 0) "–" else "%.1f".format(summary.average),
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            StarsRow(filled = Math.round(summary.average), size = 26.dp, gap = 4.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                "based on ${summary.count} review${if (summary.count == 1) "" else "s"}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(22.dp))
            val total = summary.count.coerceAtLeast(1)
            Breakdown("Excellent", summary.excellent, total, Color(0xFF4CAF50))
            Breakdown("Good", summary.good, total, Color(0xFF9ACD32))
            Breakdown("Average", summary.average5, total, Color(0xFFF4D03F))
            Breakdown("Below Average", summary.belowAverage, total, Color(0xFFF5A623))
            Breakdown("Poor", summary.poor, total, Color(0xFFE53935))
        }
        Spacer(Modifier.height(18.dp))
        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))

        if (reviews.isEmpty()) {
            Text(
                "No reviews yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(24.dp)
            )
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            reviews.take(paging.visible).forEach { r -> ReviewCard(r, onShowTask = { taskFor = r }, onShare = { scope.launch { runCatching { shareReview(context, r) } } }) }
        }
        if (paging.hasMore) com.triangle.app.ui.components.LoadMoreFooter()
        Spacer(Modifier.height(40.dp))
    }
    }
    taskFor?.let { ReviewedTaskSheet(it, onDismiss = { taskFor = null }) }
}

@Composable
private fun Breakdown(label: String, count: Int, total: Int, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(96.dp))
        Box(Modifier.weight(1f).height(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(count.toFloat() / total).clip(CircleShape).background(color))
        }
    }
}

/** One received review: thin rating-coloured line on top, reviewer + stars + age, the comment, and the "⋯" that opens the task. */
@Composable
private fun ReviewCard(r: Review, onShowTask: () -> Unit, onShare: () -> Unit) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            // Thin top line coloured by the rating: Excellent green, Good light green, Average yellow, Poor red.
            Box(Modifier.fillMaxWidth().height(3.dp).background(ratingColor(r.stars)))
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp), verticalAlignment = Alignment.Top) {
                Avatar(r.reviewerName, r.reviewerPhotoUrl, size = 44.dp, fontSize = 16.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.reviewerName.ifBlank { "Someone" }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = onSurface, maxLines = 1)
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StarsRow(filled = r.stars, size = 16.dp, gap = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("${r.stars}.0", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = onSurface)
                    }
                }
                Text(timeAgo(r.createdAt), fontSize = 12.sp, color = muted, modifier = Modifier.padding(top = 2.dp))
            }
            if (r.comment.isNotBlank()) {
                Text(
                    r.comment,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = muted,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
            // Footer: "⋯" opens the task this review was for.
            Row(Modifier.fillMaxWidth().padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onShowTask, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = "View task", tint = muted)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Share, contentDescription = "Share review", tint = muted, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

/** Bottom sheet with the task a review was for (the recipient's own copy of it). */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun ReviewedTaskSheet(review: Review, onDismiss: () -> Unit) {
    var task by remember { mutableStateOf<com.triangle.app.data.models.Task?>(null) }
    var loaded by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(review.taskId) {
        task = runCatching { ReviewRepository.loadTask(review.recipientUid, review.taskId) }.getOrNull()
        loaded = true
    }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            val t = task
            val tint = runCatching {
                Color(android.graphics.Color.parseColor(t?.iconColor ?: com.triangle.app.data.HabitPalette.DEFAULT_COLOR))
            }.getOrDefault(StarGold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    com.triangle.app.ui.SvgPathIcon(
                        t?.iconSvg ?: com.triangle.app.data.HabitPalette.ICONS.getValue(com.triangle.app.data.HabitPalette.DEFAULT_ICON_KEY),
                        tint = tint, size = 26.dp
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(t?.title ?: review.taskTitle.ifBlank { "Task" }, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            when {
                !loaded -> Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                t == null -> Text("This task is no longer available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    Text(
                        t.description.ifBlank { "No description." },
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = if (t.description.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(16.dp))
                    TaskFact("Assigned by", review.reviewerName)
                    TaskFact("Priority", com.triangle.app.data.PriorityXp.labelFor(t.priority))
                    TaskFact("Due date", t.dueDate ?: "—")
                    if (t.checklist.isNotEmpty()) TaskFact("Checklist", "${t.checklist.count { it.done }}/${t.checklist.size} done")
                }
            }
        }
    }
}

@Composable
private fun TaskFact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Colour for a star rating: 5 Excellent (green), 4 Good (light green), 3 Average (yellow), 1-2 Poor (red). */
private fun ratingColor(stars: Int): Color = when {
    stars >= 5 -> Color(0xFF0E9F6E)
    stars == 4 -> Color(0xFF8BC34A)
    stars == 3 -> Color(0xFFF5C518)
    else -> Color(0xFFE53935)
}
