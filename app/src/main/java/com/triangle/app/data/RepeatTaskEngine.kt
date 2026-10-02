package com.triangle.app.data

import com.triangle.app.data.models.Task
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Faithful port of script.js's processRepeatTasks()/shouldGenerateForDate()/
 * getDatesToCheck() — same 30-day backfill cap, same per-frequency interval
 * math, same per-day-of-week `lastGenerated` tracking for "Weeks" (so
 * "every Mon+Wed" fires both days independently instead of one blocking
 * the other), same duplicate-instance guard.
 */
object RepeatTaskEngine {

    data class Result(val tasks: List<Task>, val changed: Boolean)

    fun process(allTasks: List<Task>, today: LocalDate = LocalDate.now()): Result {
        val todayStr = today.toString()
        var tasks = allTasks
        var changed = false

        val templates = allTasks.filter { it.repeat != null && it.isTemplate }
        for (tmpl0 in templates) {
            // Re-fetch the template from `tasks` each iteration since earlier
            // iterations may have appended instances (immutable list rebuild).
            val tmpl = tasks.first { it.id == tmpl0.id }
            val rule = tmpl.repeat ?: continue

            if (tmpl.paused) continue
            if (!tmpl.dueDate.isNullOrEmpty() && todayStr > tmpl.dueDate) continue

            val checkDates = getDatesToCheck(tmpl.lastGenerated, today)
            var lastGen = tmpl.lastGenerated

            // Per-DOW tracking for "Weeks" — rebuilt fresh from existing
            // instances every run (not persisted), matching script.js's
            // processRepeatTasks() exactly: "every Mon+Wed" needs each
            // weekday's own last-generated date, not one shared tracker.
            val lastGenByDow = mutableMapOf<Int, String?>()
            if (rule.freq == "Weeks") {
                tasks.filter { it.templateId == tmpl.id && it.instanceDate != null }.forEach { inst ->
                    val d = LocalDate.parse(inst.instanceDate).dayOfWeek.toJsDow()
                    val existing = lastGenByDow[d]
                    if (existing == null || inst.instanceDate!! > existing) lastGenByDow[d] = inst.instanceDate
                }
            }

            for (checkDate in checkDates) {
                val effectiveLastGen = if (rule.freq == "Weeks") {
                    lastGenByDow[LocalDate.parse(checkDate).dayOfWeek.toJsDow()]
                } else lastGen

                if (!shouldGenerateForDate(rule, checkDate, effectiveLastGen)) continue

                val alreadyExists = tasks.any { it.templateId == tmpl.id && it.instanceDate == checkDate }
                if (alreadyExists) {
                    lastGen = checkDate
                    if (rule.freq == "Weeks") lastGenByDow[LocalDate.parse(checkDate).dayOfWeek.toJsDow()] = checkDate
                    continue
                }

                val instance = buildInstance(tmpl, checkDate)
                tasks = tasks + instance

                lastGen = checkDate
                if (rule.freq == "Weeks") lastGenByDow[LocalDate.parse(checkDate).dayOfWeek.toJsDow()] = checkDate
                tasks = tasks.map { if (it.id == tmpl.id) it.copy(lastGenerated = checkDate) else it }
                changed = true
            }
        }

        // Days generated before instances carried the reminder (and the editor reads it from the day's copy): copy the
        // template's reminder onto today's and future copies that lack one, so editing a day never shows it as "off".
        val withReminder = templates.filter { it.reminderTime != null }.associate { it.id to it.reminderTime }
        if (withReminder.isNotEmpty()) {
            tasks = tasks.map { t ->
                val inherited = t.templateId?.let { withReminder[it] }
                if (inherited != null && t.reminderTime == null && !t.isTemplate && (t.instanceDate ?: "") >= todayStr) {
                    changed = true
                    t.copy(reminderTime = inherited)
                } else t
            }
        }
        return Result(tasks, changed)
    }

    /** The concrete task a repeating [tmpl] produces for [checkDate]. */
    fun buildInstance(tmpl: Task, checkDate: String): Task {
        val instant = LocalDate.parse(checkDate).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        return Task(
            id = "ri-${tmpl.id}-$checkDate",
            title = tmpl.title,
            description = tmpl.description,
            assignedTo = tmpl.assignedTo,
            category = tmpl.category,
            points = tmpl.points,
            priority = tmpl.priority,
            iconSvg = tmpl.iconSvg,
            iconColor = tmpl.iconColor,
            checklist = tmpl.checklist.map { it.copy(done = false) },
            dueDate = checkDate,
            reminderTime = tmpl.reminderTime,
            approvalRequired = tmpl.approvalRequired,
            approvalContribId = tmpl.approvalContribId,
            isPersonal = tmpl.isPersonal,
            createdBy = tmpl.createdBy,
            createdDate = checkDate,
            createdAt = instant,
            templateId = tmpl.id,
            instanceDate = checkDate,
            repeat = null
        )
    }

    /**
     * Creates the day copy of every repeating task that falls on any of [dates] (typically days the user is
     * looking at in the calendar, including future ones), skipping days that already have one. Unlike [process]
     * it never touches `lastGenerated`, so the normal daily generation is unaffected.
     */
    fun materializeForDates(allTasks: List<Task>, dates: List<LocalDate>): Result {
        var tasks = allTasks
        var changed = false
        for (tmpl in allTasks.filter { it.repeat != null && it.isTemplate && !it.paused }) {
            val rule = tmpl.repeat ?: continue
            for (date in dates) {
                val ds = date.toString()
                if (!tmpl.dueDate.isNullOrEmpty() && ds > tmpl.dueDate) continue
                if (!occursOn(rule, date)) continue
                // Never before the day the task was created.
                if (!tmpl.createdDate.isNullOrEmpty() && ds < tmpl.createdDate) continue
                if (tasks.any { it.templateId == tmpl.id && it.instanceDate == ds }) continue
                tasks = tasks + buildInstance(tmpl, ds)
                changed = true
            }
        }
        return Result(tasks, changed)
    }

    /** Whether [rule] fires on [date], ignoring generation history — used to schedule reminders ahead of time. */
    fun occursOn(rule: com.triangle.app.data.models.RepeatRule, date: LocalDate): Boolean = shouldGenerateForDate(rule, date.toString(), null)

    /** JS's Date.getDay(): 0=Sunday..6=Saturday. */
    private fun DayOfWeek.toJsDow(): Int = this.value % 7

    private fun shouldGenerateForDate(r: com.triangle.app.data.models.RepeatRule, checkDate: String, lastGenerated: String?): Boolean {
        if (lastGenerated == checkDate) return false
        val interval = if (r.interval > 0) r.interval else 1
        val checkD = LocalDate.parse(checkDate)
        val dow = checkD.dayOfWeek.toJsDow()

        return when (r.freq) {
            "Days" -> {
                if (lastGenerated.isNullOrEmpty()) true
                else ChronoUnit.DAYS.between(LocalDate.parse(lastGenerated), checkD) >= interval
            }
            "Weekdays" -> dow in 1..5
            "DaysOfMonth" -> {
                val len = checkD.lengthOfMonth()
                // A date past the end of a short month (e.g. 31 in April) falls on that month's last day.
                r.dates.any { minOf(it, len) == checkD.dayOfMonth }
            }
            "PerPeriod" -> {
                if (r.unit == "month") {
                    val len = checkD.lengthOfMonth()
                    val n = r.count.coerceIn(1, len)
                    (0 until n).any { (it * len) / n + 1 == checkD.dayOfMonth }
                } else {
                    // Spread evenly across the Mon..Sun week (3 per week = Mon, Wed, Fri).
                    val n = r.count.coerceIn(1, 7)
                    val idx = (checkD.dayOfWeek.value + 6) % 7 // Monday = 0
                    (0 until n).any { (it * 7) / n == idx }
                }
            }
            "Weeks" -> {
                if (r.days.isEmpty() || !r.days.contains(dow)) return false
                if (lastGenerated.isNullOrEmpty()) return true
                (ChronoUnit.DAYS.between(LocalDate.parse(lastGenerated), checkD) / 7) >= interval
            }
            "Months" -> {
                if (lastGenerated.isNullOrEmpty()) true
                else {
                    val last = LocalDate.parse(lastGenerated)
                    val diff = (checkD.year - last.year) * 12 + (checkD.monthValue - last.monthValue)
                    diff >= interval
                }
            }
            "Years" -> {
                if (lastGenerated.isNullOrEmpty()) true
                else (checkD.year - LocalDate.parse(lastGenerated).year) >= interval
            }
            else -> false
        }
    }

    private fun getDatesToCheck(lastGenerated: String?, today: LocalDate): List<String> {
        if (lastGenerated.isNullOrEmpty()) return listOf(today.toString())
        var cur = LocalDate.parse(lastGenerated).plusDays(1)
        val limit = today.minusDays(30)
        if (cur < limit) cur = limit
        val dates = mutableListOf<String>()
        while (!cur.isAfter(today)) {
            dates.add(cur.toString())
            cur = cur.plusDays(1)
        }
        return dates
    }
}
