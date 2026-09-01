package com.triangle.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    lateinit var webView: WebView

    // Holds the pending file chooser callback from WebView
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private val FILE_CHOOSER_REQUEST = 1001

    // ── Push notifications: pending deep-link state ──────────────────────
    // Set from whichever Intent (cold-start onCreate, or warm-start
    // onNewIntent) carried notification-tap extras. Only actually dispatched
    // into JS once the web app confirms (via Android.notifyAppReady()) that
    // it has finished loading and logging in — see onWebAppReady() below.
    private var pendingNotifId: String? = null
    private var pendingIsAdmin: Boolean = false
    private var webAppReady = false

    // ── Back button/gesture: double-tap-to-exit state ──────────────────────
    // Only used once handleAndroidBack() in script.js reports there's
    // nothing left in-app to close/navigate (i.e. already on the home
    // dashboard) — see registerBackHandling() below.
    private var backPressedOnceAt = 0L

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    // ── Google Drive backup/restore (Settings > Integrations) ──────────────
    // Uses the modern Identity Services AuthorizationClient (NOT the
    // deprecated GoogleSignInClient.requestScopes path) to get an OAuth
    // access token scoped ONLY to drive.file — meaning this app can see/
    // manage exclusively the backup files it creates itself, never the rest
    // of the signed-in admin's Drive. See DriveBackupHelper.kt for the
    // actual upload/download REST calls and DriveAuthStore.kt for the local
    // "are we connected" / "when was the last backup" state.
    //
    // Flow: connectGoogleDrive()/startCloudBackup()/startCloudRestore() are
    // all entry points called (via runOnUiThread) from WebAppInterface.
    // Each sets `pendingDriveAction` so the shared authorization callback
    // (onAuthorizationGranted) knows what to actually do once a valid
    // access token is in hand — whether that took a full account-picker +
    // consent round trip (first time) or resolved silently (every time
    // after, since the grant is remembered by Google).
    private val DRIVE_SCOPE = Scope("https://www.googleapis.com/auth/drive.file")
    private var pendingDriveAction: String = "connect" // "connect" | "backup" | "restore" | "uploadImage"
    private var pendingBackupJson: String? = null

    // Queued image-upload request awaiting a valid access token — see
    // uploadImageToDrive() below. Only one at a time is supported (matches
    // the existing single-pending-action model this whole authorization
    // flow already used for backup/restore); call sites in script.js always
    // await one upload before starting the next, so this is never actually
    // contended in practice.
    private data class PendingImageUpload(val reqId: String, val base64Image: String, val filename: String, val folder: String)
    private var pendingImageUpload: PendingImageUpload? = null

    private val authorizationIntentLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            try {
                val authResult = Identity.getAuthorizationClient(this)
                    .getAuthorizationResultFromIntent(result.data)
                onAuthorizationGranted(authResult)
            } catch (e: ApiException) {
                notifyDriveActionFailure(e.message ?: "Google sign-in was cancelled")
            }
        }

    // ── "Continue with Google" — app LOGIN identity (Auth preview flow) ────
    // Deliberately separate from the Drive backup/restore flow above: that
    // one uses the modern Identity Services AuthorizationClient to get an
    // OAuth ACCESS token scoped only to drive.file (no user identity
    // involved at all). Logging a user INTO the app needs a Google ID
    // TOKEN instead — proof of "this is really this Google account" that
    // gets handed to Firebase Auth's accounts:signInWithIdp REST endpoint
    // (see script.js onGoogleAuthResult()). The legacy GoogleSignInClient
    // API is the one that issues ID tokens without pulling in any new
    // Gradle dependency (it ships in the same play-services-auth artifact
    // already used above for Identity.getAuthorizationClient) — a
    // Credential-Manager-based rewrite can replace this later without
    // touching anything else in this flow.
    //
    // WEB_CLIENT_ID must be the OAuth "Web client" ID from Firebase
    // Console > Authentication > Sign-in method > Google (NOT an Android
    // client ID) — that's what makes the returned ID token's audience
    // match what Firebase Auth expects when verifying it server-side.
    private val GOOGLE_AUTH_WEB_CLIENT_ID = BuildConfig.GOOGLE_WEB_CLIENT_ID
    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(GOOGLE_AUTH_WEB_CLIENT_ID)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(this, gso)
    }
    private val googleAuthSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken.isNullOrEmpty()) {
                    notifyGoogleAuthFailure("Google did not return an ID token")
                } else {
                    webView.evaluateJavascript(
                        "javascript:(function(){ if (window.onGoogleAuthResult) onGoogleAuthResult(true, ${JSONObject.quote(idToken)}, ${JSONObject.quote(account.displayName ?: "")}, ${JSONObject.quote(account.email ?: "")}, ''); })();",
                        null
                    )
                }
            } catch (e: ApiException) {
                notifyGoogleAuthFailure(e.message ?: "Google sign-in was cancelled")
            }
        }

    // Always show Android's account-chooser dialog, whether this tap came
    // from the Sign Up screen or the Login screen — GoogleSignInClient
    // otherwise silently reuses whichever account last completed sign-in
    // (no picker shown at all). Signing out first clears that cached
    // account so signInIntent has nothing to auto-select and always
    // prompts the chooser (see script.js onGoogleAuthClick()).
    fun signInWithGoogleForAuth() {
        googleSignInClient.signOut().addOnCompleteListener {
            googleAuthSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    private fun notifyGoogleAuthFailure(message: String) {
        webView.evaluateJavascript(
            "javascript:(function(){ if (window.onGoogleAuthResult) onGoogleAuthResult(false, '', '', '', ${JSONObject.quote(message)}); })();",
            null
        )
    }

    fun connectGoogleDrive() {
        pendingDriveAction = "connect"
        requestDriveAuthorization()
    }

    // NOTE: this clears the app's own local "connected" state only — it does
    // NOT revoke Google's server-side OAuth grant. RevokeAccessRequest (the
    // API for that) needs an android.accounts.Account, which the
    // AuthorizationClient flow never hands back to us (unlike the older,
    // deprecated GoogleSignInClient, which returned a GoogleSignInAccount).
    // Practically: after "Sign Out" here, this device stops using Drive, but
    // if the admin wants to fully revoke TraineeXP's Drive access from
    // Google's side too, that's done at myaccount.google.com/permissions.
    fun disconnectGoogleDrive() {
        DriveAuthStore.clear(applicationContext)
        runOnUiThread {
            webView.evaluateJavascript(
                "javascript:(function(){ if (window.onGoogleDriveDisconnected) onGoogleDriveDisconnected(); })();",
                null
            )
        }
    }

    fun startCloudBackup(jsonData: String) {
        pendingDriveAction = "backup"
        pendingBackupJson = jsonData
        requestDriveAuthorization()
    }

    fun startCloudRestore() {
        pendingDriveAction = "restore"
        requestDriveAuthorization()
    }

    private fun requestDriveAuthorization() {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(DRIVE_SCOPE))
            .build()

        Identity.getAuthorizationClient(this)
            .authorize(request)
            .addOnSuccessListener { authResult ->
                if (authResult.hasResolution()) {
                    // First time (or a previously revoked grant) — show the
                    // account picker + consent screen.
                    val pendingIntent = authResult.pendingIntent
                    try {
                        authorizationIntentLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent!!.intentSender).build()
                        )
                    } catch (e: Exception) {
                        notifyDriveActionFailure("Couldn't open Google sign-in: ${e.message}")
                    }
                } else {
                    // Already granted on a previous run — no UI shown.
                    onAuthorizationGranted(authResult)
                }
            }
            .addOnFailureListener { e ->
                notifyDriveActionFailure(e.message ?: "Google authorization failed")
            }
    }

    private fun onAuthorizationGranted(authResult: AuthorizationResult) {
        val token = authResult.accessToken
        if (token.isNullOrEmpty()) {
            notifyDriveActionFailure("No access token returned by Google")
            return
        }
        DriveAuthStore.saveAccessToken(applicationContext, token)
        DriveAuthStore.setConnected(applicationContext, true)

        when (pendingDriveAction) {
            "connect" -> webView.evaluateJavascript(
                "javascript:(function(){ if (window.onGoogleSignInResult) onGoogleSignInResult(true, ''); })();",
                null
            )
            "backup" -> {
                val json = pendingBackupJson ?: ""
                pendingBackupJson = null
                runDriveBackup(token, json)
            }
            "restore" -> runDriveRestore(token)
            "uploadImage" -> {
                val req = pendingImageUpload
                pendingImageUpload = null
                if (req == null) {
                    notifyDriveActionFailure("Internal error: missing image upload request")
                } else {
                    runImageUpload(token, req)
                }
            }
        }
    }

    private fun runDriveBackup(token: String, jsonData: String) {
        Thread {
            try {
                DriveBackupHelper.uploadBackup(token, jsonData)
                val ts = System.currentTimeMillis()
                DriveAuthStore.setLastBackupTime(applicationContext, ts)
                runOnUiThread {
                    webView.evaluateJavascript(
                        "javascript:(function(){ if (window.onCloudBackupResult) onCloudBackupResult(true, $ts, ''); })();",
                        null
                    )
                }
            } catch (e: Exception) {
                runOnUiThread { notifyDriveActionFailure(e.message ?: "Backup failed") }
            }
        }.start()
    }

    private fun runDriveRestore(token: String) {
        Thread {
            try {
                val json = DriveBackupHelper.downloadLatestBackup(token)
                if (json == null) {
                    runOnUiThread { notifyDriveActionFailure("No backup was found in Google Drive yet") }
                    return@Thread
                }
                runOnUiThread {
                    webView.evaluateJavascript(
                        "javascript:(function(){ if (window.onCloudRestoreResult) onCloudRestoreResult(true, ${JSONObject.quote(json)}, ''); })();",
                        null
                    )
                }
            } catch (e: Exception) {
                runOnUiThread { notifyDriveActionFailure(e.message ?: "Restore failed") }
            }
        }.start()
    }

    // Routes a failure back to whichever JS callback matches the action
    // that was in flight (connect/backup/restore/uploadImage) when it
    // happened. Must be called on the UI thread (it touches the WebView).
    private fun notifyDriveActionFailure(message: String) {
        val safeMsg = JSONObject.quote(message)
        val js = when (pendingDriveAction) {
            "connect" -> "javascript:(function(){ if (window.onGoogleSignInResult) onGoogleSignInResult(false, $safeMsg); })();"
            "backup" -> "javascript:(function(){ if (window.onCloudBackupResult) onCloudBackupResult(false, 0, $safeMsg); })();"
            "uploadImage" -> {
                val reqId = pendingImageUpload?.reqId ?: ""
                pendingImageUpload = null
                "javascript:(function(){ if (window.onImageUploadResult) onImageUploadResult(${JSONObject.quote(reqId)}, false, $safeMsg); })();"
            }
            else -> "javascript:(function(){ if (window.onCloudRestoreResult) onCloudRestoreResult(false, null, $safeMsg); })();"
        }
        webView.evaluateJavascript(js, null)
    }

    // ── Image upload (feed/reward/rich-text/task-photo images) ─────────────
    // Called from WebAppInterface.uploadImageToDrive(). Reuses the EXACT
    // same Drive connection Backup & Restore already established (the
    // drive.file access token in DriveAuthStore) via DriveImageHelper,
    // instead of a separate fixed-account Apps Script proxy — so uploaded
    // images always land in whichever Google account is actually connected
    // in Settings > Backup & Restore, not a hardcoded third-party account.
    // If Drive isn't connected at all yet, this fails fast WITHOUT popping
    // the Google account-picker UI (that would be a jarring surprise in the
    // middle of an ordinary reward/post save) — script.js's existing
    // fallback just keeps the image as base64 locally until Drive is
    // connected.
    private fun uploadImageToDrive(reqId: String, base64Image: String, filename: String, folder: String) {
        if (!DriveAuthStore.isConnected(applicationContext)) {
            webView.evaluateJavascript(
                "javascript:(function(){ if (window.onImageUploadResult) onImageUploadResult(${JSONObject.quote(reqId)}, false, 'Google Drive is not connected (Settings > Backup & Restore)'); })();",
                null
            )
            return
        }
        pendingDriveAction = "uploadImage"
        pendingImageUpload = PendingImageUpload(reqId, base64Image, filename, folder)
        requestDriveAuthorization()
    }

    private fun runImageUpload(token: String, req: PendingImageUpload) {
        Thread {
            try {
                val url = DriveImageHelper.uploadImage(token, req.base64Image, req.filename, req.folder)
                runOnUiThread {
                    webView.evaluateJavascript(
                        "javascript:(function(){ if (window.onImageUploadResult) onImageUploadResult(${JSONObject.quote(req.reqId)}, true, ${JSONObject.quote(url)}); })();",
                        null
                    )
                }
            } catch (e: Exception) {
                runOnUiThread {
                    webView.evaluateJavascript(
                        "javascript:(function(){ if (window.onImageUploadResult) onImageUploadResult(${JSONObject.quote(req.reqId)}, false, ${JSONObject.quote(e.message ?: "Image upload failed")}); })();",
                        null
                    )
                }
            }
        }.start()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lets you open chrome://inspect on a PC (phone connected via USB
        // debugging) and see this WebView's real console — the only way to
        // catch a silent JS error that's blocking part of a page from
        // rendering. Debug builds only; never enabled in a release APK.
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Make the status bar fully transparent so the WebView's own page
        // background (whatever color that screen's header draws at y=0)
        // shows straight through it, instead of a fixed native color that
        // would need to be kept in sync with every screen's own theme.
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        // Status bar icon color (clock/battery/wifi) — dark icons read best
        // against TraineeXP's generally light headers. Flip to `false` if a
        // given screen's header is dark and the icons disappear into it.
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = true
        // Same idea for the 3-button/gesture nav bar icons, since the app's
        // bottom nav bar is white.
        insetsController.isAppearanceLightNavigationBars = true

        // Use XML layout
        setContentView(R.layout.activity_main)

        // Get views from XML
        val mainView = findViewById<View>(R.id.main)
        webView = findViewById(R.id.webView)

        // SAFE AREA
        // Pad left/right only. Neither top NOR bottom get padded here — the
        // WebView must be allowed to draw all the way to both edges so each
        // page's own background color (header at the top, white bottom nav
        // at the bottom) extends behind the transparent system bars instead
        // of exposing the Activity's own window background. The web side
        // (style.css) keeps *content* clear of both system bars via
        // `env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`.
        ViewCompat.setOnApplyWindowInsetsListener(mainView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                systemBars.left,
                0,
                systemBars.right,
                0
            )
            insets
        }

        // WebView setup
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.allowFileAccess = true

        // Disable the WebView's own overscroll/edge-glow effect. Without
        // this, a horizontal edge swipe can get consumed by the WebView
        // itself (as an overscroll bounce) before Android's gesture-nav
        // "swipe back" recognizer ever sees it — one of the two things
        // (along with the Predictive Back migration below) needed for the
        // system back gesture to actually reach this app's back handling.
        webView.overScrollMode = View.OVER_SCROLL_NEVER

        webView.webViewClient = WebViewClient()

        // ── WebChromeClient: enables <input type="file"> gallery picker ──
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                // Cancel any previous callback to avoid leaking it
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback

                // Respect whatever accept="..." the <input type="file"> in
                // script.js actually asked for (e.g. Import Data's
                // accept="application/json,.json") instead of always forcing
                // an image-only picker — that hardcoding used to silently
                // break any non-image file input.
                val acceptTypes = fileChooserParams.acceptTypes
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()
                val mimeType = when {
                    acceptTypes.isEmpty() -> "*/*"
                    acceptTypes.size == 1 && acceptTypes[0].contains("/") -> acceptTypes[0]
                    else -> "*/*"
                }

                // Open the gallery / file picker
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = mimeType
                    if (acceptTypes.size > 1) {
                        putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes.filter { it.contains("/") }.toTypedArray())
                    }
                }
                startActivityForResult(
                    Intent.createChooser(intent, "Choose File"),
                    FILE_CHOOSER_REQUEST
                )
                return true
            }

            // Explicit confirm()/alert() handling instead of relying on
            // WebChromeClient's default implementation, whose behavior for
            // JS dialogs is inconsistent across Android/WebView versions —
            // on some it silently auto-cancels (confirm() resolves false
            // instantly, no dialog ever shown) instead of prompting the
            // user. script.js uses confirm() in several places (Drive
            // sign-out, Import Data, etc.), so an unreliable default here
            // reads exactly like "nothing happened" with no error at all.
            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: android.webkit.JsResult?
            ): Boolean {
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .setCancelable(true)
                    .show()
                return true
            }

            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: android.webkit.JsResult?
            ): Boolean {
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.confirm() }
                    .setCancelable(true)
                    .show()
                return true
            }
        }

        // ── Push notifications + history: JS <-> native bridge ────────────
        // Exposed to the web app as window.Android.getFcmToken() /
        // window.Android.notifyAppReady() / window.Android.clearHistory().
        // All @JavascriptInterface methods run on a background thread, so
        // hop back to the main thread before touching the WebView itself.
        webView.addJavascriptInterface(
            WebAppInterface(
                applicationContext,
                onReady = { runOnUiThread { onWebAppReady() } },
                onClearHistory = { runOnUiThread { webView.clearHistory() } },
                onConnectDrive = { runOnUiThread { connectGoogleDrive() } },
                onDisconnectDrive = { runOnUiThread { disconnectGoogleDrive() } },
                onStartBackup = { json -> runOnUiThread { startCloudBackup(json) } },
                onStartRestore = { runOnUiThread { startCloudRestore() } },
                isDriveConnectedProvider = { DriveAuthStore.isConnected(applicationContext) },
                lastBackupTimeProvider = { DriveAuthStore.getLastBackupTime(applicationContext) },
                onUploadImage = { reqId, base64Image, filename, folder -> uploadImageToDrive(reqId, base64Image, filename, folder) },
                onExportData = { json, filename -> runOnUiThread { exportDataToFile(json, filename) } },
                onSignInGoogleAuth = { runOnUiThread { signInWithGoogleForAuth() } }
            ),
            "Android"
        )

        webView.loadUrl("file:///android_asset/index.html")

        ensureNotificationPermission()
        fetchAndCacheFcmToken()
        captureDeepLinkExtras(intent)
        registerBackHandling()
    }

    // ── Back handling: button AND gesture ──────────────────────────────────
    // Registered via OnBackPressedDispatcher (androidx.activity) instead of
    // overriding the deprecated onBackPressed() — this is the API Android's
    // Predictive Back system actually routes both the hardware/software
    // back button AND the edge-swipe gesture through. The old
    // onBackPressed() override only ever reliably caught the button/key
    // event; whether the edge-swipe gesture also reached it was
    // inconsistent, since that's not the path Predictive Back uses.
    // Requires android:enableOnBackInvokedCallback="true" in
    // AndroidManifest.xml for the OS to actually dispatch gesture-nav
    // through here consistently — see that file's <application> tag.
    private fun registerBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val js = "(function(){ try { return !!(window.handleAndroidBack && window.handleAndroidBack()); } catch(e){ return false; } })();"
                webView.evaluateJavascript(js) { result ->
                    val handledByWebApp = result == "true"
                    if (!handledByWebApp) {
                        if (webView.canGoBack()) {
                            webView.goBack()
                        } else {
                            // Double-back-to-exit, same pattern as most Android
                            // apps — only reached when already on the home
                            // dashboard, or at the very first screen of the
                            // Sign Up/Login flow (Sign Up itself), since
                            // handleAndroidBack() otherwise pops one level of
                            // in-app navigation (or one onboarding step) first.
                            val now = System.currentTimeMillis()
                            if (now - backPressedOnceAt < 2000) {
                                // Temporarily step aside so this same callback
                                // doesn't just re-intercept the dispatcher's
                                // default behavior, then let the system finish
                                // the exit (finishes/moves the task to back).
                                isEnabled = false
                                onBackPressedDispatcher.onBackPressed()
                                isEnabled = true
                            } else {
                                backPressedOnceAt = now
                                Toast.makeText(this@MainActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        })
    }

    // ── Notification permission (Android 13+) ─────────────────────────────
    // Below API 33 notifications don't need runtime permission at all — the
    // manifest declaration is enough. On 33+, without this the push still
    // arrives and TxpMessagingService still runs, but the system silently
    // drops the notification instead of showing it.
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // ── FCM token ──────────────────────────────────────────────────────────
    // Fetched once at every app start (cheap — returns the cached token
    // instantly if it hasn't rotated) and cached locally via TokenStore so
    // WebAppInterface.getFcmToken() can hand it to the web app the moment it
    // logs in, without waiting on a fresh network round-trip.
    private fun fetchAndCacheFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                task.result?.let { TokenStore.save(applicationContext, it) }
            }
        }
    }

    // ── Notification-tap deep linking ─────────────────────────────────────
    private fun captureDeepLinkExtras(intent: Intent?) {
        val notifId = intent?.getStringExtra(TxpMessagingService.EXTRA_NOTIF_ID)
        if (!notifId.isNullOrEmpty()) {
            pendingNotifId = notifId
            pendingIsAdmin = intent.getBooleanExtra(TxpMessagingService.EXTRA_NOTIF_IS_ADMIN, false)
            // If the web app already finished loading/logging in earlier in
            // this same process (warm start via onNewIntent), fire right away
            // instead of waiting for another notifyAppReady() call that will
            // never come a second time.
            if (webAppReady) dispatchPendingDeepLink()
        }
    }

    // Warm start: app process already running, user taps a second
    // notification. MainActivity is singleTask (see AndroidManifest.xml) so
    // this fires instead of a new instance being created.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureDeepLinkExtras(intent)
    }

    // Called (already hopped onto the main thread) once the web app's JS has
    // finished loading and logging in.
    private fun onWebAppReady() {
        webAppReady = true
        dispatchPendingDeepLink()
    }

    private fun dispatchPendingDeepLink() {
        val notifId = pendingNotifId ?: return
        pendingNotifId = null
        // onNotifTap(id, isAdmin) already exists in script.js — it looks up
        // the full notification record, marks it read, and routes to the
        // right in-app page based on its type. Native just needs to pass the
        // id + role; JSONObject.quote() safely escapes the id for embedding
        // in the JS call (ids are timestamp-based strings, but this is cheap
        // insurance against ever breaking on a stray quote/backslash).
        val js = "javascript:(function(){ if (window.onNotifTap) { onNotifTap(${JSONObject.quote(notifId)}, $pendingIsAdmin); } })();"
        webView.evaluateJavascript(js, null)
    }

    // ── Export Data: writes the given JSON string (the full db — every
    // intern's tasks, habits, rewards, posts, everything) to a file, then
    // hands it straight to the system Share sheet so the admin can send it
    // anywhere (WhatsApp, Drive, Gmail, "Save to files", etc.) — matching
    // how e.g. Tickoff's own backup-file share works. The file is written
    // under the app's private cache dir (cache/exports/) rather than public
    // Downloads, since FileProvider needs an app-owned path to grant a
    // content:// URI from — see file_paths.xml / AndroidManifest's
    // <provider>. filename is passed in from script.js as something like
    // "traineexp_backup_db_<timestamp>.json" (see onStartExportDataClick).
    private fun exportDataToFile(json: String, filename: String) {
        Thread {
            try {
                val bytes = json.toByteArray(Charsets.UTF_8)
                val exportsDir = java.io.File(applicationContext.cacheDir, "exports")
                if (!exportsDir.exists()) exportsDir.mkdirs()
                // Clear out any previously exported files first so this
                // folder never silently accumulates stale exports.
                exportsDir.listFiles()?.forEach { it.delete() }
                val file = java.io.File(exportsDir, filename)
                file.writeBytes(bytes)

                val uri = androidx.core.content.FileProvider.getUriForFile(
                    applicationContext,
                    "${applicationContext.packageName}.fileprovider",
                    file
                )

                runOnUiThread {
                    try {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, filename)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(shareIntent, "Share exported data"))
                        webView.evaluateJavascript(
                            "javascript:(function(){ if (window.onExportDataResult) onExportDataResult(true, ${JSONObject.quote(filename)}); })();",
                            null
                        )
                    } catch (e: Exception) {
                        webView.evaluateJavascript(
                            "javascript:(function(){ if (window.onExportDataResult) onExportDataResult(false, ${JSONObject.quote(e.message ?: "Could not open Share sheet")}); })();",
                            null
                        )
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    webView.evaluateJavascript(
                        "javascript:(function(){ if (window.onExportDataResult) onExportDataResult(false, ${JSONObject.quote(e.message ?: "Export failed")}); })();",
                        null
                    )
                }
            }
        }.start()
    }

    // ── Deliver the selected image URI back to WebView ──
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST) {
            val results = if (resultCode == Activity.RESULT_OK && data != null) {
                WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            } else {
                null // User cancelled — pass null so WebView resets cleanly
            }
            fileChooserCallback?.onReceiveValue(results)
            fileChooserCallback = null
        }
    }

}
