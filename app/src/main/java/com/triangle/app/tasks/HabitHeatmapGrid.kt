package com.triangle.app.tasks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.triangle.app.data.HabitStats
import com.triangle.app.data.models.HabitCompletionEntry
import java.time.LocalDate

private const val GRID_COLS = 22

/**
 * 22-week x 7-day GitHub-contribution-graph-style grid — shared by HabitCard
 * (row preview), HabitDetailScreen and HabitAnalyticsScreen so all three
 * render the exact same grid instead of three copies.
 *
 * Drawn as ONE Canvas instead of a Column-of-Rows-of-Box (154 individual
 * clip+background+border UI nodes) — that per-cell node tree was the actual
 * cause of janky scrolling in the Habits list: every habit row paid for 154
 * measure/layout/draw passes just for its heatmap, and a LazyColumn with a
 * few such rows on screen at once compounded that on every frame. A single
 * Canvas draws the same 154 rounded rects in one draw pass instead, which is
 * the standard fix for this exact "grid of many tiny boxes" Compose perf trap.
 */
@Composable
fun HabitHeatmapGrid(
    color: Color,
    completions: Map<String, HabitCompletionEntry>,
    modifier: Modifier = Modifier,
    cellSpacing: androidx.compose.ui.unit.Dp = 3.dp
) {
    val today = remember { LocalDate.now() }
    val cells = remember(today) { HabitStats.heatmapGrid(today) }
    val rows = remember(cells) { cells.chunked(GRID_COLS) }
    val doneDays = remember(completions) { completions.keys }
    val rowCount = rows.size

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cellSize = (maxWidth - cellSpacing * (GRID_COLS - 1)) / GRID_COLS
        val totalHeight = cellSize * rowCount + cellSpacing * (rowCount - 1)

        Canvas(Modifier.fillMaxWidth().height(totalHeight)) {
            val cellPx = cellSize.toPx()
            val spacingPx = cellSpacing.toPx()
            val cornerPx = 3.dp.toPx()
            val borderPx = 1.dp.toPx()
            val cellSizePx = Size(cellPx, cellPx)
            val corner = CornerRadius(cornerPx, cornerPx)

            rows.forEachIndexed { r, rowCells ->
                rowCells.forEachIndexed { c, day ->
                    if (day == null) return@forEachIndexed
                    val topLeft = Offset(c * (cellPx + spacingPx), r * (cellPx + spacingPx))
                    val doneThatDay = doneDays.contains(day.toString())
                    drawRoundRect(
                        color = color.copy(alpha = if (doneThatDay) 1f else 0.18f),
                        topLeft = topLeft,
                        size = cellSizePx,
                        cornerRadius = corner
                    )
                    if (day == today) {
                        drawRoundRect(
                            color = Color.Black.copy(alpha = 0.4f),
                            topLeft = topLeft,
                            size = cellSizePx,
                            cornerRadius = corner,
                            style = Stroke(width = borderPx)
                        )
                    }
                }
            }
        }
    }
}
