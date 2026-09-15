package com.triangle.app.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.ui.theme.TriangleBrandPurple
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
                activePage = pagerState.currentPage,
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
                    .background(if (active) TriangleBrandPurple.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { onSelect(page) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (active) TriangleBrandPurple else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}

/**
 * Native port of _renderSharedHeader() — date label + performance badge,
 * the month-wide DateStrip (see DateStrip.kt), then the All/Due segmented
 * toggle and (Tasks pane only) the category pill row. `--accent` reads as
 * brand purple on this page in the source (body[data-page="internTasks"] {
 * --accent: var(--brand); }), not the global orange, so every accent here
 * uses TriangleBrandPurple.
 */
@Composable
private fun SharedHeader(state: TasksHabitsUiState, viewModel: TasksHabitsViewModel, activePage: Int, showCategoryFilter: Boolean) {
    val pct = if (activePage == 0) viewModel.tasksPctForDate(state.selectedDate) else viewModel.habitsPctForDate(state.selectedDate)
    val totalCount = if (activePage == 0) state.taskTotalCount else state.habitTotalCount
    val dueCount = if (activePage == 0) state.taskDueCount else state.habitDueCount
    val statusFilter = if (activePage == 0) state.taskStatusFilter else state.habitStatusFilter
    val setStatusFilter: (String) -> Unit = if (activePage == 0) viewModel::setTaskStatusFilter else viewModel::setHabitStatusFilter

    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val label = remember(state.selectedDate) {
                val today = java.time.LocalDate.now()
                when (state.selectedDate) {
                    today -> "Today"
                    today.minusDays(1) -> "Yesterday"
                    today.plusDays(1) -> "Tomorrow"
                    else -> state.selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
                }
            }
            Text(label, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Icon(
                if (activePage == 0) Icons.Default.BarChart else Icons.Default.Autorenew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("$pct%", fontSize = 17.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        }

        Spacer(Modifier.height(4.dp))
        DateStrip(
            selectedDate = state.selectedDate,
            brandColor = TriangleBrandPurple,
            pctForDate = { d -> if (activePage == 0) viewModel.tasksPctForDate(d) else viewModel.habitsPctForDate(d) },
            onSelect = viewModel::selectDate
        )

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusSegment(totalCount = totalCount, dueCount = dueCount, active = statusFilter, onSelect = setStatusFilter)
            if (showCategoryFilter) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    CATEGORIES.forEach { (label, icon) ->
                        CategoryPill(label, icon, state.categoryFilter == label) { viewModel.setCategoryFilter(label) }
                    }
                }
            }
        }
    }
}

private val CATEGORIES: List<Pair<String, ImageVector>> = listOf(
    "All" to Icons.Default.Apps,
    "Office" to Icons.Default.Work,
    "Academic" to Icons.Default.School,
    "Personal" to Icons.Default.Star
)

/** Matches .hd-status-group/.hd-status-btn — one pill split into "All (N)"/"Due (N)" segments. */
@Composable
private fun StatusSegment(totalCount: Int, dueCount: Int, active: String, onSelect: (String) -> Unit) {
    val dark = isSystemInDarkTheme()
    val border = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.18f else 0.13f)
    Row(
        Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, border, RoundedCornerShape(7.dp))
    ) {
        StatusSegmentItem("All ($totalCount)", active == "All") { onSelect("All") }
        Box(Modifier.fillMaxHeight().width(1.5.dp).background(border))
        StatusSegmentItem("Due ($dueCount)", active == "Due") { onSelect("Due") }
    }
}

@Composable
private fun StatusSegmentItem(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxHeight()
            .background(if (active) TriangleBrandPurple else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
            color = if (active) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
    }
}

/** Matches .hd-cat-pill/.hd-cat-active — icon + label, bordered pill; active = brand-purple border/text, not filled. */
@Composable
private fun CategoryPill(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val border = if (active) TriangleBrandPurple else MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.18f else 0.13f)
    Row(
        Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (active && dark) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .border(1.5.dp, border, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (active) TriangleBrandPurple else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(13.dp))
        Text(label, fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold, color = if (active) TriangleBrandPurple else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
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
                canComplete = state.selectedDate == java.time.LocalDate.now(),
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
