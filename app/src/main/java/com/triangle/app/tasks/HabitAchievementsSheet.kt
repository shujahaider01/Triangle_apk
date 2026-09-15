package com.triangle.app.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.HabitStats
import com.triangle.app.data.models.Habit

private val MILESTONES = listOf(1, 7, 14, 30, 50, 100, 200, 365, 730)

/** Port of script.js's openHabitAchievements() — a 9-badge milestone grid, unlocked once the habit's best streak reaches each threshold. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitAchievementsSheet(habit: Habit, streak: HabitStats.Streak, onDismiss: () -> Unit) {
    val color = remember(habit.color) { runCatching { Color(android.graphics.Color.parseColor(habit.color)) }.getOrDefault(Color(0xFFF59E0B)) }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("Achievements", fontSize = 17.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(MILESTONES) { milestone ->
                    AchievementBadge(milestone = milestone, unlocked = streak.best >= milestone, color = color)
                }
            }
            Spacer(Modifier.size(30.dp))
        }
    }
}

@Composable
private fun AchievementBadge(milestone: Int, unlocked: Boolean, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .then(
                    if (unlocked) Modifier.background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.6f))))
                    else Modifier.background(MaterialTheme.colorScheme.surfaceVariant).border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (unlocked) Icons.Default.EmojiEvents else Icons.Default.Lock,
                contentDescription = null,
                tint = if (unlocked) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(if (unlocked) 26.dp else 20.dp)
            )
        }
        Text(
            "$milestone day${if (milestone > 1) "s" else ""}",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}
