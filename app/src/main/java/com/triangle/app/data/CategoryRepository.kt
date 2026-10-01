package com.triangle.app.data

import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** A category for Solo tasks and habits: a name with its own colour and icon (see Settings > Categories). */
data class TaskCategory(val id: String, val name: String, val color: String, val iconKey: String) {
    fun toMap(): Map<String, Any?> = mapOf("id" to id, "name" to name, "color" to color, "iconKey" to iconKey)

    companion object {
        fun fromMap(m: Map<*, *>): TaskCategory? {
            val name = m["name"] as? String ?: return null
            return TaskCategory(
                id = m["id"]?.toString() ?: name,
                name = name,
                color = m["color"] as? String ?: HabitPalette.DEFAULT_COLOR,
                iconKey = m["iconKey"] as? String ?: HabitPalette.DEFAULT_ICON_KEY
            )
        }
    }
}

/**
 * The user's own category list, stored in their org at data/taskCategories. Until they save one the three
 * original categories (Office, Academic, Personal) are used, so tasks that already carry those names keep working.
 */
object CategoryRepository {
    val DEFAULTS = listOf(
        TaskCategory("default-office", "Office", "#3B82F6", "office"),
        TaskCategory("default-academic", "Academic", "#22C55E", "book"),
        TaskCategory("default-personal", "Personal", "#F59E0B", "home")
    )

    private fun ref(orgId: String) =
        FirebaseDatabase.getInstance().getReference("organizations/$orgId/data/taskCategories")

    fun categoriesFlow(orgId: String): Flow<List<TaskCategory>> =
        ref(orgId).valueFlow().map { snap ->
            if (!snap.exists()) DEFAULTS
            else anyToMapList(snap.value).mapNotNull { TaskCategory.fromMap(it) }.ifEmpty { DEFAULTS }
        }

    /** Replaces the whole list (narrow write to just this node). */
    suspend fun save(orgId: String, categories: List<TaskCategory>) {
        ref(orgId).setValue(categories.map { it.toMap() }).await()
    }
}
