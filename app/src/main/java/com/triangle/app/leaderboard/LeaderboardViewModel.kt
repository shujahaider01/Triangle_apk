package com.triangle.app.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triangle.app.data.ConnectionRepository
import com.triangle.app.data.LeaderboardRepository
import com.triangle.app.data.SessionStore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class LeaderboardUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val entries: List<LeaderboardRepository.LeaderboardEntry> = emptyList(),
    val oweAmounts: Map<String, LeaderboardRepository.OweAmounts> = emptyMap(),
    val connections: Set<String> = emptySet(),
    val myUid: String = ""
)

/** Leaderboard of me and my connections only (no global ranking). One-shot fetch, no realtime subscription. */
class LeaderboardViewModel(private val session: SessionStore.Session) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderboardUiState(myUid = session.uid))
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                coroutineScope {
                    val connections = ConnectionRepository.circleFlow(session.uid).first().toSet()
                    val entriesDeferred = async { LeaderboardRepository.fetchRanking(session.uid, connections) }
                    val oweDeferred = async { LeaderboardRepository.fetchOweAmounts(session.uid, session.orgId) }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        entries = entriesDeferred.await(),
                        oweAmounts = oweDeferred.await(),
                        connections = connections
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Could not load the leaderboard")
            }
        }
    }
}
