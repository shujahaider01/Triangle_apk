package com.triangle.app.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.charts.ProgressBarItem
import com.triangle.app.charts.ProgressBarList
import com.triangle.app.data.Badges

/** Native port of renderInternProfile()'s Overview tab: 4 equal-size KPI cards, Recent Awards, Top Strengths. */
@Composable
fun OverviewTab(state: ProfileUiState, palette: ProfilePalette) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp)) {
        // Same content shape (label + big value, no delta line) on all four
        // cards, plus IntrinsicSize.Min rows + fillMaxHeight children, so
        // every card is guaranteed the same size regardless of label length.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard(Modifier.weight(1f).fillMaxHeight(), palette, "Performance", "${state.performancePct}%", ProfileColors.Purple)
                KpiCard(Modifier.weight(1f).fillMaxHeight(), palette, "Total XP", "${state.points}", Color(0xFF06B6D4))
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard(Modifier.weight(1f).fillMaxHeight(), palette, "Tasks Completed", "${state.totalTasksDone}", Color(0xFF22C55E))
                KpiCard(Modifier.weight(1f).fillMaxHeight(), palette, "Longest Streak", "${state.bestStreak}", Color(0xFFF97316))
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Recent Awards", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.text)
        Spacer(Modifier.height(10.dp))
        val recentAwards = state.badges.filter { it.earned }.sortedByDescending { it.earnedDate ?: "" }.take(6)
        if (recentAwards.isEmpty()) {
            Card(palette) { EmptyChartNote(palette) }
        } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                recentAwards.forEach { badge -> RecentAwardCell(badge, palette) }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Top Strengths", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.text)
        Spacer(Modifier.height(10.dp))
        Card(palette) {
            ProgressBarList(
                items = listOf(
                    ProgressBarItem("Tasks", "${state.taskCompletionPct}%", state.taskCompletionPct / 100f, Color(0xFF7C3AED)),
                    ProgressBarItem("Habits", "${state.habitStrengthPct}%", state.habitStrengthPct / 100f, Color(0xFF22C55E))
                )
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun KpiCard(modifier: Modifier, palette: ProfilePalette, label: String, value: String, accent: Color) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface2)
            .padding(12.dp, 14.dp)
    ) {
        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = palette.text2, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.Black, color = accent)
    }
}

@Composable
private fun RecentAwardCell(badge: Badges.Badge, palette: ProfilePalette) {
    val accent = Color(android.graphics.Color.parseColor(badge.colorHex))
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
        Box(
            Modifier
                .size(48.dp)
                .shadow(4.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.65f)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(badge.icon, contentDescription = badge.label, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(badge.label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = palette.text, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
internal fun Card(palette: ProfilePalette, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface2)
            .padding(14.dp)
    ) { content() }
}

@Composable
internal fun EmptyChartNote(palette: ProfilePalette) {
    Text("No data yet", fontSize = 12.5.sp, color = palette.text3, modifier = Modifier.padding(vertical = 24.dp))
}
