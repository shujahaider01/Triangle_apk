package com.triangle.app.data

import com.google.firebase.database.FirebaseDatabase
import com.triangle.app.data.models.Habit
import com.triangle.app.data.models.HabitCompletionEntry
import com.triangle.app.data.models.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Cross-account delivery for the Connect->Assign->Complete->Earn model (see
 * .claude/plans/enchanted-brewing-beacon.md). Person A and Person B each
 * have their own separate Firebase "organization", so A can't just add an
 * item to a list B already reads from — instead this writes the assigned
 * Task/Habit directly into B's own org tree, same pattern as
 * NotificationRepository.push()'s cross-org lookup (`users/{uid}/orgId`),
 * and records a lightweight index under the fixed shared social/ root so A
 * can later find what they've assigned to others (their own org never sees
 * the item — it lives only in the recipient's org).
 *
 * When one task/habit is assigned to several recipients at once (the
 * Assign screen's multi-select), every copy shares the same `task.id`/
 * `habit.id` — harmless since each copy lives in a different recipient's
 * org — and the index is keyed `assignedByMe/{assignerUid}/{itemId}/
 * {recipientUid}` (one level deeper than the old single-recipient shape)
 * so those copies don't stomp each other and readAssignedGroups() can
 * join them back into one row with a per-recipient completion breakdown.
 */
object AssignmentRepository {
    private fun db() = FirebaseDatabase.getInstance()
    private fun orgData(orgId: String) = db().getReference("organizations/$orgId/data")
    private fun social() = db().getReference(TriangleConfig.SOCIAL_ROOT)

    // Best-effort background work (push notifications) that shouldn't make
    // the Save button wait — it already tolerated silent failure via
    // runCatching before, so firing it off without awaiting doesn't change
    // its semantics, only its timing. Scoped to the process, not to
    // whichever screen triggered it, so navigating away right after Save
    // can't cancel a notification that's still in flight.
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private suspend fun orgIdFor(uid: String): String? =
        (db().getReference("users/$uid/orgId").get().await().value as? String)

    suspend fun assignTask(assignerUid: String, assignerName: String, recipientUid: String, task: Task) {
        val (allowed, recipientOrgId) = coroutineScope {
            val allowedDeferred = async { ConnectionRepository.canAssign(recipientUid, assignerUid) }
            val orgIdDeferred = async { orgIdFor(recipientUid) }
            allowedDeferred.await() to orgIdDeferred.await()
        }
        if (!allowed || recipientOrgId == null) return
        val ref = orgData(recipientOrgId).child("tasks")
        val current = anyToMapList(ref.get().await().value).mapNotNull { Task.fromMap(it) }

        // The recipient's task list and the shared index entry don't depend
        // on each other's result, only on recipientOrgId above — writing
        // them concurrently instead of one-after-the-other shaves a full
        // round trip off Save's critical path.
        coroutineScope {
            val writeTaskDeferred = async { ref.setValue((current + task).map { it.toMap() }).await() }
            val writeIndexDeferred = async {
                social().child("assignedByMe/$assignerUid/${task.id}/$recipientUid").setValue(
                    mapOf(
                        "type" to "task",
                        "recipientOrgId" to recipientOrgId,
                        "title" to task.title,
                        "createdAt" to task.createdAt
                    )
                ).await()
            }
            writeTaskDeferred.await()
            writeIndexDeferred.await()
        }
        // A repeating task is a hidden template — create today's instance now instead of waiting for the recipient to reopen the app.
        if (task.repeat != null) runCatching { TaskRepository.materializeRepeatingTasks(recipientOrgId) }

        backgroundScope.launch {
            runCatching { NotificationRepository.notifyTaskAssigned(recipientUid, assignerUid, assignerName, task.title, task.id) }
        }
    }

    suspend fun assignHabit(assignerUid: String, assignerName: String, recipientUid: String, habit: Habit) {
        val (allowed, recipientOrgId) = coroutineScope {
            val allowedDeferred = async { ConnectionRepository.canAssign(recipientUid, assignerUid) }
            val orgIdDeferred = async { orgIdFor(recipientUid) }
            allowedDeferred.await() to orgIdDeferred.await()
        }
        if (!allowed || recipientOrgId == null) return
        val ref = orgData(recipientOrgId).child("habits")
        val current = anyToMapList(ref.get().await().value).mapNotNull { Habit.fromMap(it) }

        coroutineScope {
            val writeHabitDeferred = async { ref.setValue((current + habit).map { it.toMap() }).await() }
            val writeIndexDeferred = async {
                social().child("assignedByMe/$assignerUid/${habit.id}/$recipientUid").setValue(
                    mapOf(
                        "type" to "habit",
                        "recipientOrgId" to recipientOrgId,
                        "title" to habit.name,
                        "createdAt" to habit.createdAt
                    )
                ).await()
            }
            writeHabitDeferred.await()
            writeIndexDeferred.await()
        }

        backgroundScope.launch {
            runCatching { NotificationRepository.notifyHabitAssigned(recipientUid, assignerUid, assignerName, habit.name, habit.id) }
        }
    }

    /** Raw index entries under social/assignedByMe/{uid}/{itemId}/{recipientUid} — flattened one entry per recipient. */
    data class AssignedByMeIndexEntry(
        val itemId: String,
        val type: String, // "task" | "habit"
        val recipientUid: String,
        val recipientOrgId: String,
        val title: String,
        val createdAt: Long
    )

    data class AssignedTaskMember(val uid: String, val name: String, val done: Boolean, val photoUrl: String? = null)
    data class AssignedTaskGroup(val itemId: String, val task: Task, val members: List<AssignedTaskMember>) {
        val doneCount: Int get() = members.count { it.done }
    }

    data class AssignedHabitMember(val uid: String, val name: String, val completions: Map<String, HabitCompletionEntry>, val photoUrl: String? = null)
    data class AssignedHabitGroup(val itemId: String, val habit: Habit, val members: List<AssignedHabitMember>) {
        fun doneCount(dateStr: String): Int = members.count { it.completions.containsKey(dateStr) }
    }

    data class AssignedGroups(val taskGroups: List<AssignedTaskGroup>, val habitGroups: List<AssignedHabitGroup>)

    /** Recipient uids from this assigner's history, most-recently-assigned first, de-duplicated — backs the Assign screen's "Recent" tab. */
    suspend fun recentRecipients(assignerUid: String): List<String> =
        readAssignedByMe(assignerUid).map { it.recipientUid }.distinct()

    suspend fun readAssignedByMe(assignerUid: String): List<AssignedByMeIndexEntry> {
        val snap = social().child("assignedByMe/$assignerUid").get().await()
        return snap.children.flatMap { itemSnap ->
            val itemId = itemSnap.key ?: return@flatMap emptyList()
            itemSnap.children.mapNotNull { recipientSnap ->
                val m = recipientSnap.value as? Map<*, *> ?: return@mapNotNull null
                val recipientUid = recipientSnap.key ?: return@mapNotNull null
                val recipientOrgId = m["recipientOrgId"] as? String ?: return@mapNotNull null
                AssignedByMeIndexEntry(
                    itemId = itemId,
                    type = m["type"] as? String ?: "task",
                    recipientUid = recipientUid,
                    recipientOrgId = recipientOrgId,
                    title = m["title"] as? String ?: "",
                    createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L
                )
            }
        }.sortedByDescending { it.createdAt }
    }

    /**
     * One-shot join of the assignedByMe index against every recipient's live
     * org data — same "read-mostly summary view" tradeoff the old Assigned-
     * by-Me screen made (a live multi-org flow combiner would be a lot more
     * plumbing for state that only really needs to be fresh when the caller
     * asks): TasksHabitsViewModel re-calls this whenever the index changes
     * or the Tasks/Habits screen resumes.
     *
     * Every cross-org lookup here — one per recipient per item, plus one per
     * unique recipient name — runs concurrently via async/awaitAll instead of
     * a sequential loop. This is why "received" rows (a single live listener
     * on your own org) render so much faster than "sent to others" rows used
     * to: those needed N one-shot round trips to N different orgs, one after
     * another. Now they all fire at once, so the wait is as long as the
     * SLOWEST recipient lookup, not the sum of all of them.
     */
    suspend fun readAssignedGroups(assignerUid: String): AssignedGroups = coroutineScope {
        val entries = readAssignedByMe(assignerUid)

        // One record fetch per unique recipient, shared across every group/item that recipient appears in.
        val recordDeferreds = entries.map { it.recipientUid }.distinct().associateWith { uid ->
            async { UserRepository.fetchUserRecord(uid) }
        }

        val taskGroupsDeferred = entries.filter { it.type == "task" }.groupBy { it.itemId }.map { (itemId, group) ->
            async {
                // What each recipient currently has: their tasks, which ones they've completed, and who they are.
                class Fetched(val entry: AssignedByMeIndexEntry, val tasks: List<Task>, val completions: Set<String>, val record: UserRepository.UserRecord?)
                val fetched = group.map { entry ->
                    async {
                        val tasksDeferred = async { TaskRepository.tasksFlow(entry.recipientOrgId).first() }
                        val completionsDeferred = async { TaskRepository.completionsFlow(entry.recipientOrgId).first() }
                        val record = recordDeferreds.getValue(entry.recipientUid).await()
                        Fetched(entry, tasksDeferred.await(), completionsDeferred.await(), record)
                    }
                }.awaitAll()
                fun memberFor(f: Fetched, taskId: String) = AssignedTaskMember(
                    f.entry.recipientUid,
                    f.record?.name ?: "Unknown",
                    done = f.completions.contains("$taskId-${f.entry.recipientUid}"),
                    photoUrl = f.record?.photoUrl
                )
                val representative = fetched.firstNotNullOfOrNull { f -> f.tasks.firstOrNull { it.id == itemId } }
                if (representative != null && representative.isTemplate && representative.repeat != null) {
                    // A repeating assignment is a hidden template; what the assigner sees is each day's generated
                    // copy (same id on every recipient), one row per day, with each recipient's done state.
                    val instanceIds = fetched.flatMap { f -> f.tasks.filter { it.templateId == itemId }.map { it.id } }.distinct()
                    instanceIds.mapNotNull { id ->
                        val instance = fetched.firstNotNullOfOrNull { f -> f.tasks.firstOrNull { it.id == id } } ?: return@mapNotNull null
                        AssignedTaskGroup(id, instance, fetched.map { memberFor(it, id) })
                    }
                } else {
                    representative?.let { listOf(AssignedTaskGroup(itemId, it, fetched.map { f -> memberFor(f, itemId) })) } ?: emptyList()
                }
            }
        }

        val habitGroupsDeferred = entries.filter { it.type == "habit" }.groupBy { it.itemId }.map { (itemId, group) ->
            async {
                val memberResults = group.map { entry ->
                    async {
                        val habitsDeferred = async { HabitRepository.habitsFlow(entry.recipientOrgId).first() }
                        val completionsDeferred = async { HabitRepository.habitCompletionsFlow(entry.recipientOrgId, entry.recipientUid).first() }
                        val habits = habitsDeferred.await()
                        val completions = completionsDeferred.await()
                        val representative = habits.firstOrNull { it.id == itemId }
                        val record = recordDeferreds.getValue(entry.recipientUid).await()
                        val member = AssignedHabitMember(
                            entry.recipientUid,
                            record?.name ?: "Unknown",
                            completions = completions[itemId] ?: emptyMap(),
                            photoUrl = record?.photoUrl
                        )
                        representative to member
                    }
                }.awaitAll()
                val representative = memberResults.firstNotNullOfOrNull { it.first }
                representative?.let { AssignedHabitGroup(itemId, it, memberResults.map { m -> m.second }) }
            }
        }

        AssignedGroups(taskGroupsDeferred.awaitAll().flatten(), habitGroupsDeferred.awaitAll().filterNotNull())
    }

    fun assignedByMeFlow(assignerUid: String) =
        social().child("assignedByMe/$assignerUid").valueFlow()

    /**
     * Assigner-side delete: removes the item from every recipient's own org
     * (each recipient holds an independent copy, same id) and drops the whole
     * assignedByMe/{assignerUid}/{itemId} index subtree. Per-recipient
     * failures are swallowed so one unreachable org doesn't strand the rest.
     */
    /**
     * Assigner-side edit: pushes the editable fields of [edited] onto every
     * recipient's own copy (same id), leaving per-recipient state alone —
     * assignedTo, notes, and each checklist item's done flag (matched by id).
     * Per-recipient failures are swallowed, same as the delete path.
     */
    suspend fun updateTaskAssignment(assignerUid: String, editedInstance: Task) = coroutineScope {
        val edited = editedInstance.copy(id = seriesId(editedInstance.id))
        val entries = readAssignedByMe(assignerUid).filter { it.itemId == edited.id && it.type == "task" }
        entries.map { entry ->
            async {
                runCatching {
                    val theirs = TaskRepository.getTask(entry.recipientOrgId, edited.id) ?: return@runCatching
                    val doneIds = theirs.checklist.filter { it.done }.map { it.id }.toSet()
                    TaskRepository.updateTask(
                        entry.recipientOrgId,
                        theirs.copy(
                            title = edited.title,
                            description = edited.description,
                            priority = edited.priority,
                            points = edited.points,
                            dueDate = if (theirs.isTemplate) theirs.dueDate else edited.dueDate,
                            reminderTime = edited.reminderTime,
                            iconColor = edited.iconColor,
                            iconSvg = edited.iconSvg,
                            checklist = edited.checklist.map { it.copy(done = it.id in doneIds) }
                        )
                    )
                }
            }
        }.awaitAll()
        social().child("assignedByMe/$assignerUid/${edited.id}").get().await().children.forEach {
            runCatching { it.ref.child("title").setValue(edited.title).await() }
        }
    }

    /** Same idea as updateTaskAssignment(), for a habit (completions live outside the habit record, so they're untouched). */
    suspend fun updateHabitAssignment(assignerUid: String, edited: Habit) = coroutineScope {
        val entries = readAssignedByMe(assignerUid).filter { it.itemId == edited.id && it.type == "habit" }
        entries.map { entry ->
            async {
                runCatching {
                    val theirs = HabitRepository.getHabit(entry.recipientOrgId, edited.id) ?: return@runCatching
                    HabitRepository.updateHabit(
                        entry.recipientOrgId,
                        theirs.copy(
                            name = edited.name,
                            description = edited.description,
                            color = edited.color,
                            iconSvg = edited.iconSvg,
                            priority = edited.priority,
                            xpPerCompletion = edited.xpPerCompletion,
                            frequency = edited.frequency,
                            reminderTime = edited.reminderTime
                        )
                    )
                }
            }
        }.awaitAll()
        social().child("assignedByMe/$assignerUid/${edited.id}").get().await().children.forEach {
            runCatching { it.ref.child("title").setValue(edited.name).await() }
        }
    }

    /** A generated day of a repeating assignment ("ri-<templateId>-yyyy-MM-dd") belongs to the template the assigner actually created. */
    private fun seriesId(id: String): String = if (id.startsWith("ri-") && id.length > 14) id.removePrefix("ri-").dropLast(11) else id

    suspend fun deleteTaskAssignment(assignerUid: String, taskId: String) = coroutineScope {
        val itemId = seriesId(taskId)
        val entries = readAssignedByMe(assignerUid).filter { it.itemId == itemId && it.type == "task" }
        entries.map { entry -> async { runCatching { TaskRepository.deleteTask(entry.recipientOrgId, itemId) } } }.awaitAll()
        social().child("assignedByMe/$assignerUid/$itemId").removeValue().await()
    }

    suspend fun deleteHabitAssignment(assignerUid: String, itemId: String) = coroutineScope {
        val entries = readAssignedByMe(assignerUid).filter { it.itemId == itemId && it.type == "habit" }
        entries.map { entry -> async { runCatching { HabitRepository.deleteHabit(entry.recipientOrgId, itemId) } } }.awaitAll()
        social().child("assignedByMe/$assignerUid/$itemId").removeValue().await()
    }
}
