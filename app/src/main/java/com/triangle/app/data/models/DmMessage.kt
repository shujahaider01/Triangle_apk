package com.triangle.app.data.models

/**
 * Native model of a `dmData/orgs/{DM_ORG_ID}/dmMessages/{threadId}/{msgId}`
 * entry (see script.js's sendDmMessage(), script.js:1115-1155). `id` is a
 * Firebase push key, chronologically sortable, matching source.
 * A reply carries the quoted message's author and text ([replyToName]/[replyToText]).
 */
data class DmMessage(
    val id: String,
    val senderId: String,
    val text: String,
    val createdAt: Long,
    val replyToName: String? = null,
    val replyToText: String? = null,
    val replyToId: String? = null,
    val mediaUrl: String? = null,
    val mediaType: String? = null,
    val thumbUrl: String? = null,
    val durationMs: Long? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "senderId" to senderId, "text" to text, "createdAt" to createdAt,
        "replyToName" to replyToName, "replyToText" to replyToText, "replyToId" to replyToId,
        "mediaUrl" to mediaUrl, "mediaType" to mediaType, "thumbUrl" to thumbUrl, "durationMs" to durationMs
    )

    companion object {
        fun fromMap(id: String, m: Map<*, *>): DmMessage? {
            val senderId = m["senderId"]?.toString() ?: return null
            val text = m["text"] as? String ?: return null
            return DmMessage(
                id, senderId, text, (m["createdAt"] as? Number)?.toLong() ?: 0L,
                replyToName = m["replyToName"] as? String,
                replyToText = m["replyToText"] as? String,
                replyToId = m["replyToId"] as? String,
                mediaUrl = m["mediaUrl"] as? String,
                mediaType = m["mediaType"] as? String,
                thumbUrl = m["thumbUrl"] as? String,
                durationMs = (m["durationMs"] as? Number)?.toLong()
            )
        }
    }
}
