package com.triangle.app.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.triangle.app.data.AssignmentRepository
import com.triangle.app.data.HabitRepository
import com.triangle.app.data.SessionStore
import com.triangle.app.tasks.formPageColor
import com.triangle.app.ui.theme.triangleDarkTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ROW_HEIGHT = 62.dp

/** One row of the Sorting list: id is the habit id (own) or the group id (a habit I sent to others). */
private data class SortItem(val id: String, val name: String, val createdAt: Long)

/**
 * Settings > Sorting: drag the handle of a habit (mine or one I sent) to move it. The order is
 * saved (narrow write to this user's habitOrder list) each time a drag ends
 * and is what the Habits list uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitSortingScreen(session: SessionStore.Session, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val dark = triangleDarkTheme()
    val items: SnapshotStateList<SortItem> = remember { mutableStateListOf() }
    var loaded by remember { mutableStateOf(false) }

    // Initial list: the saved order, with habits that aren't in it yet on top (newest first), like the Habits list.
    LaunchedEffect(session.uid) {
        // Habits assigned to me (solo + received) plus the ones I sent to others — all of them appear in the Habits list.
        val own = HabitRepository.habitsFlow(session.orgId).first()
            .filter { !it.archived && it.assignedTo.contains(session.uid) }
            .map { SortItem(it.id, it.name, it.createdAt) }
        val sent = runCatching { AssignmentRepository.readAssignedGroups(session.uid).habitGroups }
            .getOrDefault(emptyList())
            .map { SortItem(it.itemId, it.habit.name, it.habit.createdAt) }
        val order = HabitRepository.habitOrderFlow(session.orgId, session.uid).first()
        val sorted = (own + sent).sortedWith(
            compareBy<SortItem> { order.indexOf(it.id) }.thenByDescending { it.createdAt }
        )
        items.clear()
        items.addAll(sorted)
        loaded = true
    }

    val rowPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    fun save() {
        val ids = items.map { it.id }
        scope.launch { runCatching { HabitRepository.setHabitOrder(session.orgId, session.uid, ids) } }
    }

    val tintRow = if (dark) Color(0xFF26261E) else Color(0xFFFFF9E3)
    val plainRow = MaterialTheme.colorScheme.surface

    Scaffold(
        containerColor = formPageColor(),
        topBar = {
            TopAppBar(
                title = { Text("Reorder", fontSize = 22.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = formPageColor()),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            if (loaded && items.isEmpty()) {
                Text("No habits to sort yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = plainRow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    items.forEach { habit ->
                        key(habit.id) {
                            val index = items.indexOfFirst { it.id == habit.id }
                            val dragging = draggingId == habit.id
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(ROW_HEIGHT)
                                    .zIndex(if (dragging) 1f else 0f)
                                    .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                                    .then(if (dragging) Modifier.shadow(8.dp, RoundedCornerShape(12.dp)) else Modifier)
                                    .background(if (index % 2 == 0) tintRow else plainRow)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.pointerInput(habit.id) {
                                        detectDragGestures(
                                            onDragStart = { draggingId = habit.id; dragOffset = 0f },
                                            onDragEnd = { draggingId = null; dragOffset = 0f; save() },
                                            onDragCancel = { draggingId = null; dragOffset = 0f; save() },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount.y
                                                val from = items.indexOfFirst { it.id == habit.id }
                                                val target = (from + (dragOffset / rowPx).roundToInt()).coerceIn(0, items.lastIndex)
                                                if (target != from) {
                                                    items.add(target, items.removeAt(from))
                                                    dragOffset -= (target - from) * rowPx
                                                }
                                            }
                                        )
                                    }
                                ) {
                                    Icon(Icons.Default.DragHandle, contentDescription = "Drag to reorder", modifier = Modifier.padding(6.dp))
                                }
                                Spacer(Modifier.width(14.dp))
                                Text(habit.name, fontSize = 18.sp, fontWeight = FontWeight.Normal, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
