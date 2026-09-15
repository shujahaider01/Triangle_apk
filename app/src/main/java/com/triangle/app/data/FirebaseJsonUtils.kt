package com.triangle.app.data

import org.json.JSONArray
import org.json.JSONObject

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

/**
 * Whole-subtree conversions for Settings > Backup & Restore — the only
 * place this app reads/writes an entire `organizations/{orgId}/data` node
 * as one opaque JSON blob (a deliberate exception to every other
 * repository's narrow-sub-path-write rule, since "restore my account" is
 * inherently a full-state operation). A DataSnapshot's `.value` comes back
 * as nested Map/List/primitive Kotlin objects, same shape `anyToMapList`
 * above already deals with — these just recurse through the whole tree
 * instead of one array.
 */
fun Any?.toJsonElement(): Any = when (this) {
    null -> JSONObject.NULL
    is Map<*, *> -> JSONObject().apply { entries.forEach { (k, v) -> put(k.toString(), v.toJsonElement()) } }
    is List<*> -> JSONArray().apply { forEach { put(it.toJsonElement()) } }
    else -> this
}

fun Any?.fromJsonElement(): Any? = when (this) {
    null, JSONObject.NULL -> null
    is JSONObject -> keys().asSequence().associateWith { k -> get(k).fromJsonElement() }
    is JSONArray -> (0 until length()).map { i -> get(i).fromJsonElement() }
    else -> this
}
