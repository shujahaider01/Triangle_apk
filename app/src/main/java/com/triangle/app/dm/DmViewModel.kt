package com.triangle.app.dm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triangle.app.data.ConnectionRepository
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class DmInboxUiState(val isLoading: Boolean = true, val threads: List<DmThreadSummary> = emptyList())

/**
 * "New Message" only ever lists people you're already connected with — not
 * a global directory search. [allConnections] is loaded once per screen
 * visit; [query] filters it client-side (your own connections' names, not
 * a privacy concern) rather than hitting the network per keystroke.
 */
data class DmSearchUiState(
    val query: String = "",
    val allConnections: List<UserRepository.UserSearchResult> = emptyList(),
    val isLoading: Boolean = true
) {
    val results: List<UserRepository.UserSearchResult> get() =
        if (query.isBlank()) allConnections
        else allConnections.filter { it.name.contains(query, ignoreCase = true) || it.username?.contains(query, ignoreCase = true) == true }
}
data class DmThreadUiState(
    val isLoading: Boolean = true,
    val otherUserId: String = "",
    val otherUserName: String = "",
    val otherUserPhotoUrl: String? = null,
    val messages: List<DmMessage> = emptyList(),
    val blocked: Boolean = false,
    val blockedByMe: Boolean = false
)

private data class UserBasics(val name: String, val photoUrl: String?)

/**
 * Shared ViewModel for the whole DM nested nav graph (mirrors
 * TasksHabitsViewModel — scoped to the graph's NavBackStackEntry so
 * navigating between Inbox/Search/Thread keeps seeing the same live data
 * instead of each screen re-subscribing).
 */
class DmViewModel(private val session: SessionStore.Session) : ViewModel() {

    private val nameCache = MutableStateFlow<Map<String, UserBasics>>(emptyMap())

    private val _inbox = MutableStateFlow(DmInboxUiState())
    val inbox: StateFlow<DmInboxUiState> = _inbox.asStateFlow()

    private val _search = MutableStateFlow(DmSearchUiState())
    val search: StateFlow<DmSearchUiState> = _search.asStateFlow()

    private val _thread = MutableStateFlow(DmThreadUiState())
    val thread: StateFlow<DmThreadUiState> = _thread.asStateFlow()

    private var threadJob: Job? = null

    init {
        combine(DmRepository.userThreadsFlow(session.uid), nameCache) { threads, cache ->
            threads.map { t -> cache[t.otherUserId]?.let { t.copy(otherUserName = it.name, otherUserPhotoUrl = it.photoUrl) } ?: t }
        }.onEach { list ->
            _inbox.value = DmInboxUiState(isLoading = false, threads = list)
            resolveNames(list.map { it.otherUserId })
        }.launchIn(viewModelScope)
    }

    private fun resolveNames(uids: List<String>) {
        val missing = uids.filter { it.isNotBlank() && it !in nameCache.value }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val updates = missing.mapNotNull { uid -> UserRepository.fetchUserRecord(uid)?.let { uid to UserBasics(it.name, it.photoUrl) } }
            if (updates.isNotEmpty()) nameCache.value = nameCache.value + updates
        }
    }

    /** Just the client-side filter text — the connections list itself is loaded once by loadConnectionsForNewMessage(). */
    fun setSearchQuery(query: String) {
        _search.value = _search.value.copy(query = query)
    }

    /** Call when entering DmNewMessageScreen — loads the uid's Circle so the picker has someone to list. */
    fun loadConnectionsForNewMessage() {
        viewModelScope.launch {
            _search.value = _search.value.copy(isLoading = true)
            val uids = ConnectionRepository.circleFlow(session.uid).first()
            val connections = uids.mapNotNull { uid ->
                UserRepository.fetchUserRecord(uid)?.let { UserRepository.UserSearchResult(uid, it.name, it.email, it.username, it.photoUrl) }
            }.sortedBy { it.name.lowercase() }
            _search.value = _search.value.copy(allConnections = connections, isLoading = false)
        }
    }

    /** Opens (or starts) a thread with the given user — call when navigating into DmThreadScreen. */
    fun openThread(otherUserId: String) {
        if (_thread.value.otherUserId == otherUserId && threadJob?.isActive == true) return
        threadJob?.cancel()
        val threadId = DmRepository.threadId(session.uid, otherUserId)
        _thread.value = DmThreadUiState(isLoading = true, otherUserId = otherUserId)

        viewModelScope.launch {
            val record = UserRepository.fetchUserRecord(otherUserId)
            _thread.value = _thread.value.copy(otherUserName = record?.name ?: "", otherUserPhotoUrl = record?.photoUrl)
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
