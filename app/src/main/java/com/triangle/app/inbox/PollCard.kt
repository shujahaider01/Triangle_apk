package com.triangle.app.inbox

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.PollRepository
import com.triangle.app.data.models.Poll
import com.triangle.app.ui.components.Avatar
import com.triangle.app.ui.theme.TriangleBrandPurple
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A poll in the Inbox. While it is open and you haven't voted you see tappable options and a Vote
 * button; after voting (or once it is closed, or if you created it) you see live results: a filled bar
 * and percentage per option, your own choice ticked, and the vote count. You can change your vote
 * while the poll is open. The creator gets a menu to close or delete it.
 */
@Composable
fun PollCard(
    poll: Poll,
    myUid: String,
    mine: Boolean,
    unread: Boolean,
    highlighted: Boolean,
    onVote: (List<Int>) -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onSeen: () -> Unit,
    onViewVotes: () -> Unit
) {
    val votes by PollRepository.votesFlow(poll.id).collectAsState(initial = emptyMap())
    val myVote = votes[myUid].orEmpty()
    var selected by remember(poll.id) { mutableStateOf(emptySet<Int>()) }
    var changing by remember(poll.id) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    // Closes by itself at poll.closesAt: re-check every few seconds while a deadline is pending.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(poll.closesAt, poll.closed) {
        val end = poll.closesAt
        while (end != null && !poll.closed && System.currentTimeMillis() < end) {
            kotlinx.coroutines.delay(5_000)
            now = System.currentTimeMillis()
        }
        now = System.currentTimeMillis()
    }
    val closed = poll.isClosedAt(now)

    val showResults = mine || closed || (myVote.isNotEmpty() && !changing)
    val voters = votes.size
    val counts = remember(votes, poll.options) { poll.options.indices.map { i -> votes.values.count { i in it } } }
    val top = counts.maxOrNull() ?: 0

    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = if (closed) Color(0xFF8E8E98) else TriangleBrandPurple

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (highlighted) 8.dp else 2.dp,
        border = BorderStroke(if (highlighted) 2.dp else 1.dp, if (highlighted) accent else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable { onSeen() }
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Thin accent line (purple while open, grey once closed); a dot when it's unread.
            Box(Modifier.fillMaxWidth().height(4.dp).background(accent))

            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(poll.creatorName, poll.creatorPhotoUrl, size = 40.dp, fontSize = 15.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (mine) "You" else poll.creatorName.ifBlank { "Someone" },
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (unread) Box(Modifier.padding(start = 6.dp).size(8.dp).clip(CircleShape).background(accent))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.BarChart, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Poll · ${if (poll.multiple) "choose all that apply" else "choose one"}", fontSize = 12.sp, color = muted)
                    }
                }
                Text(
                    if (closed) "CLOSED" else "OPEN",
                    fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accent.copy(alpha = 0.12f))
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                )
                if (mine) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Poll options", tint = muted) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (!closed) DropdownMenuItem(text = { Text("Close poll") }, onClick = { menuOpen = false; onClose() })
                            DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menuOpen = false; onDelete() })
                        }
                    }
                } else Spacer(Modifier.width(10.dp))
            }

            Text(
                poll.question,
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = onSurface, lineHeight = 24.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            )

            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                poll.options.forEachIndexed { i, label ->
                    if (showResults) {
                        ResultRow(label, counts[i], voters, isMine = i in myVote, isTop = counts[i] == top && top > 0, accent = accent)
                    } else {
                        ChoiceRow(label, picked = i in selected, multiple = poll.multiple, accent = accent) {
                            selected = if (poll.multiple) { if (i in selected) selected - i else selected + i } else setOf(i)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (!showResults) {
                Button(
                    onClick = { onVote(selected.sorted()); changing = false; selected = emptySet() },
                    enabled = selected.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(46.dp)
                ) { Text("Vote", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(4.dp))
            }

            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$voters ${if (voters == 1) "vote" else "votes"} · " + when {
                        !closed && poll.closesAt != null -> "closes ${formatPollDate(poll.closesAt)}"
                        closed && poll.closesAt != null && !poll.closed -> "closed ${formatPollDate(poll.closesAt)}"
                        else -> formatPollDate(poll.createdAt)
                    },
                    fontSize = 12.sp, color = muted, modifier = Modifier.weight(1f)
                )
                // The creator always can see who voted; everyone else only if the creator allowed it (and once results are showing).
                if (mine || (poll.showVoters && showResults)) {
                    TextButton(onClick = onViewVotes) { Text("View votes", fontSize = 13.sp) }
                }
                if (!mine && showResults && !closed && myVote.isNotEmpty()) {
                    TextButton(onClick = { selected = myVote.toSet(); changing = true }) { Text("Change vote", fontSize = 13.sp) }
                } else if (!showResults && changing) {
                    TextButton(onClick = { changing = false; selected = emptySet() }) { Text("Cancel", fontSize = 13.sp) }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, picked: Boolean, multiple: Boolean, accent: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (picked) accent.copy(alpha = 0.10f) else Color.Transparent)
            .border(if (picked) 2.dp else 1.dp, if (picked) accent else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Round indicator for single choice, rounded square for multiple.
        val indicatorShape = if (multiple) RoundedCornerShape(6.dp) else CircleShape
        Box(
            Modifier
                .size(22.dp)
                .clip(indicatorShape)
                .background(if (picked) accent else Color.Transparent)
                .border(2.dp, if (picked) accent else MaterialTheme.colorScheme.outline, indicatorShape),
            contentAlignment = Alignment.Center
        ) {
            if (picked) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 15.sp, fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ResultRow(label: String, count: Int, voters: Int, isMine: Boolean, isTop: Boolean, accent: Color) {
    val fraction = if (voters == 0) 0f else count.toFloat() / voters
    val animated by animateFloatAsState(fraction, label = "pollBar")
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(if (isMine) 2.dp else 0.dp, if (isMine) accent else Color.Transparent, shape)
    ) {
        // Result fill, behind the text; sized to the row via matchParentSize so it never affects the row height.
        Box(Modifier.matchParentSize()) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(animated).background(accent.copy(alpha = if (isTop) 0.32f else 0.18f)))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isMine) {
                Icon(Icons.Default.Check, contentDescription = "Your vote", tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label, fontSize = 15.sp, fontWeight = if (isTop) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text("${Math.round(fraction * 100)}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun formatPollDate(ms: Long): String =
    SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(ms))
