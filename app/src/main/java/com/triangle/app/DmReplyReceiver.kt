package com.triangle.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.triangle.app.data.DmRepository
import com.triangle.app.data.NotificationRepository
import com.triangle.app.data.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Handles the inline "Reply" action on a chat-message push: resolves who
 * sent it from the notification record the push points at, sends the reply
 * through the same DmRepository.sendMessage() the chat screen uses, marks the
 * thread read, and dismisses the notification once the send succeeded.
 */
class DmReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()?.trim()
        val notifId = intent.getStringExtra(EXTRA_NOTIF_ID)
        val systemId = intent.getIntExtra(EXTRA_SYSTEM_ID, 0)
        if (text.isNullOrEmpty() || notifId == null) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val session = SessionStore.sessionFlow(appContext).first() ?: return@launch
                val other = NotificationRepository.getNotification(session.orgId, session.uid, notifId)?.otherUserId
                    ?: return@launch
                val result = runCatching { DmRepository.sendMessage(session.uid, session.name, other, text) }.getOrNull()
                if (result == DmRepository.SendResult.Success) {
                    runCatching { DmRepository.markThreadRead(session.uid, DmRepository.threadId(session.uid, other)) }
                    runCatching { NotificationRepository.markRead(session.orgId, session.uid, notifId) }
                    (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(systemId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val KEY_REPLY = "dm_reply_text"
        const val EXTRA_NOTIF_ID = "dm_reply_notif_id"
        const val EXTRA_SYSTEM_ID = "dm_reply_system_id"
    }
}
