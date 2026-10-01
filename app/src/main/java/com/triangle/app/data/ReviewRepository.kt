package com.triangle.app.data

import com.google.firebase.database.FirebaseDatabase
import com.triangle.app.data.models.Review
import com.triangle.app.data.models.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Reviews of completed assigned tasks. Lives under the shared social/ root:
 *  - reviews/{recipientUid}/{taskId}             -> the review itself
 *  - reviewsGiven/{reviewerUid}/{taskId}_{recipientUid} -> true (lets the assigner
 *    find which completed tasks they have not reviewed yet without scanning every recipient)
 * Reviews can only be created: once written they cannot be edited or deleted.
 */
object ReviewRepository {
    private fun db() = FirebaseDatabase.getInstance()
    private fun social() = db().getReference(TriangleConfig.SOCIAL_ROOT)

    data class Summary(val average: Float, val count: Int, val excellent: Int, val good: Int, val average5: Int, val belowAverage: Int, val poor: Int) {
        companion object {
            val EMPTY = Summary(0f, 0, 0, 0, 0, 0, 0)
        }
    }

    fun summarize(reviews: List<Review>): Summary {
        if (reviews.isEmpty()) return Summary.EMPTY
        return Summary(
            average = reviews.sumOf { it.stars }.toFloat() / reviews.size,
            count = reviews.size,
            excellent = reviews.count { it.stars >= 5 },
            good = reviews.count { it.stars == 4 },
            average5 = reviews.count { it.stars == 3 },
            belowAverage = reviews.count { it.stars == 2 },
            poor = reviews.count { it.stars <= 1 }
        )
    }

    fun receivedFlow(uid: String): Flow<List<Review>> =
        social().child("reviews/$uid").valueFlow().map { snap ->
            (snap.value as? Map<*, *>)?.values
                ?.mapNotNull { (it as? Map<*, *>)?.let(Review::fromMap) }
                ?.sortedByDescending { it.createdAt }
                ?: emptyList()
        }

    suspend fun received(uid: String): List<Review> = runCatching { receivedFlow(uid).first() }.getOrDefault(emptyList())

    /** "taskId_recipientUid" keys this reviewer has already reviewed. */
    suspend fun givenKeys(reviewerUid: String): Set<String> =
        social().child("reviewsGiven/$reviewerUid").get().await().children.mapNotNull { it.key }.toSet()

    suspend fun exists(recipientUid: String, taskId: String): Boolean =
        social().child("reviews/$recipientUid/$taskId").get().await().exists()

    private suspend fun orgIdFor(uid: String): String? =
        db().getReference("users/$uid/orgId").get().await().value as? String

    /** The recipient's own copy of the task (it lives in their org, not the assigner's). */
    suspend fun loadTask(recipientUid: String, taskId: String): Task? {
        val orgId = orgIdFor(recipientUid) ?: return null
        return TaskRepository.getTask(orgId, taskId)
    }

    sealed class Result {
        object Sent : Result()
        object AlreadyReviewed : Result()
        object NotAllowed : Result()
        class Failed(val message: String) : Result()
    }

    /**
     * Writes a review if (and only if) the reviewer assigned this task, the recipient
     * has completed it, and it has not been reviewed yet. Then notifies the recipient.
     */
    suspend fun submit(review: Review): Result {
        return try {
            val orgId = orgIdFor(review.recipientUid) ?: return Result.Failed("Recipient not found")
            val task = TaskRepository.getTask(orgId, review.taskId) ?: return Result.NotAllowed
            if (task.createdBy != review.reviewerUid) return Result.NotAllowed
            val done = TaskRepository.completionsFlow(orgId).first().contains("${review.taskId}-${review.recipientUid}")
            if (!done) return Result.NotAllowed
            if (exists(review.recipientUid, review.taskId)) return Result.AlreadyReviewed

            social().updateChildren(
                mapOf(
                    "reviews/${review.recipientUid}/${review.taskId}" to review.toMap(),
                    "reviewsGiven/${review.reviewerUid}/${review.taskId}_${review.recipientUid}" to true
                )
            ).await()
            runCatching {
                NotificationRepository.notifyReviewReceived(
                    review.recipientUid, review.reviewerUid, review.reviewerName, review.stars, review.taskTitle, review.taskId
                )
            }
            Result.Sent
        } catch (e: Exception) {
            Result.Failed(e.message ?: "Could not send the review")
        }
    }
}
