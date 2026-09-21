package com.triangle.app.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector
import com.triangle.app.data.models.Task

/**
 * Faithful port of renderInternProfile()'s inline Awards list
 * (script.js:7422-7437), grouped Core / Streak / Special / Tasks. The
 * source's "Redeemer" badge (tied to the Rewards feature) is dropped —
 * the Rewards system was removed entirely, coins included, so there is no
 * "redeem a reward" action left for a badge to track.
 *
 * `icon` replaces the source's plain emoji glyph with a real vector icon
 * (the user asked for a more "professional" look, closer to a polished
 * icon set like 3dicons.co, than raw emoji text) — rendered inside a
 * gradient badge circle by AchievementTab's BadgeTile.
 */
object Badges {
    enum class Group { Streak, Special, Tasks }

    data class Badge(
        val key: String,
        val group: Group,
        val label: String,
        val desc: String,
        val earned: Boolean,
        val icon: ImageVector,
        val colorHex: String,
        val bgHex: String,
        val earnedDate: String?,
        val progressVal: Int?,
        val progressMax: Int?,
        val timesLabel: String? = null
    )

    fun compute(
        tasks: List<Task>,
        completions: Set<String>,
        uid: String,
        totalPoints: Int,
        bestStreak: Int,
        rank: Int,
        weeklyRankRecord: ProfileStats.WeeklyRankRecord,
        pointHistory: List<Map<String, Any?>>
    ): List<Badge> {
        val doneTasks = tasks.filter { it.countsForStats(uid) && completions.contains("${it.id}-$uid") }
        val doneCount = doneTasks.size
        val completedDates = doneTasks.mapNotNull { it.instanceDate ?: it.dueDate ?: it.createdDate }.sorted()
        fun taskCountCrossDate(threshold: Int): String? = completedDates.getOrNull(threshold - 1)
        val firstTaskDoneDate = completedDates.firstOrNull()

        return listOf(
            Badge("roll", Group.Streak, "On a Roll", "Reach a 5-day streak", bestStreak >= 5, Icons.Default.LocalFireDepartment, "#ef4444", "#ef4444", null, minOf(bestStreak, 5), 5),
            Badge("unstoppable", Group.Streak, "Unstoppable", "Reach a 30-day streak", bestStreak >= 30, Icons.Default.Bolt, "#f97316", "#f97316", null, minOf(bestStreak, 30), 30),
            Badge("legendary", Group.Streak, "Legendary Streak", "Reach a 90-day streak", bestStreak >= 90, Icons.Default.AutoAwesome, "#eab308", "#eab308", null, minOf(bestStreak, 90), 90),
            Badge("over", Group.Special, "Overachiever", "Earn 500 total XP", totalPoints >= 500, Icons.Default.Star, "#f59e0b", "#f59e0b", ProfileStats.findXpCrossDate(pointHistory, 500), minOf(totalPoints, 500), 500),
            Badge("expert", Group.Special, "Expert", "Earn 2,000 total XP", totalPoints >= 2000, Icons.Default.Diamond, "#06b6d4", "#06b6d4", ProfileStats.findXpCrossDate(pointHistory, 2000), minOf(totalPoints, 2000), 2000),
            Badge("highflyer", Group.Special, "High Flyer", "Reach Top 3 on the leaderboard", rank <= 3, Icons.Default.EmojiEvents, "#7c3aed", "#7c3aed", null, null, null),
            Badge("gold", Group.Special, "Ten Times #1", "Finish a week ranked #1 ten times", weeklyRankRecord.firstCount >= 10, Icons.Default.MilitaryTech, "#eab308", "#eab308", null, minOf(weeklyRankRecord.firstCount, 10), 10),
            Badge("silver", Group.Special, "Ten Times #2", "Finish a week ranked #2 ten times", weeklyRankRecord.secondCount >= 10, Icons.Default.MilitaryTech, "#94a3b8", "#94a3b8", null, minOf(weeklyRankRecord.secondCount, 10), 10),
            Badge("bronze", Group.Special, "Ten Times #3", "Finish a week ranked #3 ten times", weeklyRankRecord.thirdCount >= 10, Icons.Default.MilitaryTech, "#cd7f32", "#cd7f32", null, minOf(weeklyRankRecord.thirdCount, 10), 10),
            Badge("first", Group.Tasks, "First Steps", "Complete your first task", doneCount >= 1, Icons.Default.TaskAlt, "#22c55e", "#22c55e", firstTaskDoneDate, minOf(doneCount, 1), 1),
            Badge("tasks10", Group.Tasks, "Task Rookie", "Complete 10 tasks", doneCount >= 10, Icons.AutoMirrored.Filled.Assignment, "#22c55e", "#22c55e", taskCountCrossDate(10), minOf(doneCount, 10), 10),
            Badge("tasks50", Group.Tasks, "Task Pro", "Complete 50 tasks", doneCount >= 50, Icons.AutoMirrored.Filled.TrendingUp, "#3b82f6", "#3b82f6", taskCountCrossDate(50), minOf(doneCount, 50), 50),
            Badge("tasks100", Group.Tasks, "Centurion", "Complete 100 tasks", doneCount >= 100, Icons.Default.Shield, "#8b5cf6", "#8b5cf6", taskCountCrossDate(100), minOf(doneCount, 100), 100),
            Badge("tasks500", Group.Tasks, "Task Master", "Complete 500 tasks", doneCount >= 500, Icons.Default.Construction, "#f59e0b", "#f59e0b", taskCountCrossDate(500), minOf(doneCount, 500), 500),
            Badge("tasks1000", Group.Tasks, "Legend of Labor", "Complete 1,000 tasks", doneCount >= 1000, Icons.Default.WorkspacePremium, "#ef4444", "#ef4444", taskCountCrossDate(1000), minOf(doneCount, 1000), 1000),
        )
    }
}
