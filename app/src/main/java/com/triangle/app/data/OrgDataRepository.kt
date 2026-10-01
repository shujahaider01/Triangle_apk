package com.triangle.app.data

import com.google.firebase.database.FirebaseDatabase
import com.triangle.app.data.models.Task
import kotlinx.coroutines.tasks.await

/**
 * Reads the org's app-data blob (organizations/{orgId}/data — the JS app's
 * `db`) for the native Dashboard. One-shot `.get()` only for Milestone 1
 * (mirrors script.js's loadDB(), minus its live SSE sync layer — see the
 * plan's "Realtime updates" note; native screens don't need live multi-
 * device sync until a later milestone actually mutates data from two
 * places at once).
 *
 * Dashboard is read-only in Milestone 1, so — unlike script.js's
 * blankDB()/patchDB(), which fully models and defends every field because
 * saveDB() writes the whole blob back — this only extracts the handful of
 * fields the Dashboard actually displays, defensively (missing/malformed
 * data reads as empty/zero, never throws), rather than porting the full
 * per-org schema.
 */
object OrgDataRepository {
    private val db get() = FirebaseDatabase.getInstance()

    data class DashboardSnapshot(
        val tasks: List<Map<String, Any?>>,
        val taskCompletions: Map<String, Any?>,
        val points: Int,
        val weeklyXP: Int,
        /** id -> points for every org member with a submissions record, admin placeholder id "99" excluded. */
        val memberPoints: Map<String, Int>
    )

    suspend fun fetchDashboard(orgId: String, userId: String): DashboardSnapshot {
        val dataSnap = db.getReference("organizations/$orgId/data").get().await()

        val tasks = asMapList(dataSnap.child("tasks").value)

        @Suppress("UNCHECKED_CAST")
        val taskCompletions = (dataSnap.child("taskCompletions").value as? Map<String, Any?>) ?: emptyMap()

        @Suppress("UNCHECKED_CAST")
        val submissions = (dataSnap.child("submissions").value as? Map<String, Any?>) ?: emptyMap()

        val mySubmission = submissions[userId] as? Map<*, *>
        val points = (mySubmission?.get("points") as? Number)?.toInt() ?: 0
        val pointHistory = asMapList(mySubmission?.get("pointHistory"))
        val weeklyXP = ProfileStats.getWeeklyXpRaw(pointHistory).sum()

        // Approximates script.js's roster-scan-based INTERNS list (id != 99)
        // with the submissions map's own keys — every real org member has a
        // submissions entry (patchDB() guarantees this on the JS side), so
        // this avoids a second full `/users` root scan for the same result
        // in the common case, at the cost of not independently verifying
        // roster membership the way _refreshInternsRoster() does.
        val memberPoints = submissions
            .filterKeys { it != "99" }
            .mapValues { (_, v) -> (v as? Map<*, *>)?.get("points") as? Number }
            .mapValues { (_, v) -> v?.toInt() ?: 0 }

        return DashboardSnapshot(tasks, taskCompletions, points, weeklyXP, memberPoints)
    }

    data class ProfileSnapshot(
        val tasks: List<Task>,
        val completions: Set<String>,
        val points: Int,
        val createdAt: Long,
        /** submissions/{uid}/pointHistory — newest first, matches script.js's unshift() ordering. */
        val pointHistory: List<Map<String, Any?>>,
        /** id -> points for every org member with a submissions record, admin placeholder id "99" excluded. */
        val memberPoints: Map<String, Int>
    )

    /** Read-only extension of fetchDashboard for Milestone 3's Profile screen — same one-shot .get(), no new write paths. */
    suspend fun fetchProfileData(orgId: String, userId: String): ProfileSnapshot {
        val dataSnap = db.getReference("organizations/$orgId/data").get().await()

        val tasks = asMapList(dataSnap.child("tasks").value).mapNotNull { Task.fromMap(it) }

        @Suppress("UNCHECKED_CAST")
        val taskCompletionsRaw = (dataSnap.child("taskCompletions").value as? Map<String, Any?>) ?: emptyMap()
        val completions = taskCompletionsRaw.entries.filter { (it.value as? Map<*, *>)?.get("done") == true }.map { it.key }.toSet()

        @Suppress("UNCHECKED_CAST")
        val submissions = (dataSnap.child("submissions").value as? Map<String, Any?>) ?: emptyMap()
        val mySubmission = submissions[userId] as? Map<*, *>
        val points = (mySubmission?.get("points") as? Number)?.toInt() ?: 0

        @Suppress("UNCHECKED_CAST")
        val pointHistory = asMapList(mySubmission?.get("pointHistory"))

        val createdAt = (db.getReference("users/$userId/createdAt").get().await().value as? Number)?.toLong() ?: 0L

        val memberPoints = submissions
            .filterKeys { it != "99" }
            .mapValues { (_, v) -> (v as? Map<*, *>)?.get("points") as? Number }
            .mapValues { (_, v) -> v?.toInt() ?: 0 }

        return ProfileSnapshot(tasks, completions, points, createdAt, pointHistory, memberPoints)
    }

    @Suppress("UNCHECKED_CAST")
    private fun asMapList(raw: Any?): List<Map<String, Any?>> = when (raw) {
        is List<*> -> raw.mapNotNull { it as? Map<String, Any?> }
        is Map<*, *> -> raw.values.mapNotNull { it as? Map<String, Any?> }
        else -> emptyList()
    }
}
