package com.triangle.app.data.models

import com.triangle.app.data.HabitPalette
import com.triangle.app.data.anyToMapList

/**
 * Native model of a `db.habits[]` entry — see script.js's habit-creation
 * flow (`_renderHabitCreatePanel`/`saveHabit`) and scheduling helpers
 * (`_habitIsScheduledForDate`, `_habitIsInRange`). `assignedTo` held only
 * `[currentUser.id]` until the Connect->Assign->Complete->Earn model
 * (Milestone 3, see .claude/plans/enchanted-brewing-beacon.md) activated
 * real cross-account assignment via AssignmentRepository.
 */
data class HabitFrequency(
    val type: String, // "everyday" | "daysOfWeek" | "daysOfMonth" | "perPeriod"
    val days: List<Int> = emptyList(), // daysOfWeek: 0-6
    val dates: List<Int> = emptyList(), // daysOfMonth: 1-31
    val count: Int? = null, // perPeriod
    val unit: String? = null // perPeriod: "week" | "month"
) {
    fun toMap(): Map<String, Any?> {
        val config: Map<String, Any?> = when (type) {
            "daysOfWeek" -> mapOf("days" to days)
            "daysOfMonth" -> mapOf("dates" to dates)
            "perPeriod" -> mapOf("count" to count, "unit" to unit)
            else -> emptyMap()
        }
        return mapOf("type" to type, "config" to config)
    }

    /** Same rule as script.js's _habitIsScheduledForDate() — "perPeriod" always shows (no quota check in the source app either). */
    fun isScheduledFor(dayOfWeek: Int, dayOfMonth: Int): Boolean = when (type) {
        "everyday" -> true
        "daysOfWeek" -> days.contains(dayOfWeek)
        "daysOfMonth" -> dates.contains(dayOfMonth)
        "perPeriod" -> true
        else -> true
    }

    companion object {
        fun fromMap(m: Map<*, *>): HabitFrequency {
            val type = m["type"] as? String ?: "everyday"
            val config = m["config"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
            return HabitFrequency(
                type = type,
                days = (config["days"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList(),
                dates = (config["dates"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList(),
                count = (config["count"] as? Number)?.toInt(),
                unit = config["unit"] as? String
            )
        }
    }
}

data class Habit(
    val id: String,
    val name: String,
    val description: String = "",
    val iconSvg: String? = null,
    val color: String = HabitPalette.DEFAULT_COLOR,
    /** Only meaningful for a Solo (unassigned, self-created) habit — see TasksHabitsViewModel's ItemMode.SOLO filtering. Assigned-out habits ignore this. */
    val category: String = "Personal", // "Office" | "Academic" | "Personal"
    val assignedTo: List<String> = emptyList(),
    val frequency: HabitFrequency = HabitFrequency("everyday"),
    val startDate: String,
    val endDate: String? = null,
    val priority: String = "medium", // "low" | "medium" | "high" | "critical" — see data/PriorityXp.kt; xpPerCompletion is derived from this at assignment time, not freely entered
    val xpPerCompletion: Int = 5,
    val createdAt: Long = 0L,
    val createdBy: String? = null,
    /** Habit Detail's photo timeline (script.js's habit.photos) — reuses TaskNote's shape since it's generic enough (type/content/userId/userName/role/timestamp), same as Task.notes. */
    val photos: List<TaskNote> = emptyList()
) {
    /** Same rule as script.js's _habitIsInRange(). */
    fun isInRange(dateStr: String): Boolean =
        dateStr >= startDate && (endDate == null || dateStr <= endDate)

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id, "name" to name, "description" to description, "iconSvg" to iconSvg, "color" to color,
        "category" to category,
        "assignedTo" to assignedTo, "frequency" to frequency.toMap(), "startDate" to startDate, "endDate" to endDate,
        "priority" to priority, "xpPerCompletion" to xpPerCompletion, "createdAt" to createdAt, "createdBy" to createdBy,
        "photos" to photos.map { it.toMap() }
    )

    companion object {
        fun fromMap(m: Map<*, *>): Habit? {
            val id = m["id"]?.toString() ?: return null
            val name = m["name"] as? String ?: return null
            return Habit(
                id = id,
                name = name,
                description = m["description"] as? String ?: "",
                iconSvg = m["iconSvg"] as? String,
                color = m["color"] as? String ?: HabitPalette.DEFAULT_COLOR,
                category = m["category"] as? String ?: "Personal",
                assignedTo = (m["assignedTo"] as? List<*>)?.map { it.toString() } ?: emptyList(),
                frequency = (m["frequency"] as? Map<*, *>)?.let { HabitFrequency.fromMap(it) } ?: HabitFrequency("everyday"),
                startDate = m["startDate"] as? String ?: "",
                endDate = m["endDate"] as? String,
                priority = m["priority"] as? String ?: "medium",
                // A Solo habit's xpPerCompletion is a deliberate 0 (no XP) — only floor
                // a MISSING value to 5, not an explicit 0, so that stays 0 on reload.
                xpPerCompletion = (m["xpPerCompletion"] as? Number)?.toInt() ?: 5,
                createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
                createdBy = m["createdBy"]?.toString(),
                photos = anyToMapList(m["photos"]).mapNotNull { TaskNote.fromMap(it) }
            )
        }
    }
}

data class HabitCompletionEntry(val completedAt: Long, val xpAwarded: Int) {
    fun toMap(): Map<String, Any?> = mapOf("completedAt" to completedAt, "xpAwarded" to xpAwarded)

    companion object {
        fun fromMap(m: Map<*, *>): HabitCompletionEntry = HabitCompletionEntry(
            completedAt = (m["completedAt"] as? Number)?.toLong() ?: 0L,
            xpAwarded = (m["xpAwarded"] as? Number)?.toInt() ?: 0
        )
    }
}
