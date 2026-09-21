package com.triangle.app.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.PriorityXp
import com.triangle.app.ui.theme.TriangleBrandPurple
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class FilterPanel {
    DATE_RANGE, STATUS, PRIORITY, XP, DUE_DATE, CREATED_DATE, COMPLETED_DATE,
    HABIT_FREQUENCY, HABIT_PERFORMANCE, STREAK_LENGTH
}

/**
 * The "Filters" screen (see the Filters feature plan) — a full page rather
 * than a bottom sheet, per the user's explicit request (a capped-height
 * sheet was clipping the last row and needed an internal scroll to reach
 * it). Edits happen on a local draft copy; nothing reaches
 * TasksHabitsViewModel until "Apply" is tapped, so backing out (system back,
 * the top-left arrow) discards the draft instead of silently applying a
 * half-finished selection. "Apply" is disabled until the draft actually
 * differs from what was passed in, so there's no way to "apply" a no-op.
 * [isHabitsPane] hides the three Habit-only panels (Frequency/Performance/
 * Streak Length) when false, since none of them mean anything for a Task.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterScreen(
    initialFilters: TaskHabitFilters,
    isHabitsPane: Boolean,
    onApply: (TaskHabitFilters) -> Unit,
    onBack: () -> Unit
) {
    var draft by remember { mutableStateOf(initialFilters) }
    var activePanel by remember { mutableStateOf<FilterPanel?>(null) }
    val hasChanges = draft != initialFilters

    // Back closes the open sub-panel first — only reaches whatever BackHandler
    // the caller registered for the whole screen once back at the main list.
    BackHandler(enabled = activePanel != null) { activePanel = null }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(activePanel?.let { panelTitle(it) } ?: "Filters", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { if (activePanel != null) activePanel = null else onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { draft = TaskHabitFilters() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.onSurface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) { Text("Clear all") }
                    Button(
                        onClick = { onApply(draft); onBack() },
                        enabled = hasChanges,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TriangleBrandPurple, contentColor = Color.White)
                    ) { Text("Apply") }
                }
            }
        }
    ) { padding ->
        // Plain, non-scrolling layout — a full page has enough room for every
        // row without the internal ScrollView the old bottom sheet needed.
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            val panel = activePanel
            if (panel == null) {
                FilterMainList(draft = draft, isHabitsPane = isHabitsPane, onOpen = { activePanel = it })
            } else {
                FilterPanelContent(panel = panel, draft = draft, onChange = { draft = it })
            }
        }
    }
}

private fun panelTitle(panel: FilterPanel): String = when (panel) {
    FilterPanel.DATE_RANGE -> "Date Range"
    FilterPanel.STATUS -> "Status"
    FilterPanel.PRIORITY -> "Priority"
    FilterPanel.XP -> "XP / Points"
    FilterPanel.DUE_DATE -> "Due Date"
    FilterPanel.CREATED_DATE -> "Created"
    FilterPanel.COMPLETED_DATE -> "Completed"
    FilterPanel.HABIT_FREQUENCY -> "Habit Frequency"
    FilterPanel.HABIT_PERFORMANCE -> "Habit Performance"
    FilterPanel.STREAK_LENGTH -> "Streak Length"
}

@Composable
private fun FilterMainList(draft: TaskHabitFilters, isHabitsPane: Boolean, onOpen: (FilterPanel) -> Unit) {
    Column {
        FilterRow(Icons.Default.CalendarMonth, "Date range", dateSpanSummary(draft.dateRangeHighlight)) { onOpen(FilterPanel.DATE_RANGE) }
        FilterRow(Icons.Default.CheckCircle, "Status", setSummary(draft.statuses.map { statusLabel(it) })) { onOpen(FilterPanel.STATUS) }
        FilterRow(Icons.Default.Flag, "Priority", setSummary(draft.priorities.map { PriorityXp.labelFor(it) })) { onOpen(FilterPanel.PRIORITY) }
        FilterRow(Icons.Default.Star, "XP / Points", xpSummary(draft.xpMin, draft.xpMax)) { onOpen(FilterPanel.XP) }
        FilterRow(Icons.Default.Event, "Due date", dateSpanSummary(draft.dueDateRange)) { onOpen(FilterPanel.DUE_DATE) }
        FilterRow(Icons.Default.CalendarMonth, "Created", dateSpanSummary(draft.createdDateRange)) { onOpen(FilterPanel.CREATED_DATE) }
        FilterRow(Icons.Default.TaskAlt, "Completed", dateSpanSummary(draft.completedDateRange), isLast = !isHabitsPane) { onOpen(FilterPanel.COMPLETED_DATE) }
        if (isHabitsPane) {
            FilterRow(Icons.Default.Repeat, "Habit frequency", setSummary(draft.habitFrequencyTypes.map { frequencyLabel(it) })) { onOpen(FilterPanel.HABIT_FREQUENCY) }
            FilterRow(Icons.Default.Insights, "Habit performance", setSummary(draft.habitPerformance.map { performanceLabel(it) })) { onOpen(FilterPanel.HABIT_PERFORMANCE) }
            FilterRow(Icons.Default.LocalFireDepartment, "Streak length", streakSummary(draft.streakLength), isLast = true) { onOpen(FilterPanel.STREAK_LENGTH) }
        }
    }
}

/** [value] is null when nothing's been chosen for this category — the row then shows just the label, no "All"/"All time" placeholder underneath. */
@Composable
private fun FilterRow(icon: ImageVector, label: String, value: String?, isLast: Boolean = false, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = TriangleBrandPurple, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (value != null) {
                    Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!isLast) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

private fun setSummary(labels: List<String>): String? = if (labels.isEmpty()) null else labels.joinToString(", ")

private fun xpSummary(min: Int?, max: Int?): String? = when {
    min == null && max == null -> null
    max == null -> "$min+"
    min == null -> "0-$max"
    else -> "$min-$max"
}

private val summaryFmt = DateTimeFormatter.ofPattern("d MMM yyyy")
private fun dateSpanSummary(span: DateSpan?): String? =
    if (span == null) null else if (span.start == span.end) span.start.format(summaryFmt) else "${span.start.format(summaryFmt)} - ${span.end.format(summaryFmt)}"

private fun statusLabel(status: TaskStatus): String = when (status) {
    TaskStatus.PENDING -> "Pending"
    TaskStatus.COMPLETED -> "Completed"
    TaskStatus.OVERDUE -> "Overdue"
}

private fun frequencyLabel(type: String): String = when (type) {
    "everyday" -> "Daily"
    "daysOfWeek" -> "Weekly"
    "daysOfMonth" -> "Monthly"
    "perPeriod" -> "Custom"
    else -> type
}

private fun performanceLabel(p: HabitPerformance): String = when (p) {
    HabitPerformance.ACTIVE -> "Active"
    HabitPerformance.COMPLETED_TODAY -> "Completed Today"
    HabitPerformance.MISSED_TODAY -> "Missed Today"
    HabitPerformance.STREAK_ACTIVE -> "Streak Active"
    HabitPerformance.STREAK_BROKEN -> "Streak Broken"
}

private fun streakSummary(f: StreakLengthFilter?): String? = when (f) {
    null -> null
    StreakLengthFilter.ZERO -> "0 days"
    StreakLengthFilter.SHORT -> "1-7 days"
    StreakLengthFilter.MEDIUM -> "8-30 days"
    StreakLengthFilter.LONG -> "30+ days"
    else -> "${f.min}-${f.max ?: "∞"} days"
}

@Composable
private fun FilterPanelContent(panel: FilterPanel, draft: TaskHabitFilters, onChange: (TaskHabitFilters) -> Unit) {
    when (panel) {
        FilterPanel.DATE_RANGE -> DateRangeSingleSelectPanel(
            current = draft.dateRangeHighlight,
            presets = generalDatePresets(),
            onSelect = { onChange(draft.copy(dateRangeHighlight = it)) }
        )
        FilterPanel.STATUS -> MultiSelectPanel(
            options = TaskStatus.entries,
            selected = draft.statuses,
            label = { statusLabel(it) },
            onToggle = { status -> onChange(draft.copy(statuses = draft.statuses.toggled(status))) }
        )
        FilterPanel.PRIORITY -> MultiSelectPanel(
            options = PriorityXp.LABELS.keys.toList(),
            selected = draft.priorities,
            label = { PriorityXp.labelFor(it) },
            onToggle = { p -> onChange(draft.copy(priorities = draft.priorities.toggled(p))) }
        )
        FilterPanel.XP -> XpRangePanel(
            min = draft.xpMin,
            max = draft.xpMax,
            onChange = { min, max -> onChange(draft.copy(xpMin = min, xpMax = max)) }
        )
        FilterPanel.DUE_DATE -> DateRangeSingleSelectPanel(draft.dueDateRange, dueDatePresets()) { onChange(draft.copy(dueDateRange = it)) }
        FilterPanel.CREATED_DATE -> DateRangeSingleSelectPanel(draft.createdDateRange, generalDatePresets()) { onChange(draft.copy(createdDateRange = it)) }
        FilterPanel.COMPLETED_DATE -> DateRangeSingleSelectPanel(draft.completedDateRange, generalDatePresets()) { onChange(draft.copy(completedDateRange = it)) }
        FilterPanel.HABIT_FREQUENCY -> MultiSelectPanel(
            options = listOf("everyday", "daysOfWeek", "daysOfMonth", "perPeriod"),
            selected = draft.habitFrequencyTypes,
            label = { frequencyLabel(it) },
            onToggle = { t -> onChange(draft.copy(habitFrequencyTypes = draft.habitFrequencyTypes.toggled(t))) }
        )
        FilterPanel.HABIT_PERFORMANCE -> MultiSelectPanel(
            options = HabitPerformance.entries,
            selected = draft.habitPerformance,
            label = { performanceLabel(it) },
            onToggle = { p -> onChange(draft.copy(habitPerformance = draft.habitPerformance.toggled(p))) }
        )
        FilterPanel.STREAK_LENGTH -> StreakLengthPanel(current = draft.streakLength, onSelect = { onChange(draft.copy(streakLength = it)) })
    }
}

private fun <T> Set<T>.toggled(item: T): Set<T> = if (contains(item)) this - item else this + item

@Composable
private fun <T> MultiSelectPanel(options: List<T>, selected: Set<T>, label: (T) -> String, onToggle: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { opt -> SelectableChip(label(opt), opt in selected, Modifier.weight(1f)) { onToggle(opt) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private data class DatePreset(val label: String, val span: DateSpan?)

private fun presetRange(preset: String): DateSpan? {
    val today = LocalDate.now()
    return when (preset) {
        "Today" -> DateSpan(today, today)
        "Yesterday" -> DateSpan(today.minusDays(1), today.minusDays(1))
        "Tomorrow" -> DateSpan(today.plusDays(1), today.plusDays(1))
        "This Week" -> {
            val dow = today.dayOfWeek.value % 7
            val start = today.minusDays(dow.toLong())
            DateSpan(start, start.plusDays(6))
        }
        "Last Week" -> {
            val dow = today.dayOfWeek.value % 7
            val thisWeekStart = today.minusDays(dow.toLong())
            DateSpan(thisWeekStart.minusDays(7), thisWeekStart.minusDays(1))
        }
        "This Month" -> DateSpan(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()))
        "Last Month" -> {
            val lastMonth = today.minusMonths(1)
            DateSpan(lastMonth.withDayOfMonth(1), lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()))
        }
        "This Year" -> DateSpan(today.withDayOfYear(1), today.withDayOfYear(today.lengthOfYear()))
        "Last Year" -> {
            val ly = today.minusYears(1)
            DateSpan(ly.withDayOfYear(1), ly.withDayOfYear(ly.lengthOfYear()))
        }
        "Overdue" -> DateSpan(LocalDate.of(2000, 1, 1), today.minusDays(1))
        else -> null
    }
}

private fun generalDatePresets(): List<DatePreset> = listOf(
    DatePreset("All time", null),
    DatePreset("Today", presetRange("Today")),
    DatePreset("Yesterday", presetRange("Yesterday")),
    DatePreset("This Week", presetRange("This Week")),
    DatePreset("Last Week", presetRange("Last Week")),
    DatePreset("This Month", presetRange("This Month")),
    DatePreset("Last Month", presetRange("Last Month")),
    DatePreset("This Year", presetRange("This Year")),
    DatePreset("Last Year", presetRange("Last Year"))
)

private fun dueDatePresets(): List<DatePreset> = listOf(
    DatePreset("All time", null),
    DatePreset("Overdue", presetRange("Overdue")),
    DatePreset("Today", presetRange("Today")),
    DatePreset("Tomorrow", presetRange("Tomorrow")),
    DatePreset("This Week", presetRange("This Week")),
    DatePreset("This Month", presetRange("This Month"))
)

/**
 * A single-select date-range picker built entirely from presets — no custom
 * start/end calendar dialog yet (a deliberate scope cut for this phase; the
 * mockup's "Custom Range" option isn't offered here).
 *
 * The "All time" preset's span is `null` — same value as "nothing chosen
 * yet" — so it's deliberately never drawn as active, even when current ==
 * null. Otherwise every panel would open looking like a filter was already
 * applied. It still works as a real tap target to explicitly clear back to
 * "no filter" from some other selection.
 */
@Composable
private fun DateRangeSingleSelectPanel(current: DateSpan?, presets: List<DatePreset>, onSelect: (DateSpan?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        presets.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { preset ->
                    val active = preset.span != null && preset.span == current
                    SelectableChip(preset.label, active, Modifier.weight(1f)) { onSelect(preset.span) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun XpRangePanel(min: Int?, max: Int?, onChange: (Int?, Int?) -> Unit) {
    val presets = listOf<Pair<String, Pair<Int?, Int?>>>(
        "All" to (null to null),
        "0-25" to (0 to 25),
        "26-50" to (26 to 50),
        "51-100" to (51 to 100),
        "100+" to (100 to null)
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        presets.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, range) ->
                    // Same "don't highlight the no-filter option by default" rule as DateRangeSingleSelectPanel.
                    val isAllPreset = range.first == null && range.second == null
                    val active = !isAllPreset && min == range.first && max == range.second
                    SelectableChip(label, active, Modifier.weight(1f)) { onChange(range.first, range.second) }
                }
            }
        }
        Text("Custom range", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = min?.toString() ?: "",
                onValueChange = { onChange(it.toIntOrNull(), max) },
                label = { Text("Minimum XP") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = max?.toString() ?: "",
                onValueChange = { onChange(min, it.toIntOrNull()) },
                label = { Text("Maximum XP") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StreakLengthPanel(current: StreakLengthFilter?, onSelect: (StreakLengthFilter?) -> Unit) {
    val options = listOf<Pair<String, StreakLengthFilter?>>(
        "All" to null,
        "0 days" to StreakLengthFilter.ZERO,
        "1-7 days" to StreakLengthFilter.SHORT,
        "8-30 days" to StreakLengthFilter.MEDIUM,
        "30+ days" to StreakLengthFilter.LONG
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value) ->
                    val active = value != null && value == current
                    SelectableChip(label, active, Modifier.weight(1f)) { onSelect(value) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Matches CreateEditTaskScreen's PriorityPicker/category-pill visual language (active = brand-purple tint + border) so the sheet reads as the same design system as the rest of the app. */
@Composable
private fun SelectableChip(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) TriangleBrandPurple.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.5.dp, if (active) TriangleBrandPurple else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) TriangleBrandPurple else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
