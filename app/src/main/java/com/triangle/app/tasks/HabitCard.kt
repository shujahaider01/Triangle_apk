package com.triangle.app.tasks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.HabitPalette
import com.triangle.app.data.HabitStats
import com.triangle.app.data.models.Habit
import com.triangle.app.data.models.HabitCompletionEntry
import com.triangle.app.ui.SvgPathIcon

private val NeutralIconBgLight = Color(0xFFF0F2F5)
private val NeutralIconBgDark = Color(0xFF2C2C2E)

/** Matches script.js's _buildHabitCard()/.habit-card — icon, name, streak, heatmap, complete-today circle. */
@Composable
fun HabitCard(
    habit: Habit,
    completions: Map<String, HabitCompletionEntry>,
    streak: Int,
    doneToday: Boolean,
    canComplete: Boolean = true,
    onClick: () -> Unit,
    onCompleteToday: () -> Unit
) {
    val color = runCatching { Color(android.graphics.Color.parseColor(habit.color)) }.getOrDefault(MaterialTheme.colorScheme.primary)
    val svg = habit.iconSvg ?: HabitPalette.ICONS.getValue(HabitPalette.DEFAULT_ICON_KEY)
    val dark = isSystemInDarkTheme()

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, label = "habitCardScale")
    val cardBorder = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.07f)

    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(2.dp, RoundedCornerShape(18.dp), clip = false)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
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
            // Checkmark is always rendered; only interactive when viewing today (matches source: past/future days are read-only).
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (doneToday) color else color.copy(alpha = 0.094f))
                    .border(2.dp, if (doneToday) color else color.copy(alpha = 0.19f), CircleShape)
                    .clickable(enabled = canComplete && !doneToday, onClick = onCompleteToday),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = if (doneToday) "Completed" else if (canComplete) "Mark complete" else "Only today can be marked complete",
                    tint = if (doneToday) Color.White else Color(0xFF555555),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        HabitHeatmap(color = color, completions = completions)
    }
}

/**
 * 22-week x 7-day GitHub-contribution-graph-style grid — matches
 * _buildHabitHeatmap()'s column-major week layout: each of the 7 rows is a
 * fixed day-of-week (row 0 = Monday, row 6 = Sunday) with one cell per week
 * running left-to-right. A Column-of-Rows (not LazyVerticalGrid) sizes
 * itself to its content instead of needing a guessed fixed height — with
 * cells this small (~13dp each, based on 22 across a card's width), a
 * hardcoded height(60.dp) was too short for all 7 rows and silently
 * clipped the bottom ones.
 */
@Composable
private fun HabitHeatmap(color: Color, completions: Map<String, HabitCompletionEntry>) {
    val today = java.time.LocalDate.now()
    val cells = remember(today) { HabitStats.heatmapGrid(today) }
    val rows = remember(cells) { cells.chunked(22) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        rows.forEach { rowCells ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                rowCells.forEach { day ->
                    if (day == null) {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val doneThatDay = completions.containsKey(day.toString())
                        val isToday = day == today
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(3.dp))
                                .background(color.copy(alpha = if (doneThatDay) 1f else 0.18f))
                                .then(
                                    if (isToday) Modifier.border(1.dp, Color.Black.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                                    else Modifier
                                )
                        )
                    }
                }
            }
        }
    }
}
