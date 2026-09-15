package com.triangle.app.data.models

/**
 * Native model of a `dmData/orgs/{DM_ORG_ID}/dmMessages/{threadId}/{msgId}`
 * entry (see script.js's sendDmMessage(), script.js:1115-1155). `id` is a
 * Firebase push key, chronologically sortable, matching source.
 */
data class DmMessage(
    val id: String,
    val senderId: String,
    val text: String,
    val createdAt: Long
) {
    fun toMap(): Map<String, Any?> = mapOf("senderId" to senderId, "text" to text, "createdAt" to createdAt)

    companion object {
        fun fromMap(id: String, m: Map<*, *>): DmMessage? {
            val senderId = m["senderId"]?.toString() ?: return null
            val text = m["text"] as? String ?: return null
            return DmMessage(id, senderId, text, (m["createdAt"] as? Number)?.toLong() ?: 0L)
        }
    }
}
