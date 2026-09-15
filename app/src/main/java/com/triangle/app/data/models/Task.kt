package com.triangle.app.data.models

import com.triangle.app.data.anyToMapList

/**
 * Native model of a `db.tasks[]` entry (see script.js's saveCustomTask(),
 * processRepeatTasks(), _buildHabitRow()) — field set and semantics ported
 * from the Milestone 2 research (assets/script.js's task object shape).
 * Hand-written JSON mapping (toMap/fromMap) rather than Firebase's
 * reflection-based POJO mapper, same reasoning as Milestone 1's data
 * layer: some fields are loosely typed on the JS side (e.g. `assignedTo`
 * can be a numeric legacy id or a string uid) and defensive parsing here
 * mirrors patchDB()'s own tolerance for missing/malformed fields.
 *
 * Fields NOT ported (see the Milestone 2 plan's scope trims): `symbol`,
 * `attachmentName`, `priority`, `inProgress`, `sharedToUsers`/
 * `sharedFromUserId` etc. (the cross-account share flow, which is disabled
 * in the source app itself via `SHARED_TASK_MODE_ENABLED=false`).
 */
data class RepeatRule(
    val freq: String, // "Days" | "Weekdays" | "Weeks" | "Months" | "Years"
    val interval: Int = 1,
    val days: List<Int> = emptyList() // day-of-week 0-6, only meaningful for "Weeks"
) {
    fun toMap(): Map<String, Any?> = mapOf("freq" to freq, "interval" to interval, "days" to days)

    companion object {
        fun fromMap(m: Map<*, *>): RepeatRule? {
            val freq = m["freq"] as? String ?: return null
            return RepeatRule(
                freq = freq,
                interval = (m["interval"] as? Number)?.toInt() ?: 1,
                days = (m["days"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
            )
        }
    }
}

data class ChecklistItem(val id: String, val text: String, val done: Boolean = false) {
    fun toMap(): Map<String, Any?> = mapOf("id" to id, "text" to text, "done" to done)

    companion object {
        fun fromMap(m: Map<*, *>): ChecklistItem? {
            val id = m["id"]?.toString() ?: return null
            val text = m["text"] as? String ?: return null
            return ChecklistItem(id, text, m["done"] == true)
        }
    }
}

data class TaskNote(
    val type: String, // "text" | "photo"
    val content: String,
    val userId: String,
    val userName: String,
    val role: String,
    val timestamp: Long
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "type" to type, "content" to content, "userId" to userId,
        "userName" to userName, "role" to role, "timestamp" to timestamp
    )

    companion object {
        fun fromMap(m: Map<*, *>): TaskNote? {
            val type = m["type"] as? String ?: return null
            return TaskNote(
                type = type,
                content = m["content"] as? String ?: "",
                userId = m["userId"]?.toString() ?: "",
                userName = m["userName"] as? String ?: "",
                role = m["role"] as? String ?: "",
                timestamp = (m["timestamp"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

data class Task(
    val id: String,
    val title: String,
    val description: String = "",
    val category: String = "Personal", // "Office" | "Academic" | "Personal"
    val points: Int = 0,
    val dueDate: String? = null,
    val instanceDate: String? = null,
    val repeat: RepeatRule? = null,
    val isTemplate: Boolean = false,
    val templateId: String? = null,
    val lastGenerated: String? = null,
    val paused: Boolean = false,
    val isPersonal: Boolean = true,
    val createdBy: String? = null,
    val assignedTo: String? = null,
    val sharedWith: List<String> = emptyList(),
    val createdDate: String? = null,
    val createdAt: Long = 0L,
    val approvalRequired: Boolean = false,
    val approvalContribId: String? = null,
    val iconSvg: String? = null,
    val iconColor: String? = null,
    val checklist: List<ChecklistItem> = emptyList(),
    val notes: List<TaskNote> = emptyList()
) {
    /** Same ownership rule as script.js's _taskAppliesTo(t, id). */
    fun appliesTo(id: String): Boolean = assignedTo == id || sharedWith.contains(id)

    /** Same rule as script.js's _taskCountsForStats(t, id). */
    fun countsForStats(id: String): Boolean = appliesTo(id) && !isTemplate

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id, "title" to title, "description" to description, "category" to category,
        "points" to points, "dueDate" to dueDate, "instanceDate" to instanceDate,
        "repeat" to repeat?.toMap(), "isTemplate" to isTemplate, "templateId" to templateId,
        "lastGenerated" to lastGenerated, "paused" to paused,
        "isPersonal" to isPersonal, "createdBy" to createdBy, "assignedTo" to assignedTo, "sharedWith" to sharedWith,
        "createdDate" to createdDate, "createdAt" to createdAt,
        "approvalRequired" to approvalRequired, "approvalContribId" to approvalContribId,
        "iconSvg" to iconSvg, "iconColor" to iconColor,
        "checklist" to checklist.map { it.toMap() },
        "notes" to notes.map { it.toMap() }
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(m: Map<*, *>): Task? {
            val id = m["id"]?.toString() ?: return null
            val title = m["title"] as? String ?: return null
            return Task(
                id = id,
                title = title,
                description = m["description"] as? String ?: "",
                category = m["category"] as? String ?: "Personal",
                points = (m["points"] as? Number)?.toInt() ?: 0,
                dueDate = m["dueDate"] as? String,
                instanceDate = m["instanceDate"] as? String,
                repeat = (m["repeat"] as? Map<*, *>)?.let { RepeatRule.fromMap(it) },
                isTemplate = m["isTemplate"] == true,
                templateId = m["templateId"]?.toString(),
                lastGenerated = m["lastGenerated"] as? String,
                paused = m["paused"] == true,
                isPersonal = m["isPersonal"] != false,
                createdBy = m["createdBy"]?.toString(),
                assignedTo = m["assignedTo"]?.toString(),
                sharedWith = (m["sharedWith"] as? List<*>)?.map { it.toString() } ?: emptyList(),
                createdDate = m["createdDate"] as? String,
                createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
                approvalRequired = m["approvalRequired"] == true,
                approvalContribId = m["approvalContribId"]?.toString(),
                iconSvg = m["iconSvg"] as? String,
                iconColor = m["iconColor"] as? String,
                checklist = anyToMapList(m["checklist"]).mapNotNull { ChecklistItem.fromMap(it) },
                notes = anyToMapList(m["notes"]).mapNotNull { TaskNote.fromMap(it) }
            )
        }
    }
}
