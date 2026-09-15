package com.triangle.app.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.ui.SvgPathIcon
import kotlinx.coroutines.delay

/**
 * Shared pieces behind Task Detail and Habit Detail — both are native ports
 * of script.js's single shared "ntd" (New Task Detail) page layout (see
 * `_openNewTaskDetail()`/`openHabitDetail()`), so this file is what lets
 * TaskDetailScreen/HabitDetailScreen stay thin compositions instead of two
 * diverging copies of the same hero/accordion/timeline chrome.
 */

data class DetailHeroAction(
    val icon: ImageVector,
    val contentDescription: String,
    val enabled: Boolean = true,
    val completed: Boolean = false,
    val onClick: () -> Unit
)

/**
 * The full colored hero: topbar (back, optional edit, static status pill),
 * icon ring + title + optional description pill, and the actions row
 * (left label pill for XP/frequency, right row of circular action
 * buttons) — matches .ntd-hero/.ntd-topbar/.ntd-hero-body/.ntd-hero-actions.
 */
@Composable
fun DetailHero(
    heroColor: Color,
    iconSvg: String,
    title: String,
    description: String?,
    statusLabel: String,
    statusColor: Color,
    pillIcon: ImageVector,
    pillText: String,
    actions: List<DetailHeroAction>,
    onBack: () -> Unit,
    onEdit: (() -> Unit)? = null
) {
    Column(Modifier.fillMaxWidth().background(heroColor).padding(bottom = 70.dp)) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = 16.dp, start = 14.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroCircleButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.weight(1f))
            if (onEdit != null) {
                HeroCircleButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.2f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                Text(statusLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 0.dp).padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                SvgPathIcon(iconSvg, tint = Color.White, size = 38.dp)
            }
            Spacer(Modifier.size(10.dp))
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center, letterSpacing = (-0.3).sp)
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.size(6.dp))
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.18f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(description, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, textAlign = TextAlign.Center, lineHeight = 18.sp)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.22f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(pillIcon, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(13.dp))
                Text(pillText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.95f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                actions.forEach { action ->
                    HeroActionButton(action)
                }
            }
        }
    }
}

@Composable
private fun HeroCircleButton(onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.18f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { icon() }
}

@Composable
private fun HeroActionButton(action: DetailHeroAction) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.22f))
            .then(if (action.completed) Modifier.border(2.dp, Color(0xFF4ADE80).copy(alpha = 0.4f), CircleShape) else Modifier)
            .alpha(if (action.enabled) 1f else 0.35f)
            .clickable(enabled = action.enabled, onClick = action.onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            action.icon,
            contentDescription = action.contentDescription,
            tint = if (action.completed) Color(0xFF4ADE80) else Color.White,
            modifier = Modifier.size(19.dp)
        )
    }
}

/** The title bar that fades in once scrolled past the hero — matches .ntd-sticky-hd/.ntd-sticky-visible. */
@Composable
fun DetailStickyHeader(title: String, visible: Boolean, onBack: () -> Unit) {
    AnimatedVisibility(visible = visible) {
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(16.dp))
                }
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, modifier = Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** Collapsible section — .ntd-acc/.ntd-acc-hd/.ntd-acc-body/.ntd-chevron. */
@Composable
fun AccordionSection(title: String, badge: String? = null, initiallyOpen: Boolean, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(initiallyOpen) }
    val chevronRotation by animateFloatAsState(if (open) 180f else 0f, label = "chevron")

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                if (badge != null) {
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 7.dp, vertical = 1.dp)) {
                        Text(badge, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = chevronRotation }
            )
        }
        AnimatedVisibility(visible = open) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                content()
            }
        }
    }
}

/** One label/value row inside a Details accordion — .ntd-row/.ntd-rlabel/.ntd-rval. */
@Composable
fun DetailInfoRow(label: String, value: String, valueColor: Color? = null, isLast: Boolean = false) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = valueColor ?: MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End)
        }
        if (!isLast) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Small colored pill for a Details row's value (Category/Points) — .ntd-chip-sm. */
@Composable
fun DetailChip(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

/** A Details row whose value is a DetailChip instead of plain text. */
@Composable
fun DetailInfoRowChip(label: String, chipText: String, chipColor: Color, isLast: Boolean = false) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            DetailChip(chipText, chipColor)
        }
        if (!isLast) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** A checklist item's radio-style circular checkbox — .ntd-check-cb/.ntd-check-text. */
@Composable
fun CircularCheckItem(text: String, done: Boolean, accentColor: Color, onToggle: () -> Unit, isLast: Boolean = false) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (done) accentColor else Color.Transparent)
                    .border(1.5.dp, if (done) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (done) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (done) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (done) TextDecoration.LineThrough else null
            )
        }
        if (!isLast) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** One entry in a Notes/Photos timeline — .ntd-tl-entry. [isLast] hides the connecting line (newest entry). */
data class TimelineEntryData(
    val avatarInitial: String,
    val avatarColor: Color,
    val username: String,
    val timestampMillis: Long,
    val isLast: Boolean,
    val body: @Composable () -> Unit
)

/** Connected avatar-line timeline — .ntd-timeline/.ntd-tl-*. */
@Composable
fun NotesTimeline(entries: List<TimelineEntryData>) {
    Column {
        entries.forEach { entry -> TimelineEntry(entry) }
    }
}

@Composable
private fun TimelineEntry(entry: TimelineEntryData) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.width(38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(entry.avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(entry.avatarInitial, fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
            if (!entry.isLast) {
                Box(
                    Modifier
                        .padding(top = 3.dp)
                        .width(3.dp)
                        .weight(1f, fill = false)
                        .defaultMinSize(minHeight = 24.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(entry.avatarColor.copy(alpha = 0.33f))
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(bottom = if (entry.isLast) 4.dp else 14.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("@${entry.username.lowercase().replace(" ", "")}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = entry.avatarColor)
                val timeAgo by rememberTimeAgo(entry.timestampMillis)
                Text(timeAgo, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) { entry.body() }
        }
    }
}

/** Live "3h ago"-style label, refreshing every 30s — matches the source's setInterval(getTimeAgo,30000). */
@Composable
fun rememberTimeAgo(timestampMillis: Long): State<String> =
    produceState(initialValue = formatTimeAgo(timestampMillis), timestampMillis) {
        while (true) {
            value = formatTimeAgo(timestampMillis)
            delay(30_000)
        }
    }

private fun formatTimeAgo(ts: Long): String {
    if (ts <= 0) return ""
    val diffSec = (System.currentTimeMillis() - ts) / 1000
    return when {
        diffSec < 60 -> "Just now"
        diffSec < 3600 -> "${diffSec / 60}m ago"
        diffSec < 86400 -> "${diffSec / 3600}h ago"
        diffSec < 604800 -> "${diffSec / 86400}d ago"
        diffSec < 2_419_200 -> "${diffSec / 604800}w ago"
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(ts))
    }
}

/**
 * How far the user has scrolled past the hero, as 0f..1f — drives the
 * hero's fade-out and the sticky header's appearance. Mirrors the source's
 * own scroll listener: `t = min(1, scrollTop / (heroHeight - peek))`.
 */
@Composable
fun rememberHeroScrollProgress(scrollValuePx: Int, heroHeightPx: Int, peekDp: Dp = 60.dp): Float {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val peekPx = with(density) { peekDp.toPx() }
    val maxScroll = (heroHeightPx - peekPx).coerceAtLeast(1f)
    return (scrollValuePx / maxScroll).coerceIn(0f, 1f)
}

/** "Take Photo" / "From Gallery" action sheet — .ntd-photo-sheet, built as a real ModalBottomSheet. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PhotoSourceSheet(onTakePhoto: () -> Unit, onFromGallery: () -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text("Add Photo", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp, start = 4.dp))
            PhotoSourceOption(Icons.Default.PhotoCamera, "Take Photo", onTakePhoto)
            Spacer(Modifier.size(8.dp))
            PhotoSourceOption(Icons.Default.PhotoLibrary, "From Gallery", onFromGallery)
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun PhotoSourceOption(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
