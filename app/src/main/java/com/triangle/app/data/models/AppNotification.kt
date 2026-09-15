package com.triangle.app.data.models

/**
 * Native model of an `organizations/{orgId}/data/notifications/{uid}[]`
 * entry (see script.js's pushInternNotif()/_dmNotifyRecipient(),
 * script.js:4699-4713, 1167-1183). `otherUserId` is only meaningful for
 * `type:"chat_message"` (the DM thread's other participant).
 */
data class AppNotification(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val read: Boolean,
    val createdAt: Long,
    val otherUserId: String? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id, "type" to type, "title" to title, "body" to body,
        "read" to read, "createdAt" to createdAt, "otherUserId" to otherUserId
    )

    companion object {
        fun fromMap(m: Map<*, *>): AppNotification? {
            val id = m["id"]?.toString() ?: return null
            val type = m["type"] as? String ?: return null
            return AppNotification(
                id = id,
                type = type,
                title = m["title"] as? String ?: "",
                body = m["body"] as? String ?: "",
                read = m["read"] == true,
                createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
                otherUserId = m["otherUserId"]?.toString()
            )
        }
    }
}
