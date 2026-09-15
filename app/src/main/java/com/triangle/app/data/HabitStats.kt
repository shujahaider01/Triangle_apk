package com.triangle.app.data

import com.triangle.app.data.models.Habit
import com.triangle.app.data.models.HabitCompletionEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Faithful port of script.js's _habitCalcStreak()/_habitCalcScore()/
 * _habitGetHabitsForDate() — pure functions, reused by both the native
 * Habits screen and (per the Milestone 2 plan) DashboardViewModel's
 * "Current Streak" card, which now has real habit data to compute from
 * instead of Milestone 1's task-based placeholder.
 */
object HabitStats {

    data class Streak(val current: Int, val best: Int)

    private fun jsDow(date: LocalDate): Int = date.dayOfWeek.value % 7

    fun calcStreak(habit: Habit, completions: Map<String, HabitCompletionEntry>): Streak {
        val dates = completions.keys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()
        val best = if (dates.isEmpty()) 0 else {
            var max = 1
            var cur = 1
            for (i in 1 until dates.size) {
                cur = if (ChronoUnit.DAYS.between(dates[i - 1], dates[i]) == 1L) cur + 1 else 1
                if (cur > max) max = cur
            }
            max
        }

        val dateSet = dates.toSet()
        var current = 0
        var check = LocalDate.now()
        var iterations = 0
        while (iterations < 730) {
            if (!habit.isInRange(check.toString()) || !habit.frequency.isScheduledFor(jsDow(check), check.dayOfMonth)) {
                check = check.minusDays(1)
                iterations++
                continue
            }
            if (dateSet.contains(check)) {
                current++
                check = check.minusDays(1)
                iterations++
            } else {
                break
            }
        }
        return Streak(current, best)
    }

    /** Completion % since startDate, scheduled-days-only denominator, capped at 365 days scanned. */
    fun calcScore(habit: Habit, completions: Map<String, HabitCompletionEntry>): Int {
        val start = runCatching { LocalDate.parse(habit.startDate) }.getOrNull() ?: return 0
        val today = LocalDate.now()
        var day = if (ChronoUnit.DAYS.between(start, today) > 365) today.minusDays(365) else start
        var scheduled = 0
        var done = 0
        while (!day.isAfter(today)) {
            if (habit.isInRange(day.toString()) && habit.frequency.isScheduledFor(jsDow(day), day.dayOfMonth)) {
                scheduled++
                if (completions.containsKey(day.toString())) done++
            }
            day = day.plusDays(1)
        }
        return if (scheduled == 0) 0 else Math.round(done * 100.0 / scheduled).toInt()
    }

    /** Which of this intern's habits are scheduled (and in range) for the given day — mirrors _habitGetHabitsForDate(). */
    fun habitsForDate(habits: List<Habit>, dateStr: String, internId: String): List<Habit> {
        val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return emptyList()
        return habits.filter { h ->
            h.assignedTo.contains(internId) && h.isInRange(dateStr) && h.frequency.isScheduledFor(jsDow(date), date.dayOfMonth)
        }
    }

    fun isCompletedOn(completions: Map<String, HabitCompletionEntry>, dateStr: String): Boolean =
        completions.containsKey(dateStr)

    /**
     * Faithful port of _buildHabitHeatmap()'s grid math (script.js) — 22
     * week-columns x 7 day-rows, filled column-major (each column is one
     * calendar week, Monday..Sunday top-to-bottom) with today pinned to the
     * bottom-right column, then flattened row-major for a row-major grid
     * widget (Compose's LazyVerticalGrid fills row-major, same as the CSS
     * grid source uses). A plain chronological (153 downTo 0) list — what
     * this was originally ported as — reads left-to-right in date order
     * instead of week-columns, which is a different (wrong) picture from
     * the source's GitHub-contribution-graph layout. Cells after today in
     * today's own column are `null` (not rendered as a normal past/future
     * square — that partial column is empty, matching source's hmap-sq-empty).
     */
    fun heatmapGrid(today: LocalDate = LocalDate.now()): List<LocalDate?> {
        val todayRow = (today.dayOfWeek.value - 1) // Mon=0..Sun=6
        val totalDaysBack = 147 + todayRow // 21 full cols * 7 + todayRow
        val gridStart = today.minusDays(totalDaysBack.toLong())
        val cells = ArrayList<LocalDate?>(154)
        for (r in 0 until 7) {
            for (c in 0 until 22) {
                if (c == 21 && r > todayRow) {
                    cells.add(null)
                } else {
                    cells.add(gridStart.plusDays((c * 7 + r).toLong()))
                }
            }
        }
        return cells
    }
}
