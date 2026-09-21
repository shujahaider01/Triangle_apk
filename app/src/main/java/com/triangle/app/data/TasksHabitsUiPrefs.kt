package com.triangle.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.tasksHabitsUiDataStore by preferencesDataStore(name = "triangle_tasks_habits_ui_prefs")

/**
 * Small per-account UI memory for the Tasks/Habits screen (see
 * TasksHabitsViewModel): which pane (Tasks vs Habits) the bottom-nav
 * "Tasks" tab should reopen to, and which assigned-to-you habits have
 * already been seen. A freshly assigned habit you haven't seen yet forces
 * the Habits pane open once — overriding the remembered last-pane choice —
 * per the user's explicit request; after that one visit it's marked seen
 * and normal last-pane memory takes back over. Keyed by uid so switching
 * accounts on the same device doesn't bleed one person's last pane / seen
 * habits into another's.
 */
object TasksHabitsUiPrefs {
    private fun paneKey(uid: String) = intPreferencesKey("lastActivePane_$uid")
    private fun seenKey(uid: String) = stringSetPreferencesKey("seenAssignedHabitIds_$uid")

    /** 0 = Tasks, 1 = Habits. */
    suspend fun lastActivePane(context: Context, uid: String): Int =
        context.tasksHabitsUiDataStore.data.first()[paneKey(uid)] ?: 0

    /**
     * Live version of lastActivePane() — lets the bottom-nav "Tasks" tab's
     * own label (see AppBottomNav's `tasksLabel`) read "Tasks" or "Habits"
     * from Home/Profile too, not just from inside the Tasks/Habits screen
     * itself. Since it's the same DataStore file TasksHabitsViewModel.
     * setActivePane() writes to, a pane switch there is reflected here the
     * next time this flow re-collects — no shared ViewModel needed.
     */
    fun lastActivePaneFlow(context: Context, uid: String): Flow<Int> =
        context.tasksHabitsUiDataStore.data.map { it[paneKey(uid)] ?: 0 }

    suspend fun setLastActivePane(context: Context, uid: String, page: Int) {
        context.tasksHabitsUiDataStore.edit { it[paneKey(uid)] = page }
    }

    suspend fun seenAssignedHabitIds(context: Context, uid: String): Set<String> =
        context.tasksHabitsUiDataStore.data.first()[seenKey(uid)] ?: emptySet()

    suspend fun markAssignedHabitsSeen(context: Context, uid: String, habitIds: Set<String>) {
        if (habitIds.isEmpty()) return
        context.tasksHabitsUiDataStore.edit { prefs ->
            prefs[seenKey(uid)] = (prefs[seenKey(uid)] ?: emptySet()) + habitIds
        }
    }
}
