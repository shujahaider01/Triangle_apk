package com.triangle.app.data

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Firebase Realtime Database access for the auth/onboarding flow — mirrors
 * script.js's root-level (_authRouteAfterIdentity, _authCreateIndividualAccount,
 * _nextOrgIconAndColor, _authSendEmailCode/_authOtpNext) calls, at the exact
 * same paths (users/{uid}, organizations/{id}, orgIconCycle, emailOtps/{key})
 * so accounts created here are fully compatible with the still-WebView-based
 * rest of the app, and vice versa.
 */
object UserRepository {
    private val db get() = FirebaseDatabase.getInstance(TriangleConfig.FIREBASE_URL)

    data class UserRecord(
        val uid: String,
        val name: String,
        val email: String,
        val role: String,
        val orgId: String,
        val photoUrl: String? = null,
        /** Null until the account completes the (mandatory) username-setup step — see UsernameRepository.claim(). */
        val username: String? = null
    )

    /** users/{uid} — null if this identity has never signed in before. */
    suspend fun fetchUserRecord(uid: String): UserRecord? {
        val snap = db.getReference("users/$uid").get().await()
        if (!snap.exists()) return null
        val orgId = snap.child("orgId").getValue(String::class.java) ?: return null
        return UserRecord(
            uid = uid,
            name = snap.child("name").getValue(String::class.java) ?: "",
            email = snap.child("email").getValue(String::class.java) ?: "",
            role = snap.child("role").getValue(String::class.java) ?: "individual",
            orgId = orgId,
            photoUrl = snap.child("photoUrl").getValue(String::class.java),
            username = snap.child("username").getValue(String::class.java)
        )
    }

    /** Narrow write of just the profile-photo URL — matches the app's narrow-sub-path-write convention. */
    suspend fun updateProfilePhoto(uid: String, photoUrl: String) {
        db.getReference("users/$uid/photoUrl").setValue(photoUrl).await()
    }

    /** Narrow write of just the display name — used by Settings' General section. */
    suspend fun updateName(uid: String, name: String) {
        db.getReference("users/$uid/name").setValue(name).await()
    }

    data class UserSearchResult(val uid: String, val name: String, val email: String, val username: String? = null, val photoUrl: String? = null)

    /**
     * Exact (case-insensitive) email lookup — same fetch-everything-and-
     * filter approach as searchUsers, used by EmailInviteRepository to tell
     * an invite-by-email apart from an invite-by-email-of-an-existing-user.
     */
    suspend fun findByEmail(email: String): UserRecord? {
        val target = email.trim().lowercase()
        if (target.isEmpty()) return null
        val snap = db.getReference("users").get().await()
        return snap.children.firstNotNullOfOrNull { child ->
            val uid = child.key ?: return@firstNotNullOfOrNull null
            val childEmail = child.child("email").getValue(String::class.java) ?: return@firstNotNullOfOrNull null
            if (childEmail.lowercase() != target) return@firstNotNullOfOrNull null
            val orgId = child.child("orgId").getValue(String::class.java) ?: return@firstNotNullOfOrNull null
            UserRecord(
                uid = uid,
                name = child.child("name").getValue(String::class.java) ?: "",
                email = childEmail,
                role = child.child("role").getValue(String::class.java) ?: "individual",
                orgId = orgId,
                photoUrl = child.child("photoUrl").getValue(String::class.java),
                username = child.child("username").getValue(String::class.java)
            )
        }
    }

    /** Faithful port of registerDeviceToken (script.js:928-941) — narrow write, no read-modify needed. */
    suspend fun registerDeviceToken(uid: String, role: String, token: String) {
        db.getReference("deviceTokens/$uid").setValue(
            mapOf("token" to token, "role" to role, "updatedAt" to System.currentTimeMillis())
        ).await()
    }

    /**
     * Native replacement for doChangePassword() (script.js:6168-6191) — that
     * function hashes the password with plain unsalted SHA-256 and stores it
     * readably at submissions/{uid}/passwordHash, with no real Firebase Auth
     * involvement at all. The native app already has a real Firebase Auth
     * account per user (created via createUserWithEmailAndPassword at
     * signup), so this uses the real Auth SDK flow instead of replicating
     * that insecure scheme: reauthenticate with the current password (Auth
     * requires a recent sign-in before allowing a password change), then
     * updatePassword. No RTDB field involved.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = runCatching {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: error("Not signed in")
        val email = user.email ?: error("Account has no email on file")
        user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).await()
        user.updatePassword(newPassword).await()
    }

    // Same 12-icon set script.js's ORG_ICONS uses. The color list below is a
    // representative subset of script.js's 129-entry AUTO_COLOR_CYCLE palette
    // (one shade per hue family) rather than a full port — cosmetic only,
    // and both sides still share/advance the same root `orgIconCycle`
    // cursor, so native- and WebView-created orgs keep interleaving sensibly.
    private val ORG_ICONS = listOf(
        "building-2", "building", "landmark", "briefcase", "graduation-cap",
        "globe", "rocket", "shield", "award", "layers", "flag", "star"
    )
    private val ORG_COLORS = listOf(
        "#F44336", "#FF9800", "#FFC107", "#8BC34A", "#4CAF50", "#009688",
        "#00BCD4", "#2196F3", "#3F51B5", "#673AB7", "#9C27B0", "#E91E63"
    )

    private suspend fun nextOrgIconAndColor(): Pair<String, String> {
        val ref = db.getReference("orgIconCycle")
        val snap = ref.get().await()
        val colorIdx = (snap.child("colorIdx").getValue(Long::class.java) ?: 0L).toInt()
        val iconIdx = (snap.child("iconIdx").getValue(Long::class.java) ?: 0L).toInt()
        val color = ORG_COLORS[((colorIdx % ORG_COLORS.size) + ORG_COLORS.size) % ORG_COLORS.size]
        val icon = ORG_ICONS[((iconIdx % ORG_ICONS.size) + ORG_ICONS.size) % ORG_ICONS.size]
        ref.setValue(
            mapOf(
                "colorIdx" to (colorIdx + 1) % ORG_COLORS.size,
                "iconIdx" to (iconIdx + 1) % ORG_ICONS.size
            )
        ).await()
        return color to icon
    }

    /**
     * Individual (one-person org) account creation — the only real signup
     * path today (see script.js's _authCreateIndividualAccount(); the
     * multi-org creation/join-request wizard its old comments describe was
     * never actually built). Creates organizations/{autoId} + users/{uid}
     * with the exact same field shapes, then returns the new UserRecord.
     */
    suspend fun createIndividualAccount(uid: String, name: String, email: String): UserRecord {
        val (color, icon) = nextOrgIconAndColor()
        val orgRef = db.getReference("organizations").push()
        val orgId = orgRef.key ?: error("Firebase did not return an organization id")
        val now = System.currentTimeMillis()
        val displayName = name.ifBlank { "My" }
        orgRef.setValue(
            mapOf(
                "name" to "$displayName's Space",
                "username" to null,
                "visibility" to "private",
                "type" to "individual",
                "adminId" to uid,
                "createdAt" to now,
                "status" to "Active",
                "icon" to icon,
                "color" to color
            )
        ).await()
        db.getReference("users/$uid").setValue(
            mapOf(
                "name" to name,
                "email" to email,
                "role" to "individual",
                "orgId" to orgId,
                "createdAt" to now
            )
        ).await()
        return UserRecord(uid, name, email, "individual", orgId)
    }

    // Firebase Realtime DB keys can't contain ".", "#", "$", "[", "]" — same
    // escaping rule as script.js's _otpKey().
    private fun otpKey(email: String): String =
        email.trim().lowercase().replace(Regex("[.#$\\[\\]]"), ",")

    /** Generates + stores a 6-digit code (10-min expiry) and emails it via EmailJS. */
    suspend fun sendEmailOtp(email: String) {
        val code = (100000..999999).random().toString()
        val expiresAt = System.currentTimeMillis() + 10 * 60 * 1000
        db.getReference("emailOtps/${otpKey(email)}")
            .setValue(mapOf("code" to code, "expiresAt" to expiresAt, "attempts" to 0))
            .await()
        EmailJsClient.sendOtpEmail(email, code)
    }

    sealed class OtpResult {
        object Success : OtpResult()
        data class Failure(val message: String) : OtpResult()
    }

    /** Re-reads the stored code from Firebase (never trusts a client-held copy). */
    suspend fun verifyEmailOtp(email: String, enteredCode: String): OtpResult {
        val ref = db.getReference("emailOtps/${otpKey(email)}")
        val snap = ref.get().await()
        if (!snap.exists()) return OtpResult.Failure("Code expired or not found — request a new one")
        val storedCode = snap.child("code").getValue(String::class.java) ?: ""
        val expiresAt = snap.child("expiresAt").getValue(Long::class.java) ?: 0L
        val attempts = (snap.child("attempts").getValue(Long::class.java) ?: 0L).toInt()
        if (System.currentTimeMillis() > expiresAt) {
            ref.removeValue()
            return OtpResult.Failure("Code expired — request a new one")
        }
        if (attempts >= 5) {
            ref.removeValue()
            return OtpResult.Failure("Too many attempts — request a new one")
        }
        if (enteredCode != storedCode) {
            ref.child("attempts").setValue(attempts + 1)
            return OtpResult.Failure("Incorrect code")
        }
        ref.removeValue()
        return OtpResult.Success
    }
}
