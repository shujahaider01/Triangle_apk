package com.triangle.app.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.triangle.app.data.HabitStats
import com.triangle.app.data.models.Habit
import com.triangle.app.data.models.HabitCompletionEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Native port of script.js's openHabitAnalytics() — reached from Habit
 * Detail's hero "Analytics" button, or from an assigned-out habit row's own
 * Analytics shortcut (see AssignedRowCards.kt) — the assigner isn't the one
 * completing an assigned habit, so [completions] is passed in directly
 * (merged across every recipient for an assigned group) rather than read
 * from the viewer's own TasksHabitsViewModel state. Plain (non-hero) top
 * bar, then a scrolling stack of stat cards, a monthly completion bar chart
 * (the source itself renders this as plain divs with height percentages,
 * not an actual <canvas> — Compose Boxes are the faithful port here, not a
 * simplification), the shared HabitHeatmapGrid, streak-run lists, a
 * navigable year heatmap, and a completion history list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitAnalyticsScreen(
    habit: Habit,
    completions: Map<String, HabitCompletionEntry>,
    onBack: () -> Unit
) {
    val color = runCatching { Color(android.graphics.Color.parseColor(habit.color)) }.getOrDefault(MaterialTheme.colorScheme.primary)
    val streak = remember(habit, completions) { HabitStats.calcStreak(habit, completions) }
    val score = remember(completions) { HabitStats.calcScore(habit, completions) }

    val startDate = runCatching { LocalDate.parse(habit.startDate) }.getOrNull() ?: LocalDate.now()
    val today = LocalDate.now()
    val totalDays = (java.time.temporal.ChronoUnit.DAYS.between(startDate, today) + 1).coerceAtLeast(1)
    val completionsCount = completions.size
    val completionRate = if (totalDays == 0L) 0 else Math.round(completionsCount * 100.0 / totalDays).toInt()

    var chartOffset by remember { mutableIntStateOf(0) }
    var yearHeatmapYear by remember { mutableIntStateOf(today.year) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Stats grid
            val stats = listOf(
                "Current Streak" to "${streak.current} days",
                "Best Streak" to "${streak.best} days",
                "Completions" to "$completionsCount",
                "Completion Rate" to "$completionRate%",
                "Total Days" to "$totalDays",
                "Score" to "$score%"
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                stats.chunked(2).forEach { rowStats ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        rowStats.forEach { (label, value) -> StatCard(label, value, Modifier.weight(1f)) }
                    }
                }
            }

            // Completion (monthly bar chart)
            AnalyticsCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Completion", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { chartOffset++ }, modifier = Modifier.size(28.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Older", tint = color) }
                        IconButton(onClick = { chartOffset = (chartOffset - 1).coerceAtLeast(0) }, modifier = Modifier.size(28.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Newer", tint = color) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                MonthlyCompletionChart(habit = habit, completions = completions, offset = chartOffset, color = color)
            }

            // Heatmap
            AnalyticsCard {
                Text("Last 30 Days", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                HabitHeatmapGrid(color = color, completions = completions, cellSpacing = 5.dp)
            }

            // Latest Streaks
            AnalyticsCard {
                Text("Latest Streaks", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                val runs = remember(completions) { HabitStats.streakRuns(habit, completions) }
                StreakRunsList(runs.take(8), color)
            }

            // Top 5 Streaks
            AnalyticsCard {
                Text("Top 5 Streaks", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                val topRuns = remember(completions) { HabitStats.streakRuns(habit, completions).sortedByDescending { it.len }.take(5) }
                StreakRunsList(topRuns, color)
            }

            // Year heatmap
            AnalyticsCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Streak $yearHeatmapYear", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { yearHeatmapYear-- }, modifier = Modifier.size(28.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous year", tint = color) }
                        IconButton(onClick = { yearHeatmapYear++ }, modifier = Modifier.size(28.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next year", tint = color) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                YearHeatmap(completions = completions, year = yearHeatmapYear, color = color)
            }

            // Completion History
            AnalyticsCard {
                Text("Completion History", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                val recentEntries = remember(completions) {
                    completions.entries
                        .mapNotNull { (key, entry) -> runCatching { LocalDate.parse(key) }.getOrNull()?.let { it to entry } }
                        .sortedByDescending { it.second.completedAt }
                        .take(10)
                }
                if (recentEntries.isEmpty()) {
                    Text("No completions yet", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    recentEntries.forEachIndexed { index, (date, entry) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
                            Column(Modifier.weight(1f)) {
                                Text(date.format(DateTimeFormatter.ofPattern("d MMM yyyy")), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    formatCompletionTime(entry.completedAt),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                            Text("✓", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
                        }
                        if (index != recentEntries.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun formatCompletionTime(completedAtMillis: Long): String =
    java.time.Instant.ofEpochMilli(completedAtMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("h:mm a"))

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun AnalyticsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun MonthlyCompletionChart(habit: Habit, completions: Map<String, com.triangle.app.data.models.HabitCompletionEntry>, offset: Int, color: Color) {
    val months = remember(completions, offset) { HabitStats.monthlyCompletions(habit, completions, offset) }
    val axisMax = remember(months) { maxOf(30, (months.maxOfOrNull { it.count } ?: 0).let { raw -> ((raw + 9) / 10) * 10 }) }
    val ticks = listOf(axisMax, axisMax * 2 / 3, axisMax / 3, 0)

    Row(Modifier.fillMaxWidth().height(160.dp)) {
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            ticks.forEach { t -> Text("$t", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) }
        }
        Spacer(Modifier.width(10.dp))
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .border(androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant))
                .padding(start = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            months.forEach { m ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    val fraction = (m.count.toFloat() / axisMax).coerceIn(0.02f, 1f)
                    Box(
                        Modifier
                            .fillMaxWidth(0.6f)
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(color)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(m.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun StreakRunsList(runs: List<HabitStats.StreakRun>, color: Color) {
    if (runs.isEmpty()) {
        Text("No streaks yet", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
        return
    }
    val maxLen = runs.maxOf { it.len }
    val fmt = DateTimeFormatter.ofPattern("d MMM")
    Column {
        runs.forEach { run ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(run.start.format(fmt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Box(Modifier.weight(1f).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    val widthFraction = 0.14f + (run.len.toFloat() / maxLen) * 0.7f
                    Box(
                        Modifier
                            .fillMaxWidth(widthFraction.coerceIn(0.14f, 1f))
                            .clip(RoundedCornerShape(20.dp))
                            .background(color)
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${run.len}", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF111111))
                    }
                }
                Text(run.end.format(fmt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun YearHeatmap(completions: Map<String, com.triangle.app.data.models.HabitCompletionEntry>, year: Int, color: Color) {
    val monthLabels = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        for (month in 0 until 12) {
            val daysInMonth = LocalDate.of(year, month + 1, 1).lengthOfMonth()
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(monthLabels[month], fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                for (row in 0 until 31) {
                    val day = row + 1
                    if (day > daysInMonth) {
                        Box(Modifier.size(15.dp))
                    } else {
                        val dateStr = LocalDate.of(year, month + 1, day).toString()
                        val filled = completions.containsKey(dateStr)
                        Box(
                            Modifier
                                .size(15.dp)
                                .clip(RoundedCornerShape(3.5.dp))
                                .background(if (filled) color else MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }
            }
        }
    }
}
