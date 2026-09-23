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
 * 4832 always PUT the whole array) — full-array read-mutate-writeback here,
 * same array-write pattern other per-user array paths in this app use, with
 * low cross-user contention risk since this path is already scoped to ONE
 * user's own list.
 */
object NotificationRepository {
    private val db get() = FirebaseDatabase.getInstance()
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
     * notification array is read-unshifted-capped-written — see push() below.
     */
    suspend fun notifyDmMessage(toUid: String, fromUid: String, fromName: String, preview: String, messageId: String? = null) {
        push(toUid, "chat_message", fromName, preview, otherUserId = fromUid, itemId = messageId)
    }

    /** One-shot lookup of a single notification record — resolves a tapped push's `notifId` to its type/itemId/otherUserId. */
    suspend fun getNotification(orgId: String, uid: String, notifId: String): AppNotification? =
        anyToMapList(notifRef(orgId, uid).get().await().value).mapNotNull { AppNotification.fromMap(it) }.find { it.id == notifId }

    private fun newNotifId(): String =
        "n-${System.currentTimeMillis()}-${(('a'..'z') + ('0'..'9')).shuffled().take(5).joinToString("")}"

    /** Shared cross-org push: look up the recipient's own (dynamic) orgId, then read-unshift-cap-write their notification array — see notifyDmMessage's doc comment for why the org lookup can't just use the sender's own orgId. */
    private suspend fun push(toUid: String, type: String, title: String, body: String, otherUserId: String? = null, itemId: String? = null) {
        val recipientOrgId = (db.getReference("users/$toUid/orgId").get().await().value as? String) ?: return
        val ref = notifRef(recipientOrgId, toUid)
        val current = anyToMapList(ref.get().await().value).mapNotNull { AppNotification.fromMap(it) }
        val entry = AppNotification(
            id = newNotifId(), type = type, title = title, body = body,
            read = false, createdAt = System.currentTimeMillis(), otherUserId = otherUserId, itemId = itemId
        )
        ref.setValue(((listOf(entry) + current).take(NOTIF_MAX_PER_INBOX)).map { it.toMap() }).await()
        PushQueue.enqueue("intern", toUid, entry.id, entry.type, entry.title, entry.body)
    }

    /** Someone sent a connection request — see ConnectionRepository.sendRequest(). */
    suspend fun notifyConnectionRequest(toUid: String, fromUid: String, fromName: String, fromUsername: String) {
        push(toUid, "connection_request", fromName, "@$fromUsername wants to connect with you", otherUserId = fromUid)
    }

    /** My connection request was accepted — see ConnectionRepository.acceptRequest(). */
    suspend fun notifyConnectionAccepted(toUid: String, fromUid: String, fromName: String) {
        push(toUid, "connection_accepted", fromName, "$fromName accepted your connection request", otherUserId = fromUid)
    }

    /** Someone in my Circle assigned me a task — see AssignmentRepository.assignTask(). */
    suspend fun notifyTaskAssigned(toUid: String, fromUid: String, fromName: String, taskTitle: String, taskId: String) {
        push(toUid, "task_assigned", fromName, "assigned you a task: $taskTitle", otherUserId = fromUid, itemId = taskId)
    }

    /** Someone announced to a group I'm in — the row only carries a preview; the full text/photos live at social/announcements/{id}. */
    suspend fun notifyAnnouncement(toUid: String, fromUid: String, fromName: String, title: String, preview: String, announcementId: String) {
        push(toUid, "announcement", title, "$fromName · $preview", otherUserId = fromUid, itemId = announcementId)
    }

    /** Drops one notification row (matched by type+itemId) from a recipient's array — used when an announcement is recalled. */
    suspend fun removeByItemId(orgId: String, uid: String, type: String, itemId: String) {
        val current = anyToMapList(notifRef(orgId, uid).get().await().value).mapNotNull { AppNotification.fromMap(it) }
        val updated = current.filterNot { it.type == type && it.itemId == itemId }
        if (updated.size != current.size) notifRef(orgId, uid).setValue(updated.map { it.toMap() }).await()
    }

    /** Rewrites the title/body of a recipient's existing row in place (no new push, read state kept) — used when an announcement is edited. */
    suspend fun updateByItemId(orgId: String, uid: String, type: String, itemId: String, title: String, body: String) {
        val current = anyToMapList(notifRef(orgId, uid).get().await().value).mapNotNull { AppNotification.fromMap(it) }
        var changed = false
        val updated = current.map {
            if (it.type == type && it.itemId == itemId) { changed = true; it.copy(title = title, body = body) } else it
        }
        if (changed) notifRef(orgId, uid).setValue(updated.map { it.toMap() }).await()
    }

    /** Someone in my Circle assigned me a habit — see AssignmentRepository.assignHabit(). */
    suspend fun notifyHabitAssigned(toUid: String, fromUid: String, fromName: String, habitName: String, habitId: String) {
        push(toUid, "habit_assigned", fromName, "assigned you a habit: $habitName", otherUserId = fromUid, itemId = habitId)
    }
}
