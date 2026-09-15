package com.triangle.app.dm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triangle.app.data.DmRepository
import com.triangle.app.data.SessionStore
import com.triangle.app.data.UserRepository
import com.triangle.app.data.models.DmMessage
import com.triangle.app.data.models.DmThreadSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class DmInboxUiState(val isLoading: Boolean = true, val threads: List<DmThreadSummary> = emptyList())
data class DmSearchUiState(val query: String = "", val results: List<UserRepository.UserSearchResult> = emptyList(), val isSearching: Boolean = false)
data class DmThreadUiState(
    val isLoading: Boolean = true,
    val otherUserId: String = "",
    val otherUserName: String = "",
    val messages: List<DmMessage> = emptyList(),
    val blocked: Boolean = false,
    val blockedByMe: Boolean = false
)

/**
 * Shared ViewModel for the whole DM nested nav graph (mirrors
 * TasksHabitsViewModel/RewardsViewModel — scoped to the graph's
 * NavBackStackEntry so navigating between Inbox/Search/Thread keeps seeing
 * the same live data instead of each screen re-subscribing).
 */
class DmViewModel(private val session: SessionStore.Session) : ViewModel() {

    private val nameCache = MutableStateFlow<Map<String, String>>(emptyMap())

    private val _inbox = MutableStateFlow(DmInboxUiState())
    val inbox: StateFlow<DmInboxUiState> = _inbox.asStateFlow()

    private val _search = MutableStateFlow(DmSearchUiState())
    val search: StateFlow<DmSearchUiState> = _search.asStateFlow()

    private val _thread = MutableStateFlow(DmThreadUiState())
    val thread: StateFlow<DmThreadUiState> = _thread.asStateFlow()

    private var threadJob: Job? = null

    init {
        combine(DmRepository.userThreadsFlow(session.uid), nameCache) { threads, names ->
            threads.map { it.copy(otherUserName = names[it.otherUserId] ?: "") }
        }.onEach { list ->
            _inbox.value = DmInboxUiState(isLoading = false, threads = list)
            resolveNames(list.map { it.otherUserId })
        }.launchIn(viewModelScope)
    }

    private fun resolveNames(uids: List<String>) {
        val missing = uids.filter { it.isNotBlank() && it !in nameCache.value }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val updates = missing.mapNotNull { uid -> UserRepository.fetchUserRecord(uid)?.let { uid to it.name } }
            if (updates.isNotEmpty()) nameCache.value = nameCache.value + updates
        }
    }

    fun searchUsers(query: String) {
        _search.value = _search.value.copy(query = query)
        viewModelScope.launch {
            _search.value = _search.value.copy(isSearching = true)
            val results = if (query.isBlank()) emptyList() else UserRepository.searchUsers(query, session.uid)
            _search.value = _search.value.copy(results = results, isSearching = false)
        }
    }

    /** Opens (or starts) a thread with the given user — call when navigating into DmThreadScreen. */
    fun openThread(otherUserId: String) {
        if (_thread.value.otherUserId == otherUserId && threadJob?.isActive == true) return
        threadJob?.cancel()
        val threadId = DmRepository.threadId(session.uid, otherUserId)
        _thread.value = DmThreadUiState(isLoading = true, otherUserId = otherUserId)

        viewModelScope.launch {
            val name = UserRepository.fetchUserRecord(otherUserId)?.name ?: ""
            _thread.value = _thread.value.copy(otherUserName = name)
        }

        threadJob = combine(DmRepository.messagesFlow(threadId), DmRepository.threadFlow(threadId)) { messages, meta ->
            val blockedByMe = meta?.blockedBy?.contains(session.uid) == true
            val blockedByOther = meta?.blockedBy?.contains(otherUserId) == true
            Triple(messages, blockedByMe, blockedByOther)
        }.onEach { (messages, blockedByMe, blockedByOther) ->
            _thread.value = _thread.value.copy(
                isLoading = false,
                messages = messages,
                blocked = blockedByMe || blockedByOther,
                blockedByMe = blockedByMe
            )
        }.launchIn(viewModelScope)

        viewModelScope.launch { DmRepository.markThreadRead(session.uid, threadId) }
    }

    fun sendMessage(text: String) {
        val otherUserId = _thread.value.otherUserId
        val trimmed = text.trim()
        if (otherUserId.isBlank() || trimmed.isBlank()) return
        viewModelScope.launch {
            DmRepository.sendMessage(session.uid, session.name, otherUserId, trimmed)
        }
    }

    fun toggleBlock() {
        val otherUserId = _thread.value.otherUserId
        if (otherUserId.isBlank()) return
        val threadId = DmRepository.threadId(session.uid, otherUserId)
        val newBlocked = !_thread.value.blockedByMe
        viewModelScope.launch { DmRepository.setBlocked(threadId, session.uid, newBlocked) }
    }
}
