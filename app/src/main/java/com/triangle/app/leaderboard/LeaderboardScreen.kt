package com.triangle.app.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.LeaderboardRepository
import com.triangle.app.data.LeaderboardRepository.LeaderboardEntry
import com.triangle.app.ui.components.Avatar
import com.triangle.app.ui.theme.TriangleBrandPurple
import com.triangle.app.ui.theme.triangleDarkTheme

private enum class Period(val label: String) { WEEK("WEEKLY"), MONTH("MONTHLY"), ALL("ALL TIME") }

private fun LeaderboardEntry.pointsFor(p: Period) = when (p) {
    Period.WEEK -> weekPoints
    Period.MONTH -> monthPoints
    Period.ALL -> points
}

/**
 * Leaderboard of me and my connections in a clean classic layout: a centred title, Weekly / Monthly / All Time
 * tabs, a podium of the top three with the leader crowned, and everyone else as rounded cards with their rank
 * beside them. Scores are Points.
 */
@Composable
fun LeaderboardScreen(viewModel: LeaderboardViewModel, onBack: () -> Unit, onOpenProfile: (uid: String, orgId: String) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val dark = triangleDarkTheme()
    val pageColor = if (dark) Color(0xFF111111) else Color.White
    val listColor = if (dark) Color(0xFF0B0B0C) else Color(0xFFF3F7FA)
    val textMain = if (dark) Color.White else Color(0xFF1C1C1E)
    val textMuted = if (dark) Color(0xFF8E8E93) else Color(0xFF9A9A9A)
    val line = if (dark) Color(0xFF2C2C2E) else Color(0xFFE9E9E9)

    var period by remember { mutableStateOf(Period.WEEK) }
    val ranked = remember(state.entries, period) { state.entries.sortedByDescending { it.pointsFor(period) } }
    val noRipple = remember { MutableInteractionSource() }

    Column(Modifier.fillMaxSize().background(pageColor)) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(52.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textMain)
                }
                Text(
                    "LEADERBOARD", fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp,
                    color = textMain, modifier = Modifier.align(Alignment.Center)
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Period.values().forEach { p ->
                    val selected = p == period
                    Column(
                        Modifier.weight(1f).clickable(indication = null, interactionSource = noRipple) { period = p },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            p.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
                            color = if (selected) textMain else textMuted.copy(alpha = 0.55f), modifier = Modifier.padding(vertical = 10.dp)
                        )
                        Box(Modifier.width(52.dp).height(2.dp).background(if (selected) TriangleBrandPurple else Color.Transparent))
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = line)
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = TriangleBrandPurple) }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error ?: "Something went wrong", color = MaterialTheme.colorScheme.error)
            }
            ranked.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No rankings yet", color = textMuted) }
            else -> {
                val rest = ranked.drop(3)
                LazyColumn(Modifier.fillMaxSize().background(listColor)) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().background(pageColor).padding(top = 18.dp, bottom = 18.dp, start = 8.dp, end = 8.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Order on the podium: 2nd, 1st (centre, larger and crowned), 3rd.
                            listOf(1, 0, 2).forEach { idx ->
                                val entry = ranked.getOrNull(idx)
                                Box(Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
                                    if (entry != null) {
                                        PodiumSpot(
                                            rank = idx + 1, entry = entry, points = entry.pointsFor(period), isMe = entry.uid == state.myUid,
                                            owe = state.oweAmounts[entry.uid], dark = dark, textMain = textMain, textMuted = textMuted,
                                            onClick = { onOpenProfile(entry.uid, entry.orgId) }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                    itemsIndexed(rest, key = { _, e -> "${e.orgId}-${e.uid}" }) { i, entry ->
                        RankRow(
                            rank = i + 4, entry = entry, points = entry.pointsFor(period), isMe = entry.uid == state.myUid,
                            owe = state.oweAmounts[entry.uid], dark = dark, textMain = textMain, textMuted = textMuted,
                            onClick = { onOpenProfile(entry.uid, entry.orgId) }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

/** "You Owe +10" / "Owes +10" line, or null when the two sides cancel out. */
private fun oweLine(owe: LeaderboardRepository.OweAmounts?): Pair<String, Color>? {
    val net = (owe?.youOwe ?: 0) - (owe?.heOwe ?: 0)
    return when {
        net > 0 -> "You Owe +$net" to LeaderboardColors.YouOwe
        net < 0 -> "Owes +${-net}" to LeaderboardColors.HeOwe
        else -> null
    }
}

@Composable
private fun PodiumSpot(
    rank: Int, entry: LeaderboardEntry, points: Int, isMe: Boolean, owe: LeaderboardRepository.OweAmounts?,
    dark: Boolean, textMain: Color, textMuted: Color, onClick: () -> Unit
) {
    val first = rank == 1
    val size = if (first) 84.dp else 64.dp
    val ring = if (isMe) TriangleBrandPurple else if (dark) Color(0xFF3A3A3C) else Color(0xFFE2E2E2)
    Column(
        Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (first) Text("👑", fontSize = 24.sp) else Text("$rank", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textMain)
        Spacer(Modifier.height(if (first) 2.dp else 8.dp))
        Box(Modifier.size(size + 10.dp).border(2.dp, ring, CircleShape).padding(5.dp)) {
            Avatar(entry.name, entry.photoUrl, size = size, backgroundColor = TriangleBrandPurple.copy(alpha = 0.18f), textColor = TriangleBrandPurple)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (isMe) "${entry.name} (You)" else entry.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textMain,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 6.dp)
        )
        val o = oweLine(owe)
        Text(o?.first ?: " ", fontSize = 11.sp, color = o?.second ?: textMuted, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(4.dp))
        Text("$points Points", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textMain)
    }
}

@Composable
private fun RankRow(
    rank: Int, entry: LeaderboardEntry, points: Int, isMe: Boolean, owe: LeaderboardRepository.OweAmounts?,
    dark: Boolean, textMain: Color, textMuted: Color, onClick: () -> Unit
) {
    val card = if (dark) Color(0xFF1C1C1E) else Color.White
    val shape = RoundedCornerShape(36.dp)
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$rank", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textMain, textAlign = TextAlign.Center, modifier = Modifier.width(44.dp))
        Row(
            Modifier
                .weight(1f)
                .shadow(6.dp, shape, ambientColor = Color(0x2294A8B8), spotColor = Color(0x2294A8B8))
                .clip(shape)
                .background(card)
                .then(if (isMe) Modifier.border(1.5.dp, TriangleBrandPurple, shape) else Modifier)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(entry.name, entry.photoUrl, size = 46.dp, backgroundColor = TriangleBrandPurple.copy(alpha = 0.18f), textColor = TriangleBrandPurple)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (isMe) "${entry.name} (You)" else entry.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val o = oweLine(owe)
                if (o != null) Text(o.first, fontSize = 12.sp, color = o.second, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Text("$points", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textMain)
            Text(" Points", fontSize = 12.sp, color = textMuted, modifier = Modifier.padding(end = 8.dp))
        }
    }
}
