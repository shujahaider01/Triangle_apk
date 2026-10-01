package com.triangle.app.data.models

/**
 * One review: the person who assigned a task rating (1..5 stars, optional
 * comment) the person who completed it. Immutable once written — there is no
 * edit or delete. Stored at social/reviews/{recipientUid}/{taskId}.
 */
data class Review(
    val taskId: String,
    val recipientUid: String,
    val reviewerUid: String,
    val reviewerName: String,
    val reviewerPhotoUrl: String? = null,
    val taskTitle: String,
    val stars: Int,
    val comment: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "taskId" to taskId, "recipientUid" to recipientUid, "reviewerUid" to reviewerUid,
        "reviewerName" to reviewerName, "reviewerPhotoUrl" to reviewerPhotoUrl, "taskTitle" to taskTitle,
        "stars" to stars, "comment" to comment, "createdAt" to createdAt
    )

    companion object {
        const val MAX_STARS = 5

        fun fromMap(m: Map<*, *>): Review? {
            val taskId = m["taskId"]?.toString() ?: return null
            val stars = (m["stars"] as? Number)?.toInt() ?: return null
            return Review(
                taskId = taskId,
                recipientUid = m["recipientUid"]?.toString() ?: "",
                reviewerUid = m["reviewerUid"]?.toString() ?: "",
                reviewerName = m["reviewerName"] as? String ?: "Someone",
                reviewerPhotoUrl = m["reviewerPhotoUrl"] as? String,
                taskTitle = m["taskTitle"] as? String ?: "",
                stars = stars.coerceIn(1, MAX_STARS),
                comment = m["comment"] as? String ?: "",
                createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L
            )
        }

        /** Short word for a star count, used under the rating stars. */
        fun label(stars: Int): String = when (stars) {
            0 -> "Tap a star to rate"
            1 -> "Poor"
            2 -> "Below average"
            3 -> "Average"
            4 -> "Good"
            else -> "Excellent" // 5
        }
    }
}
