package com.triangle.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Native port of the pure, DOM-free stat-computation functions
 * renderInternDashboard() in script.js relies on (see that file's
 * getObedience/getObedienceCounts/getStreak/getCurrentStreak/getRank/
 * getWeeklyCount, and the _taskAppliesTo/_taskOwnerIds/_taskCountsForStats
 * ownership predicates). Operates on the same loosely-typed
 * Map<String, Any?> shape the org's `data` blob has in Firebase (mirrors
 * script.js's own dynamically-typed approach — see OrgDataRepository)
 * rather than a strict data-class model, since Milestone 1 only reads this
 * data, never writes it back.
 *
 * "Current Streak" here is intentionally the TASK streak (getCurrentStreak
 * in script.js), not the HABIT streak (getHabitStreak) the WebView
 * dashboard actually shows for that card — Habits aren't ported natively
 * yet (that's Milestone 2), so there is no habit data to compute a habit
 * streak from. This is a deliberate, documented Milestone 1 simplification,
 * not an attempt to replicate getHabitStreak's semantics.
 */
object DashboardStats {

    @Suppress("UNCHECKED_CAST")
    private fun asMapList(raw: Any?): List<Map<String, Any?>> = when (raw) {
        is List<*> -> raw.mapNotNull { it as? Map<String, Any?> }
        is Map<*, *> -> raw.values.mapNotNull { it as? Map<String, Any?> }
        else -> emptyList()
    }

    private fun taskAssignedTo(t: Map<String, Any?>): String? = t["assignedTo"]?.toString()

    @Suppress("UNCHECKED_CAST")
    private fun taskSharedWith(t: Map<String, Any?>): List<String> =
        (t["sharedWith"] as? List<*>)?.map { it.toString() } ?: emptyList()

    private fun taskAppliesTo(t: Map<String, Any?>, id: String): Boolean =
        taskAssignedTo(t) == id || taskSharedWith(t).contains(id)

    private fun taskCountsForStats(t: Map<String, Any?>, id: String): Boolean =
        taskAppliesTo(t, id) && t["isTemplate"] != true

    private fun taskDateKey(t: Map<String, Any?>): String? =
        (t["instanceDate"] as? String) ?: (t["dueDate"] as? String) ?: (t["createdDate"] as? String)

    private fun isDone(taskCompletions: Map<String, Any?>, taskId: String, internId: String): Boolean {
        val entry = taskCompletions["$taskId-$internId"] as? Map<String, Any?> ?: return false
        return entry["done"] == true
    }

    private fun taskId(t: Map<String, Any?>): String = t["id"]?.toString() ?: ""

    /** Completion % over the intern's last-30-days-or-one-off applicable tasks. */
    fun getObedience(tasks: List<Map<String, Any?>>, taskCompletions: Map<String, Any?>, id: String): Int {
        val cutoff = LocalDate.now().minusDays(30).toString()
        val applicable = tasks.filter { t ->
            taskCountsForStats(t, id) && ((t["instanceDate"] as? String)?.let { it >= cutoff } != false)
        }
        if (applicable.isEmpty()) return 0
        val done = applicable.count { isDone(taskCompletions, taskId(it), id) }
        return Math.round(done * 100.0 / applicable.size).toInt()
    }

    data class ObedienceCounts(val total: Int, val done: Int)

    fun getObedienceCounts(tasks: List<Map<String, Any?>>, taskCompletions: Map<String, Any?>, id: String): ObedienceCounts {
        val cutoff = LocalDate.now().minusDays(30).toString()
        val applicable = tasks.filter { t ->
            taskCountsForStats(t, id) && ((t["instanceDate"] as? String)?.let { it >= cutoff } != false)
        }
        return ObedienceCounts(applicable.size, applicable.count { isDone(taskCompletions, taskId(it), id) })
    }

    private fun completedDates(tasks: List<Map<String, Any?>>, taskCompletions: Map<String, Any?>, id: String): Set<LocalDate> {
        val dates = mutableSetOf<LocalDate>()
        for (t in tasks) {
            if (!taskCountsForStats(t, id)) continue
            if (!isDone(taskCompletions, taskId(t), id)) continue
            val d = taskDateKey(t) ?: continue
            runCatching { LocalDate.parse(d) }.getOrNull()?.let { dates.add(it) }
        }
        return dates
    }

    /** Longest-ever run of consecutive calendar days with >=1 completed task. */
    fun getStreak(tasks: List<Map<String, Any?>>, taskCompletions: Map<String, Any?>, id: String): Int {
        val sorted = completedDates(tasks, taskCompletions, id).sorted()
        if (sorted.isEmpty()) return 0
        var maxStreak = 1
        var cur = 1
        for (i in 1 until sorted.size) {
            val diff = java.time.temporal.ChronoUnit.DAYS.between(sorted[i - 1], sorted[i])
            cur = if (diff == 1L) cur + 1 else 1
            if (cur > maxStreak) maxStreak = cur
        }
        return maxStreak
    }

    /** Consecutive days ending TODAY with >=1 completed task; 0 the moment today has none. */
    fun getCurrentStreak(tasks: List<Map<String, Any?>>, taskCompletions: Map<String, Any?>, id: String): Int {
        val dates = completedDates(tasks, taskCompletions, id)
        if (dates.isEmpty()) return 0
        var cur = 0
        var check = LocalDate.now()
        while (dates.contains(check)) {
            cur++
            check = check.minusDays(1)
            if (cur > 730) break
        }
        return cur
    }

    /** 1-based rank among all non-admin org members, sorted by points desc. */
    fun getRank(memberPoints: Map<String, Int>, id: String): Int {
        val sorted = memberPoints.entries.sortedByDescending { it.value }
        val idx = sorted.indexOfFirst { it.key == id }
        return if (idx >= 0) idx + 1 else sorted.size
    }

    private fun mondayOf(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Completed-task count since this week's Monday (local time). */
    fun getWeeklyCount(tasks: List<Map<String, Any?>>, taskCompletions: Map<String, Any?>, id: String): Int {
        val mondayStr = mondayOf(LocalDate.now()).toString()
        return tasks.count { t ->
            taskCountsForStats(t, id) &&
                isDone(taskCompletions, taskId(t), id) &&
                (taskDateKey(t) ?: "") >= mondayStr
        }
    }

    /** Today's applicable tasks (all owner ids) split into pending / completed. */
    fun myTasks(tasks: List<Map<String, Any?>>, id: String): List<Map<String, Any?>> =
        tasks.filter { taskAppliesTo(it, id) && it["isTemplate"] != true }

    /** All-time count of the intern's own completed (non-template) tasks. */
    fun getCompletedCount(tasks: List<Map<String, Any?>>, taskCompletions: Map<String, Any?>, id: String): Int =
        tasks.count { taskCountsForStats(it, id) && isDone(taskCompletions, taskId(it), id) }
}
