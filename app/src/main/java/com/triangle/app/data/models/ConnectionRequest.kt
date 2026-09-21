package com.triangle.app.data.models

/** A pending outgoing/incoming connection request — see data/ConnectionRepository.kt. */
data class ConnectionRequest(
    val fromUid: String,
    val fromName: String,
    val fromUsername: String,
    val createdAt: Long
) {
    fun toMap(): Map<String, Any?> = mapOf("fromName" to fromName, "fromUsername" to fromUsername, "createdAt" to createdAt)

    companion object {
        fun fromMap(fromUid: String, m: Map<*, *>): ConnectionRequest = ConnectionRequest(
            fromUid = fromUid,
            fromName = m["fromName"] as? String ?: "",
            fromUsername = m["fromUsername"] as? String ?: "",
            createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L
        )
    }
}
