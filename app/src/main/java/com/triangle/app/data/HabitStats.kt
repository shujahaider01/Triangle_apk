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
}
