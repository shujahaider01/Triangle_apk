package com.triangle.app.data

import com.google.firebase.database.FirebaseDatabase
import com.triangle.app.data.models.DmMessage
import com.triangle.app.data.models.DmThreadMeta
import com.triangle.app.data.models.DmThreadSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * DM data layer for the native DM screens (Milestone 5) — mirrors
 * script.js's DIRECT MESSAGING section (script.js:986 onward). Built as its
 * own top-level Firebase path, deliberately outside any per-account org
 * blob, exactly like the source.
 *
 * IMPORTANT: every path here is rooted at `dmData/orgs/{TriangleConfig.
 * DM_ORG_ID}` — the FIXED shared constant `"org1"`, never `session.orgId`.
 * script.js's own `DM_ORG_ID` (script.js:1011) resolves to that same fixed
 * literal regardless of which dynamic org the signed-in user's tasks
 * actually live under; every account's DM data lives in this one
 * shared subtree. Using `session.orgId` here would put each account's DMs
 * in a different, mutually-invisible bucket and silently break
 * cross-account messaging.
 */
object DmRepository {
    private fun dmData() =
        FirebaseDatabase.getInstance().getReference("dmData/orgs/${TriangleConfig.DM_ORG_ID}")

    sealed class SendResult {
        object Success : SendResult()
        object Blocked : SendResult()
    }

    /** Deterministic thread id — matches script.js's _dmThreadId (script.js:1021-1023). */
    fun threadId(a: String, b: String): String = listOf(a, b).sorted().joinToString("_")

    fun userThreadsFlow(uid: String): Flow<List<DmThreadSummary>> =
        dmData().child("userThreads/$uid").valueFlow().map { snap ->
            snap.children.mapNotNull { child ->
                (child.value as? Map<*, *>)?.let { DmThreadSummary.fromMap(child.key ?: return@let null, it) }
            }.sortedByDescending { it.lastMessageAt }
        }

    fun messagesFlow(threadId: String): Flow<List<DmMessage>> =
        dmData().child("dmMessages/$threadId").valueFlow().map { snap ->
            snap.children.mapNotNull { child ->
                (child.value as? Map<*, *>)?.let { DmMessage.fromMap(child.key ?: return@let null, it) }
            }.sortedBy { it.createdAt }
        }

    fun threadFlow(threadId: String): Flow<DmThreadMeta?> =
        dmData().child("dmThreads/$threadId").valueFlow().map { snap ->
            (snap.value as? Map<*, *>)?.let { DmThreadMeta.fromMap(threadId, it) }
        }

    /**
     * Sends a message and updates the thread + both participants' inbox
     * rows in ONE atomic multi-path write. A deliberate improvement over
     * script.js's sendDmMessage() (script.js:1115-1155), which does this as
     * four separate sequential REST calls with no atomicity — a dropped
     * connection mid-sequence there can leave dmMessages written with
     * stale userThreads indexes. The Firebase Android SDK makes the atomic
     * version just as easy to write, so there's no reason to faithfully
     * reproduce the non-atomic version.
     */
    suspend fun sendMessage(myUid: String, myName: String, otherUid: String, text: String): SendResult {
        val threadId = threadId(myUid, otherUid)
        val root = dmData()

        val threadSnap = root.child("dmThreads/$threadId").get().await()
        val existingThread = (threadSnap.value as? Map<*, *>)?.let { DmThreadMeta.fromMap(threadId, it) }
        if (existingThread?.isBlockedFor(myUid, otherUid) == true) return SendResult.Blocked

        val pushKey = root.child("dmMessages/$threadId").push().key
            ?: throw IllegalStateException("Could not allocate a DM message id")
        val now = System.currentTimeMillis()
        val otherUnread = (root.child("userThreads/$otherUid/$threadId/unreadCount").get().await().value as? Number)?.toInt() ?: 0

        val updates: Map<String, Any?> = mapOf(
            "dmMessages/$threadId/$pushKey" to mapOf("senderId" to myUid, "text" to text, "createdAt" to now),
            "dmThreads/$threadId/participantIds" to listOf(myUid, otherUid).sorted(),
            "dmThreads/$threadId/lastMessage" to text,
            "dmThreads/$threadId/lastMessageAt" to now,
            "dmThreads/$threadId/lastSenderId" to myUid,
            "userThreads/$myUid/$threadId/otherUserId" to otherUid,
            "userThreads/$myUid/$threadId/lastMessage" to text,
            "userThreads/$myUid/$threadId/lastMessageAt" to now,
            "userThreads/$otherUid/$threadId/otherUserId" to myUid,
            "userThreads/$otherUid/$threadId/lastMessage" to text,
            "userThreads/$otherUid/$threadId/lastMessageAt" to now,
            "userThreads/$otherUid/$threadId/unreadCount" to otherUnread + 1
        )
        root.updateChildren(updates).await()

        runCatching { NotificationRepository.notifyDmMessage(otherUid, myUid, myName, text, messageId = pushKey) }
        return SendResult.Success
    }

    /** Matches markDmThreadRead() (script.js:1187-1190) — narrow write, own unread count only. */
    suspend fun markThreadRead(myUid: String, threadId: String) {
        dmData().child("userThreads/$myUid/$threadId/unreadCount").setValue(0).await()
    }

    /** Matches toggleDmBlock() (script.js:1098-1109) — narrow write to this user's own blockedBy flag. */
    suspend fun setBlocked(threadId: String, myUid: String, blocked: Boolean) {
        val ref = dmData().child("dmThreads/$threadId/blockedBy/$myUid")
        if (blocked) ref.setValue(true).await() else ref.removeValue().await()
    }
}
