package com.triangle.app.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.HabitRepository
import com.triangle.app.data.SessionStore
import com.triangle.app.data.TaskRepository
import kotlinx.coroutines.launch

/**
 * Settings > Archived Items — the task/habit copies this user archived from
 * their own Detail screen (the recipient-side alternative to deleting an item
 * someone else assigned them). Reads the org's tasks/habits flows directly,
 * not through TasksHabitsViewModel, since that ViewModel hides archived
 * copies at ingestion and lives in the Tasks nav graph, not Settings'.
 * Unarchiving only flips the flag back — the item reappears in the normal
 * Tasks/Habits panes on the next emission.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedItemsScreen(session: SessionStore.Session, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val tasks by TaskRepository.tasksFlow(session.orgId).collectAsState(initial = emptyList())
    val habits by HabitRepository.habitsFlow(session.orgId).collectAsState(initial = emptyList())
    val archivedTasks = tasks.filter { it.archived }
    val archivedHabits = habits.filter { it.archived }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Archived Items", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        if (archivedTasks.isEmpty() && archivedHabits.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Nothing archived", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (archivedTasks.isNotEmpty()) {
                SectionLabel("Archived Tasks")
                archivedTasks.forEach { task ->
                    ArchivedRow(
                        title = task.title,
                        onUnarchive = { scope.launch { runCatching { TaskRepository.updateTask(session.orgId, task.copy(archived = false)) } } }
                    )
                }
            }
            if (archivedHabits.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                SectionLabel("Archived Habits")
                archivedHabits.forEach { habit ->
                    ArchivedRow(
                        title = habit.name,
                        onUnarchive = { scope.launch { runCatching { HabitRepository.updateHabit(session.orgId, habit.copy(archived = false)) } } }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ArchivedRow(title: String, onUnarchive: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        TextButton(onClick = onUnarchive) { Text("Unarchive") }
    }
}
