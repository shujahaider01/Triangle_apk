package com.triangle.app.data.models

/**
 * Native model of a `dmData/orgs/{DM_ORG_ID}/userThreads/{uid}/{threadId}`
 * entry (see script.js:1137-1146) — one row in the DM inbox list.
 * `otherUserName` is resolved at read time via a UserRepository lookup, not
 * stored on this node (source doesn't store it either — the WebView looks
 * up the other participant's name from its own in-memory roster/cache).
 */
data class DmThreadSummary(
    val threadId: String,
    val otherUserId: String,
    val lastMessage: String,
    val lastMessageAt: Long,
    val unreadCount: Int,
    val otherUserName: String = ""
) {
    companion object {
        fun fromMap(threadId: String, m: Map<*, *>): DmThreadSummary? {
            val otherUserId = m["otherUserId"]?.toString() ?: return null
            return DmThreadSummary(
                threadId = threadId,
                otherUserId = otherUserId,
                lastMessage = m["lastMessage"] as? String ?: "",
                lastMessageAt = (m["lastMessageAt"] as? Number)?.toLong() ?: 0L,
                unreadCount = (m["unreadCount"] as? Number)?.toInt() ?: 0
            )
        }
    }
}

/** Native model of `dmData/orgs/{DM_ORG_ID}/dmThreads/{threadId}` (script.js:1066-1071, 1132-1134). */
data class DmThreadMeta(
    val threadId: String,
    val participantIds: List<String>,
    val lastMessage: String,
    val lastMessageAt: Long,
    val lastSenderId: String?,
    val blockedBy: Set<String> = emptySet()
) {
    fun isBlockedFor(myUid: String, otherUid: String): Boolean =
        blockedBy.contains(myUid) || blockedBy.contains(otherUid)

    companion object {
        fun fromMap(threadId: String, m: Map<*, *>): DmThreadMeta {
            @Suppress("UNCHECKED_CAST")
            val participantIds = (m["participantIds"] as? List<*>)?.map { it.toString() } ?: emptyList()
            val blockedByMap = m["blockedBy"] as? Map<*, *>
            val blockedBy = blockedByMap?.entries?.filter { it.value == true }?.map { it.key.toString() }?.toSet() ?: emptySet()
            return DmThreadMeta(
                threadId = threadId,
                participantIds = participantIds,
                lastMessage = m["lastMessage"] as? String ?: "",
                lastMessageAt = (m["lastMessageAt"] as? Number)?.toLong() ?: 0L,
                lastSenderId = m["lastSenderId"]?.toString(),
                blockedBy = blockedBy
            )
        }
    }
}
