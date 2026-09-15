package com.triangle.app.data

import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Faithful port of getIndividualGlobalRanking() (script.js:13214-13243) —
 * a genuinely cross-account global ranking, not a rank-of-1 degenerate like
 * some other per-org features this project has ported. Does the SAME
 * one-shot full-root-scan the source does: fetches the entire
 * `organizations` and `users` trees, filters to `type:"individual"` orgs,
 * and pulls each one's points out of its own embedded
 * `data/submissions/{adminId}/points`. There is no denormalized top-level
 * points index to query instead — this cost (reading every org's full data
 * blob just to read one field) is inherited from the source, same tradeoff
 * this project already accepted for Milestone 5's global DM user search.
 */
object LeaderboardRepository {
    private val db get() = FirebaseDatabase.getInstance(TriangleConfig.FIREBASE_URL)

    data class LeaderboardEntry(val uid: String, val orgId: String, val name: String, val points: Int)

    suspend fun fetchGlobalRanking(): List<LeaderboardEntry> {
        // Both root trees fetched once up front, then joined in memory — matches
        // source's own Promise.all([fbRootGet('organizations'), fbRootGet('users')]),
        // not a separate per-row users/{uid} lookup.
        val orgsSnap = db.getReference("organizations").get().await()
        val usersSnap = db.getReference("users").get().await()

        val entries = orgsSnap.children.mapNotNull { orgSnap ->
            val orgId = orgSnap.key ?: return@mapNotNull null
            val type = orgSnap.child("type").value as? String
            val adminId = orgSnap.child("adminId").value as? String
            if (type != "individual" || adminId.isNullOrBlank()) return@mapNotNull null
            val points = (orgSnap.child("data/submissions/$adminId/points").value as? Number)?.toInt() ?: 0
            val name = (usersSnap.child(adminId).child("name").value as? String)?.ifBlank { null } ?: "Anonymous"
            LeaderboardEntry(uid = adminId, orgId = orgId, name = name, points = points)
        }
        return entries.sortedByDescending { it.points }
    }
}
