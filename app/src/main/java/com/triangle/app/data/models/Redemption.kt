package com.triangle.app.data.models

/**
 * Native model of a `db.redemptions[]` entry (see script.js's `rwRedeemNow()`,
 * script.js:16997-17033). One deliberate semantic change from source: native
 * redemptions are written as `"approved"` immediately rather than `"pending"`
 * — script.js always creates a `pending` redemption and then makes the same
 * individual self-approve their own request from a reused admin screen (its
 * own comment: "Individual needs to self-approve their own pending
 * redemptions... Manage and Settings are the same admin-only screens reused
 * as-is"), which is a UI artifact of reusing multi-user code, not a real
 * workflow step when there is no one else to approve it. This was an
 * explicit, user-confirmed decision for the Milestone 4 native port, not an
 * oversight — see the plan. "delivered" remains a real, useful manual state
 * transition afterward.
 */
data class Redemption(
    val id: String, // "rdm-"+timestamp, matches source
    val rewardId: String,
    val internId: String,
    val coinsCost: Int,
    val status: String = "approved", // "approved" | "rejected" | "delivered" — never "pending" natively, see class doc
    val createdAt: Long,
    val updatedAt: Long,
    val adminNote: String = ""
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id, "rewardId" to rewardId, "internId" to internId, "coinsCost" to coinsCost,
        "status" to status, "createdAt" to createdAt, "updatedAt" to updatedAt, "adminNote" to adminNote
    )

    companion object {
        fun fromMap(m: Map<*, *>): Redemption? {
            val id = m["id"]?.toString() ?: return null
            val rewardId = m["rewardId"]?.toString() ?: return null
            val internId = m["internId"]?.toString() ?: return null
            return Redemption(
                id = id,
                rewardId = rewardId,
                internId = internId,
                coinsCost = (m["coinsCost"] as? Number)?.toInt() ?: 0,
                status = m["status"] as? String ?: "approved",
                createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
                updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: 0L,
                adminNote = m["adminNote"] as? String ?: ""
            )
        }
    }
}
