package com.triangle.app.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime

/**
 * Material3 has no built-in TimePickerDialog (unlike DatePickerDialog) — this
 * wraps TimePicker in an AlertDialog, styled with the same confirm/dismiss
 * TextButton pair the existing due-date DatePickerDialog uses, so the two
 * pickers read as one consistent style.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTimePickerDialog(
    initial: LocalTime?,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit
) {
    val now = LocalTime.now()
    val state = rememberTimePickerState(
        initialHour = initial?.hour ?: now.hour,
        initialMinute = initial?.minute ?: now.minute,
        is24Hour = false
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) }
    )
}

/** "HH:mm" (24h, matches the model's stored format) -> a human-readable "h:mm AM/PM" for display. */
fun formatReminderTime(hhmm: String?): String? =
    hhmm?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        ?.let { java.time.format.DateTimeFormatter.ofPattern("h:mm a").format(it) }

/**
 * Shared "Reminder" section for the Create/Edit Task and Habit screens: an
 * enable/disable Switch plus, when on, a button showing the picked time.
 * `disabledHint`, when non-null, replaces the time row with an explanatory
 * line instead (used by the Task screen when there's no due date to anchor
 * a one-shot reminder to) and forces the switch off.
 */
@Composable
fun ReminderSection(
    enabled: Boolean,
    time: String?,
    disabledHint: String? = null,
    onToggle: (Boolean) -> Unit,
    onTimeClick: () -> Unit
) {
    Column {
        Text("Reminder", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (disabledHint != null) disabledHint
                else if (enabled) (formatReminderTime(time) ?: "Set a time") else "Off",
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            if (disabledHint == null) {
                if (enabled) TextButton(onClick = onTimeClick) { Text(if (time == null) "Set" else "Change") }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
        }
    }
}
