package com.triangle.app.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.PriorityXp
import com.triangle.app.ui.theme.TriangleBrandPurple

/**
 * Filters, kept to one simple page: titled sections of pill buttons, in the style of a plain category
 * list. Every section is multi-select (tap several pills; a row matches if it fits ANY pill picked in
 * each section, and ALL sections that have something picked). Status and Priority apply to Tasks and
 * Habits; Habit frequency and Streak only appear on the Habits pane.
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
    val hasChanges = draft != initialFilters

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Filters", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { draft = TaskHabitFilters() },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) { Text("Clear all") }
                    Button(
                        // Only these four categories exist now, so anything else a saved draft carried is dropped.
                        onClick = {
                            onApply(
                                TaskHabitFilters(
                                    statuses = draft.statuses,
                                    priorities = draft.priorities,
                                    habitFrequencyTypes = if (isHabitsPane) draft.habitFrequencyTypes else emptySet(),
                                    streakLengths = if (isHabitsPane) draft.streakLengths else emptySet()
                                )
                            )
                            onBack()
                        },
                        enabled = hasChanges,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TriangleBrandPurple, contentColor = Color.White)
                    ) { Text("Apply") }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section("Status") {
                val statuses = if (isHabitsPane) listOf(TaskStatus.PENDING, TaskStatus.COMPLETED) // habits have no due date, so no Overdue
                else listOf(TaskStatus.PENDING, TaskStatus.COMPLETED, TaskStatus.OVERDUE)
                statuses.forEach { s ->
                    Pill(statusLabel(s), s in draft.statuses) { draft = draft.copy(statuses = draft.statuses.toggled(s)) }
                }
            }
            Section("Priority") {
                PriorityXp.LABELS.keys.forEach { key ->
                    Pill(PriorityXp.labelFor(key), key in draft.priorities) { draft = draft.copy(priorities = draft.priorities.toggled(key)) }
                }
            }
            if (isHabitsPane) {
                Section("Habit frequency") {
                    listOf("everyday", "daysOfWeek", "daysOfMonth", "perPeriod").forEach { t ->
                        Pill(frequencyLabel(t), t in draft.habitFrequencyTypes) {
                            draft = draft.copy(habitFrequencyTypes = draft.habitFrequencyTypes.toggled(t))
                        }
                    }
                }
                Section("Streak") {
                    STREAK_OPTIONS.forEach { (label, range) ->
                        Pill(label, range in draft.streakLengths) { draft = draft.copy(streakLengths = draft.streakLengths.toggled(range)) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private val STREAK_OPTIONS = listOf(
    "No streak" to StreakLengthFilter.ZERO,
    "1–7 days" to StreakLengthFilter.SHORT,
    "8–30 days" to StreakLengthFilter.MEDIUM,
    "30+ days" to StreakLengthFilter.LONG
)

private fun statusLabel(status: TaskStatus): String = when (status) {
    TaskStatus.PENDING -> "Pending"
    TaskStatus.COMPLETED -> "Completed"
    TaskStatus.OVERDUE -> "Overdue"
}

private fun frequencyLabel(type: String): String = when (type) {
    "everyday" -> "Everyday"
    "daysOfWeek" -> "Specific days"
    "daysOfMonth" -> "Days of month"
    "perPeriod" -> "Some days"
    else -> type
}

private fun <T> Set<T>.toggled(item: T): Set<T> = if (contains(item)) this - item else this + item

/** A titled block of pills with a grey band under it, like a plain category list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(title: String, pills: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { pills() }
    }
    Box(Modifier.fillMaxWidth().height(10.dp).background(MaterialTheme.colorScheme.surfaceVariant))
}

/** One pill: tinted when off, filled purple with a tick when on. */
@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) TriangleBrandPurple else TriangleBrandPurple.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label,
            fontSize = 16.sp,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
