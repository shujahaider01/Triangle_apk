package com.triangle.app.data

/**
 * Fixed Priority -> XP table for the Connect->Assign->Complete->Earn model
 * (see .claude/plans/enchanted-brewing-beacon.md) — XP is derived from the
 * Priority an assigner picks, never freely entered, so two assigners giving
 * the same priority always award the same XP.
 */
object PriorityXp {
    val VALUES = linkedMapOf("low" to 5, "medium" to 10, "high" to 20, "critical" to 35)
    val LABELS = linkedMapOf("low" to "Low", "medium" to "Medium", "high" to "High", "critical" to "Critical")

    fun xpFor(priority: String): Int = VALUES[priority] ?: VALUES.getValue("medium")
    fun labelFor(priority: String): String = LABELS[priority] ?: LABELS.getValue("medium")
}
