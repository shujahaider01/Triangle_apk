package com.triangle.app.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.Badges

/** Native port of renderInternProfile()'s Achievement tab: Personal Records row + 14-badge Awards grid. */
@Composable
fun AchievementTab(state: ProfileUiState, palette: ProfilePalette) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Text("Personal Records", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.text, modifier = Modifier.padding(bottom = 10.dp))
        val records = listOf(
            "Best Streak" to "${state.bestStreak}",
            "Best Rank" to (state.weeklyRankRecord?.bestRankEver?.let { "#$it" } ?: "—"),
            "Weeks at #1" to "${state.weeklyRankRecord?.firstCount ?: 0}",
            "Most XP in a Day" to "${state.mostXpInADay}",
            "Tasks Done" to "${state.totalTasksDone}",
            "Coins Earned" to "${state.totalCoinsEarned}"
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().height(((records.size + 1) / 2 * 74).dp)
        ) {
            items(records) { (label, value) ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.surface2).padding(12.dp)
                ) {
                    Text(value, fontSize = 18.sp, fontWeight = FontWeight.Black, color = ProfileColors.Purple)
                    Spacer(Modifier.height(4.dp))
                    Text(label, fontSize = 11.sp, color = palette.text3)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Awards", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.text, modifier = Modifier.padding(bottom = 10.dp))
        Badges.Group.entries.forEach { group ->
            val groupBadges = state.badges.filter { it.group == group }
            if (groupBadges.isNotEmpty()) {
                Text(group.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.text2, modifier = Modifier.padding(bottom = 8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth().height((((groupBadges.size + 2) / 3) * 92).dp)
                ) {
                    items(groupBadges) { badge -> BadgeTile(badge, palette) }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun BadgeTile(badge: Badges.Badge, palette: ProfilePalette) {
    val accent = Color(android.graphics.Color.parseColor(badge.colorHex))
    val alpha = if (badge.earned) 1f else 0.4f
    Column(Modifier.aspectRatio(0.85f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(accent.copy(alpha = if (badge.earned) 0.18f else 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Text(badge.emoji, fontSize = 20.sp, modifier = Modifier.alpha(alpha), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(6.dp))
        Text(badge.label, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = palette.text.copy(alpha = alpha), textAlign = TextAlign.Center, maxLines = 2)
        val progressLabel = badge.timesLabel ?: badge.progressVal?.let { v -> badge.progressMax?.let { m -> "$v/$m" } }
        if (progressLabel != null) {
            Text(progressLabel, fontSize = 9.5.sp, color = palette.text3, textAlign = TextAlign.Center)
        }
    }
}
