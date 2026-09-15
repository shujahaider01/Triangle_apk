package com.triangle.app

import android.content.Context

/**
 * Local cache of Google Drive backup/restore connection state — separate
 * from TokenStore (which is FCM-only). Stores:
 *  - whether the admin has granted Drive access on this device
 *  - the most recent short-lived Drive access token (valid ~1 hour; it's
 *    re-obtained transparently via AuthorizationClient before every backup/
 *    restore — this cached copy is only used so disconnectGoogleDrive() has
 *    something to pass to revokeAccess())
 *  - the last successful backup timestamp, shown in Settings > Integrations
 */
object DriveAuthStore {
    private const val PREFS_NAME = "txp_drive_prefs"
    private const val KEY_CONNECTED = "drive_connected"
    private const val KEY_ACCESS_TOKEN = "drive_access_token"
    private const val KEY_LAST_BACKUP_TS = "drive_last_backup_ts"

    fun isConnected(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_CONNECTED, false)

    fun setConnected(context: Context, connected: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_CONNECTED, connected).apply()
    }

    fun saveAccessToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    fun getAccessToken(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACCESS_TOKEN, null)

    fun setLastBackupTime(context: Context, ts: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_BACKUP_TS, ts).apply()
    }

    fun getLastBackupTime(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_BACKUP_TS, 0L)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
