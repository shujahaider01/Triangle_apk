package com.triangle.app.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.charts.BarChart
import com.triangle.app.charts.DonutChart
import com.triangle.app.charts.LineAreaChart
import com.triangle.app.charts.ProgressBarItem
import com.triangle.app.charts.ProgressBarList
import com.triangle.app.data.ProfileStats
import java.time.Month

private val MonthNames = Month.entries.map { it.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } }

/** Native port of renderInternProfile()'s Analytics tab: 5 chart cards, each faithful to script.js's ported computations. */
@Composable
fun AnalyticsTab(
    state: ProfileUiState,
    palette: ProfilePalette,
    onMonthChange: (ProfileStats.MonthRef) -> Unit,
    onYearChange: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp)) {
        MonthPicker(state.analyticsRef, palette, onMonthChange)
        Spacer(Modifier.height(14.dp))

        SectionTitle("Weekly Productivity", palette)
        Card(palette) {
            val wp = state.weeklyProductivity
            if (wp != null && wp.weeks.isNotEmpty()) {
                BarChart(
                    wp.weeks.map { it.toFloat() },
                    ProfileColors.Purple,
                    maxValueOverride = 100f,
                    labels = wp.weeks.indices.map { "W${it + 1}" },
                    valueLabels = wp.weeks.map { "$it%" },
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text("This month: ${wp.score}%", fontSize = 11.5.sp, color = palette.text3)
            } else EmptyChartNote(palette)
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Tasks by Category", palette)
        Card(palette) {
            val tbc = state.tasksByCategory
            if (tbc != null && tbc.total > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(
                        segments = tbc.cats.map { it.count.toFloat() to Color(android.graphics.Color.parseColor(it.colorHex)) },
                        modifier = Modifier.size(120.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${tbc.total}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = palette.text)
                            Text("tasks", fontSize = 10.sp, color = palette.text3)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        tbc.cats.forEach { c ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                                Box(Modifier.size(9.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(c.colorHex))))
                                Spacer(Modifier.width(6.dp))
                                Text("${c.label} · ${c.pct}%", fontSize = 12.sp, color = palette.text2)
                            }
                        }
                    }
                }
            } else EmptyChartNote(palette)
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Task Status Distribution", palette)
        Card(palette) {
            val tsd = state.taskStatusDistribution
            if (tsd != null && tsd.total > 0) {
                ProgressBarList(tsd.statuses.map { ProgressBarItem(it.label, "${it.count}", it.barFraction, Color(android.graphics.Color.parseColor(it.colorHex))) })
            } else EmptyChartNote(palette)
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Overdue Tasks Timeline", palette)
        Card(palette) {
            if (state.overdueTimeline.size >= 2) {
                LineAreaChart(
                    points = state.overdueTimeline.map { it.count.toFloat() },
                    lineColor = Color(0xFFF43F5E),
                    labels = state.overdueTimeline.map { it.label },
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text("Overdue tasks per week — higher means more piled up that week.", fontSize = 11.sp, color = palette.text3)
            } else EmptyChartNote(palette)
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Yearly Performance", palette)
            YearPicker(state.analyticsYear, palette, onYearChange)
        }
        Card(palette) {
            val mp = state.monthlyPerformance
            if (mp != null) {
                LineAreaChart(
                    points = mp.months.map { it.pct.toFloat() },
                    lineColor = Color(0xFF3B82F6),
                    maxValueOverride = 100f,
                    showDots = true,
                    labels = mp.months.map { it.label },
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatBox("Best Month", mp.best.label, palette)
                    StatBox("Avg Score", "${mp.avgScore}%", palette)
                    StatBox("Total Tasks", "${mp.totalTasks}", palette)
                }
            } else EmptyChartNote(palette)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SectionTitle(title: String, palette: ProfilePalette) {
    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.text, modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun StatBox(label: String, value: String, palette: ProfilePalette) {
    Column {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Black, color = palette.text)
        Text(label, fontSize = 10.5.sp, color = palette.text3)
    }
}

@Composable
private fun LegendRow(items: List<Pair<String, Color>>, palette: ProfilePalette) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
                Text(label, fontSize = 11.5.sp, color = palette.text2)
            }
        }
    }
}

@Composable
private fun MonthPicker(ref: ProfileStats.MonthRef, palette: ProfilePalette, onChange: (ProfileStats.MonthRef) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.surface2).padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.ChevronLeft, contentDescription = "Previous month", tint = palette.text2,
            modifier = Modifier.clickable {
                onChange(if (ref.month == 0) ProfileStats.MonthRef(ref.year - 1, 11) else ProfileStats.MonthRef(ref.year, ref.month - 1))
            }
        )
        Text("${MonthNames[ref.month]} ${ref.year}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
        Icon(
            Icons.Default.ChevronRight, contentDescription = "Next month", tint = palette.text2,
            modifier = Modifier.clickable {
                onChange(if (ref.month == 11) ProfileStats.MonthRef(ref.year + 1, 0) else ProfileStats.MonthRef(ref.year, ref.month + 1))
            }
        )
    }
}

@Composable
private fun YearPicker(year: Int, palette: ProfilePalette, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous year", tint = palette.text2, modifier = Modifier.clickable { onChange(year - 1) })
        Text("$year", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.text, modifier = Modifier.padding(horizontal = 4.dp))
        Icon(Icons.Default.ChevronRight, contentDescription = "Next year", tint = palette.text2, modifier = Modifier.clickable { onChange(year + 1) })
    }
}
