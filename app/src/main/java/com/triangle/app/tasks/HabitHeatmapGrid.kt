package com.triangle.app.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.triangle.app.data.HabitStats
import com.triangle.app.data.models.HabitCompletionEntry
import java.time.LocalDate

/**
 * 22-week x 7-day GitHub-contribution-graph-style grid — shared by HabitCard
 * (row preview), HabitDetailScreen and HabitAnalyticsScreen so all three
 * render the exact same grid instead of three copies. A Column-of-Rows
 * (not LazyVerticalGrid) sizes itself to its own content instead of a
 * guessed fixed height — see the fix on HabitCard's original copy, which a
 * hardcoded height(60.dp) was silently clipping to ~4-5 of the 7 rows.
 */
@Composable
fun HabitHeatmapGrid(
    color: Color,
    completions: Map<String, HabitCompletionEntry>,
    modifier: Modifier = Modifier,
    cellSpacing: androidx.compose.ui.unit.Dp = 3.dp
) {
    val today = LocalDate.now()
    val cells = remember(today) { HabitStats.heatmapGrid(today) }
    val rows = remember(cells) { cells.chunked(22) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(cellSpacing)) {
        rows.forEach { rowCells ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(cellSpacing)) {
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
