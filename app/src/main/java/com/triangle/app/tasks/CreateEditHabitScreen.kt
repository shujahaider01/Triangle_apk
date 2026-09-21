package com.triangle.app.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.circle.CircleMember
import com.triangle.app.data.AssignmentRepository
import com.triangle.app.data.AutoAssignCycle
import com.triangle.app.data.ConnectionRepository
import com.triangle.app.data.HabitPalette
import com.triangle.app.data.HabitRepository
import com.triangle.app.data.PriorityXp
import com.triangle.app.data.SessionStore
import com.triangle.app.data.UserRepository
import com.triangle.app.data.models.Habit
import com.triangle.app.data.models.HabitFrequency
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.LocalDate

private val DOW_LABELS = listOf("S", "M", "T", "W", "T", "F", "S") // JS Date.getDay(): 0=Sun..6=Sat

private enum class FreqMode { EVERYDAY, SPECIFIC_DAYS, DAYS_OF_MONTH, PER_PERIOD }

/**
 * Native port of the Individual-role habit create/edit form
 * (_renderHabitCreatePanel), rebuilt for the Connect->Assign->Complete->Earn
 * model (Milestone 3, see the approved plan at
 * .claude/plans/enchanted-brewing-beacon.md): a NEW habit always requires
 * picking a recipient from the creator's Circle — there is no "create for
 * myself" path anymore — and XP is derived from a Priority pick rather than
 * freely entered. Editing an EXISTING habit keeps its recipient fixed, per
 * the plan's "leave already-created items exactly as-is" decision. All four
 * of the source's frequency modes are creatable here: "Everyday", "Specific
 * Days of Week", "Specific Days of Month" (a 1-31 date-grid, multi-select),
 * and "Some Days Per Period" (a count stepper + Week/Month unit — not a
 * calendar-dates picker).
 */
private val CATEGORIES = listOf("Personal", "Office", "Academic")

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CreateEditHabitScreen(
    session: SessionStore.Session,
    viewModel: TasksHabitsViewModel,
    existingHabit: Habit?,
    isSolo: Boolean = false,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
    onFindPeople: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val isNew = existingHabit == null
    // Same reasoning as CreateEditTaskScreen: for an existing habit, Solo-ness
    // is a fact about the habit's own createdBy, not the nav arg.
    val effectiveSolo = existingHabit?.let { it.createdBy == null || it.createdBy == session.uid } ?: isSolo

    var name by remember { mutableStateOf(existingHabit?.name ?: "") }
    var description by remember { mutableStateOf(existingHabit?.description ?: "") }
    var category by remember { mutableStateOf(existingHabit?.category ?: "Personal") }
    var color by remember { mutableStateOf(existingHabit?.color ?: HabitPalette.DEFAULT_COLOR) }
    var iconKey by remember { mutableStateOf(HabitPalette.DEFAULT_ICON_KEY) }
    var iconSvg by remember { mutableStateOf(existingHabit?.iconSvg ?: HabitPalette.ICONS.getValue(HabitPalette.DEFAULT_ICON_KEY)) }
    var priority by remember { mutableStateOf(existingHabit?.priority ?: "medium") }
    var circleLoading by remember { mutableStateOf(true) }
    var circleMembers by remember { mutableStateOf(emptyList<CircleMember>()) }
    var recentRecipients by remember { mutableStateOf(emptyList<String>()) }
    var selectedUids by remember { mutableStateOf(emptySet<String>()) }
    var showAssignScreen by remember { mutableStateOf(false) }
    var freqMode by remember {
        mutableStateOf(
            when (existingHabit?.frequency?.type) {
                "daysOfWeek" -> FreqMode.SPECIFIC_DAYS
                "daysOfMonth" -> FreqMode.DAYS_OF_MONTH
                "perPeriod" -> FreqMode.PER_PERIOD
                else -> FreqMode.EVERYDAY
            }
        )
    }
    var selectedDays by remember { mutableStateOf((existingHabit?.frequency?.days ?: emptyList()).toSet()) }
    var selectedDates by remember { mutableStateOf((existingHabit?.frequency?.dates ?: emptyList()).toSet()) }
    var periodCount by remember { mutableStateOf(existingHabit?.frequency?.count ?: 3) }
    var periodUnit by remember { mutableStateOf(existingHabit?.frequency?.unit ?: "week") }
    var saving by remember { mutableStateOf(false) }

    // New habits start on the next color+icon in the auto-assign rotation
    // (matches script.js's _nextAutoColorAndIcon()) instead of always the
    // same default — editing an existing habit keeps its saved color/icon.
    LaunchedEffect(Unit) {
        if (isNew) {
            val pick = AutoAssignCycle.next(session.orgId)
            color = pick.color
            iconKey = pick.iconKey
            iconSvg = pick.iconSvg
        }
    }

    // Only NEW, Shared habits need the Circle (the recipient picker) — a
    // Solo habit has no recipient, and editing an existing habit never
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

    // Same id across every recipient's copy — see CreateEditTaskScreen's save().
    fun habitFor(groupId: String, createdAt: Long, frequency: HabitFrequency, uid: String) = Habit(
        id = groupId,
        name = name.trim(),
        description = description,
        color = color,
        iconSvg = iconSvg,
        assignedTo = listOf(uid),
        priority = priority,
        xpPerCompletion = PriorityXp.xpFor(priority),
        frequency = frequency,
        startDate = LocalDate.now().toString(),
        createdAt = createdAt,
        createdBy = session.uid
    )

    // A Solo habit has no recipient and awards no XP — see CreateEditTaskScreen's soloTaskFor() doc comment.
    fun soloHabitFor(id: String, createdAt: Long, frequency: HabitFrequency) = Habit(
        id = id,
        name = name.trim(),
        description = description,
        color = color,
        iconSvg = iconSvg,
        category = category,
        assignedTo = listOf(session.uid),
        priority = "medium",
        xpPerCompletion = 0,
        frequency = frequency,
        startDate = LocalDate.now().toString(),
        createdAt = createdAt,
        createdBy = session.uid
    )

    // Instant, optimistic Save — see CreateEditTaskScreen's save() doc comment.
    fun save() {
        if (name.isBlank() || saving) return
        if (isNew && !effectiveSolo && selectedUids.isEmpty()) return
        saving = true
        val frequency = when (freqMode) {
            FreqMode.EVERYDAY -> HabitFrequency("everyday")
            FreqMode.SPECIFIC_DAYS -> HabitFrequency("daysOfWeek", days = selectedDays.sorted())
            FreqMode.DAYS_OF_MONTH -> HabitFrequency("daysOfMonth", dates = selectedDates.sorted())
            FreqMode.PER_PERIOD -> HabitFrequency("perPeriod", count = periodCount.coerceIn(1, 30), unit = periodUnit)
        }
        if (isNew) {
            if (effectiveSolo) {
                val id = "h-${System.currentTimeMillis()}"
                val habit = soloHabitFor(id, System.currentTimeMillis(), frequency)
                viewModel.addOptimisticHabit(habit)
                viewModel.saveSoloHabit(habit)
            } else {
                val groupId = "h-${System.currentTimeMillis()}"
                val createdAt = System.currentTimeMillis()
                val representative = habitFor(groupId, createdAt, frequency, selectedUids.first())
                val members = selectedUids.map { uid ->
                    val memberName = circleMembers.firstOrNull { it.uid == uid }?.name ?: "Someone"
                    AssignmentRepository.AssignedHabitMember(uid, memberName, completions = emptyMap())
                }
                viewModel.addOptimisticHabitGroup(AssignmentRepository.AssignedHabitGroup(groupId, representative, members))
                viewModel.saveHabitAssignment(session.name, selectedUids) { uid -> habitFor(groupId, createdAt, frequency, uid) }
            }
        } else if (effectiveSolo) {
            viewModel.updateHabitInBackground(
                existingHabit!!.copy(
                    name = name.trim(),
                    description = description,
                    color = color,
                    iconSvg = iconSvg,
                    category = category,
                    frequency = frequency
                )
            )
        } else {
            viewModel.updateHabitInBackground(
                existingHabit!!.copy(
                    name = name.trim(),
                    description = description,
                    color = color,
                    iconSvg = iconSvg,
                    priority = priority,
                    xpPerCompletion = PriorityXp.xpFor(priority),
                    frequency = frequency
                )
            )
        }
        saving = false
        onSaved()
    }

    // Not a real nav destination — see CreateEditTaskScreen's save() BackHandler comment.
    BackHandler(enabled = showAssignScreen) { showAssignScreen = false }

    if (showAssignScreen) {
        AssignMembersScreen(
            itemLabel = "Habit",
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
                title = { Text(if (isNew) "New Habit" else "Edit Habit", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    TextButton(
                        onClick = { save() },
                        enabled = name.isNotBlank() && !saving && (!isNew || effectiveSolo || selectedUids.isNotEmpty())
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
                value = name,
                onValueChange = { name = it },
                label = { Text("Habit name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                minLines = 2,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                modifier = Modifier.fillMaxWidth()
            )

            IconColorPicker(
                selectedColor = color,
                selectedIconKey = iconKey,
                onColorSelected = { color = it },
                onIconSelected = { key, svg -> iconKey = key; iconSvg = svg }
            )

            Column {
                Text("Frequency", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    listOf(
                        "Every day" to FreqMode.EVERYDAY,
                        "Specific days" to FreqMode.SPECIFIC_DAYS,
                        "Days of month" to FreqMode.DAYS_OF_MONTH,
                        "Some days" to FreqMode.PER_PERIOD
                    ).forEach { (label, mode) ->
                        val active = freqMode == mode
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { freqMode = mode }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(label, fontSize = 13.sp, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (freqMode == FreqMode.SPECIFIC_DAYS) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DOW_LABELS.forEachIndexed { dow, label ->
                            val active = selectedDays.contains(dow)
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { selectedDays = if (active) selectedDays - dow else selectedDays + dow },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, fontSize = 12.sp, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                // "Specific Days of Month" — a 7-per-row grid of day numbers 1-31,
                // multi-select (matches .ct-dom-grid). Hand-rolled Column-of-Rows,
                // not LazyVerticalGrid, so it always self-sizes to its own content
                // instead of needing a guessed fixed height (see the Dashboard
                // Analytics grid fix for why that matters).
                if (freqMode == FreqMode.DAYS_OF_MONTH) {
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..31).chunked(7).forEach { week ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                week.forEach { day ->
                                    val active = selectedDates.contains(day)
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { selectedDates = if (active) selectedDates - day else selectedDates + day },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(day.toString(), fontSize = 12.sp, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                // Pad the last (partial) row so its cells stay the same size as full rows.
                                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                // "Some days per week/month" — source's SomeDaysPerPeriod mode: a
                // count (1-30) rather than specific calendar dates, matching
                // .ct-dom-grid's sibling stepper+unit control (not a date-grid).
                if (freqMode == FreqMode.PER_PERIOD) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { periodCount = (periodCount - 1).coerceAtLeast(1) }) {
                            Icon(Icons.Default.Remove, contentDescription = "Fewer days")
                        }
                        Text(periodCount.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
                        IconButton(onClick = { periodCount = (periodCount + 1).coerceAtMost(30) }) {
                            Icon(Icons.Default.Add, contentDescription = "More days")
                        }
                        Spacer(Modifier.width(2.dp))
                        Text("days per", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        listOf("week" to "Week", "month" to "Month").forEach { (value, label) ->
                            val active = periodUnit == value
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { periodUnit = value }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(label, fontSize = 12.5.sp, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

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

            if (!isNew && existingHabit != null) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = {
                    scope.launch {
                        runCatching { HabitRepository.deleteHabit(session.orgId, existingHabit.id) }
                        onDeleted()
                    }
                }) {
                    Text("Delete habit", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
