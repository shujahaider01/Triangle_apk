package com.triangle.app

import android.content.Context

/**
 * Tiny SharedPreferences wrapper for caching the device's current FCM token
 * locally. Written to by TxpMessagingService.onNewToken() (called whenever
 * the token is first generated or later rotated) and read by
 * WebAppInterface.getFcmToken() when the web app asks for it on login.
 *
 * Kept as a plain local cache (not synced to Firebase from here) because the
 * *web app* is what knows which logged-in user this device belongs to —
 * native code has no concept of "which intern/admin is logged in," so it
 * just hands the token up to JS and Phase 3's JS is what saves
 * token -> /deviceTokens/{userId} in the Realtime Database.
 */
object TokenStore {
    private const val PREFS_NAME = "txp_prefs"
    private const val KEY_FCM_TOKEN = "fcm_token"

    fun save(context: Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FCM_TOKEN, token)
            .apply()
    }

    fun get(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FCM_TOKEN, "") ?: ""
    }
}
