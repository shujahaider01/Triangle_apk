package com.triangle.app.inbox

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triangle.app.circle.CircleMember
import com.triangle.app.data.ConnectionRepository
import com.triangle.app.data.PollRepository
import com.triangle.app.data.SessionStore
import com.triangle.app.data.UserRepository
import com.triangle.app.data.models.Poll
import com.triangle.app.tasks.AssignMembersScreen
import com.triangle.app.tasks.FormCard
import com.triangle.app.tasks.FormCardTitle
import com.triangle.app.tasks.FormSaveButton
import com.triangle.app.tasks.FormTextField
import com.triangle.app.tasks.FormToggleCard
import com.triangle.app.tasks.formPageColor
import com.triangle.app.tasks.formScroll
import com.triangle.app.util.capFirst
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class PollComposeState(val isLoading: Boolean = true, val members: List<CircleMember> = emptyList())

/** Backs the poll composer: the creator's Circle (the recipient pool) and the send call. */
class PollComposeViewModel(private val session: SessionStore.Session) : ViewModel() {
    private val _state = MutableStateFlow(PollComposeState())
    val state: StateFlow<PollComposeState> = _state.asStateFlow()

    init {
        ConnectionRepository.circleFlow(session.uid)
            .onEach { uids ->
                val members = uids.mapNotNull { uid ->
                    UserRepository.fetchUserRecord(uid)?.let { CircleMember(uid, it.name, it.username, photoUrl = it.photoUrl) }
                }.sortedBy { it.name.lowercase() }
                _state.value = PollComposeState(isLoading = false, members = members)
            }.launchIn(viewModelScope)
    }

    fun send(
        question: String,
        options: List<String>,
        multiple: Boolean,
        showVoters: Boolean,
        closesAt: Long?,
        recipients: Set<String>,
        onResult: (Result<PollRepository.SendResult>) -> Unit
    ) {
        viewModelScope.launch {
            val names = _state.value.members.filter { it.uid in recipients }.associate { it.uid to it.name }
            onResult(runCatching { PollRepository.create(session, question, options, multiple, showVoters, closesAt, recipients, names) })
        }
    }
}

/** New poll form: a question, 2-6 options, single/multiple choice, and who to send it to. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollComposeScreen(
    viewModel: PollComposeViewModel,
    onSent: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    var multiple by remember { mutableStateOf(false) }
    var showVoters by remember { mutableStateOf(false) }
    var chosenUids by remember { mutableStateOf(emptySet<String>()) }
    var picking by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }

    val recipients: Set<String> = chosenUids.intersect(state.members.map { it.uid }.toSet())
    val filledOptions = options.map { it.trim() }.filter { it.isNotEmpty() }
    // Optional closing time: voting stops by itself then.
    var closingOn by remember { mutableStateOf(false) }
    var closeDate by remember { mutableStateOf(java.time.LocalDate.now().plusDays(1)) }
    var closeTime by remember { mutableStateOf(java.time.LocalTime.of(18, 0)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    fun closesAtMillis(): Long? =
        if (!closingOn) null else java.time.LocalDateTime.of(closeDate, closeTime).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val closingValid = !closingOn || (closesAtMillis() ?: 0L) > System.currentTimeMillis()

    val canSend = question.isNotBlank() && filledOptions.size >= Poll.MIN_OPTIONS && recipients.isNotEmpty() && closingValid && !sending

    BackHandler(enabled = picking) { picking = false }
    if (picking) {
        AssignMembersScreen(
            itemLabel = "",
            members = state.members,
            recentUids = emptyList(),
            initiallySelected = chosenUids,
            onClose = { picking = false },
            onDone = { chosenUids = it; picking = false },
            title = "Choose members",
            heading = "Send to",
            showRecentTab = false
        )
        return
    }

    fun send() {
        if (!canSend) return
        sending = true
        viewModel.send(question, filledOptions, multiple, showVoters, closesAtMillis(), recipients) { result ->
            sending = false
            result.onSuccess { r ->
                val msg = when {
                    r.sent == 0 && r.skippedBlocked > 0 -> "Not sent — everyone selected has blocked inbox messages from you"
                    r.skippedBlocked > 0 -> "Sent to ${r.sent}, ${r.skippedBlocked} skipped (blocked)"
                    else -> "Poll sent to ${r.sent} ${if (r.sent == 1) "person" else "people"}"
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                if (r.sent > 0) onSent()
            }.onFailure {
                Toast.makeText(context, "Couldn't send the poll: ${it.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        containerColor = formPageColor(),
        topBar = {
            TopAppBar(
                title = { Text("New Poll", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = formPageColor()),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "Close") } },
                actions = { FormSaveButton(saving = sending, enabled = canSend, onClick = { send() }, label = "Send") }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).formScroll().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FormCard {
                FormCardTitle("Question")
                Spacer(Modifier.height(10.dp))
                FormTextField(
                    value = question,
                    onValueChange = { question = it.take(Poll.QUESTION_MAX).capFirst() },
                    placeholder = "Ask something…",
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
            }

            FormCard {
                FormCardTitle("Options")
                Spacer(Modifier.height(10.dp))
                options.forEachIndexed { i, text ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        FormTextField(
                            value = text,
                            onValueChange = { options[i] = it.take(Poll.OPTION_MAX) },
                            placeholder = "Option ${i + 1}",
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                        )
                        if (options.size > Poll.MIN_OPTIONS) {
                            IconButton(onClick = { options.removeAt(i) }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove option", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (options.size < Poll.MAX_OPTIONS) {
                    TextButton(onClick = { options.add("") }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add option")
                    }
                }
            }

            FormToggleCard(
                title = "Allow multiple answers",
                subtitle = "People can pick more than one option",
                checked = multiple,
                onCheckedChange = { multiple = it }
            )
            FormToggleCard(
                title = "Allow others to see who voted",
                subtitle = "Everyone who receives the poll can view the voter list",
                checked = showVoters,
                onCheckedChange = { showVoters = it }
            )

            FormToggleCard(
                title = "Closing time",
                subtitle = "Voting stops automatically",
                checked = closingOn,
                onCheckedChange = { closingOn = it }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(closeDate.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM yyyy")))
                    }
                    TextButton(onClick = { showTimePicker = true }) {
                        Text(closeTime.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")))
                    }
                }
                if (!closingValid) {
                    Text("Pick a time in the future", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }

            FormCard(onClick = { picking = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        FormCardTitle("Send to")
                        Spacer(Modifier.height(2.dp))
                        Text(
                            when (recipients.size) {
                                0 -> "Choose from your Circle"
                                1 -> state.members.firstOrNull { it.uid in recipients }?.name ?: "1 person"
                                else -> "${recipients.size} people"
                            },
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val zoneUtc = java.time.ZoneId.of("UTC")
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = closeDate.atStartOfDay(zoneUtc).toInstant().toEpochMilli()
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        closeDate = java.time.Instant.ofEpochMilli(it).atZone(zoneUtc).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { androidx.compose.material3.DatePicker(state = pickerState) }
    }
    if (showTimePicker) {
        com.triangle.app.tasks.ReminderTimePickerDialog(
            initial = closeTime,
            onDismiss = { showTimePicker = false },
            onConfirm = { closeTime = it; showTimePicker = false }
        )
    }
}
