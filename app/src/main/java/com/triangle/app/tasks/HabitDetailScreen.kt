package com.triangle.app.tasks

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.HabitPalette
import com.triangle.app.data.models.Habit
import com.triangle.app.ui.SvgPathIcon
import java.time.LocalDate

/** Native port of script.js's openHabitDetail() (shares the "ntd" layout with TaskDetailScreen). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(
    viewModel: TasksHabitsViewModel,
    habit: Habit,
    onEdit: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val completions = state.habitCompletions[habit.id] ?: emptyMap()
    val streak = viewModel.streakFor(habit)
    val doneToday = completions.containsKey(LocalDate.now().toString())

    val color = runCatching { Color(android.graphics.Color.parseColor(habit.color)) }.getOrDefault(MaterialTheme.colorScheme.primary)
    val svg = habit.iconSvg ?: HabitPalette.ICONS.getValue(HabitPalette.DEFAULT_ICON_KEY)

    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = color.copy(alpha = 0.14f))
            )
        }
    ) { scaffoldPadding ->
        Column(Modifier.fillMaxSize().padding(scaffoldPadding)) {
        Box(Modifier.fillMaxWidth().background(color.copy(alpha = 0.14f)).padding(bottom = 20.dp)) {
            Column {
                Column(Modifier.padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        SvgPathIcon(svg, tint = color, size = 36.dp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(habit.name, fontSize = 20.sp, fontWeight = FontWeight.Black, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text("🔥 ${streak.current}-day streak  ·  best ${streak.best}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                }
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (habit.description.isNotBlank()) {
                Text(habit.description, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            }

            Column {
                Text("Last 22 weeks", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                HabitDetailHeatmap(color = color, completions = completions)
            }

            Spacer(Modifier.height(8.dp))
            if (doneToday) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f)).padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Completed today", fontWeight = FontWeight.Bold, color = color)
                }
            } else {
                Button(onClick = { viewModel.completeHabitToday(habit) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Mark Done Today")
                }
            }

            TextButton(onClick = {
                viewModel.deleteHabit(habit.id)
                onBack()
            }) {
                Text("Delete habit", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
        }
        }
    }
}

@Composable
private fun HabitDetailHeatmap(color: Color, completions: Map<String, com.triangle.app.data.models.HabitCompletionEntry>) {
    val today = LocalDate.now()
    val cells = remember(today) { com.triangle.app.data.HabitStats.heatmapGrid(today) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(22),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.fillMaxWidth().height(60.dp)
    ) {
        items(cells) { day ->
            if (day == null) {
                Box(Modifier.size(9.dp))
            } else {
                val doneThatDay = completions.containsKey(day.toString())
                Box(
                    Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(color.copy(alpha = if (doneThatDay) 1f else 0.18f))
                        .size(9.dp)
                )
            }
        }
    }
}
