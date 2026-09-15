package com.triangle.app.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.HabitPalette
import com.triangle.app.data.HabitRepository
import com.triangle.app.data.SessionStore
import com.triangle.app.data.models.Habit
import com.triangle.app.data.models.HabitFrequency
import kotlinx.coroutines.launch
import java.time.LocalDate

private val DOW_LABELS = listOf("S", "M", "T", "W", "T", "F", "S") // JS Date.getDay(): 0=Sun..6=Sat

/**
 * Native port of the Individual-role habit create/edit form
 * (_renderHabitCreatePanel with no "Assign To" section — an Individual's
 * habits are always self-only, per the source app's own behavior). Only
 * "Everyday" and "Days of Week" frequency types are creatable natively for
 * Milestone 2 (a deliberate scope trim — "Days of Month" and "Per Period"
 * habits created via the WebView still render/complete correctly here,
 * since HabitStats/HabitFrequency handle all four types; they just aren't
 * offered in this creation form yet).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CreateEditHabitScreen(
    session: SessionStore.Session,
    existingHabit: Habit?,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val isNew = existingHabit == null

    var name by remember { mutableStateOf(existingHabit?.name ?: "") }
    var description by remember { mutableStateOf(existingHabit?.description ?: "") }
    var color by remember { mutableStateOf(existingHabit?.color ?: HabitPalette.COLORS.random()) }
    var iconKey by remember { mutableStateOf(HabitPalette.DEFAULT_ICON_KEY) }
    var iconSvg by remember { mutableStateOf(existingHabit?.iconSvg ?: HabitPalette.ICONS.getValue(HabitPalette.DEFAULT_ICON_KEY)) }
    var xp by remember { mutableStateOf(existingHabit?.xpPerCompletion ?: 5) }
    var isEveryday by remember { mutableStateOf(existingHabit?.frequency?.type != "daysOfWeek") }
    var selectedDays by remember { mutableStateOf((existingHabit?.frequency?.days ?: emptyList()).toSet()) }
    var saving by remember { mutableStateOf(false) }

    fun save() {
        if (name.isBlank() || saving) return
        saving = true
        val frequency = if (isEveryday) HabitFrequency("everyday") else HabitFrequency("daysOfWeek", days = selectedDays.sorted())
        val habit = (existingHabit ?: Habit(
            id = "h-${System.currentTimeMillis()}",
            name = "",
            assignedTo = listOf(session.uid),
            startDate = LocalDate.now().toString(),
            createdAt = System.currentTimeMillis(),
            createdBy = session.uid
        )).copy(
            name = name.trim(),
            description = description,
            color = color,
            iconSvg = iconSvg,
            xpPerCompletion = xp.coerceIn(1, 999),
            frequency = frequency
        )
        scope.launch {
            runCatching {
                if (isNew) HabitRepository.saveNewHabit(session.orgId, habit) else HabitRepository.updateHabit(session.orgId, habit)
            }
            saving = false
            onSaved()
        }
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(if (isNew) "New Habit" else "Edit Habit", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    TextButton(onClick = { save() }, enabled = name.isNotBlank() && !saving) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                }
            )
        }
    ) { scaffoldPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Habit name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())

            IconColorPicker(
                selectedColor = color,
                selectedIconKey = iconKey,
                onColorSelected = { color = it },
                onIconSelected = { key, svg -> iconKey = key; iconSvg = svg }
            )

            Column {
                Text("Frequency", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Every day" to true, "Specific days" to false).forEach { (label, everyday) ->
                        val active = isEveryday == everyday
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { isEveryday = everyday }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(label, fontSize = 13.sp, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (!isEveryday) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DOW_LABELS.forEachIndexed { dow, label ->
                            val active = selectedDays.contains(dow)
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { selectedDays = if (active) selectedDays - dow else selectedDays + dow },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, fontSize = 12.sp, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Column {
                Text("XP per completion", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconButton(onClick = { xp = (xp - 1).coerceAtLeast(1) }) { Icon(Icons.Default.Remove, contentDescription = "Decrease") }
                    Text(xp.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { xp = (xp + 1).coerceAtMost(999) }) { Icon(Icons.Default.Add, contentDescription = "Increase") }
                }
            }

            if (!isNew && existingHabit != null) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = {
                    scope.launch {
                        runCatching { HabitRepository.deleteHabit(session.orgId, existingHabit.id) }
                        onDeleted()
                    }
                }) {
                    Text("Delete habit", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
