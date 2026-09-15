package com.triangle.app.data

import com.google.firebase.database.FirebaseDatabase
import com.triangle.app.data.models.Habit
import com.triangle.app.data.models.HabitCompletionEntry
import com.triangle.app.data.models.TaskNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Habits data layer (Milestone 2) — same narrow-sub-path-write discipline
 * as TaskRepository (see its header comment / the plan's write-strategy
 * decision). `deleteHabit` fills a real gap noted in the Milestone 2 plan:
 * the source WebView app has no delete-habit entry point at all for the
 * Individual role (only reachable from an admin-only "manage habit"
 * modal), which is a genuine product gap rather than something worth
 * porting faithfully — Individual users should be able to delete their
 * own habits.
 */
object HabitRepository {
    private fun orgData(orgId: String) =
        FirebaseDatabase.getInstance(TriangleConfig.FIREBASE_URL).getReference("organizations/$orgId/data")

    fun habitsFlow(orgId: String): Flow<List<Habit>> =
        orgData(orgId).child("habits").valueFlow().map { snap ->
            anyToMapList(snap.value).mapNotNull { Habit.fromMap(it) }
        }

    /** habitId -> dateStr -> completion entry, for one intern (db.habitCompletions[uid] in script.js). */
    fun habitCompletionsFlow(orgId: String, uid: String): Flow<Map<String, Map<String, HabitCompletionEntry>>> =
        orgData(orgId).child("habitCompletions/$uid").valueFlow().map { snap ->
            val raw = (snap.value as? Map<*, *>) ?: emptyMap<Any?, Any?>()
            raw.entries.associate { (habitId, byDate) ->
                val dateMap = (byDate as? Map<*, *>) ?: emptyMap<Any?, Any?>()
                habitId.toString() to dateMap.entries.associate { (date, entry) ->
                    date.toString() to HabitCompletionEntry.fromMap((entry as? Map<*, *>) ?: emptyMap<Any?, Any?>())
                }
            }
        }

    suspend fun saveNewHabit(orgId: String, habit: Habit) {
        val current = readHabits(orgId)
        orgData(orgId).child("habits").setValue((current + habit).map { it.toMap() }).await()
    }

    suspend fun updateHabit(orgId: String, habit: Habit) {
        val current = readHabits(orgId)
        orgData(orgId).child("habits").setValue(current.map { if (it.id == habit.id) habit else it }.map { it.toMap() }).await()
    }

    suspend fun deleteHabit(orgId: String, habitId: String) {
        val current = readHabits(orgId)
        orgData(orgId).child("habits").setValue(current.filterNot { it.id == habitId }.map { it.toMap() }).await()
    }

    private suspend fun readHabits(orgId: String): List<Habit> =
        anyToMapList(orgData(orgId).child("habits").get().await().value).mapNotNull { Habit.fromMap(it) }

    /**
     * Matches script.js's completeHabit(): idempotent (a day already marked
     * done is a no-op — there's no un-complete UI anywhere in the source
     * app either), retried up to twice on failure, points/coins awarded
     * only on success.
     */
    suspend fun completeHabit(orgId: String, uid: String, habit: Habit, dateStr: String) {
        val entryRef = orgData(orgId).child("habitCompletions/$uid/${habit.id}/$dateStr")
        if (entryRef.get().await().exists()) return

        val xp = habit.xpPerCompletion
        val entry = mapOf("completedAt" to System.currentTimeMillis(), "xpAwarded" to xp)

        var lastError: Exception? = null
        repeat(2) {
            try {
                entryRef.setValue(entry).await()
                if (xp > 0) {
                    val pointsRef = orgData(orgId).child("submissions/$uid/points")
                    val curPoints = (pointsRef.get().await().value as? Number)?.toInt() ?: 0
                    pointsRef.setValue(curPoints + xp).await()

                    // script.js's own completeHabit() actually writes pointHistory with a
                    // `points` field instead of the `pts` field every *reader* (getWeeklyXPRaw,
                    // getMonthlyPerformance, ...) expects — a real inconsistency in the source
                    // that makes habit-earned XP silently vanish from those charts. Standardizing
                    // on `pts` here (matching TaskRepository and every reader) rather than
                    // faithfully reproducing a bug that would visibly break this milestone's own
                    // charts for anyone whose XP came from habits.
                    val historyRef = orgData(orgId).child("submissions/$uid/pointHistory")
                    val history = anyToMapList(historyRef.get().await().value)
                    val entry = mapOf("type" to "habit", "label" to habit.name, "pts" to xp, "date" to dateStr)
                    historyRef.setValue(listOf(entry) + history).await()

                    CoinWallet.award(orgId, uid, xp, "habit", habit.name, habit.id)
                }
                return
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Failed to complete habit")
    }

    suspend fun addHabitNote(orgId: String, uid: String, habitId: String, dateStr: String, text: String) {
        orgData(orgId).child("habitNotes/$uid/$habitId/$dateStr").setValue(text).await()
    }

    /** One-shot read of the note addHabitNote() wrote for that day — Habit Detail prefills its "Add Note" dialog with this. */
    suspend fun getHabitNote(orgId: String, uid: String, habitId: String, dateStr: String): String =
        (orgData(orgId).child("habitNotes/$uid/$habitId/$dateStr").get().await().value as? String) ?: ""

    /** Prepends a photo note to the habit's `photos` timeline — same read-modify-write-whole-array pattern as TaskRepository.addNote. */
    suspend fun addHabitPhoto(orgId: String, habitId: String, note: TaskNote) {
        val current = readHabits(orgId)
        val updated = current.map { h -> if (h.id == habitId) h.copy(photos = listOf(note) + h.photos) else h }
        orgData(orgId).child("habits").setValue(updated.map { it.toMap() }).await()
    }
}
