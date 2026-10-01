package com.triangle.app.data

import kotlinx.coroutines.flow.MutableStateFlow

enum class HighlightKind { TASK, HABIT, MESSAGE, ANNOUNCEMENT }

/** [nonce] makes two taps on the same item distinct requests, so the second one re-triggers the reveal. */
data class HighlightRequest(val kind: HighlightKind, val itemId: String, val title: String? = null, val nonce: Long = System.nanoTime())

/**
 * Hands "scroll to and highlight this item" from a notification tap (resolved in
 * AppNavHost) to whichever screen shows the item — deliberately not a nav
 * argument, since the Tasks list is the start destination of a saved/restored
 * nav graph and can already be on screen when the tap arrives.
 */
object HighlightBus {
    val request = MutableStateFlow<HighlightRequest?>(null)

    /** [itemId] may be blank for old notifications that predate item ids — the screen then finds the item by [title]. */
    fun post(kind: HighlightKind, itemId: String, title: String? = null) {
        request.value = HighlightRequest(kind, itemId, title)
    }

    /** The task/habit name out of an "assigned you a task: NAME" style notification body. */
    fun titleFromAssignedBody(body: String): String? = body.substringAfter(": ", "").trim().ifBlank { null }

    fun clear(handled: HighlightRequest) {
        if (request.value == handled) request.value = null
    }
}
