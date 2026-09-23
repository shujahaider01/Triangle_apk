package com.triangle.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.triangle.app.data.HabitRepository
import com.triangle.app.data.SessionStore
import com.triangle.app.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires when an AlarmManager alarm scheduled by ReminderScheduler goes off.
 * Re-reads the live Task/Habit from Firebase before posting anything, since
 * the alarm may have been left dangling by an edit/delete/completion that
 * raced the firing instant (the receiver only re-validates — it doesn't
 * trust stale intent extras for title/body). A habit reminder re-arms its
 * own next occurrence here, since AlarmManager has no reliable exact-repeat
 * primitive (see ReminderScheduler's header comment).
 */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(ReminderScheduler.EXTRA_KIND) ?: return
        val itemId = intent.getStringExtra(ReminderScheduler.EXTRA_ITEM_ID) ?: return
        val orgId = intent.getStringExtra(ReminderScheduler.EXTRA_ORG_ID) ?: return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (kind) {
                    ReminderScheduler.KIND_TASK -> handleTask(appContext, orgId, itemId)
                    ReminderScheduler.KIND_HABIT -> handleHabit(appContext, orgId, itemId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleTask(context: Context, orgId: String, taskId: String) {
        val task = TaskRepository.getTask(orgId, taskId) ?: return
        if (task.reminderTime == null) return // disabled/edited away since the alarm was scheduled

        val session = SessionStore.sessionFlow(context).first()
        if (session != null) {
            val doneKey = "$taskId-${session.uid}"
            val done = TaskRepository.completionsFlow(orgId).first().contains(doneKey)
            if (done) return
        }

        ReminderNotifier.show(context, ReminderScheduler.KIND_TASK, task.id, "Task reminder", task.title)
        // One-shot per decision #4 — no re-arming for tasks.
    }

    private suspend fun handleHabit(context: Context, orgId: String, habitId: String) {
        val habit = HabitRepository.getHabit(orgId, habitId) ?: return
        if (habit.reminderTime == null) return // disabled/edited away since the alarm was scheduled

        ReminderNotifier.show(context, ReminderScheduler.KIND_HABIT, habit.id, "Habit reminder", habit.name)
        // Re-arm the next matching occurrence — habits have no exact repeating alarm.
        ReminderScheduler.scheduleForHabit(context, orgId, habit)
    }
}
