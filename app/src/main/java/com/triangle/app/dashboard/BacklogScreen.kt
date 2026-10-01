package com.triangle.app.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextDecoration
import com.triangle.app.ui.theme.triangleDarkTheme
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.SessionStore
import com.triangle.app.data.TaskRepository
import com.triangle.app.data.models.Task
import com.triangle.app.ui.components.LoadMoreFooter
import com.triangle.app.ui.components.rememberLazyPaging
import com.triangle.app.ui.theme.TriangleBrandPurple
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/** Which slice of the backlog is showing. The Home analytics cards open the Backlog already on the matching one. */
enum class TasksListMode(val label: String) {
    ALL("All"),
    PENDING("Pending"),
    OVERDUE("Overdue"),
    COMPLETED("Completed")
}

private enum class TaskState(val label: String, val color: Color) {
    COMPLETED("Completed", Color(0xFF22C55E)),
    OVERDUE("Overdue", Color(0xFFEF4444)),
    PENDING("Pending", Color(0xFFF59E0B))
}

private data class TaskLine(val task: Task, val state: TaskState)

/**
 * Backlog: every one of my tasks regardless of date, with All / Pending / Overdue / Completed pills (each with
 * its count) to narrow it. "Pending" means not done yet, so it includes overdue ones. The Home analytics cards
 * open it already on the matching pill; tapping a task opens the Tasks tab on that task.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacklogScreen(
    session: SessionStore.Session,
    mode: TasksListMode,
    onOpenTask: (String) -> Unit,
    onBack: () -> Unit
) {
    var selected by remember(mode) { mutableStateOf(mode) }
    var query by remember { mutableStateOf("") }
    val lines by remember(session.orgId) {
        combine(TaskRepository.tasksFlow(session.orgId), TaskRepository.completionsFlow(session.orgId)) { tasks, done ->
            val today = LocalDate.now().toString()
            tasks.filter { it.appliesTo(session.uid) && !it.isTemplate && !it.archived }.map { t ->
                val state = when {
                    "${t.id}-${session.uid}" in done -> TaskState.COMPLETED
                    t.dueDate != null && t.dueDate < today -> TaskState.OVERDUE
                    else -> TaskState.PENDING
                }
                TaskLine(t, state)
            }
        }
    }.collectAsState(initial = null)

    fun matches(line: TaskLine, m: TasksListMode) = when (m) {
        TasksListMode.ALL -> true
        TasksListMode.PENDING -> line.state != TaskState.COMPLETED // pending includes overdue
        TasksListMode.OVERDUE -> line.state == TaskState.OVERDUE
        TasksListMode.COMPLETED -> line.state == TaskState.COMPLETED
    }

    val q = query.trim()
    val all = lines?.let { l -> if (q.isEmpty()) l else l.filter { it.task.title.contains(q, ignoreCase = true) } }
    val shown = remember(all, selected) {
        all?.filter { matches(it, selected) }
            ?.sortedByDescending { it.task.dueDate ?: it.task.instanceDate ?: it.task.createdDate ?: "" }
    }
    // Date-wise sections: one header per day (newest day first, "No due date" last), the day's tasks under it.
    val rows = remember(shown) {
        val out = mutableListOf<Any>()
        // Dated days first, newest day at the top; tasks with no date at all go last.
        val byDate = shown.orEmpty().groupBy { lineDate(it) }
        byDate.keys.filterNotNull().sortedDescending().forEach { date ->
            out.add(dateHeader(date))
            out.addAll(byDate.getValue(date))
        }
        byDate[null]?.let { undated ->
            out.add(dateHeader(null))
            out.addAll(undated)
        }
        out
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backlog", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Search by task title.
            androidx.compose.material3.OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search tasks") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear search") }
                },
                singleLine = true,
                shape = RoundedCornerShape(6.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 6.dp)
            )
            // Filter pills with counts.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TasksListMode.entries.forEach { m ->
                    val count = all?.count { matches(it, m) }
                    val active = m == selected
                    Text(
                        if (count != null) "${m.label} ($count)" else m.label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (active) TriangleBrandPurple else Color.Transparent)
                            .border(1.dp, if (active) TriangleBrandPurple else MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                            .clickable { selected = m }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            when {
                shown == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                shown.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (q.isNotEmpty()) "No tasks match “$q”" else when (selected) {
                            TasksListMode.ALL -> "No tasks yet"
                            TasksListMode.PENDING -> "Nothing pending, you are all caught up"
                            TasksListMode.OVERDUE -> "Nothing overdue"
                            TasksListMode.COMPLETED -> "No completed tasks yet"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    val listState = rememberLazyListState()
                    val paging = rememberLazyPaging(listState, rows.size, 25, resetKey = selected to query)
                    // One light rounded card holding the rows (header label, then a row per task), like a plain checklist widget.
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = if (triangleDarkTheme()) Color(0xFF1F1F23) else Color(0xFFF7F7F7),
                        modifier = Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp)
                    ) {
                        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(vertical = 14.dp)) {
                            items(rows.take(paging.visible), key = { if (it is TaskLine) it.task.id else "h-$it" }) { row ->
                                if (row is TaskLine) {
                                    BacklogRow(row, onClick = { onOpenTask(row.task.id) })
                                } else {
                                    Text(
                                        row as String,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp)
                                    )
                                }
                            }
                            if (paging.hasMore) item(key = "load-more") { LoadMoreFooter() }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One checklist row: a thin coloured bar at the left, the title (struck through once done), and a round check on
 * the right: filled with the task's colour when completed, an outline when not. Overdue rows get a soft red wash
 * across the row, the same way unread notifications get a soft tint.
 */
@Composable
private fun BacklogRow(line: TaskLine, onClick: () -> Unit) {
    val t = line.task
    val accent = remember(t.iconColor) {
        runCatching { Color(android.graphics.Color.parseColor(t.iconColor ?: "#9E9E9E")) }.getOrDefault(Color(0xFF9E9E9E))
    }
    val done = line.state == TaskState.COMPLETED
    val overdue = line.state == TaskState.OVERDUE
    // Dark tick on light colours (like the yellow one), white tick on dark ones.
    val tickOnFill = if (accent.luminance() > 0.6f) Color.Black else Color.White

    Column(Modifier.fillMaxWidth().background(if (overdue) Color(0xFFEF4444).copy(alpha = 0.10f) else Color.Transparent)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(3.dp).height(26.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            Spacer(Modifier.width(12.dp))
            Text(
                t.title,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (done) accent else Color.Transparent)
                    .border(if (done) 0.dp else 2.dp, accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = if (done) "Completed" else "Not completed",
                    tint = if (done) tickOnFill else accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
}

/** The date a task is filed under: its due date, else the day it was generated/created; null = no date at all. */
private fun lineDate(line: TaskLine): String? = line.task.dueDate ?: line.task.instanceDate ?: line.task.createdDate

private fun dateHeader(date: String?): String {
    val d = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return "No date"
    val today = LocalDate.now()
    return when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> d.format(java.time.format.DateTimeFormatter.ofPattern(if (d.year == today.year) "EEE, d MMM" else "EEE, d MMM yyyy"))
    }
}
