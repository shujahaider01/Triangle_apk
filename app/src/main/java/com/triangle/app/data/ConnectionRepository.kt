package com.triangle.app.data

import com.google.firebase.database.FirebaseDatabase
import com.triangle.app.data.models.ConnectionRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Connections/Circle data layer for the Connect->Assign->Complete->Earn
 * model (see the approved plan at .claude/plans/enchanted-brewing-beacon.md)
 * — lives under the fixed shared TriangleConfig.SOCIAL_ROOT, same reasoning
 * as DmRepository's DM_ORG_ID: two connected people are in two different,
 * mutually-invisible per-account orgs, so their connection state can't live
 * in either one.
 */
object ConnectionRepository {
    private fun social() = FirebaseDatabase.getInstance().getReference(TriangleConfig.SOCIAL_ROOT)

    /** uids of everyone the given uid is connected with — their Circle. */
    fun circleFlow(uid: String): Flow<List<String>> =
        social().child("connections/$uid").valueFlow().map { snap ->
            (snap.value as? Map<*, *>)?.keys?.map { it.toString() } ?: emptyList()
        }

    fun incomingRequestsFlow(uid: String): Flow<List<ConnectionRequest>> =
        social().child("connectionRequests/$uid").valueFlow().map { snap ->
            snap.children.mapNotNull { child ->
                val fromUid = child.key ?: return@mapNotNull null
                (child.value as? Map<*, *>)?.let { ConnectionRequest.fromMap(fromUid, it) }
            }.sortedByDescending { it.createdAt }
        }

    suspend fun isConnected(myUid: String, otherUid: String): Boolean =
        social().child("connections/$myUid/$otherUid").get().await().value == true

    suspend fun hasPendingOutgoing(myUid: String, otherUid: String): Boolean =
        social().child("connectionRequests/$otherUid/$myUid").get().await().exists()

    suspend fun hasPendingIncoming(myUid: String, otherUid: String): Boolean =
        social().child("connectionRequests/$myUid/$otherUid").get().await().exists()

    /** Keyed by sender uid, so sending twice just overwrites the same pending entry — no duplicate requests possible. */
    suspend fun sendRequest(fromUid: String, fromName: String, fromUsername: String, toUid: String) {
        val entry = ConnectionRequest(fromUid, fromName, fromUsername, System.currentTimeMillis())
        social().child("connectionRequests/$toUid/$fromUid").setValue(entry.toMap()).await()
        runCatching { NotificationRepository.notifyConnectionRequest(toUid, fromUid, fromName, fromUsername) }
    }

    /** Withdraws a request I sent (before it's been accepted/declined). */
    suspend fun cancelRequest(fromUid: String, toUid: String) {
        social().child("connectionRequests/$toUid/$fromUid").removeValue().await()
    }

    suspend fun declineRequest(myUid: String, fromUid: String) {
        social().child("connectionRequests/$myUid/$fromUid").removeValue().await()
    }

    /**
     * Symmetric connect (both sides can now assign Tasks/Habits to each
     * other) + clears the pending request, in one atomic multi-path write —
     * same reasoning as DmRepository.sendMessage()'s atomic update over the
     * source's sequential-REST-call equivalent.
     */
    suspend fun acceptRequest(myUid: String, myName: String, fromUid: String) {
        val updates: Map<String, Any?> = mapOf(
            "connections/$myUid/$fromUid" to true,
            "connections/$fromUid/$myUid" to true,
            "connectionRequests/$myUid/$fromUid" to null
        )
        social().updateChildren(updates).await()
        runCatching { NotificationRepository.notifyConnectionAccepted(fromUid, myUid, myName) }
    }

    /**
     * Directly connects both sides with no request/accept dance — used when
     * consent was already given some other way (e.g. EmailInviteRepository:
     * the inviter already expressed consent by inviting that email).
     */
    suspend fun connectDirectly(uidA: String, uidB: String) {
        val updates: Map<String, Any?> = mapOf(
            "connections/$uidA/$uidB" to true,
            "connections/$uidB/$uidA" to true
        )
        social().updateChildren(updates).await()
    }

    /**
     * Per-member permission controlling whether `memberUid` is allowed to
     * assign Tasks/Habits to `ownerUid` — set by ownerUid from their Members
     * screen (see circle/MemberPermissionsSheet.kt). Absent means allowed
     * (default true), so existing connections need no migration write.
     */
    fun assignPermissionsFlow(ownerUid: String): Flow<Map<String, Boolean>> =
        social().child("assignPermissions/$ownerUid").valueFlow().map { snap ->
            snap.children.mapNotNull { child ->
                val uid = child.key ?: return@mapNotNull null
                val allowed = child.getValue(Boolean::class.java) ?: true
                uid to allowed
            }.toMap()
        }

    suspend fun canAssign(ownerUid: String, memberUid: String): Boolean =
        social().child("assignPermissions/$ownerUid/$memberUid").get().await().getValue(Boolean::class.java) ?: true

    suspend fun setAssignPermission(ownerUid: String, memberUid: String, canAssign: Boolean) {
        social().child("assignPermissions/$ownerUid/$memberUid").setValue(canAssign).await()
    }

    /** Symmetric disconnect — removes both sides' connection entries plus my own permission entry for them. */
    suspend fun removeConnection(myUid: String, otherUid: String) {
        val updates: Map<String, Any?> = mapOf(
            "connections/$myUid/$otherUid" to null,
            "connections/$otherUid/$myUid" to null,
            "assignPermissions/$myUid/$otherUid" to null
        )
        social().updateChildren(updates).await()
    }
}
