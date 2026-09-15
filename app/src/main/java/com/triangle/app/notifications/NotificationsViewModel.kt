package com.triangle.app.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triangle.app.data.NotificationRepository
import com.triangle.app.data.SessionStore
import com.triangle.app.data.models.AppNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class NotificationsUiState(val isLoading: Boolean = true, val notifications: List<AppNotification> = emptyList())

/** Native port of renderInternNotifs()'s data half (script.js:4769-4798). */
class NotificationsViewModel(private val session: SessionStore.Session) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        NotificationRepository.notificationsFlow(session.orgId, session.uid)
            .onEach { _uiState.value = NotificationsUiState(isLoading = false, notifications = it) }
            .launchIn(viewModelScope)
    }

    fun markAllRead() {
        viewModelScope.launch { runCatching { NotificationRepository.markAllRead(session.orgId, session.uid) } }
    }

    /** Matches onNotifTap()'s mark-read half (script.js:4808-4832) — tap-routing lives in the screen's caller. */
    fun markRead(notifId: String) {
        viewModelScope.launch { runCatching { NotificationRepository.markRead(session.orgId, session.uid, notifId) } }
    }
}
