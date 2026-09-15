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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.HabitPalette
import com.triangle.app.data.SessionStore
import com.triangle.app.data.TaskRepository
import com.triangle.app.data.models.ChecklistItem
import com.triangle.app.data.models.Task
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val CATEGORIES = listOf("Personal", "Office", "Academic")

/**
 * Native port of script.js's _openCtPanel(false) / saveCustomTask() — the
 * real, working Individual-role create/edit task form (see the Milestone 2
 * plan for why admin's own create/edit path isn't ported: it's dead code
 * in the source app). Description is a plain multi-line field instead of
 * the WebView's rich-text WYSIWYG editor, and there's no attachment picker
 * — both deliberate Milestone 2 scope trims, see the plan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditTaskScreen(
    session: SessionStore.Session,
    existingTask: Task?,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val isNew = existingTask == null

    var title by remember { mutableStateOf(existingTask?.title ?: "") }
    var description by remember { mutableStateOf(existingTask?.description ?: "") }
    var category by remember { mutableStateOf(existingTask?.category ?: "Personal") }
    var points by remember { mutableStateOf(existingTask?.points ?: 10) }
    var dueDate by remember { mutableStateOf(existingTask?.dueDate) }
    var color by remember { mutableStateOf(existingTask?.iconColor ?: HabitPalette.COLORS.random()) }
    var iconKey by remember { mutableStateOf(HabitPalette.DEFAULT_ICON_KEY) }
    var iconSvg by remember { mutableStateOf(existingTask?.iconSvg ?: HabitPalette.ICONS.getValue(HabitPalette.DEFAULT_ICON_KEY)) }
    var checklist by remember { mutableStateOf(existingTask?.checklist ?: emptyList()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    fun save() {
        if (title.isBlank() || saving) return
        saving = true
        val task = (existingTask ?: Task(
            id = "pt-${System.currentTimeMillis()}",
            title = "",
            isPersonal = true,
            createdBy = session.uid,
            assignedTo = session.uid,
            createdDate = LocalDate.now().toString(),
            createdAt = System.currentTimeMillis()
        )).copy(
            title = title.trim(),
            description = description,
            category = category,
            points = points.coerceIn(0, 9999),
            dueDate = dueDate,
            iconColor = color,
            iconSvg = iconSvg,
            checklist = checklist
        )
        scope.launch {
            runCatching {
                if (isNew) TaskRepository.saveNewTask(session.orgId, task) else TaskRepository.updateTask(session.orgId, task)
            }
            saving = false
            onSaved()
        }
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(if (isNew) "Add Task" else "Edit Task", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    TextButton(onClick = { save() }, enabled = title.isNotBlank() && !saving) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                }
            )
        }
    ) { scaffoldPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Task title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )

            IconColorPicker(
                selectedColor = color,
                selectedIconKey = iconKey,
                onColorSelected = { color = it },
                onIconSelected = { key, svg -> iconKey = key; iconSvg = svg }
            )

            Column {
                Text("Category", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORIES.forEach { cat ->
                        val active = category == cat
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { category = cat }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(cat, fontSize = 13.sp, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Column {
                Text("XP / Points", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconButton(onClick = { points = (points - 5).coerceAtLeast(0) }) { Icon(Icons.Default.Remove, contentDescription = "Decrease") }
                    Text(points.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { points = (points + 5).coerceAtMost(9999) }) { Icon(Icons.Default.Add, contentDescription = "Increase") }
                }
            }

            Column {
                Text("Due date", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dueDate ?: "No due date", fontSize = 15.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showDatePicker = true }) { Text(if (dueDate == null) "Set" else "Change") }
                    if (dueDate != null) TextButton(onClick = { dueDate = null }) { Text("Clear") }
                }
            }

            ChecklistEditor(items = checklist, onChange = { checklist = it })

            if (!isNew && existingTask?.isPersonal == true && existingTask.createdBy == session.uid) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = {
                    scope.launch {
                        runCatching { TaskRepository.deleteTask(session.orgId, existingTask.id) }
                        onDeleted()
                    }
                }) {
                    Text("Delete task", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (showDatePicker) {
        val initialMillis = dueDate?.let { LocalDate.parse(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        dueDate = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().toString()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun ChecklistEditor(items: List<ChecklistItem>, onChange: (List<ChecklistItem>) -> Unit) {
    Column {
        Text("Checklist", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        items.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                OutlinedTextField(
                    value = item.text,
                    onValueChange = { new -> onChange(items.toMutableList().also { it[index] = item.copy(text = new) }) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onChange(items.toMutableList().also { it.removeAt(index) }) }) {
                    Icon(Icons.Default.Close, contentDescription = "Remove")
                }
            }
        }
        TextButton(onClick = {
            onChange(items + ChecklistItem(id = "ci-${System.currentTimeMillis()}", text = ""))
        }) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add item")
        }
    }
}
