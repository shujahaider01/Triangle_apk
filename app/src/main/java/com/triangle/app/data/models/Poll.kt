package com.triangle.app.data.models

/**
 * A poll sent to people in the creator's Circle and shown in their Inbox.
 * Stored once at social/polls/{id}; each person's vote lives at
 * social/pollVotes/{id}/{voterUid} as the list of option indexes they picked.
 */
data class Poll(
    val id: String,
    val creatorUid: String,
    val creatorName: String,
    val creatorPhotoUrl: String? = null,
    val question: String,
    val options: List<String>,
    /** True = a voter may pick several options; false = exactly one. */
    val multiple: Boolean = false,
    /** True = recipients can open the voter list too; false = only the creator can. */
    val showVoters: Boolean = false,
    val closed: Boolean = false,
    /** Optional deadline (epoch millis): voting stops by itself at this time. */
    val closesAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** recipientUid -> that person's orgId (where their notification row lives). */
    val recipients: Map<String, String> = emptyMap(),
    val recipientNames: Map<String, String> = emptyMap()
) {
    /** Closed by the creator, or past its closing time. */
    fun isClosedAt(now: Long): Boolean = closed || (closesAt != null && now >= closesAt)

    fun toMap(): Map<String, Any?> = mapOf(
        "creatorUid" to creatorUid, "creatorName" to creatorName, "creatorPhotoUrl" to creatorPhotoUrl,
        "question" to question, "options" to options, "multiple" to multiple, "showVoters" to showVoters, "closed" to closed, "closesAt" to closesAt,
        "createdAt" to createdAt, "recipients" to recipients, "recipientNames" to recipientNames
    )

    companion object {
        const val QUESTION_MAX = 200
        const val OPTION_MAX = 80
        const val MIN_OPTIONS = 2
        const val MAX_OPTIONS = 6

        fun fromMap(id: String, m: Map<*, *>): Poll? {
            val question = m["question"] as? String ?: return null
            val rawOptions = m["options"]
            val options = when (rawOptions) {
                is List<*> -> rawOptions.mapNotNull { it?.toString() }
                is Map<*, *> -> rawOptions.entries.sortedBy { it.key.toString().toIntOrNull() ?: Int.MAX_VALUE }.mapNotNull { it.value?.toString() }
                else -> emptyList()
            }
            if (options.size < MIN_OPTIONS) return null
            return Poll(
                id = id,
                creatorUid = m["creatorUid"]?.toString() ?: "",
                creatorName = m["creatorName"] as? String ?: "Someone",
                creatorPhotoUrl = m["creatorPhotoUrl"] as? String,
                question = question,
                options = options,
                multiple = m["multiple"] == true,
                showVoters = m["showVoters"] == true,
                closed = m["closed"] == true,
                closesAt = (m["closesAt"] as? Number)?.toLong(),
                createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
                recipients = (m["recipients"] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value.toString() } ?: emptyMap(),
                recipientNames = (m["recipientNames"] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value.toString() } ?: emptyMap()
            )
        }
    }
}
