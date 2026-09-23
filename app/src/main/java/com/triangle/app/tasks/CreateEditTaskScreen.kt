package com.triangle.app.tasks

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.circle.CircleMember
import com.triangle.app.data.AssignmentRepository
import com.triangle.app.data.AutoAssignCycle
import com.triangle.app.data.ConnectionRepository
import com.triangle.app.data.HabitPalette
import com.triangle.app.data.PriorityXp
import com.triangle.app.data.SessionStore
import com.triangle.app.data.TaskRepository
import com.triangle.app.data.UserRepository
import com.triangle.app.data.models.ChecklistItem
import com.triangle.app.data.models.Task
import com.triangle.app.reminders.ReminderPermissions
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Native port of script.js's _openCtPanel(false) / saveCustomTask(), rebuilt
 * for the Connect->Assign->Complete->Earn model (Milestone 3, see the
 * approved plan at .claude/plans/enchanted-brewing-beacon.md): a NEW task
 * always requires picking a recipient from the creator's Circle — there is
 * no "create for myself" path anymore — and XP is derived from a Priority
 * pick rather than freely entered. Editing an EXISTING task (always a
 * self-created one, per canEdit's own createdBy check in AppNavHost) keeps
 * its recipient fixed and only lets Priority/other fields change, per the
 * plan's "leave already-created items exactly as-is" decision. Description
 * is a plain multi-line field instead of the WebView's rich-text WYSIWYG
 * editor, and there's no attachment picker — both deliberate Milestone 2
 * scope trims, see the plan.
 */
private val CATEGORIES = listOf("Personal", "Office", "Academic")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditTaskScreen(
    session: SessionStore.Session,
    viewModel: TasksHabitsViewModel,
    existingTask: Task?,
    isSolo: Boolean = false,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
    onFindPeople: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val isNew = existingTask == null
    // For an existing task, whether it's Solo is a fact about the task
    // itself (createdBy), never the nav arg — that arg only matters for a
    // brand-new item, which doesn't have a createdBy yet.
    val effectiveSolo = existingTask?.let { it.createdBy == null || it.createdBy == session.uid } ?: isSolo

    var title by remember { mutableStateOf(existingTask?.title ?: "") }
    var description by remember { mutableStateOf(existingTask?.description ?: "") }
    var category by remember { mutableStateOf(existingTask?.category ?: "Personal") }
    var priority by remember { mutableStateOf(existingTask?.priority ?: "medium") }
    var dueDate by remember { mutableStateOf(existingTask?.dueDate) }
    var color by remember { mutableStateOf(existingTask?.iconColor ?: HabitPalette.DEFAULT_COLOR) }
    var iconKey by remember { mutableStateOf(HabitPalette.DEFAULT_ICON_KEY) }
    var iconSvg by remember { mutableStateOf(existingTask?.iconSvg ?: HabitPalette.ICONS.getValue(HabitPalette.DEFAULT_ICON_KEY)) }
    var checklist by remember { mutableStateOf(existingTask?.checklist ?: emptyList()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var reminderTime by remember { mutableStateOf(existingTask?.reminderTime) }
    var reminderEnabled by remember { mutableStateOf(existingTask?.reminderTime != null) }
    var showReminderTimePicker by remember { mutableStateOf(false) }
    val exactAlarmSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* result ignored — ReminderScheduler falls back to an inexact alarm if still not granted */ }

    var circleLoading by remember { mutableStateOf(true) }
    var circleMembers by remember { mutableStateOf(emptyList<CircleMember>()) }
    var recentRecipients by remember { mutableStateOf(emptyList<String>()) }
    var selectedUids by remember { mutableStateOf(emptySet<String>()) }
    var showAssignScreen by remember { mutableStateOf(false) }

    // New tasks start on the next color+icon in the auto-assign rotation
    // (matches script.js's _nextAutoColorAndIcon()) instead of always the
    // same default — editing an existing task keeps its saved color/icon.
    LaunchedEffect(Unit) {
        if (isNew) {
            val pick = AutoAssignCycle.next(session.orgId)
            color = pick.color
            iconKey = pick.iconKey
            iconSvg = pick.iconSvg
        }
    }

    // Only NEW, Shared tasks need the Circle (the recipient picker) — a
    // Solo task has no recipient at all, and editing an existing task never
    // touches assignedTo, so skip the fetch entirely in both cases.
    LaunchedEffect(isNew, effectiveSolo) {
        if (!isNew || effectiveSolo) { circleLoading = false; return@LaunchedEffect }
        ConnectionRepository.circleFlow(session.uid).onEach { uids ->
            circleMembers = uids.mapNotNull { uid ->
                // Only members who currently allow ME to assign to them — see
                // circle/MemberPermissionsSheet.kt's "Can assign" permission.
                if (!ConnectionRepository.canAssign(uid, session.uid)) return@mapNotNull null
                UserRepository.fetchUserRecord(uid)?.let { CircleMember(uid, it.name, it.username, photoUrl = it.photoUrl) }
            }.sortedBy { it.name.lowercase() }
            circleLoading = false
        }.launchIn(scope)
        scope.launch { recentRecipients = AssignmentRepository.recentRecipients(session.uid) }
    }

    // Same id across every recipient's copy — harmless since each lives in a
    // different recipient's org — so AssignmentRepository can join them back
    // into one row for the assigner's own list.
    fun taskFor(groupId: String, createdAt: Long, points: Int, uid: String) = Task(
        id = groupId,
        title = title.trim(),
        description = description,
        isPersonal = true,
        createdBy = session.uid,
        assignedTo = uid,
        priority = priority,
        points = points,
        dueDate = dueDate,
        reminderTime = if (reminderEnabled) reminderTime else null,
        iconColor = color,
        iconSvg = iconSvg,
        checklist = checklist,
        createdDate = LocalDate.now().toString(),
        createdAt = createdAt
    )

    // A Solo task has no recipient and no priority/XP — it lives only in
    // this user's own org (TaskRepository.saveNewTask), not via
    // AssignmentRepository, and its category is the old fixed Office/
    // Academic/Personal field, meaningful again now that it's scoped to
    // exactly the items that don't otherwise carry a relationship.
    fun soloTaskFor(id: String, createdAt: Long) = Task(
        id = id,
        title = title.trim(),
        description = description,
        category = category,
        isPersonal = true,
        createdBy = session.uid,
        assignedTo = session.uid,
        priority = "medium",
        points = 0,
        dueDate = dueDate,
        reminderTime = if (reminderEnabled) reminderTime else null,
        iconColor = color,
        iconSvg = iconSvg,
        checklist = checklist,
        createdDate = LocalDate.now().toString(),
        createdAt = createdAt
    )

    // Instant, optimistic Save: the new task/edit shows up in the Tasks list
    // the moment Save is tapped — the actual Firebase writes run in the
    // background on TasksHabitsViewModel's own scope (see saveTaskAssignment/
    // saveSoloTask/updateTaskInBackground), which survives onSaved()'s
    // immediate navigation away from this screen. Whether the write has
    // actually landed yet is invisible to the user, exactly as asked.
    fun save() {
        if (title.isBlank() || saving) return
        if (isNew && !effectiveSolo && selectedUids.isEmpty()) return
        saving = true
        if (isNew) {
            if (effectiveSolo) {
                val id = "pt-${System.currentTimeMillis()}"
                val task = soloTaskFor(id, System.currentTimeMillis())
                viewModel.addOptimisticTask(task)
                viewModel.saveSoloTask(task)
            } else {
                val points = PriorityXp.xpFor(priority)
                val groupId = "pt-${System.currentTimeMillis()}"
                val createdAt = System.currentTimeMillis()
                val representative = taskFor(groupId, createdAt, points, selectedUids.first())
                val members = selectedUids.map { uid ->
                    val memberName = circleMembers.firstOrNull { it.uid == uid }?.name ?: "Someone"
                    AssignmentRepository.AssignedTaskMember(uid, memberName, done = false)
                }
                viewModel.addOptimisticTaskGroup(AssignmentRepository.AssignedTaskGroup(groupId, representative, members))
                viewModel.saveTaskAssignment(session.name, selectedUids) { uid -> taskFor(groupId, createdAt, points, uid) }
            }
        } else if (effectiveSolo) {
            viewModel.updateTaskInBackground(
                existingTask!!.copy(
                    title = title.trim(),
                    description = description,
                    category = category,
                    dueDate = dueDate,
                    reminderTime = if (reminderEnabled) reminderTime else null,
                    iconColor = color,
                    iconSvg = iconSvg,
                    checklist = checklist
                )
            )
        } else {
            val points = PriorityXp.xpFor(priority)
            viewModel.updateTaskInBackground(
                existingTask!!.copy(
                    title = title.trim(),
                    description = description,
                    priority = priority,
                    points = points,
                    dueDate = dueDate,
                    reminderTime = if (reminderEnabled) reminderTime else null,
                    iconColor = color,
                    iconSvg = iconSvg,
                    checklist = checklist
                )
            )
        }
        saving = false
        onSaved()
    }

    // Not a real nav destination (see TasksHabitsScreen's AssignedTaskDetailScreen
    // comment for why) — without this, system back would fall through to the
    // NavController and pop this whole Create/Edit Task screen instead of just
    // closing the picker.
    BackHandler(enabled = showAssignScreen) { showAssignScreen = false }

    if (showAssignScreen) {
        AssignMembersScreen(
            itemLabel = "Task",
            members = circleMembers,
            recentUids = recentRecipients,
            initiallySelected = selectedUids,
            onClose = { showAssignScreen = false },
            onDone = { selectedUids = it; showAssignScreen = false }
        )
        return
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(if (isNew) "Add Task" else "Edit Task", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    TextButton(
                        onClick = { save() },
                        enabled = title.isNotBlank() && !saving && (!isNew || effectiveSolo || selectedUids.isNotEmpty())
                    ) {
                        if (saving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(if (saving) "Saving…" else "Save")
                    }
                }
            )
        }
    ) { scaffoldPadding ->
        if (isNew && !effectiveSolo && circleLoading) {
            Box(Modifier.fillMaxSize().padding(scaffoldPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        if (isNew && !effectiveSolo && circleMembers.isEmpty()) {
            EmptyCircleState(Modifier.padding(scaffoldPadding), onFindPeople = onFindPeople)
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (!effectiveSolo) {
                if (isNew) {
                    AssignSummaryRow(
                        members = circleMembers,
                        selectedUids = selectedUids,
                        onClick = { showAssignScreen = true }
                    )
                } else {
                    Column {
                        Text("Assigned to", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("You", fontSize = 15.sp)
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Task title") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                minLines = 3,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                modifier = Modifier.fillMaxWidth()
            )

            IconColorPicker(
                selectedColor = color,
                selectedIconKey = iconKey,
                onColorSelected = { color = it },
                onIconSelected = { key, svg -> iconKey = key; iconSvg = svg }
            )

            if (effectiveSolo) {
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
            } else {
                PriorityPicker(selected = priority, onSelect = { priority = it })
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

            ReminderSection(
                enabled = reminderEnabled,
                time = reminderTime,
                disabledHint = if (dueDate == null) "Set a due date first" else null,
                onToggle = { on ->
                    if (on && !ReminderPermissions.canScheduleExactAlarms(context)) {
                        exactAlarmSettingsLauncher.launch(ReminderPermissions.exactAlarmSettingsIntent(context))
                    }
                    reminderEnabled = on
                    if (on && reminderTime == null) showReminderTimePicker = true
                },
                onTimeClick = { showReminderTimePicker = true }
            )

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

    if (showReminderTimePicker) {
        ReminderTimePickerDialog(
            initial = reminderTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
            onDismiss = { showReminderTimePicker = false },
            onConfirm = { picked ->
                val isDueToday = dueDate == LocalDate.now().toString()
                if (isDueToday && picked.isBefore(LocalTime.now())) {
                    Toast.makeText(context, "Reminder time must be after the current time", Toast.LENGTH_SHORT).show()
                } else {
                    reminderTime = picked.toString().take(5) // LocalTime.toString() is "HH:mm[:ss]" — keep just "HH:mm"
                    showReminderTimePicker = false
                }
            }
        )
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
