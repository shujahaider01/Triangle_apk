package com.triangle.app.circle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triangle.app.data.ConnectionRepository
import com.triangle.app.data.EmailInviteRepository
import com.triangle.app.data.SessionStore
import com.triangle.app.data.UserRepository
import com.triangle.app.data.UsernameRepository
import com.triangle.app.data.models.ConnectionRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class CircleMember(val uid: String, val name: String, val username: String?, val canAssign: Boolean = true, val photoUrl: String? = null)
data class CircleUiState(val isLoading: Boolean = true, val members: List<CircleMember> = emptyList())
data class RequestsUiState(val isLoading: Boolean = true, val requests: List<ConnectionRequest> = emptyList())

/**
 * Deliberately just Idle/Sending — never a per-attempt success/failure/
 * not-found result. Whether the username existed, was already connected,
 * or was your own is never surfaced here: the caller always shows the same
 * "request sent" toast, so a stranger fishing for valid usernames learns
 * nothing from the UI (see ConnectByUsernameScreen).
 */
enum class UsernameRequestState { Idle, Sending }

sealed class EmailInviteUiState {
    object Idle : EmailInviteUiState()
    object Sending : EmailInviteUiState()
    object SentToExistingUser : EmailInviteUiState()
    object EmailInviteSent : EmailInviteUiState()
    data class Error(val message: String) : EmailInviteUiState()
}

/**
 * Shared ViewModel for the whole Circle nested nav graph (list/search/
 * requests), scoped to the graph's own NavBackStackEntry — same pattern as
 * DmViewModel/TasksHabitsViewModel. Part of the Connect->Assign->Complete->
 * Earn model (see .claude/plans/enchanted-brewing-beacon.md).
 */
class CircleViewModel(private val session: SessionStore.Session) : ViewModel() {

    private val _circle = MutableStateFlow(CircleUiState())
    val circle: StateFlow<CircleUiState> = _circle.asStateFlow()

    private val _requests = MutableStateFlow(RequestsUiState())
    val requests: StateFlow<RequestsUiState> = _requests.asStateFlow()

    private val _usernameRequest = MutableStateFlow(UsernameRequestState.Idle)
    val usernameRequest: StateFlow<UsernameRequestState> = _usernameRequest.asStateFlow()

    private val _emailInvite = MutableStateFlow<EmailInviteUiState>(EmailInviteUiState.Idle)
    val emailInvite: StateFlow<EmailInviteUiState> = _emailInvite.asStateFlow()

    init {
        combine(
            ConnectionRepository.circleFlow(session.uid),
            ConnectionRepository.assignPermissionsFlow(session.uid)
        ) { uids, permissions -> uids to permissions }
            .onEach { (uids, permissions) ->
                val members = uids.mapNotNull { uid ->
                    UserRepository.fetchUserRecord(uid)?.let {
                        CircleMember(uid, it.name, it.username, canAssign = permissions[uid] ?: true, photoUrl = it.photoUrl)
                    }
                }.sortedBy { it.name.lowercase() }
                _circle.value = CircleUiState(isLoading = false, members = members)
            }.launchIn(viewModelScope)

        ConnectionRepository.incomingRequestsFlow(session.uid).onEach { list ->
            _requests.value = RequestsUiState(isLoading = false, requests = list)
        }.launchIn(viewModelScope)
    }

    /**
     * Resolves the username to a uid via an exact single-key lookup (never a
     * scan) and sends a request only when it's a real, different, not-
     * already-connected/pending account — but the completion callback fires
     * unconditionally either way, so the UI has nothing to branch on that
     * would leak whether the username exists. See UsernameRequestState's doc.
     */
    fun sendConnectionRequestByUsername(rawUsername: String, onDone: () -> Unit) {
        _usernameRequest.value = UsernameRequestState.Sending
        viewModelScope.launch {
            val targetUid = UsernameRepository.resolveUid(rawUsername)
            if (targetUid != null &&
                targetUid != session.uid &&
                !ConnectionRepository.isConnected(session.uid, targetUid) &&
                !ConnectionRepository.hasPendingOutgoing(session.uid, targetUid)
            ) {
                ConnectionRepository.sendRequest(session.uid, session.name, session.username ?: "", targetUid)
            }
            _usernameRequest.value = UsernameRequestState.Idle
            onDone()
        }
    }

    fun acceptRequest(fromUid: String) {
        viewModelScope.launch { ConnectionRepository.acceptRequest(session.uid, session.name, fromUid) }
    }

    fun declineRequest(fromUid: String) {
        viewModelScope.launch { ConnectionRepository.declineRequest(session.uid, fromUid) }
    }

    fun setAssignPermission(uid: String, canAssign: Boolean) {
        viewModelScope.launch { ConnectionRepository.setAssignPermission(session.uid, uid, canAssign) }
    }

    fun removeMember(uid: String) {
        viewModelScope.launch { ConnectionRepository.removeConnection(session.uid, uid) }
    }

    fun resetEmailInvite() {
        _emailInvite.value = EmailInviteUiState.Idle
    }

    fun sendEmailInvite(email: String) {
        if (!Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email.trim())) {
            _emailInvite.value = EmailInviteUiState.Error("Enter a valid email address")
            return
        }
        _emailInvite.value = EmailInviteUiState.Sending
        viewModelScope.launch {
            try {
                val result = EmailInviteRepository.sendInvite(session.uid, session.name, session.username ?: "", email.trim())
                _emailInvite.value = when (result) {
                    EmailInviteRepository.InviteResult.SentToExistingUser -> EmailInviteUiState.SentToExistingUser
                    EmailInviteRepository.InviteResult.EmailInviteSent -> EmailInviteUiState.EmailInviteSent
                    EmailInviteRepository.InviteResult.AlreadyYourEmail -> EmailInviteUiState.Error("That's your own email address")
                }
            } catch (e: Exception) {
                _emailInvite.value = EmailInviteUiState.Error(e.message ?: "Could not send the invite")
            }
        }
    }
}
