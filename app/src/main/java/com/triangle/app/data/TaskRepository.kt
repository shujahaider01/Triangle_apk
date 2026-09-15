package com.triangle.app.data

import com.google.firebase.database.FirebaseDatabase
import com.triangle.app.data.models.Task
import com.triangle.app.data.models.TaskNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * Tasks data layer for the native Tasks screen (Milestone 2). Every write
 * here targets only the narrow sub-path it changes (`.../data/tasks`,
 * `.../data/taskCompletions`, `.../data/submissions/{uid}/points` etc.) —
 * NEVER a full `organizations/{orgId}/data` blob write — see the
 * Milestone 2 plan's "Firebase writes: narrow sub-path writes only"
 * decision for why (a full-blob write from native code would silently
 * destroy sibling fields, like rewards/feed/complaints, this app never
 * loaded).
 */
object TaskRepository {
    private fun orgData(orgId: String) =
        FirebaseDatabase.getInstance(TriangleConfig.FIREBASE_URL).getReference("organizations/$orgId/data")

    fun tasksFlow(orgId: String): Flow<List<Task>> =
        orgData(orgId).child("tasks").valueFlow().map { snap ->
            anyToMapList(snap.value).mapNotNull { Task.fromMap(it) }
        }

    /** Set of "taskId-internId" keys currently marked done — mirrors script.js's isTaskDone(). */
    fun completionsFlow(orgId: String): Flow<Set<String>> =
        orgData(orgId).child("taskCompletions").valueFlow().map { snap ->
            @Suppress("UNCHECKED_CAST")
            val raw = (snap.value as? Map<String, Any?>) ?: emptyMap()
            raw.entries.filter { (it.value as? Map<*, *>)?.get("done") == true }.map { it.key }.toSet()
        }

    suspend fun saveNewTask(orgId: String, task: Task) {
        val current = readTasks(orgId)
        orgData(orgId).child("tasks").setValue((current + task).map { it.toMap() }).await()
    }

    suspend fun updateTask(orgId: String, task: Task) {
        val current = readTasks(orgId)
        orgData(orgId).child("tasks").setValue(current.map { if (it.id == task.id) task else it }.map { it.toMap() }).await()
    }

    /** Matches script.js's deletePersonalTask() — also removes any repeat-generated children. */
    suspend fun deleteTask(orgId: String, taskId: String) {
        val before = readTasks(orgId)
        val removedIds = before.filter { it.id == taskId || it.templateId == taskId }.map { it.id }.toSet()
        if (removedIds.isEmpty()) return
        orgData(orgId).child("tasks").setValue(before.filterNot { it.id in removedIds }.map { it.toMap() }).await()
        purgeCompletions(orgId, removedIds)
    }

    private suspend fun purgeCompletions(orgId: String, taskIds: Set<String>) {
        val ref = orgData(orgId).child("taskCompletions")
        @Suppress("UNCHECKED_CAST")
        val raw = (ref.get().await().value as? Map<String, Any?>) ?: emptyMap()
        val filtered = raw.filterKeys { key -> taskIds.none { key.startsWith("$it-") } }
        ref.setValue(filtered).await()
    }

    /** Immediate write (completion flag + points/coins) — matches script.js's ntdChangeStatus() "Mark Complete" path. */
    suspend fun completeTask(orgId: String, uid: String, task: Task) {
        setCompletionFlag(orgId, task.id, uid, done = true)
        if (task.points > 0) awardTaskPoints(orgId, uid, task)
    }

    /**
     * Matches ntdChangeStatus()'s un-complete branch: clears the completion
     * flag only. Deliberately does NOT revert points/coins — neither does
     * the source app via this path (only the 5-second undo toast on the
     * task-LIST quick-complete flow reverts points, and it does so by
     * simply never having committed the write yet — see TasksViewModel).
     */
    suspend fun uncompleteTask(orgId: String, uid: String, taskId: String) {
        setCompletionFlag(orgId, taskId, uid, done = false)
    }

    private suspend fun setCompletionFlag(orgId: String, taskId: String, uid: String, done: Boolean) {
        val ref = orgData(orgId).child("taskCompletions")
        @Suppress("UNCHECKED_CAST")
        val raw = (ref.get().await().value as? Map<String, Any?>) ?: emptyMap()
        val key = "$taskId-$uid"
        val updated = if (done) raw + (key to mapOf("done" to true)) else raw - key
        ref.setValue(updated).await()
    }

    private suspend fun awardTaskPoints(orgId: String, uid: String, task: Task) {
        val pointsRef = orgData(orgId).child("submissions/$uid/points")
        val curPoints = (pointsRef.get().await().value as? Number)?.toInt() ?: 0
        pointsRef.setValue(curPoints + task.points).await()

        // Field names match script.js's own pointHistory.unshift({type:'task', label, pts, date})
        // exactly (see e.g. ntdChangeStatus()) — getWeeklyXPRaw()/getMonthlyPerformance()/etc. all
        // read `h.pts`, not `h.points`, so this has to match precisely or a task's XP silently
        // vanishes from every chart that reads pointHistory.
        val historyRef = orgData(orgId).child("submissions/$uid/pointHistory")
        val history = anyToMapList(historyRef.get().await().value)
        val entry = mapOf(
            "type" to "task",
            "label" to task.title,
            "pts" to task.points,
            "date" to LocalDate.now().toString()
        )
        historyRef.setValue(listOf(entry) + history).await() // unshift — newest first, matches source

        CoinWallet.award(orgId, uid, task.points, "task", task.title, task.id)
    }

    /**
     * Prepends a new note (text or photo) to the task's `notes` timeline —
     * see TaskNoteRepository for the photo-upload step that produces a
     * `content` URL before this is called for a photo note. Same
     * read-modify-write-the-whole-array pattern as every other task
     * mutation here (tasks are array-shaped in Firebase, not a new
     * exception).
     */
    suspend fun addNote(orgId: String, taskId: String, note: TaskNote) {
        val current = readTasks(orgId)
        val updated = current.map { t -> if (t.id == taskId) t.copy(notes = listOf(note) + t.notes) else t }
        orgData(orgId).child("tasks").setValue(updated.map { it.toMap() }).await()
    }

    private suspend fun readTasks(orgId: String): List<Task> =
        anyToMapList(orgData(orgId).child("tasks").get().await().value).mapNotNull { Task.fromMap(it) }

    /** Materializes today's (and any missed) instance(s) of every repeat template — see RepeatTaskEngine. */
    suspend fun materializeRepeatingTasks(orgId: String) {
        val tasks = readTasks(orgId)
        val result = RepeatTaskEngine.process(tasks)
        if (result.changed) {
            orgData(orgId).child("tasks").setValue(result.tasks.map { it.toMap() }).await()
        }
    }
}
