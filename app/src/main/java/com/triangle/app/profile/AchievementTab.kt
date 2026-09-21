package com.triangle.app.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.Badges

/** Native port of renderInternProfile()'s Achievement tab: Personal Records row + 13-badge Awards grid. */
@Composable
fun AchievementTab(state: ProfileUiState, palette: ProfilePalette) {
    var selectedBadge by remember { mutableStateOf<Badges.Badge?>(null) }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Text("Personal Records", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.text, modifier = Modifier.padding(bottom = 10.dp))
        // "Weeks at #1" replaced with a combined podium-finish count (1st +
        // 2nd + 3rd place weeks) — the big value + small caption below it
        // read together as "9 times on podium", matching every other card's
        // number-then-caption shape instead of needing its own styling.
        val podiumCount = state.weeklyRankRecord?.let { it.firstCount + it.secondCount + it.thirdCount } ?: 0
        val records = listOf(
            "Best Streak" to "${state.bestStreak}",
            "times on podium" to "$podiumCount",
            "Most XP in a Day" to "${state.mostXpInADay}",
            "Tasks Done" to "${state.totalTasksDone}"
        )
        // Plain, non-lazy 2-column grid (not LazyVerticalGrid) — same reasoning
        // as DashboardScreen's Analytics cards: a small fixed list doesn't need
        // its own scrollable container, and giving it one made the whole
        // Achievement tab feel like it had scrolling everywhere at once
        // (this grid, each Awards group's grid, and the tab's own outer
        // scroll all fighting for the same drag gesture).
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            records.chunked(2).forEach { rowRecords ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowRecords.forEach { (label, value) ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(palette.surface2).padding(12.dp)
                        ) {
                            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Black, color = ProfileColors.Purple)
                            Spacer(Modifier.height(4.dp))
                            Text(label, fontSize = 11.sp, color = palette.text3)
                        }
                    }
                    if (rowRecords.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Awards", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.text, modifier = Modifier.padding(bottom = 10.dp))
        Badges.Group.entries.forEach { group ->
            val groupBadges = state.badges.filter { it.group == group }
            if (groupBadges.isNotEmpty()) {
                Text(group.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.text2, modifier = Modifier.padding(bottom = 8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    groupBadges.chunked(3).forEach { rowBadges ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowBadges.forEach { badge ->
                                Box(Modifier.weight(1f)) { BadgeTile(badge, palette, onClick = { selectedBadge = badge }) }
                            }
                            repeat(3 - rowBadges.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    selectedBadge?.let { badge ->
        AchievementDetailSheet(badge = badge, palette = palette, onDismiss = { selectedBadge = null })
    }
}

@Composable
private fun BadgeTile(badge: Badges.Badge, palette: ProfilePalette, onClick: () -> Unit) {
    val accent = Color(android.graphics.Color.parseColor(badge.colorHex))
    val alpha = if (badge.earned) 1f else 0.4f
    Column(
        Modifier.aspectRatio(0.85f).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(46.dp)
                .then(if (badge.earned) Modifier.shadow(4.dp, CircleShape, clip = false) else Modifier)
                .clip(CircleShape)
                .then(
                    if (badge.earned) Modifier.background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.65f))))
                    else Modifier.background(palette.surface2).border(1.5.dp, palette.border, CircleShape)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                badge.icon,
                contentDescription = badge.label,
                tint = if (badge.earned) Color.White else palette.text3,
                modifier = Modifier.size(22.dp).alpha(if (badge.earned) 1f else 0.7f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(badge.label, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = palette.text.copy(alpha = alpha), textAlign = TextAlign.Center, maxLines = 2)
        val progressLabel = badge.timesLabel ?: badge.progressVal?.let { v -> badge.progressMax?.let { m -> "$v/$m" } }
        if (progressLabel != null) {
            Text(progressLabel, fontSize = 9.5.sp, color = palette.text3, textAlign = TextAlign.Center)
        }
    }
}

/** "General detail" sheet opened by tapping an award — bigger badge, earned/locked status, description, progress and (if earned) the date it was unlocked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AchievementDetailSheet(badge: Badges.Badge, palette: ProfilePalette, onDismiss: () -> Unit) {
    val accent = Color(android.graphics.Color.parseColor(badge.colorHex))
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(84.dp)
                    .then(if (badge.earned) Modifier.shadow(8.dp, CircleShape, clip = false) else Modifier)
                    .clip(CircleShape)
                    .then(
                        if (badge.earned) Modifier.background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.65f))))
                        else Modifier.background(palette.surface2).border(2.dp, palette.border, CircleShape)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    badge.icon,
                    contentDescription = null,
                    tint = if (badge.earned) Color.White else palette.text3,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(badge.label, fontSize = 19.sp, fontWeight = FontWeight.Black, color = palette.text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background((if (badge.earned) accent else palette.text3).copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    if (badge.earned) "Earned" else "Locked",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (badge.earned) accent else palette.text3
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(badge.desc, fontSize = 14.sp, color = palette.text2, textAlign = TextAlign.Center, lineHeight = 20.sp)

            if (badge.earnedDate != null) {
                Spacer(Modifier.height(10.dp))
                Text("Earned on ${badge.earnedDate}", fontSize = 12.5.sp, color = palette.text3, textAlign = TextAlign.Center)
            }

            val progressVal = badge.progressVal
            val progressMax = badge.progressMax
            if (progressVal != null && progressMax != null && progressMax > 0) {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Progress", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.text2)
                    Text("$progressVal/$progressMax", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.text)
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(palette.surface2)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((progressVal.toFloat() / progressMax).coerceIn(0f, 1f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(accent)
                    )
                }
            } else if (badge.timesLabel != null) {
                Spacer(Modifier.height(10.dp))
                Text(badge.timesLabel, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = palette.text2, textAlign = TextAlign.Center)
            }
        }
    }
}
