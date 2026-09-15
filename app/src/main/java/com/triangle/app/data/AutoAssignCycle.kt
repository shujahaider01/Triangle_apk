package com.triangle.app.data

import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Faithful port of script.js's _nextAutoColorAndIcon() — used so a newly
 * created task or habit gets a different icon/color than the previous one
 * instead of always defaulting to the same one (HabitPalette.DEFAULT_COLOR/
 * DEFAULT_ICON_KEY). The current position in the rotation is persisted at
 * `.../data/autoAssignCycle` (same path/shape the source itself writes),
 * so the rotation keeps advancing across sessions and devices rather than
 * resetting every time the app is reopened.
 */
object AutoAssignCycle {
    data class Pick(val color: String, val iconKey: String, val iconSvg: String)

    private fun ref(orgId: String) =
        FirebaseDatabase.getInstance(TriangleConfig.FIREBASE_URL).getReference("organizations/$orgId/data/autoAssignCycle")

    /** Returns the next color+icon in the rotation and advances (and best-effort persists) it. */
    suspend fun next(orgId: String): Pick {
        val iconKeys = HabitPalette.ICONS.keys.toList()
        val colorCycle = HabitPalette.AUTO_COLOR_CYCLE

        val snapshot = runCatching { ref(orgId).get().await() }.getOrNull()
        val storedColorIdx = (snapshot?.child("colorIdx")?.value as? Number)?.toInt() ?: 0
        val storedIconIdx = (snapshot?.child("iconIdx")?.value as? Number)?.toInt() ?: 0

        val colorIdx = storedColorIdx.mod(colorCycle.size)
        val iconIdx = storedIconIdx.mod(iconKeys.size)
        val pick = Pick(colorCycle[colorIdx], iconKeys[iconIdx], HabitPalette.ICONS.getValue(iconKeys[iconIdx]))

        val next = mapOf(
            "colorIdx" to (colorIdx + 1) % colorCycle.size,
            "iconIdx" to (iconIdx + 1) % iconKeys.size
        )
        // Best-effort background persist — matches the source's fire-and-forget
        // fbPut('autoAssignCycle', ...): the picker showing the right icon/color
        // right now doesn't depend on this write succeeding.
        runCatching { ref(orgId).setValue(next).await() }

        return pick
    }
}
