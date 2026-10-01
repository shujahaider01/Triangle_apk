package com.triangle.app.data

import com.google.firebase.database.FirebaseDatabase
import com.triangle.app.data.models.Poll
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Polls in the Inbox. Like announcements, the poll is stored ONCE (social/polls/{id}) and each
 * recipient's notification row only carries the question + that id. Votes are separate
 * (social/pollVotes/{id}/{voterUid} -> [option indexes]) so voting never rewrites the poll.
 */
object PollRepository {
    private fun db() = FirebaseDatabase.getInstance()
    private fun social() = db().getReference(TriangleConfig.SOCIAL_ROOT)

    private suspend fun orgIdFor(uid: String): String? =
        db().getReference("users/$uid/orgId").get().await().value as? String

    data class SendResult(val pollId: String, val sent: Int, val skippedBlocked: Int)

    data class SentSummary(val id: String, val question: String, val createdAt: Long)

    /** Creates the poll and notifies every recipient who hasn't blocked inbox messages from the creator. */
    suspend fun create(
        creator: SessionStore.Session,
        question: String,
        options: List<String>,
        multiple: Boolean,
        showVoters: Boolean,
        closesAt: Long?,
        recipientUids: Set<String>,
        recipientNames: Map<String, String>
    ): SendResult = coroutineScope {
        val id = social().child("polls").push().key ?: throw IllegalStateException("Could not allocate a poll id")
        val resolved = recipientUids.filter { it != creator.uid }.map { uid ->
            async {
                val allowed = ConnectionRepository.canAnnounce(uid, creator.uid)
                uid to (if (allowed) orgIdFor(uid) ?: "" else null)
            }
        }.awaitAll()
        val deliverable = resolved.filter { !it.second.isNullOrEmpty() }.associate { it.first to it.second!! }
        val skipped = resolved.count { it.second == null }
        if (deliverable.isEmpty()) return@coroutineScope SendResult(id, 0, skipped)

        val poll = Poll(
            id = id, creatorUid = creator.uid, creatorName = creator.name, creatorPhotoUrl = creator.photoUrl,
            question = question.trim(), options = options.map { it.trim() }.filter { it.isNotEmpty() },
            multiple = multiple, showVoters = showVoters, closesAt = closesAt, recipients = deliverable,
            recipientNames = recipientNames.filterKeys { it in deliverable }
        )
        social().updateChildren(
            mapOf(
                "polls/$id" to poll.toMap(),
                "pollIndex/${creator.uid}/$id" to mapOf("question" to poll.question, "createdAt" to poll.createdAt)
            )
        ).await()
        deliverable.keys.map { uid ->
            async { runCatching { NotificationRepository.notifyPoll(uid, creator.uid, creator.name, poll.question, id) } }
        }.awaitAll()
        SendResult(id, deliverable.size, skipped)
    }

    fun pollFlow(id: String): Flow<Poll?> =
        social().child("polls/$id").valueFlow().map { snap -> (snap.value as? Map<*, *>)?.let { Poll.fromMap(id, it) } }

    fun sentFlow(creatorUid: String): Flow<List<SentSummary>> =
        social().child("pollIndex/$creatorUid").valueFlow().map { snap ->
            snap.children.mapNotNull { child ->
                val id = child.key ?: return@mapNotNull null
                val m = child.value as? Map<*, *> ?: return@mapNotNull null
                SentSummary(id, m["question"] as? String ?: "", (m["createdAt"] as? Number)?.toLong() ?: 0L)
            }.sortedByDescending { it.createdAt }
        }

    /** One person's vote: the option indexes they picked and when (0 for votes saved before times were recorded). */
    data class Vote(val picked: List<Int>, val at: Long)

    /** voterUid -> their vote, with the time. Reads both the current {picks, at} shape and the older plain list. */
    fun voteDetailsFlow(pollId: String): Flow<Map<String, Vote>> =
        social().child("pollVotes/$pollId").valueFlow().map { snap ->
            snap.children.mapNotNull { child ->
                val uid = child.key ?: return@mapNotNull null
                val v = child.value
                val vote = when {
                    v is Map<*, *> && v["picks"] != null -> {
                        val picks = when (val p = v["picks"]) {
                            is List<*> -> p.mapNotNull { (it as? Number)?.toInt() }
                            is Map<*, *> -> p.values.mapNotNull { (it as? Number)?.toInt() }
                            else -> emptyList()
                        }
                        Vote(picks, (v["at"] as? Number)?.toLong() ?: 0L)
                    }
                    v is List<*> -> Vote(v.mapNotNull { (it as? Number)?.toInt() }, 0L)
                    v is Map<*, *> -> Vote(v.values.mapNotNull { (it as? Number)?.toInt() }, 0L)
                    else -> Vote(emptyList(), 0L)
                }
                uid to vote
            }.toMap()
        }

    /** voterUid -> the option indexes they picked. */
    fun votesFlow(pollId: String): Flow<Map<String, List<Int>>> =
        voteDetailsFlow(pollId).map { all -> all.mapValues { it.value.picked } }

    /** Records (or replaces) [voterUid]'s vote while the poll is open. Returns false if it's closed or gone. */
    suspend fun vote(pollId: String, voterUid: String, picked: List<Int>): Boolean {
        val snap = social().child("polls/$pollId").get().await()
        val poll = (snap.value as? Map<*, *>)?.let { Poll.fromMap(pollId, it) } ?: return false
        if (poll.isClosedAt(System.currentTimeMillis())) return false
        val valid = picked.distinct().filter { it in poll.options.indices }.let { if (poll.multiple) it else it.take(1) }
        if (valid.isEmpty()) return false
        social().child("pollVotes/$pollId/$voterUid").setValue(mapOf("picks" to valid.sorted(), "at" to System.currentTimeMillis())).await()
        return true
    }

    /** Stops further voting (results stay visible). Only the creator may do this. */
    suspend fun close(creatorUid: String, pollId: String) {
        val snap = social().child("polls/$pollId").get().await()
        val poll = (snap.value as? Map<*, *>)?.let { Poll.fromMap(pollId, it) } ?: return
        if (poll.creatorUid != creatorUid) return
        social().child("polls/$pollId/closed").setValue(true).await()
    }

    /** Removes the poll, its votes and every recipient's notification row. */
    suspend fun delete(creatorUid: String, pollId: String) = coroutineScope {
        val snap = social().child("polls/$pollId").get().await()
        val poll = (snap.value as? Map<*, *>)?.let { Poll.fromMap(pollId, it) }
        if (poll != null && poll.creatorUid != creatorUid) return@coroutineScope
        poll?.recipients?.map { (uid, orgId) ->
            async { runCatching { NotificationRepository.removeByItemId(orgId, uid, "poll", pollId) } }
        }?.awaitAll()
        social().updateChildren(
            mapOf("polls/$pollId" to null, "pollVotes/$pollId" to null, "pollIndex/$creatorUid/$pollId" to null)
        ).await()
    }
}
