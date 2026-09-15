package com.triangle.app.data.models

/**
 * Native model of a `db.rewards[]` entry (see script.js's `_rwForm`'s save
 * handler, script.js:16799-16812). Hand-written toMap/fromMap, same
 * defensive-parsing idiom as [Task] — some fields are loosely typed on the
 * JS side (`stock` can be absent/null for "unlimited").
 *
 * Fields NOT ported (see the Milestone 4 plan's scope trims): `imageSquare`/
 * `imageBanner`/`imageData` (no image upload this cut — text-only cards with
 * a category emoji/color stand in for a photo) and `assignees` (meaningless
 * per-member targeting for a 1-person org — always written as `["all"]`,
 * never read back).
 */
data class Reward(
    val id: String,
    val name: String,
    val description: String = "",
    val terms: String = "",
    val category: String = "physical", // digital|learning|merch|office|physical|giftcard|perk|experience
    val coinCost: Int,
    val stock: Int? = null, // null = unlimited
    val active: Boolean = true,
    val validityType: String? = null,
    val validityStart: String? = null,
    val validityEnd: String? = null,
    val createdAt: Long = 0L
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id, "name" to name, "description" to description, "terms" to terms,
        "category" to category, "coinCost" to coinCost, "stock" to stock, "active" to active,
        "validityType" to validityType, "validityStart" to validityStart, "validityEnd" to validityEnd,
        "assignees" to listOf("all"), "createdAt" to createdAt
    )

    companion object {
        fun fromMap(m: Map<*, *>): Reward? {
            val id = m["id"]?.toString() ?: return null
            val name = m["name"] as? String ?: return null
            return Reward(
                id = id,
                name = name,
                description = m["description"] as? String ?: "",
                terms = m["terms"] as? String ?: "",
                category = m["category"] as? String ?: "physical",
                coinCost = (m["coinCost"] as? Number)?.toInt() ?: 0,
                stock = (m["stock"] as? Number)?.toInt(),
                active = m["active"] != false,
                validityType = m["validityType"] as? String,
                validityStart = m["validityStart"] as? String,
                validityEnd = m["validityEnd"] as? String,
                createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}
