package com.triangle.app.tasks

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.AssignmentRepository
import com.triangle.app.data.HabitPalette
import com.triangle.app.data.HabitStats
import com.triangle.app.data.models.HabitCompletionEntry
import com.triangle.app.ui.SvgPathIcon
import com.triangle.app.ui.components.Avatar
import com.triangle.app.ui.theme.TriangleBrandPurple
import com.triangle.app.ui.theme.triangleDarkTheme

private val NeutralIconBgLight = Color(0xFFF0F2F5)
private val NeutralIconBgDark = Color(0xFF2C2C2E)

/**
 * The trailing badge for a row the viewer assigned out to their Circle,
 * instead of TaskRowCard/HabitCard's own tap-to-complete checkmark (the
 * viewer isn't the one completing these) — a border ring around a "+"
 * glyph that fills clockwise as `fraction` of the recipients complete
 * their copy. The "+" glyph never changes to a checkmark, even once
 * everyone has finished — at fraction 1.0 the arc simply closes into a
 * full, solid-colored ring around the "+" (the user explicitly asked for
 * this instead of a tick-icon takeover, so a single-recipient item and a
 * multi-recipient item both read the same way: a ring that closes as
 * people finish). The faint ring TRACK is always drawn (even at 0%, e.g.
 * a single-recipient item nobody has finished yet) so the badge always
 * reads as a progress indicator rather than looking like a plain "+" button.
 */
@Composable
fun AssignedProgressBadge(fraction: Float, color: Color, size: Dp = 38.dp, onClick: (() -> Unit)? = null) {
    val clamped = fraction.coerceIn(0f, 1f)
    val animated by animateFloatAsState(clamped, label = "assignedProgress")
    val dark = triangleDarkTheme()
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(if (dark) NeutralIconBgDark else NeutralIconBgLight))
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.11f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            // Full track, always visible — this is what makes a single-recipient, 0%-done item still show a ring.
            drawArc(
                color = color.copy(alpha = 0.2f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = Offset(inset, inset),
                size = arcSize
            )
            if (animated > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    topLeft = Offset(inset, inset),
                    size = arcSize
                )
            }
        }
        Icon(Icons.Default.Add, contentDescription = "In progress", tint = color, modifier = Modifier.size(size * 0.42f))
    }
}

/**
 * Assigned-out counterpart to TaskRowCard — same card shape, but the
 * trailing action is AssignedProgressBadge and the caption names who it
 * went to instead of a description. Tapping the row opens the task's own
 * detail page ([onClick]); tapping the badge specifically shows the
 * per-recipient completion breakdown ([onShowProgress]) instead — same
 * "row opens detail, one inner control does its own thing" split
 * TaskRowCard's checkmark and HabitCard's Analytics shortcut already use.
 */
@Composable
fun AssignedTaskRowCard(group: AssignmentRepository.AssignedTaskGroup, onClick: () -> Unit, onShowProgress: () -> Unit) {
    val task = group.task
    val color = runCatching { Color(android.graphics.Color.parseColor(task.iconColor ?: HabitPalette.DEFAULT_COLOR)) }
        .getOrDefault(MaterialTheme.colorScheme.primary)
    val svg = task.iconSvg ?: HabitPalette.ICONS.getValue(HabitPalette.DEFAULT_ICON_KEY)
    val dark = triangleDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, label = "assignedTaskRowScale")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (dark) Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                else Modifier.shadow(2.dp, RoundedCornerShape(16.dp), clip = false)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(if (dark) NeutralIconBgDark else NeutralIconBgLight),
            contentAlignment = Alignment.Center
        ) {
            SvgPathIcon(svg, tint = color, size = 24.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(task.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        AssignedProgressBadge(fraction = group.doneCount.toFloat() / group.members.size, color = color, onClick = onShowProgress)
    }
}

/**
 * Same row-vs-badge click split as AssignedTaskRowCard — see its doc
 * comment. Shows the same 22-day heatmap grid as HabitCard, but merged
 * across every recipient's completions (a day lights up if ANY recipient
 * did the habit that day) — there's no single "my" completion history for
 * something the viewer doesn't do themselves, so the group's combined
 * activity is what's shown instead of leaving the heatmap off entirely.
 */
@Composable
fun AssignedHabitRowCard(
    group: AssignmentRepository.AssignedHabitGroup,
    dateStr: String,
    onClick: () -> Unit,
    onShowProgress: () -> Unit,
    onOpenAnalytics: () -> Unit
) {
    val habit = group.habit
    val color = runCatching { Color(android.graphics.Color.parseColor(habit.color)) }.getOrDefault(MaterialTheme.colorScheme.primary)
    val svg = habit.iconSvg ?: HabitPalette.ICONS.getValue(HabitPalette.DEFAULT_ICON_KEY)
    val dark = triangleDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, label = "assignedHabitRowScale")
    val doneCount = group.doneCount(dateStr)
    val mergedCompletions = remember(group) {
        group.members.fold(emptyMap<String, HabitCompletionEntry>()) { acc, member -> acc + member.completions }
    }
    val streak = remember(group) {
        group.members.maxOfOrNull { HabitStats.calcStreak(habit, it.completions).current } ?: 0
    }

    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(2.dp, RoundedCornerShape(18.dp), clip = false)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(16.dp, 16.dp, 16.dp, 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(if (dark) NeutralIconBgDark else NeutralIconBgLight),
                contentAlignment = Alignment.Center
            ) {
                SvgPathIcon(svg, tint = color, size = 22.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(habit.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Streak: $streak day${if (streak == 1) "" else "s"}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            // Analytics shortcut — same .habit-note-btn styling as HabitCard's own,
            // now shown here too so the assigner can see the group's combined
            // analytics, not just whoever received the habit.
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.125f))
                    .border(1.5.dp, color.copy(alpha = 0.125f), CircleShape)
                    .clickable(onClick = onOpenAnalytics),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.BarChart, contentDescription = "Analytics", tint = Color.Black, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            AssignedProgressBadge(fraction = doneCount.toFloat() / group.members.size, color = color, onClick = onShowProgress)
        }
        Spacer(Modifier.height(12.dp))
        HabitHeatmapGrid(color = color, completions = mergedCompletions)
    }
}

/** A recipient's display info for AssignedGroupMembersSheet/AssignedMemberRow. */
data class AssignedMemberStatus(val uid: String, val name: String, val photoUrl: String?, val done: Boolean)

/** Bottom sheet listing who an assigned task/habit went to and whether each has finished it — opened by tapping an AssignedTaskRowCard/AssignedHabitRowCard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignedGroupMembersSheet(title: String, members: List<AssignedMemberStatus>, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth()) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "${members.count { it.done }}/${members.size} completed",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            LazyColumn(Modifier.padding(bottom = 24.dp)) {
                items(members, key = { it.uid }) { m -> AssignedMemberRow(m.name, m.done, m.photoUrl) }
            }
        }
    }
}

/** One recipient's name + completion status — shared by AssignedGroupMembersSheet and the read-only Assigned*DetailScreen "Recipients" section. */
@Composable
fun AssignedMemberRow(name: String, done: Boolean, photoUrl: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Avatar(name, photoUrl, size = 36.dp, backgroundColor = TriangleBrandPurple.copy(alpha = 0.18f), textColor = TriangleBrandPurple, fontSize = 14.sp)
        Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(if (done) TriangleBrandPurple else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (done) Icon(Icons.Default.Check, contentDescription = "Completed", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}
