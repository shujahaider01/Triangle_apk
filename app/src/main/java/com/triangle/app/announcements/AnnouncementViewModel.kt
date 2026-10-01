package com.triangle.app.announcements

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import androidx.lifecycle.viewModelScope
import com.triangle.app.circle.CircleMember
import com.triangle.app.data.AnnouncementRepository
import com.triangle.app.data.ConnectionRepository
import com.triangle.app.data.SessionStore
import com.triangle.app.data.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class AnnouncementUiState(
    val isLoading: Boolean = true,
    /** Every Circle member (the sender's recipient pool) — blocking is enforced at send time, not here. */
    val members: List<CircleMember> = emptyList()
)

/** Backs the composer: the sender's Circle, from which they choose recipients. */
class AnnouncementViewModel(private val session: SessionStore.Session) : ViewModel() {

    private val _state = MutableStateFlow(AnnouncementUiState())
    val state: StateFlow<AnnouncementUiState> = _state.asStateFlow()

    init {
        ConnectionRepository.circleFlow(session.uid)
            .onEach { uids ->
                val members = uids.mapNotNull { uid ->
                    UserRepository.fetchUserRecord(uid)?.let { CircleMember(uid, it.name, it.username, photoUrl = it.photoUrl) }
                }.sortedBy { it.name.lowercase() }
                _state.value = AnnouncementUiState(isLoading = false, members = members)
            }.launchIn(viewModelScope)
    }

    private val driveScope = Scope("https://www.googleapis.com/auth/drive.file")
    private var pendingWithToken: ((String?) -> Unit)? = null

    /**
     * The screen stays up with a "Sending…" state until this finishes. Attachments (photos, videos,
     * PDFs) are uploaded to the sender's Google Drive (same drive.file authorization as profile
     * photos — see DriveImageHelper), so a send with attachments first gets a Drive token, possibly
     * via Google's consent screen ([launchIntent]); a text-only send skips that entirely.
     */
    fun send(
        context: Context,
        title: String,
        body: String,
        colorHex: String,
        attachments: List<AnnouncementRepository.PendingAttachment>,
        recipients: Set<String>,
        launchIntent: (IntentSenderRequest) -> Unit,
        onResult: (Result<AnnouncementRepository.SendResult>) -> Unit
    ) {
        val resolver = context.applicationContext.contentResolver
        fun run(token: String?) {
            viewModelScope.launch {
                val names = _state.value.members.filter { it.uid in recipients }.associate { it.uid to it.name }
                onResult(runCatching { AnnouncementRepository.send(session, title, body, colorHex, attachments, resolver, recipients, names, token) })
            }
        }
        if (attachments.isEmpty()) return run(null)

        pendingWithToken = { token ->
            if (token.isNullOrEmpty()) onResult(Result.failure(IllegalStateException("No Google Drive access")))
            else run(token)
        }
        Identity.getAuthorizationClient(context)
            .authorize(AuthorizationRequest.builder().setRequestedScopes(listOf(driveScope)).build())
            .addOnSuccessListener { auth ->
                if (auth.hasResolution()) {
                    try {
                        launchIntent(IntentSenderRequest.Builder(auth.pendingIntent!!.intentSender).build())
                    } catch (e: Exception) {
                        pendingWithToken?.invoke(null); pendingWithToken = null
                    }
                } else {
                    pendingWithToken?.invoke(auth.accessToken); pendingWithToken = null
                }
            }
            .addOnFailureListener { pendingWithToken?.invoke(null); pendingWithToken = null }
    }

    /** Called from the composer's launcher once Google's consent screen finishes. */
    fun onDriveAuthorizationResult(context: Context, data: Intent?) {
        val token = runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).accessToken }.getOrNull()
        pendingWithToken?.invoke(token)
        pendingWithToken = null
    }
}
