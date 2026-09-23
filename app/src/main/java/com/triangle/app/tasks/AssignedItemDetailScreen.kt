package com.triangle.app.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.AssignmentRepository
import com.triangle.app.data.HabitPalette

/**
 * Read-only detail page for a task/habit the viewer assigned out to their
 * Circle — opened by tapping an AssignedTaskRowCard/AssignedHabitRowCard
 * (the badge itself instead opens AssignedGroupMembersSheet, a quicker
 * glance at just the completion breakdown; see AssignedRowCards.kt). This
 * intentionally isn't TaskDetailScreen/HabitDetailScreen reused: the item
 * lives in the RECIPIENT's org, not the viewer's own, and it's not the
 * viewer's to edit or complete, so there's no add-note/add-photo/checklist-
 * toggle chrome here — just what the task/habit says plus who it went to.
 */
@Composable
fun AssignedTaskDetailScreen(group: AssignmentRepository.AssignedTaskGroup, onDelete: () -> Unit, onBack: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val task = group.task
    val color = runCatching { Color(android.graphics.Color.parseColor(task.iconColor ?: HabitPalette.DEFAULT_COLOR)) }
        .getOrDefault(MaterialTheme.colorScheme.primary)
    val svg = task.iconSvg ?: HabitPalette.ICONS.getValue(HabitPalette.DEFAULT_ICON_KEY)

    // Same "black area at the bottom" fix as TaskDetailScreen/HabitDetailScreen.
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            DetailHero(
                heroColor = color,
                iconSvg = svg,
                title = task.title,
                pillIcon = Icons.Default.Star,
                pillText = if (task.points > 0) "${task.points} XP" else "—",
                actions = emptyList(),
                onBack = onBack,
                menuOptions = listOf(DetailMenuOption("Delete", destructive = true, onClick = { showDeleteConfirm = true }))
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .offset(y = (-60).dp)
                    .defaultMinSize(minHeight = 500.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Spacer(Modifier.height(18.dp))
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AccordionSection(title = "General", initiallyOpen = task.description.isNotBlank()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("DESCRIPTION", fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                            Spacer(Modifier.height(8.dp))
                            if (task.description.isNotBlank()) {
                                Text(task.description, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 22.sp)
                            } else {
                                Text("No description added.", fontSize = 15.sp, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            }
                        }
                    }
                    AccordionSection(title = "Details", initiallyOpen = true) {
                        Column {
                            if (task.points > 0) DetailInfoRowChip("Points", "${task.points} XP", color)
                            DetailInfoRow("Due Date", task.dueDate ?: "—", valueColor = if (task.dueDate != null) Color(0xFFEF4444) else null, isLast = true)
                        }
                    }
                    AccordionSection(title = "Recipients", badge = "${group.doneCount}/${group.members.size}", initiallyOpen = true) {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            group.members.forEach { AssignedMemberRow(it.name, it.done, it.photoUrl) }
                        }
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Delete this task?",
            body = "This removes it for you and every recipient. This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { showDeleteConfirm = false; onDelete() },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

/** Same idea as AssignedTaskDetailScreen, for a habit — see its doc comment. */
@Composable
fun AssignedHabitDetailScreen(group: AssignmentRepository.AssignedHabitGroup, dateStr: String, onDelete: () -> Unit, onBack: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val habit = group.habit
    val color = runCatching { Color(android.graphics.Color.parseColor(habit.color)) }.getOrDefault(MaterialTheme.colorScheme.primary)
    val svg = habit.iconSvg ?: HabitPalette.ICONS.getValue(HabitPalette.DEFAULT_ICON_KEY)
    val doneCount = group.doneCount(dateStr)

    // Same "black area at the bottom" fix as TaskDetailScreen/HabitDetailScreen.
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            DetailHero(
                heroColor = color,
                iconSvg = svg,
                title = habit.name,
                pillIcon = Icons.Default.Star,
                pillText = "${habit.xpPerCompletion} XP",
                actions = emptyList(),
                onBack = onBack,
                menuOptions = listOf(DetailMenuOption("Delete", destructive = true, onClick = { showDeleteConfirm = true }))
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .offset(y = (-60).dp)
                    .defaultMinSize(minHeight = 500.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Spacer(Modifier.height(18.dp))
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AccordionSection(title = "General", initiallyOpen = habit.description.isNotBlank()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("DESCRIPTION", fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                            Spacer(Modifier.height(8.dp))
                            if (habit.description.isNotBlank()) {
                                Text(habit.description, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 22.sp)
                            } else {
                                Text("No description added.", fontSize = 15.sp, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            }
                        }
                    }
                    AccordionSection(title = "Details", initiallyOpen = true) {
                        Column {
                            DetailInfoRow("Frequency", frequencyLabel(habit))
                            DetailInfoRow("Habit Created", habit.startDate, isLast = true)
                        }
                    }
                    AccordionSection(title = "Recipients", badge = "$doneCount/${group.members.size}", initiallyOpen = true) {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            group.members.forEach { AssignedMemberRow(it.name, it.completions.containsKey(dateStr), it.photoUrl) }
                        }
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Delete this habit?",
            body = "This removes it for you and every recipient. This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { showDeleteConfirm = false; onDelete() },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
