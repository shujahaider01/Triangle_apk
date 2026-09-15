package com.triangle.app.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.HabitPalette
import com.triangle.app.data.models.Task
import com.triangle.app.ui.SvgPathIcon

/**
 * Native port of script.js's _openNewTaskDetail() (the "ntd" full-screen
 * task detail page) — hero (icon/title/status/XP), details, checklist,
 * Mark Complete. Notes/photos timeline isn't ported this pass (a smaller,
 * lower-traffic part of the page) — the checklist and completion flow are
 * the parts users actually interact with daily.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    viewModel: TasksHabitsViewModel,
    task: Task,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val done = viewModel.isDone(task, state.completions)
    val color = runCatching { Color(android.graphics.Color.parseColor(task.iconColor ?: HabitPalette.DEFAULT_COLOR)) }
        .getOrDefault(MaterialTheme.colorScheme.primary)
    val svg = task.iconSvg ?: HabitPalette.ICONS.getValue(HabitPalette.DEFAULT_ICON_KEY)

    val statusLabel: String
    val statusColor: Color
    when {
        done -> { statusLabel = "Completed"; statusColor = Color(0xFF22C55E) }
        !task.dueDate.isNullOrEmpty() && task.dueDate < java.time.LocalDate.now().toString() -> {
            statusLabel = "Pending"; statusColor = Color(0xFFEF4444)
        }
        else -> { statusLabel = "To Do"; statusColor = Color(0xFF94A3B8) }
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(statusColor.copy(alpha = 0.16f)).padding(10.dp, 4.dp)) {
                        Text(statusLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = statusColor)
                    }
                    if (canEdit) {
                        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = color.copy(alpha = 0.14f))
            )
        }
    ) { scaffoldPadding ->
        Column(Modifier.fillMaxSize().padding(scaffoldPadding)) {
        Box(Modifier.fillMaxWidth().background(color.copy(alpha = 0.14f)).padding(bottom = 20.dp)) {
            Column {
                Column(Modifier.padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        SvgPathIcon(svg, tint = color, size = 36.dp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(task.title, fontSize = 20.sp, fontWeight = FontWeight.Black, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    if (task.points > 0) {
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.16f)).padding(12.dp, 4.dp)) {
                            Text("+${task.points} XP", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
                        }
                    }
                }
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (task.description.isNotBlank()) {
                Section(title = "Description") { Text(task.description, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)) }
            }

            Section(title = "Details") {
                DetailRow("Category", task.category)
                DetailRow("Due date", task.dueDate ?: "None")
                if (task.points > 0) DetailRow("Points", "${task.points} XP")
            }

            if (task.checklist.isNotEmpty()) {
                Section(title = "Checklist") {
                    task.checklist.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Icon(
                                if (item.done) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = null,
                                tint = if (item.done) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(item.text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (item.done) 0.5f else 1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            if (done) {
                OutlinedButton(onClick = { viewModel.setTaskDoneImmediate(task, false) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Mark as Not Done")
                }
            } else {
                Button(onClick = { viewModel.setTaskDoneImmediate(task, true) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Mark Complete")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
