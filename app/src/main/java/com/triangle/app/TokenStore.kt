package com.triangle.app

import android.content.Context

/**
 * Tiny SharedPreferences wrapper for caching the device's current FCM token
 * locally. Written to by TxpMessagingService.onNewToken() (called whenever
 * the token is first generated or later rotated) and read by AppNavHost
 * once a session is known, so it can register the token via
 * UserRepository.registerDeviceToken() without waiting on a fresh network
 * round-trip.
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
