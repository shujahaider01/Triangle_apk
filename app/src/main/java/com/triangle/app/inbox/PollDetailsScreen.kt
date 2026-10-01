package com.triangle.app.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.PollRepository
import com.triangle.app.data.UserRepository
import com.triangle.app.data.models.Poll
import com.triangle.app.ui.components.Avatar
import com.triangle.app.ui.theme.TriangleBrandPurple
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val COLLAPSED_COUNT = 5

/**
 * Poll details: the question with how many members voted, then every option as its own section
 * (its vote count) followed by the people who picked it, each with when they voted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollDetailsScreen(pollId: String, myUid: String, onBack: () -> Unit) {
    val poll by PollRepository.pollFlow(pollId).collectAsState(initial = null)
    val votes by PollRepository.voteDetailsFlow(pollId).collectAsState(initial = emptyMap())

    // Names and pictures are looked up live from the voters' uids (falls back to the name stored when it was sent).
    val people by produceState<Map<String, Pair<String, String?>>>(emptyMap(), votes.keys, poll) {
        value = votes.keys.associateWith { uid ->
            val record = runCatching { UserRepository.fetchUserRecord(uid) }.getOrNull()
            (record?.name?.takeIf { it.isNotBlank() } ?: poll?.recipientNames?.get(uid) ?: "Someone") to record?.photoUrl
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Poll details", fontSize = 20.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        val p = poll
        if (p == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("This poll is no longer available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        val gap = MaterialTheme.colorScheme.surfaceVariant
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            // Header: question + how many have voted.
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(p.question, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${votes.size} of ${p.recipients.size} members voted",
                    fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(Modifier.fillMaxWidth().height(10.dp).background(gap))

            p.options.forEachIndexed { index, option ->
                // Everyone who picked this option, newest vote first.
                val voters = votes.entries.filter { index in it.value.picked }.sortedByDescending { it.value.at }
                var expanded by remember(pollId, index) { mutableStateOf(false) }
                val shown = if (expanded) voters else voters.take(COLLAPSED_COUNT)

                Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    Text(option, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp))
                    Text(
                        "${voters.size} ${if (voters.size == 1) "vote" else "votes"}",
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 10.dp)
                    )
                    if (voters.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    shown.forEach { (uid, vote) ->
                        val (name, photo) = people[uid] ?: ("…" to null)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(name, photo, size = 44.dp, fontSize = 16.sp)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(if (uid == myUid) "You" else name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                if (vote.at > 0) {
                                    Text(formatVoteTime(vote.at), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (!expanded && voters.size > COLLAPSED_COUNT) {
                        Text(
                            "See all (${voters.size - COLLAPSED_COUNT} more)",
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TriangleBrandPurple,
                            modifier = Modifier.clickable { expanded = true }.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Box(Modifier.fillMaxWidth().height(10.dp).background(gap))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun formatVoteTime(ms: Long): String =
    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(ms))
