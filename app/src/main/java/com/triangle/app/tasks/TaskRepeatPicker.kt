package com.triangle.app.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.models.RepeatRule
import java.time.LocalDate

private val DOW = listOf("S", "M", "T", "W", "T", "F", "S") // 0=Sun..6=Sat, same as RepeatRule.days

private val MODES = listOf(
    "Everyday" to "Days",
    "Specific days" to "Weeks",
    "Days of month" to "DaysOfMonth",
    "Some days" to "PerPeriod"
)

/**
 * "Repeat" toggle (off by default) for a NEW task. Turning it on reveals the
 * same four frequency modes the habit form offers: Everyday, Specific days,
 * Days of month, Some days per week/month. They are stored as a RepeatRule
 * that RepeatTaskEngine expands into one task per matching day.
 * [rule] == null means the toggle is off.
 */
@Composable
fun TaskRepeatPicker(rule: RepeatRule?, onChange: (RepeatRule?) -> Unit) {
    FormToggleCard(
        title = "Frequency",
        subtitle = "Repeat this task on a schedule",
        checked = rule != null,
        onCheckedChange = { on -> onChange(if (on) RepeatRule("Days", 1) else null) }
    ) {
        if (rule != null) RepeatDetails(rule, onChange)
    }
}

@Composable
private fun RepeatDetails(rule: RepeatRule, onChange: (RepeatRule?) -> Unit) {
    Column {

        Spacer(Modifier.height(10.dp))
        var expanded by remember { mutableStateOf(false) }
        val currentLabel = MODES.firstOrNull { it.second == rule.freq }?.first ?: "Everyday"
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(currentLabel, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                MODES.forEach { (label, freq) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            expanded = false
                            if (rule.freq != freq) onChange(
                                when (freq) {
                                    "Weeks" -> RepeatRule("Weeks", 1, days = listOf(LocalDate.now().dayOfWeek.value % 7))
                                    "DaysOfMonth" -> RepeatRule("DaysOfMonth", dates = listOf(LocalDate.now().dayOfMonth))
                                    "PerPeriod" -> RepeatRule("PerPeriod", count = 3, unit = "week")
                                    else -> RepeatRule("Days", 1)
                                }
                            )
                        }
                    )
                }
            }
        }

        when (rule.freq) {
            "Weeks" -> {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DOW.forEachIndexed { dow, label ->
                        val active = rule.days.contains(dow)
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    // Keep at least one day, otherwise the rule could never fire.
                                    val next = if (active) rule.days - dow else rule.days + dow
                                    if (next.isNotEmpty()) onChange(rule.copy(days = next.sorted()))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, fontSize = 12.sp, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            "DaysOfMonth" -> {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..31).chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            week.forEach { day ->
                                val active = rule.dates.contains(day)
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            val next = if (active) rule.dates - day else rule.dates + day
                                            if (next.isNotEmpty()) onChange(rule.copy(dates = next.sorted()))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(day.toString(), fontSize = 12.sp, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            "PerPeriod" -> {
                val max = if (rule.unit == "month") 30 else 7
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { onChange(rule.copy(count = (rule.count - 1).coerceAtLeast(1))) }) {
                        Icon(Icons.Default.Remove, contentDescription = "Fewer days")
                    }
                    Text(rule.count.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
                    IconButton(onClick = { onChange(rule.copy(count = (rule.count + 1).coerceAtMost(max))) }) {
                        Icon(Icons.Default.Add, contentDescription = "More days")
                    }
                    Spacer(Modifier.width(2.dp))
                    Text("days per", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    listOf("week" to "Week", "month" to "Month").forEach { (value, label) ->
                        val active = rule.unit == value
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onChange(rule.copy(unit = value, count = rule.count.coerceAtMost(if (value == "month") 30 else 7))) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(label, fontSize = 12.5.sp, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // The days are spread evenly (you can't pick them), so show exactly which ones this will be.
                val n = rule.count.coerceAtLeast(1)
                val falls = if (rule.unit == "month") {
                    val len = 30
                    "Falls on day " + (0 until n.coerceAtMost(len)).map { (it * len) / n.coerceAtMost(len) + 1 }.joinToString(", ") + " of each month"
                } else {
                    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    "Falls on " + (0 until n.coerceAtMost(7)).map { names[(it * 7) / n.coerceAtMost(7)] }.joinToString(", ") + " each week"
                }
                Text(falls, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
