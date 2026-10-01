package com.triangle.app.data

import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Backend for the "Connect by email" flow (circle/ConnectEmailScreen.kt) —
 * see the approved plan at .claude/plans/expressive-munching-karp.md.
 * Lives under the fixed shared TriangleConfig.SOCIAL_ROOT, same reasoning as
 * ConnectionRepository's connections/connectionRequests: two connected
 * people are in two different, mutually-invisible per-account orgs.
 */
object EmailInviteRepository {
    private fun social() = FirebaseDatabase.getInstance().getReference(TriangleConfig.SOCIAL_ROOT)

    sealed class InviteResult {
        object SentToExistingUser : InviteResult()
        object EmailInviteSent : InviteResult()
        object AlreadyYourEmail : InviteResult()
    }

    // Firebase Realtime DB keys can't contain ".", "#", "$", "[", "]" — same escaping as UserRepository's otpKey().
    private fun emailKey(email: String): String =
        email.trim().lowercase().replace(Regex("[.#$\\[\\]]"), ",")

    private fun randomToken(): String =
        (('a'..'z') + ('A'..'Z') + ('0'..'9')).shuffled().take(24).joinToString("")

    /**
     * Enter an email address. If it matches an existing user, this is just a
     * username invite by another name (in-app notification), plus a backup
     * email. Otherwise it stores a pending invite keyed by email and sends a
     * real invite email — see consumePendingInvites() for the other half.
     */
    suspend fun sendInvite(fromUid: String, fromName: String, fromUsername: String, toEmail: String): InviteResult {
        val existing = UserRepository.findByEmail(toEmail)
        if (existing != null) {
            if (existing.uid == fromUid) return InviteResult.AlreadyYourEmail
            ConnectionRepository.sendRequest(fromUid, fromName, fromUsername, existing.uid)
            runCatching { EmailJsClient.sendInviteEmail(toEmail = toEmail, inviterName = fromName) }
            return InviteResult.SentToExistingUser
        }

        val token = randomToken()
        social().child("pendingEmailInvites/${emailKey(toEmail)}/$fromUid").setValue(
            mapOf(
                "fromName" to fromName,
                "fromUsername" to fromUsername,
                "toEmail" to toEmail,
                "token" to token,
                "createdAt" to System.currentTimeMillis()
            )
        ).await()
        EmailJsClient.sendInviteEmail(toEmail = toEmail, inviterName = fromName)
        return InviteResult.EmailInviteSent
    }

    /**
     * Called once, right after a brand-new account is created (see
     * AuthViewModel.routeAfterIdentity) — checks pending invites by this
     * account's own email and auto-connects with everyone who invited it,
     * since the inviter already expressed consent by inviting.
     */
    suspend fun consumePendingInvites(newUid: String, newName: String, email: String) {
        val ref = social().child("pendingEmailInvites/${emailKey(email)}")
        val snap = ref.get().await()
        if (!snap.exists()) return
        for (child in snap.children) {
            val fromUid = child.key ?: continue
            ConnectionRepository.connectDirectly(newUid, fromUid)
            runCatching { NotificationRepository.notifyConnectionAccepted(fromUid, newUid, newName) }
        }
        ref.removeValue().await()
    }
}
