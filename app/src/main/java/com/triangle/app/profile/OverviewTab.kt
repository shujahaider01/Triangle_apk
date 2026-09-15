package com.triangle.app.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.charts.LineAreaChart
import com.triangle.app.charts.ProgressBarItem
import com.triangle.app.charts.ProgressBarList

/** Native port of renderInternProfile()'s Overview tab: 3 KPI cards, Performance Trend, Top Strengths. */
@Composable
fun OverviewTab(state: ProfileUiState, palette: ProfilePalette) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiCard(
                Modifier.weight(1f), palette, "Performance", "${state.performancePct}%",
                deltaText(state.performanceDelta, "pt"), ProfileColors.Purple
            )
            KpiCard(
                Modifier.weight(1f), palette, "XP This Month", state.monthXp.toString(),
                deltaText(state.xpDeltaPct, "%"), Color(0xFF06B6D4)
            )
            KpiCard(
                Modifier.weight(1f), palette, "Coins This Month", state.monthCoins.toString(),
                null, Color(0xFFFBBF24)
            )
        }

        Spacer(Modifier.height(16.dp))
        Text("Performance Trend", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.text)
        Text("Weekly completion rate this month", fontSize = 11.5.sp, color = palette.text3)
        Spacer(Modifier.height(10.dp))
        Card(palette) {
            if (state.trendPct.size >= 2) {
                LineAreaChart(
                    points = state.trendPct.map { it.toFloat() },
                    lineColor = ProfileColors.Purple,
                    maxValueOverride = 100f,
                    showGridLines = true,
                    modifier = Modifier.fillMaxWidth().height(140.dp)
                )
            } else {
                EmptyChartNote(palette)
            }
        }

        Spacer(Modifier.height(16.dp))
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
private fun KpiCard(modifier: Modifier, palette: ProfilePalette, label: String, value: String, delta: String?, accent: Color) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface2)
            .padding(12.dp, 14.dp)
    ) {
        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = palette.text2, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.Black, color = accent)
        if (delta != null) {
            Spacer(Modifier.height(2.dp))
            Text(delta, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = palette.text3)
        }
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

private fun deltaText(delta: Int?, unit: String): String? = when {
    delta == null -> null
    delta > 0 -> "+$delta$unit vs last month"
    delta < 0 -> "$delta$unit vs last month"
    else -> "No change vs last month"
}
