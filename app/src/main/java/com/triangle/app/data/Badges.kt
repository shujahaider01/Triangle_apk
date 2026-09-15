package com.triangle.app.data

import com.triangle.app.data.models.Task

/**
 * Faithful port of renderInternProfile()'s inline 14-badge Awards list
 * (script.js:7422-7437), grouped Core / Streak / Special / Tasks.
 *
 * One field is knowingly ported as permanently dead rather than "fixed":
 * the Redeemer badge reads `s.rewards` (`submissions/{uid}/rewards`), a
 * field `blankDB()` never initializes and nothing in script.js ever writes
 * to (the org-level `data.rewards`/`data.redemptions` at blankDB() are a
 * different, unrelated pair of fields) — so in the source app this badge
 * can never actually be earned by anyone. Porting it as always-unearned
 * here is the faithful behavior, not a bug in this port.
 */
object Badges {
    enum class Group { Core, Streak, Special, Tasks }

    data class Badge(
        val key: String,
        val group: Group,
        val label: String,
        val desc: String,
        val earned: Boolean,
        val emoji: String,
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
        val rewardsRedeemed = 0 // see class doc — s.rewards is a dead field, never populated upstream

        return listOf(
            Badge("first", Group.Core, "First Steps", "Complete your first task", doneCount >= 1, "✓", "#22c55e", "#22c55e", firstTaskDoneDate, minOf(doneCount, 1), 1),
            Badge("redeemer", Group.Core, "Redeemer", "Redeem your first reward", rewardsRedeemed > 0, "🎁", "#ec4899", "#ec4899", null, rewardsRedeemed.coerceAtMost(1), 1),
            Badge("roll", Group.Streak, "On a Roll", "Reach a 5-day streak", bestStreak >= 5, "🔥", "#ef4444", "#ef4444", null, minOf(bestStreak, 5), 5),
            Badge("unstoppable", Group.Streak, "Unstoppable", "Reach a 30-day streak", bestStreak >= 30, "⚡", "#f97316", "#f97316", null, minOf(bestStreak, 30), 30),
            Badge("legendary", Group.Streak, "Legendary Streak", "Reach a 90-day streak", bestStreak >= 90, "🌟", "#eab308", "#eab308", null, minOf(bestStreak, 90), 90),
            Badge("over", Group.Special, "Overachiever", "Earn 500 total XP", totalPoints >= 500, "⭐", "#f59e0b", "#f59e0b", ProfileStats.findXpCrossDate(pointHistory, 500), minOf(totalPoints, 500), 500),
            Badge("expert", Group.Special, "Expert", "Earn 2,000 total XP", totalPoints >= 2000, "💎", "#06b6d4", "#06b6d4", ProfileStats.findXpCrossDate(pointHistory, 2000), minOf(totalPoints, 2000), 2000),
            Badge("highflyer", Group.Special, "High Flyer", "Reach Top 3 on the leaderboard", rank <= 3, "🏆", "#7c3aed", "#7c3aed", null, null, null),
            Badge("silver", Group.Special, "Runner-Up", "Finish a week ranked #2", weeklyRankRecord.secondCount >= 1, "🥈", "#94a3b8", "#94a3b8", null, null, null, "${weeklyRankRecord.secondCount}× at #2"),
            Badge("bronze", Group.Special, "On the Podium", "Finish a week ranked #3", weeklyRankRecord.thirdCount >= 1, "🥉", "#cd7f32", "#cd7f32", null, null, null, "${weeklyRankRecord.thirdCount}× at #3"),
            Badge("tasks10", Group.Tasks, "Task Rookie", "Complete 10 tasks", doneCount >= 10, "📋", "#22c55e", "#22c55e", taskCountCrossDate(10), minOf(doneCount, 10), 10),
            Badge("tasks50", Group.Tasks, "Task Pro", "Complete 50 tasks", doneCount >= 50, "📈", "#3b82f6", "#3b82f6", taskCountCrossDate(50), minOf(doneCount, 50), 50),
            Badge("tasks100", Group.Tasks, "Centurion", "Complete 100 tasks", doneCount >= 100, "💯", "#8b5cf6", "#8b5cf6", taskCountCrossDate(100), minOf(doneCount, 100), 100),
            Badge("tasks500", Group.Tasks, "Task Master", "Complete 500 tasks", doneCount >= 500, "🏗️", "#f59e0b", "#f59e0b", taskCountCrossDate(500), minOf(doneCount, 500), 500),
            Badge("tasks1000", Group.Tasks, "Legend of Labor", "Complete 1,000 tasks", doneCount >= 1000, "👑", "#ef4444", "#ef4444", taskCountCrossDate(1000), minOf(doneCount, 1000), 1000),
        )
    }
}
