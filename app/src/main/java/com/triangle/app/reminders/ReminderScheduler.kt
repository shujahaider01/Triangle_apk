package com.triangle.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.triangle.app.data.HabitRepository
import com.triangle.app.data.TaskRepository
import com.triangle.app.data.models.Habit
import com.triangle.app.data.models.Task
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Schedules/cancels the on-device AlarmManager alarms that back the Reminder
 * feature. AlarmManager (not WorkManager) is used because a reminder has to
 * fire at (close to) an exact instant — WorkManager's one-time/periodic work
 * is explicitly inexact under Doze, which would defeat the feature.
 *
 * Tasks get a single one-shot alarm at dueDate+reminderTime. Habits have no
 * reliable *exact* repeating alarm primitive (AlarmManager.setRepeating() is
 * inexact), so instead only the NEXT matching occurrence is scheduled at a
 * time; ReminderAlarmReceiver re-arms the following occurrence each time a
 * habit reminder fires (see its onReceive()).
 */
object ReminderScheduler {
    const val EXTRA_KIND = "kind"
    const val EXTRA_ITEM_ID = "itemId"
    const val EXTRA_ORG_ID = "orgId"
    const val KIND_TASK = "task"
    const val KIND_HABIT = "habit"

    private fun alarmManager(context: Context) =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pendingIntent(context: Context, kind: String, itemId: String, orgId: String, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = "com.triangle.app.REMINDER_$kind:$itemId"
            putExtra(EXTRA_KIND, kind)
            putExtra(EXTRA_ITEM_ID, itemId)
            putExtra(EXTRA_ORG_ID, orgId)
        }
        val flags = (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, "$kind:$itemId".hashCode(), intent, flags)
    }

    private fun scheduleExact(context: Context, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        val am = alarmManager(context)
        val canExact = Build.VERSION_CODES.S > Build.VERSION.SDK_INT || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun scheduleForTask(context: Context, orgId: String, task: Task) {
        val dueDate = task.dueDate
        val reminderTime = task.reminderTime
        if (dueDate == null || reminderTime == null) {
            cancelForTask(context, orgId, task.id)
            return
        }
        val triggerMillis = try {
            LocalDate.parse(dueDate).atTime(LocalTime.parse(reminderTime))
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            null
        }
        if (triggerMillis == null || triggerMillis <= System.currentTimeMillis()) {
            // Either unparsable, or the moment has already passed — a stale
            // one-shot reminder shouldn't fire immediately on save/sync.
            cancelForTask(context, orgId, task.id)
            return
        }
        val pi = pendingIntent(context, KIND_TASK, task.id, orgId, create = true) ?: return
        scheduleExact(context, triggerMillis, pi)
    }

    fun cancelForTask(context: Context, orgId: String, taskId: String) {
        val pi = pendingIntent(context, KIND_TASK, taskId, orgId, create = false) ?: return
        alarmManager(context).cancel(pi)
        pi.cancel()
    }

    /** Finds the next date on/after `from` the habit is scheduled for, within the habit's active range. Looks up to a year ahead. */
    private fun nextOccurrence(habit: Habit, from: LocalDate): LocalDate? {
        var date = from
        repeat(366) {
            val dateStr = date.toString()
            if (habit.isInRange(dateStr) && habit.frequency.isScheduledFor(date.dayOfWeek.value % 7, date.dayOfMonth)) {
                return date
            }
            date = date.plusDays(1)
        }
        return null
    }

    fun scheduleForHabit(context: Context, orgId: String, habit: Habit) {
        val reminderTime = habit.reminderTime
        if (reminderTime == null) {
            cancelForHabit(context, orgId, habit.id)
            return
        }
        val time = try {
            LocalTime.parse(reminderTime)
        } catch (e: Exception) {
            null
        }
        if (time == null) {
            cancelForHabit(context, orgId, habit.id)
            return
        }
        val now = java.time.LocalDateTime.now()
        // If today is a scheduled day but the time already passed, start looking from tomorrow.
        val searchFrom = if (now.toLocalTime() >= time) now.toLocalDate().plusDays(1) else now.toLocalDate()
        val nextDate = nextOccurrence(habit, searchFrom) ?: run {
            cancelForHabit(context, orgId, habit.id)
            return
        }
        val triggerMillis = nextDate.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = pendingIntent(context, KIND_HABIT, habit.id, orgId, create = true) ?: return
        scheduleExact(context, triggerMillis, pi)
    }

    fun cancelForHabit(context: Context, orgId: String, habitId: String) {
        val pi = pendingIntent(context, KIND_HABIT, habitId, orgId, create = false) ?: return
        alarmManager(context).cancel(pi)
        pi.cancel()
    }

    /** Re-arms every reminder for an org from scratch — used after a device reboot, since AlarmManager alarms don't survive one. */
    suspend fun rescheduleAll(context: Context, orgId: String) {
        TaskRepository.getAllTasks(orgId).forEach { scheduleForTask(context, orgId, it) }
        HabitRepository.getAllHabits(orgId).forEach { scheduleForHabit(context, orgId, it) }
    }
}
