package com.triangle.app.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.LeaderboardRepository
import com.triangle.app.data.ProfileStats
import com.triangle.app.ui.components.Avatar

/** Native port of renderIndividualLeaderboard() (podium + list, script.js:13331-13397) — league tiers and Friends League dropped, see the Milestone 6 plan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(viewModel: LeaderboardViewModel, onBack: () -> Unit, onOpenProfile: (uid: String, orgId: String) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val palette = leaderboardPalette()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Leaderboard", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LeaderboardColors.Accent)
            }
            state.error != null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(state.error ?: "Something went wrong", color = MaterialTheme.colorScheme.error)
            }
            state.entries.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No rankings yet", color = palette.text3)
            }
            else -> {
                val podium = state.entries.take(3)
                val rest = state.entries.drop(3)
                LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (podium.isNotEmpty()) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                podium.forEachIndexed { i, entry ->
                                    val isMe = entry.uid == state.myUid
                                    val isConnected = entry.uid in state.connections
                                    PodiumCard(
                                        rank = i + 1,
                                        entry = entry,
                                        isMe = isMe,
                                        isConnected = isConnected,
                                        owe = state.oweAmounts[entry.uid],
                                        palette = palette,
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        // Can't view a profile for someone who isn't a connection —
                                        // this app never exposes anyone else's profile page.
                                        onClick = if (isMe || isConnected) { { onOpenProfile(entry.uid, entry.orgId) } } else null
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                    itemsIndexed(rest, key = { _, entry -> entry.uid }) { index, entry ->
                        val isMe = entry.uid == state.myUid
                        val isConnected = entry.uid in state.connections
                        LeaderboardRow(
                            rank = podium.size + index + 1,
                            entry = entry,
                            isMe = isMe,
                            isConnected = isConnected,
                            owe = state.oweAmounts[entry.uid],
                            palette = palette,
                            onClick = if (isMe || isConnected) { { onOpenProfile(entry.uid, entry.orgId) } } else null
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

/**
 * Nets the two directions into a single line — "he owes 15xp" already means
 * "after subtracting what you owe him," per the feature's own wording, so
 * showing both raw totals alongside each other double-counts the overlap.
 * Null when the two sides cancel out (including the common 0/0 case).
 */
private fun netOwe(owe: LeaderboardRepository.OweAmounts?): Pair<String, Int>? {
    val net = (owe?.youOwe ?: 0) - (owe?.heOwe ?: 0)
    return when {
        net > 0 -> "You Owe" to net
        net < 0 -> "He Owe" to -net
        else -> null
    }
}

@Composable
private fun PodiumCard(
    rank: Int,
    entry: LeaderboardRepository.LeaderboardEntry,
    isMe: Boolean,
    isConnected: Boolean,
    owe: LeaderboardRepository.OweAmounts?,
    palette: LeaderboardPalette,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val medal = medalColor(rank) ?: LeaderboardColors.Accent
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isMe) medal.copy(alpha = 0.15f) else palette.surface2)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp, 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The crown line is always reserved (just invisible for rank 2/3)
        // instead of only existing for rank 1 — otherwise the #1 card is
        // taller than its neighbors purely because it has one more line.
        Text("👑", fontSize = 22.sp, modifier = Modifier.alpha(if (rank == 1) 1f else 0f))
        Spacer(Modifier.height(4.dp))
        Box {
            Avatar(entry.name, entry.photoUrl, size = 48.dp, backgroundColor = medal.copy(alpha = 0.25f), textColor = medal, fontSize = 18.sp)
            if (isConnected && !isMe) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(palette.surface2)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(LeaderboardColors.Accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.People, contentDescription = "Connection", tint = Color.White, modifier = Modifier.size(10.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(entry.name, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = palette.text, maxLines = 1)
        if (isMe) {
            Text("Level ${ProfileStats.level(entry.points)}", fontSize = 10.sp, color = palette.text3)
        } else {
            val net = netOwe(owe)
            if (net != null) {
                val (label, amount) = net
                Text(
                    "$label +$amount",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (label == "You Owe") LeaderboardColors.YouOwe else LeaderboardColors.HeOwe
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("${entry.points} XP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = medal)
    }
}

@Composable
private fun LeaderboardRow(rank: Int, entry: LeaderboardRepository.LeaderboardEntry, isMe: Boolean, isConnected: Boolean, owe: LeaderboardRepository.OweAmounts?, palette: LeaderboardPalette, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isMe) LeaderboardColors.Accent.copy(alpha = 0.12f) else palette.surface2)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("#$rank", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.text3, modifier = Modifier.width(36.dp))
        Box {
            Avatar(entry.name, entry.photoUrl, size = 38.dp, backgroundColor = LeaderboardColors.Accent.copy(alpha = 0.18f), textColor = LeaderboardColors.Accent, fontSize = 15.sp)
            if (isConnected && !isMe) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(palette.surface2)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(LeaderboardColors.Accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.People, contentDescription = "Connection", tint = Color.White, modifier = Modifier.size(9.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.text, maxLines = 1)
            if (isMe) {
                Text("Level ${ProfileStats.level(entry.points)}", fontSize = 11.sp, color = palette.text3)
            } else {
                val net = netOwe(owe)
                if (net != null) {
                    val (label, amount) = net
                    Text(
                        "$label +$amount",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (label == "You Owe") LeaderboardColors.YouOwe else LeaderboardColors.HeOwe
                    )
                }
            }
        }
        Text("${entry.points} XP", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.text)
    }
}
