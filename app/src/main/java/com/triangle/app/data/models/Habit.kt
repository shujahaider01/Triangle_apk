package com.triangle.app.data.models

import com.triangle.app.data.HabitPalette

/**
 * Native model of a `db.habits[]` entry — see script.js's habit-creation
 * flow (`_renderHabitCreatePanel`/`saveHabit`) and scheduling helpers
 * (`_habitIsScheduledForDate`, `_habitIsInRange`). For the Individual role
 * (this milestone's scope), `assignedTo` is always `[currentUser.id]` —
 * there's no "assign to" picker for Individual accounts in the source app.
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
    val assignedTo: List<String> = emptyList(),
    val frequency: HabitFrequency = HabitFrequency("everyday"),
    val startDate: String,
    val endDate: String? = null,
    val xpPerCompletion: Int = 5,
    val createdAt: Long = 0L,
    val createdBy: String? = null
) {
    /** Same rule as script.js's _habitIsInRange(). */
    fun isInRange(dateStr: String): Boolean =
        dateStr >= startDate && (endDate == null || dateStr <= endDate)

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id, "name" to name, "description" to description, "iconSvg" to iconSvg, "color" to color,
        "assignedTo" to assignedTo, "frequency" to frequency.toMap(), "startDate" to startDate, "endDate" to endDate,
        "xpPerCompletion" to xpPerCompletion, "createdAt" to createdAt, "createdBy" to createdBy
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
                assignedTo = (m["assignedTo"] as? List<*>)?.map { it.toString() } ?: emptyList(),
                frequency = (m["frequency"] as? Map<*, *>)?.let { HabitFrequency.fromMap(it) } ?: HabitFrequency("everyday"),
                startDate = m["startDate"] as? String ?: "",
                endDate = m["endDate"] as? String,
                xpPerCompletion = ((m["xpPerCompletion"] as? Number)?.toInt() ?: 5).let { if (it > 0) it else 5 },
                createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
                createdBy = m["createdBy"]?.toString()
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
