package com.triangle.app.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triangle.app.data.DashboardStats
import com.triangle.app.data.HabitRepository
import com.triangle.app.data.HabitStats
import com.triangle.app.data.OrgDataRepository
import com.triangle.app.data.SessionStore
import com.triangle.app.data.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val firstName: String = "",
    val fullName: String = "",
    val points: Int = 0,
    val obediencePercent: Int = 0,
    val rank: Int = 1,
    val totalMembers: Int = 1,
    val longestStreak: Int = 0,
    val currentStreak: Int = 0,
    val weeklyCount: Int = 0,
    val weeklyXP: Int = 0,
    val totalTasksAll: Int = 0,
    val tasksCompleted: Int = 0
)

/**
 * Native port of the DATA half of script.js's renderInternDashboard() —
 * see DashboardStats for the ported pure computations. Points/rank/weekly
 * XP/obedience/longest-streak still come from
 * OrgDataRepository's one-shot read (unchanged from Milestone 1 — those
 * aren't part of Milestone 2's scope). "Total Tasks" / "This week: N tasks
 * completed" and "Current Streak" now read from TaskRepository/
 * HabitRepository's realtime flows (Milestone 2), replacing Milestone 1's
 * one-shot task-based "Current Streak" placeholder with the real
 * habit-based one (max current streak across the intern's habits — same
 * semantics as script.js's getHabitStreak()).
 */
class DashboardViewModel(private val session: SessionStore.Session) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState(firstName = firstNameOf(session.name), fullName = session.name))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        load()
        observeTasksAndHabits()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val snap = OrgDataRepository.fetchDashboard(session.orgId, session.uid)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    points = snap.points,
                    obediencePercent = DashboardStats.getObedience(snap.tasks, snap.taskCompletions, session.uid),
                    rank = DashboardStats.getRank(snap.memberPoints, session.uid),
                    totalMembers = maxOf(snap.memberPoints.size, 1),
                    longestStreak = DashboardStats.getStreak(snap.tasks, snap.taskCompletions, session.uid),
                    weeklyXP = snap.weeklyXP
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Could not load your dashboard")
            }
        }
    }

    private data class TaskHabitStats(val totalTasks: Int, val weeklyCount: Int, val streak: Int, val completed: Int)

    private fun observeTasksAndHabits() {
        combine(
            TaskRepository.tasksFlow(session.orgId),
            TaskRepository.completionsFlow(session.orgId),
            HabitRepository.habitsFlow(session.orgId),
            HabitRepository.habitCompletionsFlow(session.orgId, session.uid)
        ) { tasks, completions, habits, habitCompletions ->
            val taskMaps = tasks.map { it.toMap() }
            val completionsMap: Map<String, Any?> = completions.associateWith { mapOf("done" to true) }
            val totalTasks = DashboardStats.myTasks(taskMaps, session.uid).size
            val weeklyCount = DashboardStats.getWeeklyCount(taskMaps, completionsMap, session.uid)
            val completed = DashboardStats.getCompletedCount(taskMaps, completionsMap, session.uid)
            val bestCurrentStreak = habits
                .filter { it.assignedTo.contains(session.uid) }
                .maxOfOrNull { h -> HabitStats.calcStreak(h, habitCompletions[h.id] ?: emptyMap()).current } ?: 0
            TaskHabitStats(totalTasks, weeklyCount, bestCurrentStreak, completed)
        }.onEach { stats ->
            _uiState.value = _uiState.value.copy(
                totalTasksAll = stats.totalTasks,
                weeklyCount = stats.weeklyCount,
                currentStreak = stats.streak,
                tasksCompleted = stats.completed
            )
        }.launchIn(viewModelScope)
    }

    private fun firstNameOf(name: String): String = name.trim().substringBefore(" ").ifBlank { "there" }
}
