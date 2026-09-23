package com.triangle.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.triangle.app.data.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * AlarmManager alarms are cleared on reboot, so every pending reminder has
 * to be re-armed from Firebase once the device (and, implicitly, this app's
 * process) comes back up.
 */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val session = SessionStore.sessionFlow(appContext).first() ?: return@launch
                ReminderScheduler.rescheduleAll(appContext, session.orgId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
