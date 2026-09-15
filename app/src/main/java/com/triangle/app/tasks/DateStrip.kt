package com.triangle.app.tasks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private val DAY_WIDTH = 52.dp
private val RING_SIZE = 46.dp
private val RING_STROKE = 2.5.dp

/**
 * Native port of script.js's _buildDateStrip() — the full calendar month
 * containing [selectedDate], scrollable, each day showing a circular
 * completion-% progress ring (matches .ds-day/.ds-day-ring/.ds-ring-fill).
 * [pctForDate] is whichever tab is active (tasksPctForDate/habitsPctForDate
 * on TasksHabitsViewModel) — the ring reflects that tab's data.
 */
@Composable
fun DateStrip(
    selectedDate: LocalDate,
    brandColor: Color,
    pctForDate: (LocalDate) -> Int,
    onSelect: (LocalDate) -> Unit
) {
    val today = remember { LocalDate.now() }
    val daysInMonth = remember(selectedDate.year, selectedDate.monthValue) {
        val first = selectedDate.withDayOfMonth(1)
        val last = selectedDate.withDayOfMonth(selectedDate.lengthOfMonth())
        generateSequence(first) { d -> if (d.isBefore(last)) d.plusDays(1) else null }.toList()
    }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var viewportWidthPx by remember { mutableIntStateOf(0) }

    Box {
        val visibleCount = max(1, with(density) { viewportWidthPx.toDp() / DAY_WIDTH }.toInt())
        LaunchedEffect(selectedDate, viewportWidthPx) {
            if (viewportWidthPx == 0) return@LaunchedEffect
            val targetIndex = daysInMonth.indexOf(selectedDate).coerceAtLeast(0)
            val centered = max(0, min(daysInMonth.lastIndex, targetIndex - visibleCount / 2))
            listState.animateScrollToItem(centered)
        }

        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.onSizeChanged { viewportWidthPx = it.width }
        ) {
            itemsIndexed(daysInMonth, key = { _, d -> d.toString() }) { _, date ->
                DateStripDay(
                    date = date,
                    isToday = date == today,
                    isSelected = date == selectedDate,
                    isPast = date.isBefore(today),
                    pct = pctForDate(date),
                    brandColor = brandColor,
                    onClick = { onSelect(date) }
                )
            }
        }
    }
}

@Composable
private fun DateStripDay(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    isPast: Boolean,
    pct: Int,
    brandColor: Color,
    onClick: () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val neutralTrack = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.14f else 0.09f)
    val trackColor = when {
        isToday -> brandColor.copy(alpha = 0.15f)
        isSelected -> brandColor.copy(alpha = 0.25f)
        isPast -> brandColor.copy(alpha = 0.15f)
        else -> neutralTrack
    }
    // Label: today always wins (stays dark even if also selected); day-num: selected always wins (even over today).
    val labelColor = when {
        isToday -> MaterialTheme.colorScheme.onSurface
        isSelected -> brandColor
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    }
    val numColor = when {
        isSelected -> brandColor
        isToday -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    }
    val labelWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium
    val numWeight = if (isToday || isSelected) FontWeight.Black else FontWeight.SemiBold

    Column(
        modifier = Modifier.width(DAY_WIDTH).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase().take(3),
            fontSize = 11.sp,
            fontWeight = labelWeight,
            color = labelColor
        )
        Spacer(Modifier.height(6.dp))
        Box(Modifier.size(RING_SIZE), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(RING_SIZE)) {
                val strokePx = RING_STROKE.toPx()
                val inset = strokePx / 2
                val arcSize = androidx.compose.ui.geometry.Size(size.width - strokePx, size.height - strokePx)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                )
                if (pct > 0) {
                    drawArc(
                        color = brandColor,
                        startAngle = -90f,
                        sweepAngle = 360f * (pct.coerceIn(0, 100) / 100f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
            }
            Text(date.dayOfMonth.toString(), fontSize = 15.sp, fontWeight = numWeight, color = numColor)
        }
    }
}
