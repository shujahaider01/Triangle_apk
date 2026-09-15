package com.triangle.app.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.ui.theme.TriangleOrange
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/**
 * Native port of script.js's renderInternTasks() — a swipeable Tasks |
 * Habits two-pane page (HorizontalPager, matching the WebView's
 * scroll-snap two-pane layout) with a shared date/category-filter header.
 * See TasksHabitsViewModel for the shared state both panes read from.
 */
@Composable
fun TasksHabitsScreen(
    viewModel: TasksHabitsViewModel,
    onOpenTaskDetail: (String) -> Unit,
    onOpenHabitDetail: (String) -> Unit,
    onCreateTask: () -> Unit,
    onCreateHabit: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { if (pagerState.currentPage == 0) onCreateTask() else onCreateHabit() }) {
                Icon(Icons.Default.Add, contentDescription = "Create")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Spacer(Modifier.width(4.dp))
                PaneTabs(activePage = pagerState.currentPage, onSelect = { page ->
                    scope.launch { pagerState.animateScrollToPage(page) }
                })
            }

            SharedHeader(
                state = state,
                viewModel = viewModel,
                showCategoryFilter = pagerState.currentPage == 0
            )

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                if (page == 0) {
                    TasksPane(state = state, viewModel = viewModel, onOpenDetail = onOpenTaskDetail)
                } else {
                    HabitsPane(state = state, viewModel = viewModel, onOpenDetail = onOpenHabitDetail)
                }
            }
        }
    }
}

@Composable
private fun PaneTabs(activePage: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("Tasks" to 0, "Habits" to 1).forEach { (label, page) ->
            val active = activePage == page
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) TriangleOrange.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { onSelect(page) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (active) TriangleOrange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun SharedHeader(state: TasksHabitsUiState, viewModel: TasksHabitsViewModel, showCategoryFilter: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.selectDate(state.selectedDate.minusDays(1)) }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous day")
            }
            val label = remember(state.selectedDate) {
                val today = java.time.LocalDate.now()
                when (state.selectedDate) {
                    today -> "Today"
                    today.minusDays(1) -> "Yesterday"
                    today.plusDays(1) -> "Tomorrow"
                    else -> state.selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
                }
            }
            Text(label, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.selectDate(state.selectedDate.plusDays(1)) }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next day")
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            FilterPill("All", state.taskStatusFilter == "All") { viewModel.setTaskStatusFilter("All") }
            FilterPill("Due", state.taskStatusFilter == "Due") { viewModel.setTaskStatusFilter("Due") }
            if (showCategoryFilter) {
                Spacer(Modifier.width(10.dp))
                listOf("All", "Office", "Academic", "Personal").forEach { cat ->
                    FilterPill(cat, state.categoryFilter == cat) { viewModel.setCategoryFilter(cat) }
                }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (active) TriangleOrange else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (active) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun TasksPane(state: TasksHabitsUiState, viewModel: TasksHabitsViewModel, onOpenDetail: (String) -> Unit) {
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (state.visibleTasks.isEmpty()) {
        EmptyState(emoji = "🎉", message = "All clear for this day")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(state.visibleTasks, key = { it.id }) { task ->
            val done = viewModel.isDone(task, state.completions)
            TaskRowCard(
                task = task,
                done = done,
                onClick = { onOpenDetail(task.id) },
                onToggleDone = { viewModel.completeTaskWithUndo(task) }
            )
        }
    }
}

@Composable
private fun HabitsPane(state: TasksHabitsUiState, viewModel: TasksHabitsViewModel, onOpenDetail: (String) -> Unit) {
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (state.visibleHabits.isEmpty()) {
        EmptyState(emoji = "✨", message = "Nothing scheduled for this day")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(state.visibleHabits, key = { it.id }) { habit ->
            val dateStr = state.selectedDate.toString()
            val completionsForHabit = state.habitCompletions[habit.id] ?: emptyMap()
            HabitCard(
                habit = habit,
                completions = completionsForHabit,
                streak = viewModel.streakFor(habit).current,
                doneToday = completionsForHabit.containsKey(dateStr),
                onClick = { onOpenDetail(habit.id) },
                onCompleteToday = { viewModel.completeHabitToday(habit) }
            )
        }
    }
}

@Composable
private fun EmptyState(emoji: String, message: String) {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(emoji, fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(message, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}
