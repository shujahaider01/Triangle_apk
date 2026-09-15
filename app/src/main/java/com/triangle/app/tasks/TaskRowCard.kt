package com.triangle.app.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.HabitPalette
import com.triangle.app.data.models.Task
import com.triangle.app.ui.SvgPathIcon

/** Matches script.js's _buildHabitRow()/.habit-row — task icon, title, XP badge, check button. */
@Composable
fun TaskRowCard(
    task: Task,
    done: Boolean,
    onClick: () -> Unit,
    onToggleDone: () -> Unit
) {
    val color = runCatching { Color(android.graphics.Color.parseColor(task.iconColor ?: HabitPalette.DEFAULT_COLOR)) }
        .getOrDefault(MaterialTheme.colorScheme.primary)
    val svg = task.iconSvg ?: HabitPalette.ICONS.getValue(HabitPalette.DEFAULT_ICON_KEY)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            SvgPathIcon(svg, tint = color, size = 24.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (done) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            if (task.description.isNotBlank()) {
                Text(
                    task.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        if (task.points > 0) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(color.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("+${task.points}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
            }
            Spacer(Modifier.width(10.dp))
        }
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (done) color else color.copy(alpha = 0.14f))
                .clickable(enabled = !done, onClick = onToggleDone),
            contentAlignment = Alignment.Center
        ) {
            if (done) Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}
