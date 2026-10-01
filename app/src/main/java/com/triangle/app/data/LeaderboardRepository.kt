package com.triangle.app.data

import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/** Leaderboard data: points of the signed-in user and their connections (not a global ranking). */
object LeaderboardRepository {
    private val db get() = FirebaseDatabase.getInstance()

    /** [points] is the all-time total; [weekPoints] the last 7 days (like "Points This Week"); [monthPoints] the current calendar month. */
    data class LeaderboardEntry(
        val uid: String, val orgId: String, val name: String, val points: Int, val photoUrl: String? = null,
        val weekPoints: Int = 0, val monthPoints: Int = 0
    )

    /**
     * Ranking of ME and my connections only — nobody outside my circle is read or shown. Per person it fetches
     * just their user record and three tiny values from their own org (never the whole `organizations` tree).
     */
    suspend fun fetchRanking(myUid: String, connectionUids: Set<String>): List<LeaderboardEntry> = coroutineScope {
        val uids = (connectionUids + myUid).toList()
        val entries = uids.chunked(20).flatMap { chunk ->
            chunk.map { uid ->
                async {
                    runCatching {
                        val user = db.getReference("users/$uid").get().await()
                        val orgId = (user.child("orgId").value as? String)?.takeIf { it.isNotBlank() } ?: return@runCatching null
                        val name = (user.child("name").value as? String)?.ifBlank { null } ?: "Anonymous"
                        val photoUrl = user.child("photoUrl").value as? String
                        val org = db.getReference("organizations/$orgId")
                        val type = org.child("type").get().await().value as? String
                        val adminId = org.child("adminId").get().await().value as? String
                        if (type != "individual" || adminId != uid) return@runCatching null
                        val points = (org.child("data/submissions/$uid/points").get().await().value as? Number)?.toInt() ?: 0
                        val today = java.time.LocalDate.now()
                        val todayStr = today.toString()
                        val weekStart = today.minusDays(6).toString()
                        val monthPrefix = todayStr.substring(0, 7)
                        var week = 0
                        var month = 0
                        org.child("data/submissions/$uid/pointHistory").get().await().children.forEach { h ->
                            val date = h.child("date").value as? String ?: return@forEach
                            val pts = (h.child("pts").value as? Number)?.toInt() ?: (h.child("points").value as? Number)?.toInt() ?: 0
                            if (date >= weekStart && date <= todayStr) week += pts
                            if (date.startsWith(monthPrefix)) month += pts
                        }
                        LeaderboardEntry(uid = uid, orgId = orgId, name = name, points = points, photoUrl = photoUrl, weekPoints = week, monthPoints = month)
                    }.getOrNull()
                }
            }.awaitAll()
        }.filterNotNull()
        entries.sortedByDescending { it.points }
    }

    /** Per-Circle-member XP totals for the leaderboard's "You Owe"/"Owes" lines, keyed by the other person's uid. */
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
