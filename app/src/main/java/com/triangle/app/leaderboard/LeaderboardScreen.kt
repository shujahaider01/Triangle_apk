package com.triangle.app.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.LeaderboardRepository
import com.triangle.app.data.ProfileStats

/** Native port of renderIndividualLeaderboard() (podium + list, script.js:13331-13397) — league tiers and Friends League dropped, see the Milestone 6 plan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(viewModel: LeaderboardViewModel, onBack: () -> Unit) {
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
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                podium.forEachIndexed { i, entry ->
                                    PodiumCard(rank = i + 1, entry = entry, isMe = entry.uid == state.myUid, palette = palette, modifier = Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                    itemsIndexed(rest, key = { _, entry -> entry.uid }) { index, entry ->
                        LeaderboardRow(rank = podium.size + index + 1, entry = entry, isMe = entry.uid == state.myUid, palette = palette)
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PodiumCard(rank: Int, entry: LeaderboardRepository.LeaderboardEntry, isMe: Boolean, palette: LeaderboardPalette, modifier: Modifier = Modifier) {
    val medal = medalColor(rank) ?: LeaderboardColors.Accent
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isMe) medal.copy(alpha = 0.15f) else palette.surface2)
            .padding(12.dp, 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (rank == 1) {
            Text("👑", fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
        }
        Box(Modifier.size(48.dp).clip(CircleShape).background(medal.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
            Text((entry.name.firstOrNull() ?: '?').uppercase(), color = medal, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(8.dp))
        Text(entry.name, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = palette.text, maxLines = 1)
        Text("Level ${ProfileStats.level(entry.points)}", fontSize = 10.sp, color = palette.text3)
        Spacer(Modifier.height(4.dp))
        Text("${entry.points} XP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = medal)
    }
}

@Composable
private fun LeaderboardRow(rank: Int, entry: LeaderboardRepository.LeaderboardEntry, isMe: Boolean, palette: LeaderboardPalette) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isMe) LeaderboardColors.Accent.copy(alpha = 0.12f) else palette.surface2)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("#$rank", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.text3, modifier = Modifier.width(36.dp))
        Box(Modifier.size(38.dp).clip(CircleShape).background(LeaderboardColors.Accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text((entry.name.firstOrNull() ?: '?').uppercase(), color = LeaderboardColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.text, maxLines = 1)
            Text("Level ${ProfileStats.level(entry.points)}", fontSize = 11.sp, color = palette.text3)
        }
        Text("${entry.points} XP", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.text)
    }
}
