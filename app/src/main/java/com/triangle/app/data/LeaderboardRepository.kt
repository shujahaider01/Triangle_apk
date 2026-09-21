package com.triangle.app.data

import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
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
    private val db get() = FirebaseDatabase.getInstance()

    data class LeaderboardEntry(val uid: String, val orgId: String, val name: String, val points: Int, val photoUrl: String? = null)

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
            val photoUrl = usersSnap.child(adminId).child("photoUrl").value as? String
            LeaderboardEntry(uid = adminId, orgId = orgId, name = name, points = points, photoUrl = photoUrl)
        }
        return entries.sortedByDescending { it.points }
    }

    /** Per-Circle-member XP totals for the leaderboard's "You Owe"/"He Owe" lines, keyed by the other person's uid. */
    data class OweAmounts(val youOwe: Int, val heOwe: Int)

    /**
     * "You Owe" is the total XP of everything [myUid] has assigned OUT to
     * each recipient (readAssignedGroups() already has the recipient list
     * and the full Task/Habit, so no extra lookups needed there). "He Owe"
     * is the total XP of everything in MY OWN org's tasks/habits that someone
     * ELSE created — those already carry a `createdBy` field identifying the
     * assigner, so this reads only my own org data, never a foreign one.
     * Neither total is filtered by completion — see the "He Owe"/"You Owe"
     * wording, which counts total assigned value, not just what's done.
     */
    suspend fun fetchOweAmounts(myUid: String, myOrgId: String): Map<String, OweAmounts> = coroutineScope {
        val assignedGroupsDeferred = async { AssignmentRepository.readAssignedGroups(myUid) }
        val myTasksDeferred = async { TaskRepository.tasksFlow(myOrgId).first() }
        val myHabitsDeferred = async { HabitRepository.habitsFlow(myOrgId).first() }

        val youOwe = mutableMapOf<String, Int>()
        val groups = assignedGroupsDeferred.await()
        groups.taskGroups.forEach { g -> g.members.forEach { m -> youOwe[m.uid] = (youOwe[m.uid] ?: 0) + g.task.points } }
        groups.habitGroups.forEach { g -> g.members.forEach { m -> youOwe[m.uid] = (youOwe[m.uid] ?: 0) + g.habit.xpPerCompletion } }

        val heOwe = mutableMapOf<String, Int>()
        myTasksDeferred.await().forEach { t ->
            val by = t.createdBy
            if (by != null && by != myUid) heOwe[by] = (heOwe[by] ?: 0) + t.points
        }
        myHabitsDeferred.await().forEach { h ->
            val by = h.createdBy
            if (by != null && by != myUid) heOwe[by] = (heOwe[by] ?: 0) + h.xpPerCompletion
        }

        (youOwe.keys + heOwe.keys).associateWith { uid -> OweAmounts(youOwe[uid] ?: 0, heOwe[uid] ?: 0) }
    }
}
