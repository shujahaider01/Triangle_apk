package com.triangle.app.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.HabitStats
import com.triangle.app.data.models.Habit
import com.triangle.app.data.models.HabitCompletionEntry
import com.triangle.app.ui.theme.triangleDarkTheme
import java.time.LocalDate

/**
 * Milestone badge grid (1, 7, 14, 30 / 50, 100, 200, 365, 730 days): an
 * illustrated medal once the habit's best streak reaches it, a faded badge
 * with a padlock before that. Tapping an unlocked badge re-opens its
 * "Well done!" card with the date it was actually reached.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitAchievementsSheet(
    habit: Habit,
    streak: HabitStats.Streak,
    completions: Map<String, HabitCompletionEntry>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val dark = triangleDarkTheme()
    var opened by remember { mutableStateOf<StreakUnlock?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (dark) MaterialTheme.colorScheme.surface else Color(0xFFF7F7F7)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                "Achievements",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 28.dp)
            )
            listOf(STREAK_MILESTONES.take(4) to 82.dp, STREAK_MILESTONES.drop(4) to 66.dp).forEach { (row, badgeSize) ->
                Row(Modifier.fillMaxWidth().padding(bottom = 22.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { milestone ->
                        val unlocked = streak.best >= milestone
                        AchievementBadge(milestone, unlocked, badgeSize) {
                            val date = HabitStats.unlockDate(completions, milestone) ?: LocalDate.now()
                            opened = StreakUnlock(habit.name, milestone, date)
                        }
                    }
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
    opened?.let { StreakUnlockDialog(it, onDismiss = { opened = null }) }
}

@Composable
private fun AchievementBadge(milestone: Int, unlocked: Boolean, badgeSize: Dp, onClick: () -> Unit) {
    Column(
        Modifier.then(if (unlocked) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StreakBadge(milestone, badgeSize, locked = !unlocked)
        Spacer(Modifier.height(8.dp))
        Text(
            "$milestone day${if (milestone > 1) "s" else ""}",
            fontSize = if (badgeSize > 70.dp) 15.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
