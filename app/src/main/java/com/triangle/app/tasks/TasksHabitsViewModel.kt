package com.triangle.app.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triangle.app.data.HabitRepository
import com.triangle.app.data.HabitStats
import com.triangle.app.data.SessionStore
import com.triangle.app.data.TaskRepository
import com.triangle.app.data.models.Habit
import com.triangle.app.data.models.HabitCompletionEntry
import com.triangle.app.data.models.Task
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TasksHabitsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedDate: LocalDate = LocalDate.now(),
    val taskStatusFilter: String = "All", // "All" | "Due"
    val habitStatusFilter: String = "All",
    val categoryFilter: String = "All", // "All" | "Office" | "Academic" | "Personal"
    val visibleTasks: List<Task> = emptyList(),
    val completions: Set<String> = emptySet(),
    /** Task ids whose completion is optimistically shown but not yet committed to Firebase (5s undo window). */
    val optimisticDone: Set<String> = emptySet(),
    val visibleHabits: List<Habit> = emptyList(),
    val habitCompletions: Map<String, Map<String, HabitCompletionEntry>> = emptyMap()
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
class TasksHabitsViewModel(private val session: SessionStore.Session) : ViewModel() {

    private val allTasks = MutableStateFlow<List<Task>>(emptyList())
    private val allCompletions = MutableStateFlow<Set<String>>(emptySet())
    private val allHabits = MutableStateFlow<List<Habit>>(emptyList())
    private val allHabitCompletions = MutableStateFlow<Map<String, Map<String, HabitCompletionEntry>>>(emptyMap())

    /** Full (unfiltered) live lists — used by TaskDetailScreen/HabitDetailScreen/AppNavHost to look up a specific item by id. */
    val tasks: StateFlow<List<Task>> = allTasks.asStateFlow()
    val habits: StateFlow<List<Habit>> = allHabits.asStateFlow()

    private val _uiState = MutableStateFlow(TasksHabitsUiState())
    val uiState: StateFlow<TasksHabitsUiState> = _uiState.asStateFlow()

    private val optimisticDoneJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            runCatching { TaskRepository.materializeRepeatingTasks(session.orgId) }
        }
        TaskRepository.tasksFlow(session.orgId).onEach { allTasks.value = it }.launchIn(viewModelScope)
        TaskRepository.completionsFlow(session.orgId).onEach { allCompletions.value = it }.launchIn(viewModelScope)
        HabitRepository.habitsFlow(session.orgId).onEach { allHabits.value = it }.launchIn(viewModelScope)
        HabitRepository.habitCompletionsFlow(session.orgId, session.uid).onEach { allHabitCompletions.value = it }.launchIn(viewModelScope)

        combine(allTasks, allCompletions, allHabits, allHabitCompletions) { tasks, completions, habits, habitCompletions ->
            Quad(tasks, completions, habits, habitCompletions)
        }.onEach { (tasks, completions, habits, habitCompletions) ->
            recompute(tasks, completions, habits, habitCompletions)
        }.launchIn(viewModelScope)
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    private fun recompute(
        tasks: List<Task>,
        completions: Set<String>,
        habits: List<Habit>,
        habitCompletions: Map<String, Map<String, HabitCompletionEntry>>
    ) {
        val s = _uiState.value
        val dateStr = s.selectedDate.toString()
        val tasksForDate = tasks.filter { t ->
            t.appliesTo(session.uid) && !t.isTemplate &&
                (t.instanceDate ?: t.dueDate ?: t.createdDate ?: dateStr) == dateStr
        }
        val statusFiltered = if (s.taskStatusFilter == "Due") {
            tasksForDate.filter { !isDone(it, completions) }
        } else tasksForDate
        val catFiltered = if (s.categoryFilter == "All") statusFiltered
        else statusFiltered.filter { (it.category.ifBlank { "Personal" }) == s.categoryFilter }

        val habitsForDate = HabitStats.habitsForDate(habits, dateStr, session.uid)
        val habitStatusFiltered = if (s.habitStatusFilter == "Due") {
            habitsForDate.filter { h -> !(habitCompletions[h.id]?.containsKey(dateStr) ?: false) }
        } else habitsForDate
        val habitCatFiltered = habitStatusFiltered // habits aren't categorized in the source app

        _uiState.value = s.copy(
            isLoading = false,
            visibleTasks = catFiltered,
            completions = completions,
            visibleHabits = habitCatFiltered,
            habitCompletions = habitCompletions
        )
    }

    fun isDone(task: Task, completions: Set<String> = _uiState.value.completions): Boolean =
        _uiState.value.optimisticDone.contains(task.id) || completions.contains("${task.id}-${session.uid}")

    fun selectDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
        recompute(allTasks.value, allCompletions.value, allHabits.value, allHabitCompletions.value)
    }

    fun goToToday() = selectDate(LocalDate.now())

    fun setTaskStatusFilter(filter: String) {
        _uiState.value = _uiState.value.copy(taskStatusFilter = filter)
        recompute(allTasks.value, allCompletions.value, allHabits.value, allHabitCompletions.value)
    }

    fun setHabitStatusFilter(filter: String) {
        _uiState.value = _uiState.value.copy(habitStatusFilter = filter)
        recompute(allTasks.value, allCompletions.value, allHabits.value, allHabitCompletions.value)
    }

    fun setCategoryFilter(filter: String) {
        _uiState.value = _uiState.value.copy(categoryFilter = filter)
        recompute(allTasks.value, allCompletions.value, allHabits.value, allHabitCompletions.value)
    }

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

    /** No-undo immediate complete/uncomplete — matches ntdChangeStatus() (Task Detail page). */
    fun setTaskDoneImmediate(task: Task, done: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (done) TaskRepository.completeTask(session.orgId, session.uid, task)
                else TaskRepository.uncompleteTask(session.orgId, session.uid, task.id)
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch { runCatching { TaskRepository.deleteTask(session.orgId, taskId) } }
    }

    fun saveTask(task: Task, isNew: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (isNew) TaskRepository.saveNewTask(session.orgId, task) else TaskRepository.updateTask(session.orgId, task)
            }
        }
    }

    fun completeHabitToday(habit: Habit) {
        viewModelScope.launch {
            runCatching { HabitRepository.completeHabit(session.orgId, session.uid, habit, LocalDate.now().toString()) }
        }
    }

    fun saveHabit(habit: Habit, isNew: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (isNew) HabitRepository.saveNewHabit(session.orgId, habit) else HabitRepository.updateHabit(session.orgId, habit)
            }
        }
    }

    fun deleteHabit(habitId: String) {
        viewModelScope.launch { runCatching { HabitRepository.deleteHabit(session.orgId, habitId) } }
    }

    fun streakFor(habit: Habit): HabitStats.Streak =
        HabitStats.calcStreak(habit, allHabitCompletions.value[habit.id] ?: emptyMap())
}
