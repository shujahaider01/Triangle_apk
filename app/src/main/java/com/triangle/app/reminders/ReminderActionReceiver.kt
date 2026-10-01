package com.triangle.app.reminders

import android.app.NotificationManager
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
import java.time.LocalDate

/**
 * Handles the "Mark completed" button on a reminder notification: completes
 * the task (or today's habit check-in) with the same repository calls the
 * in-app check button uses, so XP is awarded identically. The notification
 * is dismissed right away; if the write then fails it is posted again so
 * the user can retry.
 */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(ReminderScheduler.EXTRA_KIND) ?: return
        val itemId = intent.getStringExtra(ReminderScheduler.EXTRA_ITEM_ID) ?: return
        val orgId = intent.getStringExtra(ReminderScheduler.EXTRA_ORG_ID) ?: return

        val appContext = context.applicationContext
        // Dismiss immediately so the tap feels instant — the Firebase work below
        // can take a second or more on a cold process. If it fails the
        // notification is posted again (see below).
        (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(itemId.hashCode())
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val session = SessionStore.sessionFlow(appContext).first() ?: return@launch
                var title = ""
                val ok = runCatching {
                    when (kind) {
                        ReminderScheduler.KIND_TASK -> TaskRepository.getTask(orgId, itemId)
                            ?.also { title = it.title }
                            ?.let { TaskRepository.completeTask(orgId, session.uid, it) }
                        ReminderScheduler.KIND_HABIT -> HabitRepository.getHabit(orgId, itemId)
                            ?.also { title = it.name }
                            ?.let { HabitRepository.completeHabit(orgId, session.uid, it, LocalDate.now().toString()) }
                    }
                }.isSuccess
                if (!ok) {
                    ReminderNotifier.show(appContext, kind, itemId, orgId, "Couldn't mark completed", title.ifEmpty { "Tap to open Triangle and try again" })
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
