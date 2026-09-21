package com.triangle.app.data

import com.triangle.app.data.models.Task
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Faithful port of renderInternProfile()'s chart-computation functions
 * (getWeeklyProductivity/getTasksByCategory/getTaskStatusDistribution/
 * getOverdueTasksTimeline/getMonthlyPerformance/
 * getWeeklyRankRecord/getMostXpInADay) — see the Milestone 3 plan for which
 * Profile functions were deliberately NOT ported (getCategoryPerformance,
 * getLeaderboardRanking, getMilestones's unused UI, the whole V/S tab —
 * all confirmed dead or structurally meaningless for a one-person org).
 *
 * `getWeeklyRankRecord` is the one function genuinely simplified rather
 * than literally translated: script.js re-ranks every org member per week,
 * but every native account is a one-person org (see
 * [[project-individual-only-scope]]), so that re-ranking always degenerates
 * to "rank #1 of 1 in any week with recorded XP" — this ports that
 * degenerate case directly instead of hand-rolling a multi-member
 * comparison this app can never actually have.
 */
object ProfileStats {

    data class MonthRef(val year: Int, val month: Int) // month 0-11 (JS Date convention)
    data class YearRef(val year: Int)

    fun currentMonthRef(): MonthRef = LocalDate.now().let { MonthRef(it.year, it.monthValue - 1) }

    private fun taskDateKey(t: Task): String? = t.instanceDate ?: t.dueDate ?: t.createdDate
    private fun isDone(completions: Set<String>, taskId: String, uid: String) = completions.contains("$taskId-$uid")
    private fun ownedTasks(tasks: List<Task>, uid: String) = tasks.filter { it.appliesTo(uid) && !it.isTemplate }
    private fun monthBounds(ref: MonthRef): Pair<LocalDate, LocalDate> {
        val start = LocalDate.of(ref.year, ref.month + 1, 1)
        return start to start.plusMonths(1).minusDays(1)
    }
    private fun mondayOf(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    // ── Weekly Productivity ─────────────────────────────────────────────────
    data class WeeklyProductivity(val weeks: List<Int>, val score: Int, val delta: Int)

    fun getWeeklyProductivity(tasks: List<Task>, completions: Set<String>, uid: String, ref: MonthRef): WeeklyProductivity {
        val owned = ownedTasks(tasks, uid)
        fun pctForRange(start: String, end: String): Int {
            val inRange = owned.filter { val d = taskDateKey(it); d != null && d >= start && d <= end }
            if (inRange.isEmpty()) return 0
            val done = inRange.count { isDone(completions, it.id, uid) }
            return (done * 100.0 / inRange.size).roundToInt()
        }
        val (monthStart, monthEnd) = monthBounds(ref)
        val weeks = mutableListOf<Int>()
        var cursor = monthStart
        while (!cursor.isAfter(monthEnd)) {
            val bucketEnd = minOf(cursor.plusDays(6), monthEnd)
            weeks.add(pctForRange(cursor.toString(), bucketEnd.toString()))
            cursor = bucketEnd.plusDays(1)
        }
        val prevMonthStart = monthStart.minusMonths(1)
        val prevMonthEnd = monthStart.minusDays(1)
        val score = pctForRange(monthStart.toString(), monthEnd.toString())
        val prevScore = pctForRange(prevMonthStart.toString(), prevMonthEnd.toString())
        return WeeklyProductivity(weeks, score, score - prevScore)
    }

    // ── Tasks by Category ───────────────────────────────────────────────────
    data class CategorySlice(val label: String, val count: Int, val colorHex: String, val pct: Int)
    data class TasksByCategory(val total: Int, val cats: List<CategorySlice>)

    private val CAT_COLORS = mapOf("Office" to "#8b5cf6", "Academic" to "#3b82f6", "Personal" to "#22c55e", "Other" to "#9ca3af")

    fun getTasksByCategory(tasks: List<Task>, uid: String, ref: MonthRef, allTime: Boolean = false): TasksByCategory {
        val (monthStart, monthEnd) = monthBounds(ref)
        val owned = tasks.filter { it.appliesTo(uid) && !it.isTemplate }
        val inScope = owned.filter { t ->
            if (allTime) true else { val d = taskDateKey(t); d != null && d >= monthStart.toString() && d <= monthEnd.toString() }
        }
        val counts = linkedMapOf<String, Int>()
        inScope.forEach { t ->
            val c = if (t.category in listOf("Office", "Academic", "Personal")) t.category else "Other"
            counts[c] = (counts[c] ?: 0) + 1
        }
        val total = inScope.size
        val cats = counts.entries
            .map { (label, count) -> CategorySlice(label, count, CAT_COLORS[label] ?: "#9ca3af", if (total > 0) (count * 100.0 / total).roundToInt() else 0) }
            .sortedByDescending { it.count }
        return TasksByCategory(total, cats)
    }

    // ── Task Status Distribution ────────────────────────────────────────────
    data class StatusSlice(val label: String, val count: Int, val colorHex: String, val pct: Int, val barFraction: Float)
    data class TaskStatusDistribution(val total: Int, val statuses: List<StatusSlice>)

    private val STATUS_COLORS = mapOf("Completed" to "#22c55e", "In Review" to "#f97316", "Pending" to "#eab308", "Overdue" to "#f43f5e")

    fun getTaskStatusDistribution(tasks: List<Task>, completions: Set<String>, uid: String, ref: MonthRef): TaskStatusDistribution {
        val (monthStart, monthEnd) = monthBounds(ref)
        // getTaskStatusDistribution's own month-scope filter reads dueDate first
        // (t.dueDate||t.instanceDate||t.createdDate) — the opposite field order
        // from every other function here (instanceDate-first, see taskDateKey) —
        // a real inconsistency in the source itself, ported faithfully rather
        // than unified across functions.
        val inScope = tasks.filter { t ->
            t.countsForStats(uid) && (t.dueDate ?: t.instanceDate ?: t.createdDate ?: "").let { it >= monthStart.toString() && it <= monthEnd.toString() }
        }
        val today = LocalDate.now().toString()
        val counts = linkedMapOf("Completed" to 0, "In Review" to 0, "Pending" to 0, "Overdue" to 0)
        inScope.forEach { t ->
            val done = isDone(completions, t.id, uid)
            when {
                done -> counts["Completed"] = counts.getValue("Completed") + 1
                // "In Review" (pendingRequests) isn't modeled natively yet (no approval-review
                // flow ported) — every non-done task falls through to Pending/Overdue, same as
                // the source would for any task with no matching pendingRequests entry.
                else -> {
                    val due = t.dueDate ?: t.instanceDate
                    if (due != null && due < today) counts["Overdue"] = counts.getValue("Overdue") + 1
                    else counts["Pending"] = counts.getValue("Pending") + 1
                }
            }
        }
        val total = inScope.size
        val maxCount = max(1, counts.values.maxOrNull() ?: 0)
        val statuses = counts.map { (label, count) ->
            val barPct = max(if (count > 0) 10 else 3, (count * 100.0 / maxCount).roundToInt())
            StatusSlice(label, count, STATUS_COLORS.getValue(label), if (total > 0) (count * 100.0 / total).roundToInt() else 0, barPct / 100f)
        }
        return TaskStatusDistribution(total, statuses)
    }

    // ── Overdue Tasks Timeline ──────────────────────────────────────────────
    data class OverdueWeek(val label: String, val count: Int)

    fun getOverdueTasksTimeline(tasks: List<Task>, completions: Set<String>, uid: String, ref: MonthRef): List<OverdueWeek> {
        val inScope = tasks.filter { it.countsForStats(uid) }
        val today = LocalDate.now().toString()
        val (monthStart, monthEnd) = monthBounds(ref)
        val weeks = mutableListOf<OverdueWeek>()
        var cursor = monthStart
        while (!cursor.isAfter(monthEnd)) {
            val bucketEnd = minOf(cursor.plusDays(6), monthEnd)
            val startStr = cursor.toString()
            val endStr = bucketEnd.toString()
            val count = inScope.count { t ->
                val done = isDone(completions, t.id, uid)
                val due = t.dueDate ?: t.instanceDate
                !done && due != null && due in startStr..endStr && due < today
            }
            val label = "${bucketEnd.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${bucketEnd.dayOfMonth}"
            weeks.add(OverdueWeek(label, count))
            cursor = bucketEnd.plusDays(1)
        }
        return weeks
    }

    // ── Monthly (Yearly) Performance ────────────────────────────────────────
    data class MonthSlice(val label: String, val pct: Int, val total: Int)
    data class MonthlyPerformance(val months: List<MonthSlice>, val avgScore: Int, val totalTasks: Int, val best: MonthSlice, val delta: Int)

    fun getMonthlyPerformance(tasks: List<Task>, completions: Set<String>, uid: String, ref: YearRef): MonthlyPerformance {
        val inScope = tasks.filter { it.countsForStats(uid) }
        val months = (0..11).map { m ->
            val start = LocalDate.of(ref.year, m + 1, 1)
            val end = start.plusMonths(1).minusDays(1)
            val inMonth = inScope.filter { val due = it.dueDate ?: it.instanceDate; due != null && due >= start.toString() && due <= end.toString() }
            val done = inMonth.count { isDone(completions, it.id, uid) }
            val pct = if (inMonth.isNotEmpty()) (done * 100.0 / inMonth.size).roundToInt() else 0
            MonthSlice(start.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, pct, inMonth.size)
        }
        val totalTasksThisYear = months.sumOf { it.total }
        val avgScore = (months.sumOf { it.pct } / months.size.toDouble()).roundToInt()
        val best = months.maxByOrNull { it.pct } ?: months.first()
        val delta = months.last().pct - (months.getOrNull(months.size - 2)?.pct ?: 0)
        return MonthlyPerformance(months, avgScore, totalTasksThisYear, best, delta)
    }

    // ── Weekly Rank Record (simplified — see class doc) ─────────────────────
    data class WeeklyRankRecord(val firstCount: Int, val secondCount: Int, val thirdCount: Int, val bestRankEver: Int?)

    fun getWeeklyRankRecord(pointHistory: List<Map<String, Any?>>): WeeklyRankRecord {
        val weekTotals = mutableMapOf<String, Int>()
        pointHistory.forEach { h ->
            val date = h["date"] as? String ?: return@forEach
            val pts = (h["pts"] as? Number)?.toInt() ?: (h["points"] as? Number)?.toInt() ?: 0
            val week = runCatching { mondayOf(LocalDate.parse(date)).toString() }.getOrNull() ?: return@forEach
            weekTotals[week] = (weekTotals[week] ?: 0) + pts
        }
        val currentWeek = mondayOf(LocalDate.now()).toString()
        val firstCount = weekTotals.entries.count { (week, pts) -> week < currentWeek && pts > 0 }
        return WeeklyRankRecord(firstCount, 0, 0, if (firstCount > 0) 1 else null)
    }

    fun getMostXpInADay(pointHistory: List<Map<String, Any?>>): Int {
        val byDate = mutableMapOf<String, Int>()
        pointHistory.forEach { h ->
            val date = h["date"] as? String ?: return@forEach
            val pts = (h["pts"] as? Number)?.toInt() ?: (h["points"] as? Number)?.toInt() ?: 0
            byDate[date] = (byDate[date] ?: 0) + pts
        }
        return byDate.values.maxOrNull() ?: 0
    }

    /** Last-7-day XP array (oldest→newest), real data only — no fabricated demo curve (see plan). */
    fun getWeeklyXpRaw(pointHistory: List<Map<String, Any?>>): List<Int> {
        val today = LocalDate.now()
        return (6 downTo 0).map { i ->
            val ds = today.minusDays(i.toLong()).toString()
            pointHistory.filter { (it["date"] as? String) == ds }
                .sumOf { (it["pts"] as? Number)?.toInt() ?: (it["points"] as? Number)?.toInt() ?: 0 }
        }
    }

    /** XP earned within a given calendar month, from pointHistory. */
    fun monthXp(pointHistory: List<Map<String, Any?>>, ref: MonthRef): Int {
        val (start, end) = monthBounds(ref)
        return pointHistory.filter { val d = it["date"] as? String; d != null && d >= start.toString() && d <= end.toString() }
            .sumOf { (it["pts"] as? Number)?.toInt() ?: (it["points"] as? Number)?.toInt() ?: 0 }
    }

    fun findXpCrossDate(pointHistory: List<Map<String, Any?>>, threshold: Int): String? {
        val sorted = pointHistory.sortedBy { it["date"] as? String ?: "" }
        var cum = 0
        for (h in sorted) {
            cum += (h["pts"] as? Number)?.toInt() ?: (h["points"] as? Number)?.toInt() ?: 0
            if (cum >= threshold) return h["date"] as? String
        }
        return null
    }

    fun level(points: Int): Int = max(1, points / 500 + 1)
    fun tier(points: Int): String = when {
        points >= 5000 -> "Elite"
        points >= 2000 -> "Expert"
        points >= 1000 -> "Advanced"
        points >= 500 -> "Intermediate"
        else -> "Beginner"
    }
}
