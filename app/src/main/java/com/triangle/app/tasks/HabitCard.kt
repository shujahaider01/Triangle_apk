package com.triangle.app.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.HabitPalette
import com.triangle.app.data.HabitStats
import com.triangle.app.data.models.Habit
import com.triangle.app.data.models.HabitCompletionEntry
import com.triangle.app.ui.SvgPathIcon
import java.time.LocalDate

/** Matches script.js's _buildHabitCard()/.habit-card — icon, name, streak, heatmap, complete-today circle. */
@Composable
fun HabitCard(
    habit: Habit,
    completions: Map<String, HabitCompletionEntry>,
    streak: Int,
    doneToday: Boolean,
    onClick: () -> Unit,
    onCompleteToday: () -> Unit
) {
    val color = runCatching { Color(android.graphics.Color.parseColor(habit.color)) }.getOrDefault(MaterialTheme.colorScheme.primary)
    val svg = habit.iconSvg ?: HabitPalette.ICONS.getValue(HabitPalette.DEFAULT_ICON_KEY)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp, 16.dp, 16.dp, 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)),
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
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (doneToday) color else Color.Transparent)
                    .clickable(enabled = !doneToday, onClick = onCompleteToday),
                contentAlignment = Alignment.Center
            ) {
                if (doneToday) {
                    Icon(Icons.Default.Check, contentDescription = "Done today", tint = Color.White, modifier = Modifier.size(18.dp))
                } else {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        HabitHeatmap(color = color, completions = completions)
    }
}

/** 22x7 GitHub-contribution-graph-style grid, most recent day last — matches _buildHabitHeatmap(). */
@Composable
private fun HabitHeatmap(color: Color, completions: Map<String, HabitCompletionEntry>) {
    val today = LocalDate.now()
    val days = (153 downTo 0).map { today.minusDays(it.toLong()) } // 22*7 = 154 days
    LazyVerticalGrid(
        columns = GridCells.Fixed(22),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.fillMaxWidth().height(60.dp)
    ) {
        items(days) { day ->
            val doneThatDay = completions.containsKey(day.toString())
            Box(
                Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = if (doneThatDay) 1f else 0.18f))
            )
        }
    }
}
