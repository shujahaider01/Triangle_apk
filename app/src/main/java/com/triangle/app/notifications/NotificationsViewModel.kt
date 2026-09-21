package com.triangle.app.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triangle.app.data.ConnectionRepository
import com.triangle.app.data.NotificationRepository
import com.triangle.app.data.SessionStore
import com.triangle.app.data.models.AppNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val isLoading: Boolean = true,
    val notifications: List<AppNotification> = emptyList(),
    /** uids with a still-pending incoming request — a connection_request notification only gets inline Accept/Decline while its sender is in here (see NotificationsScreen). */
    val pendingConnectionUids: Set<String> = emptySet()
)

/** Native port of renderInternNotifs()'s data half (script.js:4769-4798). */
class NotificationsViewModel(private val session: SessionStore.Session) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        combine(
            NotificationRepository.notificationsFlow(session.orgId, session.uid),
            ConnectionRepository.incomingRequestsFlow(session.uid)
        ) { notifications, requests -> notifications to requests.map { it.fromUid }.toSet() }
            .onEach { (notifications, pendingUids) ->
                _uiState.value = NotificationsUiState(isLoading = false, notifications = notifications, pendingConnectionUids = pendingUids)
            }.launchIn(viewModelScope)
    }

    fun markAllRead() {
        viewModelScope.launch { runCatching { NotificationRepository.markAllRead(session.orgId, session.uid) } }
    }

    /** Matches onNotifTap()'s mark-read half (script.js:4808-4832) — tap-routing lives in the screen's caller. */
    fun markRead(notifId: String) {
        viewModelScope.launch { runCatching { NotificationRepository.markRead(session.orgId, session.uid, notifId) } }
    }

    fun acceptConnectionRequest(fromUid: String) {
        viewModelScope.launch { runCatching { ConnectionRepository.acceptRequest(session.uid, session.name, fromUid) } }
    }

    fun declineConnectionRequest(fromUid: String) {
        viewModelScope.launch { runCatching { ConnectionRepository.declineRequest(session.uid, fromUid) } }
    }
}
