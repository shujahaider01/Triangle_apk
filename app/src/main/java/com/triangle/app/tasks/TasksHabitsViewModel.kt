package com.triangle.app.tasks

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triangle.app.data.AssignmentRepository
import com.triangle.app.data.HabitRepository
import com.triangle.app.data.HabitStats
import com.triangle.app.data.SessionStore
import com.triangle.app.data.TaskRepository
import com.triangle.app.data.TasksHabitsUiPrefs
import com.triangle.app.data.models.Habit
import com.triangle.app.data.models.HabitCompletionEntry
import com.triangle.app.data.models.Task
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One row of the Tasks pane — either the viewer's own task, or one they assigned out to their Circle (rendered with a completion-fraction ring instead of a checkbox, see AssignedTaskRowCard). */
sealed class TaskRow {
    data class Own(val task: Task) : TaskRow()
    data class Assigned(val group: AssignmentRepository.AssignedTaskGroup) : TaskRow()
}

/** Same idea as TaskRow, for the Habits pane. */
sealed class HabitRow {
    data class Own(val habit: Habit) : HabitRow()
    data class Assigned(val group: AssignmentRepository.AssignedHabitGroup) : HabitRow()
}

/**
 * The top-level mode toggle (replaces the old date-status "All/Due"
 * segment) — SHARED is anything involving another person (received from,
 * or sent to, someone in your Circle); SOLO is a self-created item with
 * nobody else's name on it. Which mode is active changes BOTH which items
 * show (see recompute()'s relationship split) and what the "+" FAB creates
 * (see TasksHabitsScreen's FAB — a Solo create skips the recipient picker
 * and priority/XP entirely, per the user's explicit request).
 */
enum class ItemMode(val label: String) { SHARED("Shared"), SOLO("Solo") }

/** Shared mode's sub-filter chips — read off the same createdBy/Own-vs-Assigned-group info both Tasks and Habits already have, so it applies identically to both panes. */
enum class SharedSubFilter(val label: String) {
    ALL("All"),
    ASSIGNED_TO_ME("Assigned to Me"),
    ASSIGNED_BY_ME("Assigned by Me")
}

/** Coarse completion/due status for a task. Habits have no due date, so a habit only ever resolves to PENDING or COMPLETED — see habitPasses(). */
enum class TaskStatus { PENDING, COMPLETED, OVERDUE }

/** An inclusive day range — backs the Filters sheet's Due/Created/Completed date pickers. */
data class DateSpan(val start: LocalDate, val end: LocalDate) {
    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

/**
 * Habits-pane-only performance buckets — a habit can match more than one at
 * once (e.g. ACTIVE and STREAK_ACTIVE together), so selecting several is an
 * OR, same as Priority/Status. MISSED_TODAY and COMPLETED_TODAY are always
 * evaluated against the real calendar today, not whatever date is selected
 * on the strip, matching the "Today" wording the mockup uses for them.
 */
enum class HabitPerformance { ACTIVE, COMPLETED_TODAY, MISSED_TODAY, STREAK_ACTIVE, STREAK_BROKEN }

/** An inclusive current-streak-length bucket (days) — `max == null` means no upper bound (the mockup's "30+ days"). */
data class StreakLengthFilter(val min: Int, val max: Int?) {
    fun contains(streakDays: Int): Boolean = streakDays >= min && (max == null || streakDays <= max)
    companion object {
        val ZERO = StreakLengthFilter(0, 0)
        val SHORT = StreakLengthFilter(1, 7)
        val MEDIUM = StreakLengthFilter(8, 30)
        val LONG = StreakLengthFilter(31, null)
    }
}

/**
 * The "Filters" bottom sheet's state (see the Filters feature plan) —
 * applied ON TOP OF the existing itemMode/sharedSubFilter/soloCategoryFilter
 * split, not instead of it. Empty/null fields mean "don't filter on this."
 * Several fields are narrower than the mockup they came from:
 * - `statuses` only ever resolves to PENDING/COMPLETED/OVERDUE — there's no
 *   real In Progress/Cancelled/Rejected/Waiting-for-Approval workflow behind
 *   this app's data yet, so those values aren't offered.
 * - `dueDateRange` only ever narrows Task rows (Habits aren't due on a
 *   date), and `completedDateRange` only ever narrows Own rows (an
 *   assigned-out group has no per-recipient completion timestamp today, so
 *   it's excluded rather than guessed at) — see taskPasses()/habitPasses()/
 *   assignedTaskGroupPasses()/assignedHabitGroupPasses().
 * - `habitFrequencyTypes`, `habitPerformance`, and `streakLength` only ever
 *   narrow Habit rows (Tasks ignore them). `habitFrequencyTypes` is a subset
 *   of Habit.frequency.type ("everyday"/"daysOfWeek"/"daysOfMonth"/
 *   "perPeriod" — the mockup's Daily/Weekly/Monthly/Custom). Performance and
 *   streak length only apply to Own habit rows: an assigned-out group can
 *   have several recipients each with their own streak/completion state, so
 *   there's no single answer to "is this streak active" for the group as a
 *   whole — those two are skipped (not enforced) for Assigned rows, same
 *   "can't check it, don't guess" reasoning as completedDateRange.
 * - `dateRangeHighlight` is display-only and never narrows either list — per
 *   the user's explicit call, the screen keeps showing one selected day's
 *   items rather than flattening a whole range into the list (that's what
 *   dueDateRange/createdDateRange/completedDateRange are for, each scoped to
 *   a specific date meaning). This field only tells DateStrip which pills to
 *   paint as part of the active range, so picking "This Week" in the sheet
 *   visibly bands those 7 days without changing what the list shows.
 */
data class TaskHabitFilters(
    val statuses: Set<TaskStatus> = emptySet(),
    val priorities: Set<String> = emptySet(),
    val xpMin: Int? = null,
    val xpMax: Int? = null,
    val dueDateRange: DateSpan? = null,
    val createdDateRange: DateSpan? = null,
    val completedDateRange: DateSpan? = null,
    val habitFrequencyTypes: Set<String> = emptySet(),
    val habitPerformance: Set<HabitPerformance> = emptySet(),
    val streakLength: StreakLengthFilter? = null,
    val dateRangeHighlight: DateSpan? = null
) {
    /** How many separate filter categories are set — drives the numbered badge on the filter icon (see DateHeader). */
    val activeCount: Int
        get() = listOf(
            statuses.isNotEmpty(),
            priorities.isNotEmpty(),
            xpMin != null || xpMax != null,
            dueDateRange != null,
            createdDateRange != null,
            completedDateRange != null,
            habitFrequencyTypes.isNotEmpty(),
            habitPerformance.isNotEmpty(),
            streakLength != null,
            dateRangeHighlight != null
        ).count { it }

    val isActive: Boolean get() = activeCount > 0
}

data class TasksHabitsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedDate: LocalDate = LocalDate.now(),
    val itemMode: ItemMode = ItemMode.SHARED,
    val sharedSubFilter: SharedSubFilter = SharedSubFilter.ALL,
    /** Solo mode's own category sub-filter — the old fixed Office/Academic/Personal system, now scoped to Solo (self-created, unassigned) items only, via Task.category/Habit.category. */
    val soloCategoryFilter: String = "All", // "All" | "Office" | "Academic" | "Personal"
    val filters: TaskHabitFilters = TaskHabitFilters(),
    val visibleTasks: List<TaskRow> = emptyList(),
    val completions: Set<String> = emptySet(),
    /** Task ids whose completion is optimistically shown but not yet committed to Firebase (5s undo window). */
    val optimisticDone: Set<String> = emptySet(),
    val visibleHabits: List<HabitRow> = emptyList(),
    val habitCompletions: Map<String, Map<String, HabitCompletionEntry>> = emptyMap(),
    /** Counts for the selected date, BEFORE the sub-filter chips — label the Shared/Solo segment itself (e.g. "Shared (3)"), same "count before the finer filter" idea the old All/Due pill used. */
    val sharedTaskCount: Int = 0,
    val soloTaskCount: Int = 0,
    val sharedHabitCount: Int = 0,
    val soloHabitCount: Int = 0
)

/**
 * Backs both panes of TasksHabitsScreen (Tasks + Habits) — deliberately ONE
 * ViewModel rather than the two separate ones the Milestone 2 plan sketched
 * as a file list, because script.js's own _renderSharedHeader() genuinely
 * shares date/category-filter state across both panes (only the status
 * filter is per-pane) — splitting the state in two would fight that shared
 * model instead of matching it. See TasksHabitsScreen for the two panes'
 * composables.
 */
class TasksHabitsViewModel(private val session: SessionStore.Session, private val appContext: Context) : ViewModel() {

    private val allTasks = MutableStateFlow<List<Task>>(emptyList())
    private val allCompletions = MutableStateFlow<Set<String>>(emptySet())
    /** "taskId-uid" -> when it was marked done — feeds the Filters sheet's "Completed" date range (see TaskRepository.completionTimestampsFlow's doc comment). */
    private val allTaskCompletionTimestamps = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val allHabits = MutableStateFlow<List<Habit>>(emptyList())
    private val allHabitCompletions = MutableStateFlow<Map<String, Map<String, HabitCompletionEntry>>>(emptyMap())
    private val assignedTaskGroups = MutableStateFlow<List<AssignmentRepository.AssignedTaskGroup>>(emptyList())
    private val assignedHabitGroups = MutableStateFlow<List<AssignmentRepository.AssignedHabitGroup>>(emptyList())

    /** Full (unfiltered) live lists — used by TaskDetailScreen/HabitDetailScreen/AppNavHost to look up a specific item by id. */
    val tasks: StateFlow<List<Task>> = allTasks.asStateFlow()
    val habits: StateFlow<List<Habit>> = allHabits.asStateFlow()

    private val _uiState = MutableStateFlow(TasksHabitsUiState())
    val uiState: StateFlow<TasksHabitsUiState> = _uiState.asStateFlow()

    /**
     * Which pane (0 = Tasks, 1 = Habits) TasksHabitsScreen's pager should
     * open on — null while still resolving. Two rules, in priority order:
     * (1) any habit assigned to you by someone else that you haven't opened
     * this screen since forces Habits open once (then is marked seen), else
     * (2) whichever pane you last left this screen on. Replaces the old
     * "always opens on Tasks" behavior the user explicitly called out as
     * wrong — the assigner/recipient relationship means whichever pane has
     * fresh activity should win, not a hardcoded default.
     */
    private val _initialPane = MutableStateFlow<Int?>(null)
    val initialPane: StateFlow<Int?> = _initialPane.asStateFlow()

    private val optimisticDoneJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            runCatching { TaskRepository.materializeRepeatingTasks(session.orgId) }
        }
        TaskRepository.tasksFlow(session.orgId).onEach { allTasks.value = it }.launchIn(viewModelScope)
        TaskRepository.completionsFlow(session.orgId).onEach { allCompletions.value = it }.launchIn(viewModelScope)
        TaskRepository.completionTimestampsFlow(session.orgId).onEach { allTaskCompletionTimestamps.value = it }.launchIn(viewModelScope)
        HabitRepository.habitsFlow(session.orgId).onEach { allHabits.value = it }.launchIn(viewModelScope)
        HabitRepository.habitCompletionsFlow(session.orgId, session.uid).onEach { allHabitCompletions.value = it }.launchIn(viewModelScope)

        viewModelScope.launch {
            val receivedHabitIds = runCatching { HabitRepository.habitsFlow(session.orgId).first() }.getOrDefault(emptyList())
                .filter { it.createdBy != null && it.createdBy != session.uid }
                .map { it.id }
                .toSet()
            val seen = runCatching { TasksHabitsUiPrefs.seenAssignedHabitIds(appContext, session.uid) }.getOrDefault(emptySet())
            val hasUnseenAssignedHabit = (receivedHabitIds - seen).isNotEmpty()
            if (hasUnseenAssignedHabit) {
                _initialPane.value = 1
                runCatching { TasksHabitsUiPrefs.markAssignedHabitsSeen(appContext, session.uid, receivedHabitIds) }
            } else {
                _initialPane.value = runCatching { TasksHabitsUiPrefs.lastActivePane(appContext, session.uid) }.getOrDefault(0)
            }
        }

        // Assigned-out items live in each recipient's own org, not ours — the
        // index only tells us WHAT was assigned to WHOM, so it's re-joined
        // one-shot against every recipient's data (see readAssignedGroups)
        // whenever that index changes, and again on refreshAssignedGroups()
        // (TasksHabitsScreen calls that on resume so a recipient's completion
        // shows up without a full live multi-org combiner).
        AssignmentRepository.assignedByMeFlow(session.uid).onEach { refreshAssignedGroups() }.launchIn(viewModelScope)

        val ownFlow = combine(allTasks, allCompletions, allHabits, allHabitCompletions, allTaskCompletionTimestamps) { t, c, h, hc, cts -> OwnSnapshot(t, c, h, hc, cts) }
        val assignedFlow = combine(assignedTaskGroups, assignedHabitGroups) { tg, hg -> AssignedSnapshot(tg, hg) }
        ownFlow.combine(assignedFlow) { own, assigned -> own to assigned }
            .onEach { (own, assigned) -> recompute(own.tasks, own.completions, own.habits, own.habitCompletions, assigned.taskGroups, assigned.habitGroups, own.completionTimestamps) }
            .launchIn(viewModelScope)
    }

    private data class OwnSnapshot(
        val tasks: List<Task>,
        val completions: Set<String>,
        val habits: List<Habit>,
        val habitCompletions: Map<String, Map<String, HabitCompletionEntry>>,
        val completionTimestamps: Map<String, Long>
    )

    private data class AssignedSnapshot(
        val taskGroups: List<AssignmentRepository.AssignedTaskGroup>,
        val habitGroups: List<AssignmentRepository.AssignedHabitGroup>
    )

    /** Called when the pager settles on a page — persists it as the pane the bottom-nav "Tasks" tab reopens to next time. */
    fun setActivePane(page: Int) {
        viewModelScope.launch { runCatching { TasksHabitsUiPrefs.setLastActivePane(appContext, session.uid, page) } }
    }

    fun refreshAssignedGroups() {
        viewModelScope.launch {
            val groups = runCatching { AssignmentRepository.readAssignedGroups(session.uid) }.getOrNull() ?: return@launch
            assignedTaskGroups.value = groups.taskGroups
            assignedHabitGroups.value = groups.habitGroups
        }
    }

    /**
     * Shows a just-created assignment INSTANTLY — before, not after, the
     * actual Firebase writes land — by inserting it straight into the same
     * state readAssignedGroups() would eventually produce. Replaces any
     * earlier optimistic entry with the same itemId rather than duplicating
     * it, so calling this again with corrected data (there isn't a need to
     * today, but it's the safe behavior) never doubles up a row.
     */
    fun addOptimisticTaskGroup(group: AssignmentRepository.AssignedTaskGroup) {
        assignedTaskGroups.value = listOf(group) + assignedTaskGroups.value.filterNot { it.itemId == group.itemId }
        recompute(allTasks.value, allCompletions.value, allHabits.value, allHabitCompletions.value)
    }

    /** Same idea as addOptimisticTaskGroup(), for a habit. */
    fun addOptimisticHabitGroup(group: AssignmentRepository.AssignedHabitGroup) {
        assignedHabitGroups.value = listOf(group) + assignedHabitGroups.value.filterNot { it.itemId == group.itemId }
        recompute(allTasks.value, allCompletions.value, allHabits.value, allHabitCompletions.value)
    }

    /**
     * Fires the actual cross-org assignment writes on THIS view model's own
     * viewModelScope, not the create-screen composable's — CreateEditTaskScreen
     * calls onSaved() (navigating away) the instant this is kicked off rather
     * than waiting for it, per the user's explicit "should display instantly,
     * no matter if it's saved to the DB yet" request, so the write has to
     * survive that composable leaving composition. refreshAssignedGroups() at
     * the end reconciles the optimistic entry with the server's real one
     * (harmless no-op visually — same data — unless the write actually failed).
     */
    fun saveTaskAssignment(assignerName: String, recipientUids: Set<String>, taskFor: (uid: String) -> Task) {
        viewModelScope.launch {
            coroutineScope {
                recipientUids.map { uid ->
                    async { runCatching { AssignmentRepository.assignTask(session.uid, assignerName, uid, taskFor(uid)) } }
                }.awaitAll()
            }
            refreshAssignedGroups()
        }
    }

    /** Same idea as saveTaskAssignment(), for a habit. */
    fun saveHabitAssignment(assignerName: String, recipientUids: Set<String>, habitFor: (uid: String) -> Habit) {
        viewModelScope.launch {
            coroutineScope {
                recipientUids.map { uid ->
                    async { runCatching { AssignmentRepository.assignHabit(session.uid, assignerName, uid, habitFor(uid)) } }
                }.awaitAll()
            }
            refreshAssignedGroups()
        }
    }

    /** Fire-and-forget update for an already-existing own task — same "don't make Save wait" reasoning, but no optimistic row needed since allTasks is already a live Firebase listener that'll pick this up the instant the write lands. */
    fun updateTaskInBackground(task: Task) {
        viewModelScope.launch { runCatching { TaskRepository.updateTask(session.orgId, task) } }
    }

    /** Same idea as updateTaskInBackground(), for a habit. */
    fun updateHabitInBackground(habit: Habit) {
        viewModelScope.launch { runCatching { HabitRepository.updateHabit(session.orgId, habit) } }
    }

    /**
     * Instant display for a brand-new Solo (unassigned, self-created) task —
     * same "show it before the write lands" reasoning as
     * addOptimisticTaskGroup(), but inserted straight into allTasks since a
     * Solo task lives in THIS user's own org, not a recipient's — it's a
     * TaskRow.Own the moment the live tasksFlow listener catches up anyway,
     * this just doesn't make the user wait for that round trip first.
     */
    fun addOptimisticTask(task: Task) {
        allTasks.value = listOf(task) + allTasks.value.filterNot { it.id == task.id }
        recompute(allTasks.value, allCompletions.value, allHabits.value, allHabitCompletions.value)
    }

    /** Same idea as addOptimisticTask(), for a habit. */
    fun addOptimisticHabit(habit: Habit) {
        allHabits.value = listOf(habit) + allHabits.value.filterNot { it.id == habit.id }
        recompute(allTasks.value, allCompletions.value, allHabits.value, allHabitCompletions.value)
    }

    /** Writes a new Solo task directly into this user's own org — no cross-org AssignmentRepository involved, since there's no recipient. */
    fun saveSoloTask(task: Task) {
        viewModelScope.launch { runCatching { TaskRepository.saveNewTask(session.orgId, task) } }
    }

    /** Same idea as saveSoloTask(), for a habit. */
    fun saveSoloHabit(habit: Habit) {
        viewModelScope.launch { runCatching { HabitRepository.saveNewHabit(session.orgId, habit) } }
    }

    private fun recompute(
        tasks: List<Task>,
        completions: Set<String>,
        habits: List<Habit>,
        habitCompletions: Map<String, Map<String, HabitCompletionEntry>>,
        assignedTasks: List<AssignmentRepository.AssignedTaskGroup> = assignedTaskGroups.value,
        assignedHabits: List<AssignmentRepository.AssignedHabitGroup> = assignedHabitGroups.value,
        completionTimestamps: Map<String, Long> = allTaskCompletionTimestamps.value
    ) {
        val s = _uiState.value
        val dateStr = s.selectedDate.toString()
        val filters = s.filters
        val tasksForDate = tasksForDateFrom(tasks, dateStr)
        val assignedTasksForDate = assignedTasks.filter { taskMatchesDate(it.task, dateStr) }

        // Own rows split into "Solo" (no other creator on record — including
        // legacy/imported items with no recorded creator) vs "Shared,
        // received" (createdBy names someone else). Assigned rows (sent BY
        // this viewer) are always Shared, never Solo.
        fun isSoloRelationship(createdBy: String?) = createdBy == null || createdBy == session.uid
        val sharedOwnTasksForDate = tasksForDate.filter { !isSoloRelationship(it.createdBy) }
        val soloOwnTasksForDate = tasksForDate.filter { isSoloRelationship(it.createdBy) }

        fun soloCategoryPasses(category: String) =
            s.soloCategoryFilter == "All" || category.ifBlank { "Personal" } == s.soloCategoryFilter

        fun ownTaskFilterPasses(task: Task): Boolean {
            if (!filters.isActive) return true
            val done = isDoneOn(task, completions)
            if (!taskStatusPasses(filters, task, done, dateStr)) return false
            if (!priorityXpPasses(filters, task.priority, task.points)) return false
            if (!dueDatePasses(filters, task.dueDate)) return false
            if (!createdDatePasses(filters, task.createdAt, task.createdDate)) return false
            if (filters.completedDateRange != null) {
                val completedAt = completionTimestamps["${task.id}-${session.uid}"]
                if (!completedDatePasses(filters, completedAt)) return false
            }
            return true
        }

        val taskRows = when (s.itemMode) {
            ItemMode.SOLO -> soloOwnTasksForDate.filter { soloCategoryPasses(it.category) && ownTaskFilterPasses(it) }.map { TaskRow.Own(it) }
            ItemMode.SHARED -> {
                val ownPart = if (s.sharedSubFilter != SharedSubFilter.ASSIGNED_BY_ME) {
                    sharedOwnTasksForDate.filter { ownTaskFilterPasses(it) }.map { TaskRow.Own(it) }
                } else emptyList()
                val assignedPart = if (s.sharedSubFilter != SharedSubFilter.ASSIGNED_TO_ME) {
                    assignedTasksForDate.filter { assignedTaskGroupFilterPasses(filters, it, dateStr) }.map { TaskRow.Assigned(it) }
                } else emptyList()
                ownPart + assignedPart
            }
        }.sortedByDescending { row -> creationSortKeyForRow(row) } // newest-created on top

        val habitsForDate = HabitStats.habitsForDate(habits, dateStr, session.uid)
        val assignedHabitsForDate = assignedHabits.filter { habitScheduledOn(it.habit, s.selectedDate) }
        val sharedOwnHabitsForDate = habitsForDate.filter { !isSoloRelationship(it.createdBy) }
        val soloOwnHabitsForDate = habitsForDate.filter { isSoloRelationship(it.createdBy) }

        fun ownHabitFilterPasses(habit: Habit): Boolean {
            if (!filters.isActive) return true
            val done = habitCompletions[habit.id]?.containsKey(dateStr) ?: false
            // No OVERDUE for habits — they recur rather than fall due, so PENDING/COMPLETED is all that applies.
            if (filters.statuses.isNotEmpty() && (if (done) TaskStatus.COMPLETED else TaskStatus.PENDING) !in filters.statuses) return false
            if (!priorityXpPasses(filters, habit.priority, habit.xpPerCompletion)) return false
            // dueDateRange is skipped here on purpose — habits have no due date to check against.
            if (!createdDatePasses(filters, habit.createdAt, null)) return false
            if (filters.completedDateRange != null) {
                val completedAt = habitCompletions[habit.id]?.get(dateStr)?.completedAt
                if (!completedDatePasses(filters, completedAt)) return false
            }
            if (filters.habitFrequencyTypes.isNotEmpty() && habit.frequency.type !in filters.habitFrequencyTypes) return false
            if (filters.habitPerformance.isNotEmpty() || filters.streakLength != null) {
                val habitCompletionsMap = habitCompletions[habit.id] ?: emptyMap()
                val streak = HabitStats.calcStreak(habit, habitCompletionsMap)
                if (filters.streakLength != null && !filters.streakLength.contains(streak.current)) return false
                if (filters.habitPerformance.isNotEmpty()) {
                    val performances = habitPerformancesFor(habit, habitCompletionsMap, streak)
                    if (filters.habitPerformance.none { it in performances }) return false
                }
            }
            return true
        }

        val habitRows = when (s.itemMode) {
            ItemMode.SOLO -> soloOwnHabitsForDate.filter { soloCategoryPasses(it.category) && ownHabitFilterPasses(it) }.map { HabitRow.Own(it) }
            ItemMode.SHARED -> {
                val ownPart = if (s.sharedSubFilter != SharedSubFilter.ASSIGNED_BY_ME) {
                    sharedOwnHabitsForDate.filter { ownHabitFilterPasses(it) }.map { HabitRow.Own(it) }
                } else emptyList()
                val assignedPart = if (s.sharedSubFilter != SharedSubFilter.ASSIGNED_TO_ME) {
                    assignedHabitsForDate.filter { assignedHabitGroupFilterPasses(filters, it, dateStr) }.map { HabitRow.Assigned(it) }
                } else emptyList()
                ownPart + assignedPart
            }
        }.sortedByDescending { row -> creationSortKeyForRow(row) } // same relationship split and newest-first order as tasks

        _uiState.value = s.copy(
            isLoading = false,
            visibleTasks = taskRows,
            completions = completions,
            visibleHabits = habitRows,
            habitCompletions = habitCompletions,
            sharedTaskCount = sharedOwnTasksForDate.size + assignedTasksForDate.size,
            soloTaskCount = soloOwnTasksForDate.size,
            sharedHabitCount = sharedOwnHabitsForDate.size + assignedHabitsForDate.size,
            soloHabitCount = soloOwnHabitsForDate.size
        )
    }

    private fun creationSortKeyForRow(row: TaskRow): Long = when (row) {
        is TaskRow.Own -> creationSortKey(row.task.createdAt, row.task.id)
        is TaskRow.Assigned -> creationSortKey(row.group.task.createdAt, row.group.itemId)
    }

    private fun creationSortKeyForRow(row: HabitRow): Long = when (row) {
        is HabitRow.Own -> creationSortKey(row.habit.createdAt, row.habit.id)
        is HabitRow.Assigned -> creationSortKey(row.group.habit.createdAt, row.group.itemId)
    }

    // ── Filters sheet predicates (see TaskHabitFilters' doc comment) ──

    private fun taskStatus(dueDate: String?, done: Boolean, todayStr: String): TaskStatus = when {
        done -> TaskStatus.COMPLETED
        dueDate != null && dueDate < todayStr -> TaskStatus.OVERDUE
        else -> TaskStatus.PENDING
    }

    private fun taskStatusPasses(filters: TaskHabitFilters, task: Task, done: Boolean, todayStr: String): Boolean {
        if (filters.statuses.isEmpty()) return true
        return taskStatus(task.dueDate, done, todayStr) in filters.statuses
    }

    private fun priorityXpPasses(filters: TaskHabitFilters, priority: String, xp: Int): Boolean {
        if (filters.priorities.isNotEmpty() && priority !in filters.priorities) return false
        if (filters.xpMin != null && xp < filters.xpMin) return false
        if (filters.xpMax != null && xp > filters.xpMax) return false
        return true
    }

    private fun localDateOrNull(dateStr: String?): LocalDate? =
        dateStr?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun localDateFromMillis(millis: Long?): LocalDate? =
        if (millis == null || millis <= 0L) null
        else java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()

    private fun dueDatePasses(filters: TaskHabitFilters, dueDate: String?): Boolean {
        val range = filters.dueDateRange ?: return true
        val due = localDateOrNull(dueDate) ?: return false
        return range.contains(due)
    }

    private fun createdDatePasses(filters: TaskHabitFilters, createdAtMillis: Long, createdDateStr: String?): Boolean {
        val range = filters.createdDateRange ?: return true
        val created = localDateFromMillis(createdAtMillis) ?: localDateOrNull(createdDateStr) ?: return false
        return range.contains(created)
    }

    private fun completedDatePasses(filters: TaskHabitFilters, completedAtMillis: Long?): Boolean {
        val range = filters.completedDateRange ?: return true
        val completed = localDateFromMillis(completedAtMillis) ?: return false
        return range.contains(completed)
    }

    /** An assigned-out task group counts as done for status purposes once every recipient has completed their copy. No per-recipient completion timestamp is tracked, so a Completed-date filter always excludes these — see TaskHabitFilters' doc comment. */
    private fun assignedTaskGroupFilterPasses(filters: TaskHabitFilters, group: AssignmentRepository.AssignedTaskGroup, todayStr: String): Boolean {
        if (!filters.isActive) return true
        val done = group.members.isNotEmpty() && group.doneCount >= group.members.size
        if (!taskStatusPasses(filters, group.task, done, todayStr)) return false
        if (!priorityXpPasses(filters, group.task.priority, group.task.points)) return false
        if (!dueDatePasses(filters, group.task.dueDate)) return false
        if (!createdDatePasses(filters, group.task.createdAt, group.task.createdDate)) return false
        if (filters.completedDateRange != null) return false
        return true
    }

    /**
     * Same reasoning as assignedTaskGroupFilterPasses() — no OVERDUE (habits
     * recur) and no per-recipient completion timestamp. Frequency is safe to
     * check (one Habit object, one frequency, shared by every recipient's
     * copy), but Performance and Streak Length are NOT — see TaskHabitFilters'
     * doc comment for why those two are skipped for assigned-out groups.
     */
    private fun assignedHabitGroupFilterPasses(filters: TaskHabitFilters, group: AssignmentRepository.AssignedHabitGroup, dateStr: String): Boolean {
        if (!filters.isActive) return true
        val done = group.members.isNotEmpty() && group.doneCount(dateStr) >= group.members.size
        if (filters.statuses.isNotEmpty() && (if (done) TaskStatus.COMPLETED else TaskStatus.PENDING) !in filters.statuses) return false
        if (!priorityXpPasses(filters, group.habit.priority, group.habit.xpPerCompletion)) return false
        if (!createdDatePasses(filters, group.habit.createdAt, null)) return false
        if (filters.completedDateRange != null) return false
        if (filters.habitFrequencyTypes.isNotEmpty() && group.habit.frequency.type !in filters.habitFrequencyTypes) return false
        return true
    }

    /** Every Habit Performance bucket (see HabitPerformance's doc comment) that currently applies to this habit — a habit can match several at once. */
    private fun habitPerformancesFor(
        habit: Habit,
        completions: Map<String, HabitCompletionEntry>,
        streak: HabitStats.Streak
    ): Set<HabitPerformance> {
        val today = LocalDate.now()
        val result = mutableSetOf<HabitPerformance>()
        val stillOngoing = habit.endDate == null || runCatching { !LocalDate.parse(habit.endDate).isBefore(today) }.getOrDefault(true)
        if (stillOngoing) result += HabitPerformance.ACTIVE
        val doneToday = completions.containsKey(today.toString())
        if (doneToday) {
            result += HabitPerformance.COMPLETED_TODAY
        } else if (habitScheduledOn(habit, today)) {
            result += HabitPerformance.MISSED_TODAY
        }
        result += if (streak.current > 0) HabitPerformance.STREAK_ACTIVE else HabitPerformance.STREAK_BROKEN
        return result
    }

    /** Same date match rule as tasksForDateFrom(), minus the "applies to me" check — an assigned-out group is already scoped to items THIS user handed out. */
    private fun taskMatchesDate(task: Task, dateStr: String): Boolean =
        !task.isTemplate && (task.instanceDate ?: task.dueDate ?: task.createdDate ?: dateStr) == dateStr

    /** Same scheduling rule as HabitStats.habitsForDate(), minus the assignedTo check — the recipient, not this user, is who the habit's assignedTo names. */
    private fun habitScheduledOn(habit: Habit, date: LocalDate): Boolean =
        habit.isInRange(date.toString()) && habit.frequency.isScheduledFor(date.dayOfWeek.value % 7, date.dayOfMonth)

    /**
     * `createdAt` is 0 on tasks/habits that predate this field (or were
     * seeded/imported through a path that never set it) — sorting purely by
     * createdAt would leave those stuck wherever they happened to sit in the
     * array instead of a real newest-first order. Both `saveNewTask()` and
     * `saveNewHabit()` mint ids as `pt-<millis>`/`h-<millis>` (see
     * CreateEditTaskScreen/CreateEditHabitScreen), so falling back to the
     * digits in the id is a reliable stand-in creation timestamp.
     */
    private fun creationSortKey(createdAt: Long, id: String): Long =
        createdAt.takeIf { it > 0 } ?: id.filter { it.isDigit() }.toLongOrNull() ?: 0L

    private fun tasksForDateFrom(tasks: List<Task>, dateStr: String): List<Task> =
        tasks.filter { t -> t.appliesTo(session.uid) && taskMatchesDate(t, dateStr) }

    private fun isDoneOn(task: Task, completions: Set<String>): Boolean =
        completions.contains("${task.id}-${session.uid}")

    fun isDone(task: Task, completions: Set<String> = _uiState.value.completions): Boolean =
        _uiState.value.optimisticDone.contains(task.id) || completions.contains("${task.id}-${session.uid}")

    private fun isSoloOwner(createdBy: String?) = createdBy == null || createdBy == session.uid

    private fun passesSoloCategory(category: String, s: TasksHabitsUiState) =
        s.soloCategoryFilter == "All" || category.ifBlank { "Personal" } == s.soloCategoryFilter

    /**
     * Counts (done, total) for this date restricted to whichever Shared/Solo
     * mode (and sub-filter/category) is currently active — mirrors the same
     * relationship split recompute() uses for the visible list, so the
     * date-strip ring actually changes when the mode toggle changes instead
     * of always reflecting every own task regardless of mode.
     */
    private fun taskModeCounts(date: LocalDate): Pair<Int, Int> {
        val s = _uiState.value
        val dateStr = date.toString()
        val ownForDate = tasksForDateFrom(allTasks.value, dateStr)
        val completions = allCompletions.value
        return when (s.itemMode) {
            ItemMode.SOLO -> {
                val filtered = ownForDate.filter { isSoloOwner(it.createdBy) && passesSoloCategory(it.category, s) }
                filtered.count { isDoneOn(it, completions) } to filtered.size
            }
            ItemMode.SHARED -> {
                val ownPart = if (s.sharedSubFilter != SharedSubFilter.ASSIGNED_BY_ME) ownForDate.filter { !isSoloOwner(it.createdBy) } else emptyList()
                val assignedPart = if (s.sharedSubFilter != SharedSubFilter.ASSIGNED_TO_ME) assignedTaskGroups.value.filter { taskMatchesDate(it.task, dateStr) } else emptyList()
                val done = ownPart.count { isDoneOn(it, completions) } + assignedPart.count { it.members.isNotEmpty() && it.doneCount >= it.members.size }
                done to (ownPart.size + assignedPart.size)
            }
        }
    }

    private fun habitModeCounts(date: LocalDate): Pair<Int, Int> {
        val s = _uiState.value
        val dateStr = date.toString()
        val ownForDate = HabitStats.habitsForDate(allHabits.value, dateStr, session.uid)
        val completionsByHabit = allHabitCompletions.value
        return when (s.itemMode) {
            ItemMode.SOLO -> {
                val filtered = ownForDate.filter { isSoloOwner(it.createdBy) && passesSoloCategory(it.category, s) }
                filtered.count { h -> completionsByHabit[h.id]?.containsKey(dateStr) ?: false } to filtered.size
            }
            ItemMode.SHARED -> {
                val ownPart = if (s.sharedSubFilter != SharedSubFilter.ASSIGNED_BY_ME) ownForDate.filter { !isSoloOwner(it.createdBy) } else emptyList()
                val assignedPart = if (s.sharedSubFilter != SharedSubFilter.ASSIGNED_TO_ME) assignedHabitGroups.value.filter { habitScheduledOn(it.habit, date) } else emptyList()
                val done = ownPart.count { h -> completionsByHabit[h.id]?.containsKey(dateStr) ?: false } +
                    assignedPart.count { it.members.isNotEmpty() && it.doneCount(dateStr) >= it.members.size }
                done to (ownPart.size + assignedPart.size)
            }
        }
    }

    /** Matches _habitDayPct() — % of this date's tasks completed. Used by the date-strip's progress ring. */
    fun tasksPctForDate(date: LocalDate): Int {
        val (done, total) = taskModeCounts(date)
        if (total == 0) return 0
        return Math.round(done * 100f / total)
    }

    /** Matches _hbDayPct() — % of this date's scheduled habits completed. */
    fun habitsPctForDate(date: LocalDate): Int {
        val (done, total) = habitModeCounts(date)
        if (total == 0) return 0
        return Math.round(done * 100f / total)
    }

    /** Same as tasksPctForDate() but unrounded, for the header's one-decimal "10.0%" display. */
    fun tasksPctPreciseForDate(date: LocalDate): Double {
        val (done, total) = taskModeCounts(date)
        if (total == 0) return 0.0
        return done * 100.0 / total
    }

    /** Same as habitsPctForDate() but unrounded, for the header's one-decimal "10.0%" display. */
    fun habitsPctPreciseForDate(date: LocalDate): Double {
        val (done, total) = habitModeCounts(date)
        if (total == 0) return 0.0
        return done * 100.0 / total
    }

    fun selectDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
        recompute(allTasks.value, allCompletions.value, allHabits.value, allHabitCompletions.value)
    }

    fun goToToday() = selectDate(LocalDate.now())

    fun setItemMode(mode: ItemMode) {
        _uiState.value = _uiState.value.copy(itemMode = mode)
        recompute(allTasks.value, allCompletions.value, allHabits.value, allHabitCompletions.value)
    }

    fun setSharedSubFilter(filter: SharedSubFilter) {
        _uiState.value = _uiState.value.copy(sharedSubFilter = filter)
        recompute(allTasks.value, allCompletions.value, allHabits.value, allHabitCompletions.value)
    }

    fun setSoloCategoryFilter(filter: String) {
        _uiState.value = _uiState.value.copy(soloCategoryFilter = filter)
        recompute(allTasks.value, allCompletions.value, allHabits.value, allHabitCompletions.value)
    }

    /** Applies the Filters bottom sheet's chosen values — see TaskHabitFilters' doc comment for what each field does and its known limits. */
    fun setFilters(filters: TaskHabitFilters) {
        _uiState.value = _uiState.value.copy(filters = filters)
        recompute(allTasks.value, allCompletions.value, allHabits.value, allHabitCompletions.value)
    }

    fun clearFilters() = setFilters(TaskHabitFilters())

    /**
     * Matches script.js's completeTask()'s 5-second-undo quick-complete
     * flow: shows as done immediately, but the actual Firebase write is
     * deferred until the undo window elapses — undo just cancels the
     * pending job, nothing to revert since nothing was ever written.
     */
    fun completeTaskWithUndo(task: Task, onUndoWindowClosed: () -> Unit = {}) {
        _uiState.value = _uiState.value.copy(optimisticDone = _uiState.value.optimisticDone + task.id)
        val job = viewModelScope.launch {
            delay(5000)
            runCatching { TaskRepository.completeTask(session.orgId, session.uid, task) }
            _uiState.value = _uiState.value.copy(optimisticDone = _uiState.value.optimisticDone - task.id)
            optimisticDoneJobs.remove(task.id)
            onUndoWindowClosed()
        }
        optimisticDoneJobs[task.id] = job
    }

    fun undoComplete(task: Task) {
        optimisticDoneJobs.remove(task.id)?.cancel()
        _uiState.value = _uiState.value.copy(optimisticDone = _uiState.value.optimisticDone - task.id)
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch { runCatching { TaskRepository.deleteTask(session.orgId, taskId) } }
    }

    fun toggleChecklistItem(task: Task, itemId: String, done: Boolean) {
        viewModelScope.launch { runCatching { TaskRepository.toggleChecklistItem(session.orgId, task.id, itemId, done) } }
    }

    fun completeHabitToday(habit: Habit) {
        viewModelScope.launch {
            runCatching { HabitRepository.completeHabit(session.orgId, session.uid, habit, LocalDate.now().toString()) }
        }
    }

    fun deleteHabit(habitId: String) {
        viewModelScope.launch { runCatching { HabitRepository.deleteHabit(session.orgId, habitId) } }
    }

    fun streakFor(habit: Habit): HabitStats.Streak =
        HabitStats.calcStreak(habit, allHabitCompletions.value[habit.id] ?: emptyMap())
}
