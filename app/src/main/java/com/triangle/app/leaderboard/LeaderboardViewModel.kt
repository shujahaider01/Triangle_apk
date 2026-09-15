package com.triangle.app.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triangle.app.data.LeaderboardRepository
import com.triangle.app.data.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LeaderboardUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val entries: List<LeaderboardRepository.LeaderboardEntry> = emptyList(),
    val myUid: String = ""
)

/** Native port of the data half of renderIndividualLeaderboard()/getIndividualGlobalRanking(). One-shot fetch — source uses a plain fetch(), no realtime subscription. */
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
                val entries = LeaderboardRepository.fetchGlobalRanking()
                _uiState.value = _uiState.value.copy(isLoading = false, entries = entries)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Could not load the leaderboard")
            }
        }
    }
}
