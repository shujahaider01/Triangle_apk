package com.triangle.app.data

import com.google.firebase.database.FirebaseDatabase
import com.triangle.app.data.models.AppNotification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Notifications data layer for the native Notifications screen (Milestone
 * 5). `organizations/{orgId}/data/notifications/{uid}` is a full per-user
 * ARRAY (script.js's pushInternNotif()/markAllNotifsRead(), script.js:4699-
 * 4832 always PUT the whole array) — full-array read-mutate-writeback here
 * too, same as Rewards' `rewards`/`redemptions` exception, but lower risk:
 * this path is already scoped to ONE user's own list, so there's no
 * cross-user contention the way Rewards' shared catalog had.
 */
object NotificationRepository {
    private val db get() = FirebaseDatabase.getInstance(TriangleConfig.FIREBASE_URL)
    private const val NOTIF_MAX_PER_INBOX = 200

    private fun notifRef(orgId: String, uid: String) =
        db.getReference("organizations/$orgId/data/notifications/$uid")

    fun notificationsFlow(orgId: String, uid: String): Flow<List<AppNotification>> =
        notifRef(orgId, uid).valueFlow().map { snap ->
            anyToMapList(snap.value).mapNotNull { AppNotification.fromMap(it) }.sortedByDescending { it.createdAt }
        }

    suspend fun markAllRead(orgId: String, uid: String) {
        val current = anyToMapList(notifRef(orgId, uid).get().await().value).mapNotNull { AppNotification.fromMap(it) }
        notifRef(orgId, uid).setValue(current.map { it.copy(read = true).toMap() }).await()
    }

    suspend fun markRead(orgId: String, uid: String, notifId: String) {
        val current = anyToMapList(notifRef(orgId, uid).get().await().value).mapNotNull { AppNotification.fromMap(it) }
        val updated = current.map { if (it.id == notifId) it.copy(read = true) else it }
        notifRef(orgId, uid).setValue(updated.map { it.toMap() }).await()
    }

    /**
     * Faithful port of _dmNotifyRecipient()'s cross-org branch
     * (script.js:1170-1182): the recipient's own org is looked up (it's
     * NOT the sender's org — every account has its own dynamic org, unlike
     * the fixed DM_ORG_ID DM data itself lives under), then their
     * notification array is read-unshifted-capped-written.
     */
    suspend fun notifyDmMessage(toUid: String, fromUid: String, fromName: String, preview: String) {
        val recipientOrgId = (db.getReference("users/$toUid/orgId").get().await().value as? String) ?: return
        val ref = notifRef(recipientOrgId, toUid)
        val current = anyToMapList(ref.get().await().value).mapNotNull { AppNotification.fromMap(it) }
        val entry = AppNotification(
            id = "n-${System.currentTimeMillis()}-${(('a'..'z') + ('0'..'9')).shuffled().take(5).joinToString("")}",
            type = "chat_message",
            title = fromName,
            body = preview,
            read = false,
            createdAt = System.currentTimeMillis(),
            otherUserId = fromUid
        )
        val updated = (listOf(entry) + current).take(NOTIF_MAX_PER_INBOX)
        ref.setValue(updated.map { it.toMap() }).await()
        PushQueue.enqueue("intern", toUid, entry.id, entry.type, entry.title, entry.body)
    }
}
