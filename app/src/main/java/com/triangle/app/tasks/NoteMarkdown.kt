package com.triangle.app.tasks

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Minimal hand-rolled markup for task notes — `**bold**` / `*italic*` only,
 * entered via AddNoteScreen's two toggle buttons (wrap/unwrap the current
 * selection), not a general Markdown parser. A deliberate simplification
 * of the WebView's full contenteditable/execCommand rich-text editor — see
 * the Task Notes plan's "explicitly out of scope" section.
 */
private val MARK_REGEX = Regex("\\*\\*(.+?)\\*\\*|\\*(.+?)\\*")

fun parseNoteMarkdown(raw: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (match in MARK_REGEX.findAll(raw)) {
        if (match.range.first > cursor) append(raw.substring(cursor, match.range.first))
        val bold = match.groups[1]?.value
        if (bold != null) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
        } else {
            val italic = match.groups[2]?.value ?: ""
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
        }
        cursor = match.range.last + 1
    }
    if (cursor < raw.length) append(raw.substring(cursor))
}

/** Wraps (or unwraps, if already wrapped) the current text-field selection in `marker` — no-op with nothing selected. */
fun toggleMarkAtSelection(text: String, selStart: Int, selEnd: Int, marker: String): Pair<String, IntRange>? {
    val lo = minOf(selStart, selEnd)
    val hi = maxOf(selStart, selEnd)
    if (lo == hi) return null
    val selected = text.substring(lo, hi)
    val alreadyWrapped = selected.length >= marker.length * 2 && selected.startsWith(marker) && selected.endsWith(marker)
    val replacement = if (alreadyWrapped) selected.substring(marker.length, selected.length - marker.length) else "$marker$selected$marker"
    val newText = text.substring(0, lo) + replacement + text.substring(hi)
    return newText to (lo until (lo + replacement.length))
}
