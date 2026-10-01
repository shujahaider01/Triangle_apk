package com.triangle.app.util

/** Upper-cases the first letter of the text, whatever the keyboard does — used to force a capital start on free-text fields. */
fun String.capFirst(): String {
    val i = indexOfFirst { !it.isWhitespace() }
    if (i < 0 || !this[i].isLowerCase()) return this
    return substring(0, i) + this[i].uppercaseChar() + substring(i + 1)
}
