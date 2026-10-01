package com.triangle.app.data.models

/**
 * One attached file — a photo, video or PDF: a link-viewable Drive URL plus the display
 * name/size/type shown on the announcement card's attachment chip. [mimeType] defaults to
 * JPEG for announcements sent before videos/PDFs were supported (those were photos only).
 */
data class AnnouncementAttachment(val url: String, val name: String, val sizeBytes: Long, val mimeType: String = "image/jpeg") {
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isPdf: Boolean get() = mimeType == "application/pdf"

    /** Drive file id, parsed back out of [url] — used to build the open/download links. */
    private val fileId: String? get() = Regex("[?&]id=([^&]+)").find(url)?.groupValues?.get(1)

    /** Where tapping a video/PDF sends the user (Drive's viewer/player). Images open in the in-app lightbox from [url] instead. */
    val viewUrl: String get() = fileId?.let { "https://drive.google.com/file/d/$it/view" } ?: url

    /** Direct-download link for "Save attachments". */
    val downloadUrl: String get() = fileId?.let { "https://drive.google.com/uc?export=download&id=$it" } ?: url

    fun toMap(): Map<String, Any?> = mapOf("url" to url, "name" to name, "sizeBytes" to sizeBytes, "mimeType" to mimeType)

    companion object {
        fun fromMap(m: Map<*, *>): AnnouncementAttachment? {
            val url = m["url"] as? String ?: return null
            return AnnouncementAttachment(
                url, m["name"] as? String ?: "Photo.jpg",
                (m["sizeBytes"] as? Number)?.toLong() ?: 0L,
                m["mimeType"] as? String ?: "image/jpeg"
            )
        }
    }
}

/**
 * The canonical announcement at `social/announcements/{id}` — written once and read live by
 * every recipient's Notifications list, which renders it inline as a card. Recipients'
 * notification rows only carry a short preview plus this id, so a big body/attachment set
 * never gets rewritten into every recipient's notification array on each markRead, and an
 * edit/recall touches one node. [recipients] maps uid -> that recipient's orgId (kept so
 * edit/recall can reach each notification array without a lookup). [color] is the hex of the
 * card's title bar, chosen by the sender.
 */
data class Announcement(
    val id: String,
    val senderUid: String,
    val senderName: String,
    val senderPhotoUrl: String? = null,
    val title: String,
    val body: String,
    val color: String = DEFAULT_COLOR,
    val attachments: List<AnnouncementAttachment> = emptyList(),
    val createdAt: Long,
    val editedAt: Long? = null,
    val recipients: Map<String, String> = emptyMap(),
    /** uid -> display name for the "To …" line; absent on announcements sent before this field existed. */
    val recipientNames: Map<String, String> = emptyMap()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "senderUid" to senderUid, "senderName" to senderName, "senderPhotoUrl" to senderPhotoUrl,
        "title" to title, "body" to body, "color" to color,
        "attachments" to attachments.map { it.toMap() },
        "createdAt" to createdAt, "editedAt" to editedAt, "recipients" to recipients, "recipientNames" to recipientNames
    )

    companion object {
        const val DEFAULT_COLOR = "#B8962E"
        val PALETTE = listOf("#B8962E", "#DC2626", "#2563EB", "#16A34A", "#7C3AED", "#E85D26", "#0D9488", "#DB2777")

        fun fromMap(id: String, m: Map<*, *>): Announcement? {
            val senderUid = m["senderUid"]?.toString() ?: return null
            return Announcement(
                id = id,
                senderUid = senderUid,
                senderName = m["senderName"] as? String ?: "",
                senderPhotoUrl = m["senderPhotoUrl"] as? String,
                title = m["title"] as? String ?: "",
                body = m["body"] as? String ?: "",
                color = m["color"] as? String ?: DEFAULT_COLOR,
                attachments = com.triangle.app.data.anyToMapList(m["attachments"]).mapNotNull { AnnouncementAttachment.fromMap(it) },
                createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
                editedAt = (m["editedAt"] as? Number)?.toLong(),
                recipients = stringMap(m["recipients"]),
                recipientNames = stringMap(m["recipientNames"])
            )
        }
    }
}

private fun stringMap(raw: Any?): Map<String, String> =
    (raw as? Map<*, *>)?.entries
        ?.mapNotNull { (k, v) -> (k?.toString() ?: return@mapNotNull null) to (v?.toString() ?: return@mapNotNull null) }
        ?.toMap() ?: emptyMap()

/** One row of the sender's Sent index — `social/announcementIndex/{senderUid}/{id}`; the list itself renders the full [Announcement]. */
data class AnnouncementSummary(val id: String, val title: String, val createdAt: Long, val recipientCount: Int)
