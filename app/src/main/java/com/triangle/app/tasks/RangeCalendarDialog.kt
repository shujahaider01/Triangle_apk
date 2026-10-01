package com.triangle.app.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.ui.theme.TriangleBrandPurple
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val WEEKDAYS = listOf("S", "M", "T", "W", "T", "F", "S")

/**
 * A plain one-month calendar for picking a date range: arrows step between
 * months (no scrolling), nothing is selected when it opens, and no
 * range text is shown — tap a start day, then an end day (Apply / Clear). Tapping again
 * after a full range starts a new one.
 */
@Composable
fun RangeCalendarDialog(
    initialMonth: YearMonth,
    onApply: (start: LocalDate, end: LocalDate?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var month by remember { mutableStateOf(initialMonth) }
    var start by remember { mutableStateOf<LocalDate?>(null) }
    var end by remember { mutableStateOf<LocalDate?>(null) }
    val today = remember { LocalDate.now() }

    fun tap(d: LocalDate) {
        val s = start
        when {
            s == null || end != null -> { start = d; end = null }
            d.isBefore(s) -> start = d
            else -> end = d
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = start != null, onClick = { start?.let { onApply(it, end) } }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onClear) { Text("Clear") } },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = month.minusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                    }
                    Text(
                        "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    IconButton(onClick = { month = month.plusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth()) {
                    WEEKDAYS.forEach {
                        Text(
                            it,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))

                // Sunday-first grid: leading blanks, then the month's days, padded to full weeks.
                val firstOffset = month.atDay(1).dayOfWeek.value % 7
                val cells: List<LocalDate?> =
                    List(firstOffset) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { day -> DayCell(day, today, start, end, Modifier.weight(1f)) { tap(it) } }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    )
}

@Composable
private fun DayCell(
    day: LocalDate?,
    today: LocalDate,
    start: LocalDate?,
    end: LocalDate?,
    modifier: Modifier,
    onTap: (LocalDate) -> Unit
) {
    if (day == null) {
        Spacer(modifier.height(40.dp))
        return
    }
    val isEdge = day == start || day == end
    val inBand = start != null && end != null && !day.isBefore(start) && !day.isAfter(end)
    Box(
        modifier
            .height(40.dp)
            .then(if (inBand) Modifier.background(TriangleBrandPurple.copy(alpha = 0.15f)) else Modifier)
            .clickable { onTap(day) },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .then(if (isEdge) Modifier.background(TriangleBrandPurple) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Text(
                day.dayOfMonth.toString(),
                fontSize = 14.sp,
                fontWeight = if (day == today || isEdge) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isEdge -> Color.White
                    day == today -> TriangleBrandPurple
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}
