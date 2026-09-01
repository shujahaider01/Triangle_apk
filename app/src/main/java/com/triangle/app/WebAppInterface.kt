package com.triangle.app

import android.content.Context
import android.webkit.JavascriptInterface

/**
 * JS -> native bridge, attached to the WebView as `window.Android` (see
 * MainActivity: webView.addJavascriptInterface(..., "Android")).
 *
 * Three responsibilities:
 *  1. getFcmToken() — the web app calls this on successful login to read the
 *     device's current push token and save it to Firebase under that user's
 *     record (Phase 3 JS work). Returns "" if a token hasn't arrived from
 *     Firebase yet (rare, but possible on a very first cold start before
 *     FirebaseMessaging.getInstance().token resolves).
 *  2. notifyAppReady() — the web app calls this once it has finished loading
 *     AND the user is logged in (db + currentUser both ready). This is the
 *     signal MainActivity waits for before dispatching a pending
 *     notification-tap deep link — calling straight into JS before the app
 *     has logged in would hit onNotifTap() with no db/currentUser yet and
 *     silently no-op.
 *  3. clearHistory() — the web app calls this once per login (right
 *     alongside establishing its first real history.replaceState() entry —
 *     see clearAndroidHistory() in script.js), and once on logout. Wipes the
 *     WebView's native back/forward list down to just the current entry, so
 *     an old session's navigation history can never resurface after a
 *     logout/login cycle.
 *
 * IMPORTANT: methods annotated @JavascriptInterface run on a background
 * thread, not the main/UI thread — that's why onReady()/onClearHistory()
 * below are callbacks MainActivity wraps in runOnUiThread{} itself, rather
 * than this class ever touching the WebView directly.
 *
 * ── Google Drive backup/restore (Settings > Integrations) ──────────────
 * Same pattern as the above: this class is just a thin dispatcher, every
 * actual Drive/UI action lives in MainActivity (see its "Google Drive
 * backup/restore" section) and gets wired in here as a lambda so this file
 * never needs to know about AuthorizationClient, WebView, etc. directly.
 *  - onConnectDrive/onDisconnectDrive/onStartBackup/onStartRestore trigger
 *    the native flow; results come back to the web app asynchronously via
 *    window.onGoogleSignInResult / onGoogleDriveDisconnected /
 *    onCloudBackupResult / onCloudRestoreResult (see MainActivity).
 *  - isDriveConnectedProvider/lastBackupTimeProvider are synchronous reads
 *    (plain SharedPreferences via DriveAuthStore) so the Settings screen
 *    can paint its initial state instantly on page load, no round trip.
 */
class WebAppInterface(
    private val context: Context,
    private val onReady: () -> Unit,
    private val onClearHistory: () -> Unit,
    private val onConnectDrive: () -> Unit,
    private val onDisconnectDrive: () -> Unit,
    private val onStartBackup: (String) -> Unit,
    private val onStartRestore: () -> Unit,
    private val isDriveConnectedProvider: () -> Boolean,
    private val lastBackupTimeProvider: () -> Long,
    private val onUploadImage: (String, String, String, String) -> Unit,
    private val onExportData: (String, String) -> Unit,
    private val onSignInGoogleAuth: () -> Unit
) {
    @JavascriptInterface
    fun getFcmToken(): String = TokenStore.get(context)

    @JavascriptInterface
    fun notifyAppReady() {
        onReady()
    }

    @JavascriptInterface
    fun clearHistory() {
        onClearHistory()
    }

    @JavascriptInterface
    fun connectGoogleDrive() {
        onConnectDrive()
    }

    @JavascriptInterface
    fun disconnectGoogleDrive() {
        onDisconnectDrive()
    }

    @JavascriptInterface
    fun startCloudBackup(jsonData: String) {
        onStartBackup(jsonData)
    }

    @JavascriptInterface
    fun startCloudRestore() {
        onStartRestore()
    }

    @JavascriptInterface
    fun isGoogleDriveConnected(): Boolean = isDriveConnectedProvider()

    @JavascriptInterface
    fun getLastBackupTime(): Long = lastBackupTimeProvider()

    // ── Image upload proxy (feed/reward/rich-text/task-photo images) ──────
    // Routed through native networking instead of the WebView's own
    // fetch()/JS layer: calling the Apps Script Web App directly from
    // page JS running on a file:// origin hit a browser-CORS "Failed to
    // fetch" wall (a plain address-bar visit to the same URL works fine
    // precisely because page navigation isn't subject to CORS at all,
    // unlike a fetch() call from within a loaded page — that's what made
    // this look like it should work but didn't). HttpURLConnection here
    // has no concept of CORS, so it sidesteps the problem entirely. See
    // MainActivity.uploadImageToDrive() for the actual HTTP call and
    // ImageUploadHelper.kt for the request/response handling.
    @JavascriptInterface
    fun uploadImageToDrive(reqId: String, base64Image: String, filename: String, folder: String) {
        onUploadImage(reqId, base64Image, filename, folder)
    }

    // ── Export Data (Settings > Backup & Restore) ──────────────────────────
    // Writes the current db, as JSON, to a real file in the device's public
    // Downloads folder — see MainActivity.exportDataToFile(). Result comes
    // back to the web app via window.onExportDataResult(success, message).
    @JavascriptInterface
    fun exportDataToFile(jsonData: String, filename: String) {
        onExportData(jsonData, filename)
    }

    // ── "Continue with Google" login identity (Auth preview flow) ─────────
    // Separate from connectGoogleDrive() above — this is app LOGIN (a
    // Google ID token for Firebase Auth), not a Drive access grant. See
    // MainActivity.signInWithGoogleForAuth(). Result comes back to the web
    // app via window.onGoogleAuthResult(success, idToken, name, email, message).
    @JavascriptInterface
    fun signInWithGoogleForAuth() {
        onSignInGoogleAuth()
    }
}
