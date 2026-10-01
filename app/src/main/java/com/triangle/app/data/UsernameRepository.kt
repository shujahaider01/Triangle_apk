package com.triangle.app.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Unique-username index for the Connect->Assign->Complete->Earn model —
 * usernames are how people find each other to connect (see the Circle/
 * Connections milestone), so uniqueness has to be enforced server-side
 * with a real transaction (two people could otherwise both "win" the same
 * name from a plain read-then-write race), not just a client-side check.
 * Lives under TriangleConfig.SOCIAL_ROOT, a fixed shared path outside any
 * per-account org — same reasoning as DmRepository's DM_ORG_ID.
 */
object UsernameRepository {
    private fun db() = FirebaseDatabase.getInstance()
    private fun usernameRef(username: String) = db().getReference("${TriangleConfig.SOCIAL_ROOT}/usernames/${normalize(username)}")

    private val VALID_PATTERN = Regex("^[a-z0-9_]{3,20}$")

    fun normalize(raw: String): String = raw.trim().lowercase()

    /** Client-side format check only — actual uniqueness is only ever decided by the claim() transaction below. */
    fun formatError(raw: String): String? {
        val n = normalize(raw)
        return when {
            n.isBlank() -> "Choose a username"
            !VALID_PATTERN.matches(n) -> "3-20 characters — letters, numbers, and underscore only"
            else -> null
        }
    }

    /** One-shot availability check for live "is this taken?" UI feedback — not itself a reservation (claim() is). */
    suspend fun isAvailable(raw: String): Boolean = !usernameRef(raw).get().await().exists()

    /**
     * Exact single-key lookup — not a search. Used to resolve a typed
     * @username to a uid for sending a connection request, without ever
     * scanning or listing other accounts (see ConnectByUsernameScreen):
     * the caller can't distinguish "no such username" from "request sent"
     * in the UI, so this never needs to expose which usernames exist.
     */
    suspend fun resolveUid(rawUsername: String): String? {
        if (formatError(rawUsername) != null) return null
        return usernameRef(rawUsername).get().await().value as? String
    }

    sealed class ClaimResult {
        object Success : ClaimResult()
        object Taken : ClaimResult()
        data class Error(val message: String) : ClaimResult()
    }

    /**
     * Atomically reserves `social/usernames/{name} -> uid` (aborts if already
     * present — this is the actual uniqueness guarantee, not the isAvailable()
     * pre-check above, which only exists for UX) then narrow-writes the
     * username onto the account's own users/{uid} record. Only ever claims a
     * FIRST username for an account — changing an already-set username is
     * out of scope here (see the plan's deferred-details note).
     */
    suspend fun claim(uid: String, rawUsername: String): ClaimResult {
        formatError(rawUsername)?.let { return ClaimResult.Error(it) }
        val username = normalize(rawUsername)
        return try {
            val won = runReservationTransaction(usernameRef(username), uid)
            if (!won) return ClaimResult.Taken
            db().getReference("users/$uid/username").setValue(username).await()
            ClaimResult.Success
        } catch (e: Exception) {
            ClaimResult.Error(e.message ?: "Couldn't claim that username — try again")
        }
    }

    /**
     * Changes an already-set username — Settings' General section is the one
     * place this is allowed (claim() above stays first-claim-only for the
     * onboarding step). Reserves the new name with the same transaction as
     * claim(), then frees the old index entry so it becomes available again;
     * a no-op Success if the new value normalizes to the account's current
     * username (nothing to reserve or free).
     */
    suspend fun changeUsername(uid: String, currentUsername: String?, rawNewUsername: String): ClaimResult {
        formatError(rawNewUsername)?.let { return ClaimResult.Error(it) }
        val newUsername = normalize(rawNewUsername)
        if (newUsername == currentUsername) return ClaimResult.Success
        return try {
            val won = runReservationTransaction(usernameRef(newUsername), uid)
            if (!won) return ClaimResult.Taken
            db().getReference("users/$uid/username").setValue(newUsername).await()
            if (currentUsername != null) usernameRef(currentUsername).removeValue().await()
            ClaimResult.Success
        } catch (e: Exception) {
            ClaimResult.Error(e.message ?: "Couldn't change that username — try again")
        }
    }

    private suspend fun runReservationTransaction(ref: DatabaseReference, uid: String): Boolean =
        suspendCancellableCoroutine { cont ->
            ref.runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    if (currentData.value != null) return Transaction.abort()
                    currentData.value = uid
                    return Transaction.success(currentData)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    if (error != null) cont.resumeWithException(error.toException()) else cont.resume(committed)
                }
            })
        }
}
