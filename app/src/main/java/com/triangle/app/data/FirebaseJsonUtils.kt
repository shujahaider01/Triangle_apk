package com.triangle.app.data

/**
 * Firebase's Realtime Database stores a JSON array with only sequential
 * numeric keys as a real array (returned as a `List`) — but if any keys are
 * missing/non-sequential (deleted middle elements, e.g.), it comes back as
 * a `Map` instead. Every place in this app that reads an array-shaped field
 * needs to tolerate both shapes, same as script.js's own defensive handling
 * of this (see patchDB()'s array-repair logic).
 */
@Suppress("UNCHECKED_CAST")
fun anyToMapList(raw: Any?): List<Map<String, Any?>> = when (raw) {
    is List<*> -> raw.mapNotNull { it as? Map<String, Any?> }
    is Map<*, *> -> raw.values.mapNotNull { it as? Map<String, Any?> }
    else -> emptyList()
}
