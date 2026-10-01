package com.triangle.app.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import com.google.firebase.database.FirebaseDatabase
import com.triangle.app.DriveImageHelper
import com.triangle.app.data.models.Announcement
import com.triangle.app.data.models.AnnouncementAttachment
import com.triangle.app.data.models.AnnouncementSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Announcements data layer — see the plan at .claude/plans (Announcements). The full
 * announcement is stored ONCE at `social/announcements/{id}`; each recipient's
 * notification row only carries a preview + that id, so a big body/photo set never
 * gets rewritten into every recipient's notification array on each markRead. The
 * sender's Sent list lives at `social/announcementIndex/{senderUid}/{id}`.
 */
object AnnouncementRepository {
    const val TITLE_MAX = 80
    const val BODY_MAX = 1000
    const val MAX_ATTACHMENTS = 6
    /** Per-file cap for videos/PDFs: the Drive upload holds a file in memory (twice, while building the request), so keep it modest. */
    const val MAX_FILE_BYTES = 15L * 1024 * 1024
    private const val PREVIEW_MAX = 120
    private const val MAX_PHOTO_PX = 1600

    private fun db() = FirebaseDatabase.getInstance()
    private fun social() = db().getReference(TriangleConfig.SOCIAL_ROOT)

    private suspend fun orgIdFor(uid: String): String? =
        db().getReference("users/$uid/orgId").get().await().value as? String

    // ── Send / read / edit / recall ───────────────────────────────────────

    /** [skippedBlocked] counts recipients who blocked announcements from this sender — they get nothing, so the sender is told. */
    data class SendResult(val announcementId: String, val sent: Int, val skippedBlocked: Int)

    /**
     * A file the sender picked but hasn't sent yet. Exactly one of [bitmap] (a photo — camera or
     * gallery — downscaled and re-encoded as JPEG at send time) or [uri] (a video/PDF, read from
     * the device only when uploading so several big files aren't held in memory at once) is set.
     */
    data class PendingAttachment(
        val name: String,
        val mimeType: String,
        val sizeBytes: Long,
        val bitmap: Bitmap? = null,
        val uri: Uri? = null
    ) {
        val isImage: Boolean get() = mimeType.startsWith("image/")
        val isVideo: Boolean get() = mimeType.startsWith("video/")
    }

    /** [driveToken] is only needed (and required) when [attachments] is non-empty — files go to Google Drive via DriveImageHelper, like profile photos. */
    suspend fun send(
        sender: SessionStore.Session,
        title: String,
        body: String,
        colorHex: String,
        attachments: List<PendingAttachment>,
        resolver: ContentResolver,
        recipientUids: Set<String>,
        recipientNames: Map<String, String>,
        driveToken: String? = null
    ): SendResult = coroutineScope {
        val id = social().child("announcements").push().key ?: throw IllegalStateException("Could not allocate an announcement id")

        // Recipient-side block + org lookup, all recipients concurrently (same shape as assignTask).
        val resolved = recipientUids.filter { it != sender.uid }.map { uid ->
            async {
                val allowed = ConnectionRepository.canAnnounce(uid, sender.uid)
                uid to (if (allowed) orgIdFor(uid) ?: "" else null)
            }
        }.awaitAll()
        val deliverable = resolved.filter { !it.second.isNullOrEmpty() }.associate { it.first to it.second!! }
        val skipped = resolved.count { it.second == null }
        if (deliverable.isEmpty()) return@coroutineScope SendResult(id, sent = 0, skippedBlocked = skipped)

        val uploaded = if (attachments.isEmpty()) emptyList() else {
            val token = driveToken ?: throw IllegalStateException("Google Drive access is needed to attach files")
            withContext(Dispatchers.IO) {
                // One at a time, so at most one file's bytes are in memory.
                attachments.take(MAX_ATTACHMENTS).mapIndexed { i, a ->
                    val (bytes, mime) = when {
                        a.bitmap != null -> DriveImageHelper.toJpegBytes(downscaled(a.bitmap)) to "image/jpeg"
                        a.uri != null -> (resolver.openInputStream(a.uri)?.use { it.readBytes() } ?: throw IllegalStateException("Couldn't read ${a.name}")) to a.mimeType
                        else -> throw IllegalStateException("Empty attachment")
                    }
                    val url = DriveImageHelper.uploadFile(token, "TriangleAnnouncement_${sender.uid}_${id}_${i}_${a.name}", mime, bytes)
                    AnnouncementAttachment(url, a.name, bytes.size.toLong(), mime)
                }
            }
        }
        val now = System.currentTimeMillis()
        val announcement = Announcement(
            id = id, senderUid = sender.uid, senderName = sender.name, senderPhotoUrl = sender.photoUrl,
            title = title.trim(), body = body.trim(), color = colorHex, attachments = uploaded,
            createdAt = now, recipients = deliverable,
            recipientNames = recipientNames.filterKeys { it in deliverable }
        )
        social().updateChildren(
            mapOf(
                "announcements/$id" to announcement.toMap(),
                "announcementIndex/${sender.uid}/$id" to mapOf("title" to announcement.title, "createdAt" to now, "recipientCount" to deliverable.size)
            )
        ).await()

        val preview = previewOf(announcement.body)
        deliverable.keys.map { uid ->
            async { runCatching { NotificationRepository.notifyAnnouncement(uid, sender.uid, sender.name, announcement.title, preview, id) } }
        }.awaitAll()
        SendResult(id, sent = deliverable.size, skippedBlocked = skipped)
    }

    suspend fun get(id: String): Announcement? {
        val snap = social().child("announcements/$id").get().await()
        return (snap.value as? Map<*, *>)?.let { Announcement.fromMap(id, it) }
    }

    /** Live view of one announcement — null once it's recalled. The Notifications list renders each announcement card from this, so edits/recalls show up immediately. */
    fun announcementFlow(id: String): Flow<Announcement?> =
        social().child("announcements/$id").valueFlow().map { snap ->
            (snap.value as? Map<*, *>)?.let { Announcement.fromMap(id, it) }
        }

    fun sentFlow(senderUid: String): Flow<List<AnnouncementSummary>> =
        social().child("announcementIndex/$senderUid").valueFlow().map { snap ->
            snap.children.mapNotNull { child ->
                val id = child.key ?: return@mapNotNull null
                val m = child.value as? Map<*, *> ?: return@mapNotNull null
                AnnouncementSummary(
                    id = id,
                    title = m["title"] as? String ?: "",
                    createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
                    recipientCount = (m["recipientCount"] as? Number)?.toInt() ?: 0
                )
            }.sortedByDescending { it.createdAt }
        }

    /** Edits title/body/color (attachments are fixed once sent). Refreshes each recipient's row in place — no new push. */
    suspend fun edit(senderUid: String, id: String, title: String, body: String, colorHex: String) = coroutineScope {
        val current = get(id) ?: return@coroutineScope
        if (current.senderUid != senderUid) return@coroutineScope
        val now = System.currentTimeMillis()
        val newTitle = title.trim()
        val newBody = body.trim()
        social().updateChildren(
            mapOf(
                "announcements/$id/title" to newTitle,
                "announcements/$id/body" to newBody,
                "announcements/$id/color" to colorHex,
                "announcements/$id/editedAt" to now,
                "announcementIndex/$senderUid/$id/title" to newTitle
            )
        ).await()
        val preview = previewOf(newBody)
        current.recipients.map { (uid, orgId) ->
            async { runCatching { NotificationRepository.updateByItemId(orgId, uid, "announcement", id, newTitle, "${current.senderName} · $preview") } }
        }.awaitAll()
    }

    /** Recall for everyone: removes the announcement + index, then every recipient's notification row. Per-recipient failures are swallowed. */
    suspend fun recall(senderUid: String, id: String) = coroutineScope {
        val current = get(id)
        if (current != null && current.senderUid != senderUid) return@coroutineScope
        current?.recipients?.map { (uid, orgId) ->
            async { runCatching { NotificationRepository.removeByItemId(orgId, uid, "announcement", id) } }
        }?.awaitAll()
        social().updateChildren(mapOf("announcements/$id" to null, "announcementIndex/$senderUid/$id" to null)).await()
        // Photos live in the sender's Google Drive as unlisted link-viewable files; with no Drive
        // token here they can't be deleted, but nothing references them once the record is gone.
    }

    private fun previewOf(body: String): String {
        val oneLine = body.replace('\n', ' ').trim()
        return if (oneLine.length > PREVIEW_MAX) oneLine.take(PREVIEW_MAX - 1) + "…" else oneLine
    }

    /** Full-resolution camera/gallery bitmaps are big; announcements only need screen-sized images. */
    private fun downscaled(source: Bitmap): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= MAX_PHOTO_PX) return source
        val scale = MAX_PHOTO_PX.toFloat() / longest
        return Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true)
    }
}
