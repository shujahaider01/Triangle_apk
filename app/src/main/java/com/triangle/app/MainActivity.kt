package com.triangle.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import com.triangle.app.data.FeatureFlags
import com.triangle.app.data.ThemeMode
import com.triangle.app.data.ThemeStore
import com.triangle.app.navigation.AppNavHost
import com.triangle.app.ui.theme.TriangleTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    // ── Push notifications: pending deep-link state ──────────────────────
    // Set from whichever Intent (cold-start onCreate, or warm-start
    // onNewIntent) carried notification-tap extras. AppNavHost's Dashboard
    // route consumes this to route straight to the native Notifications
    // screen — see hasPendingDeepLink()/consumePendingDeepLink() below.
    /** A tapped push (resolved later via [notifId]) or reminder ([reminderKind]+[reminderItemId]) waiting for AppNavHost to route it. */
    data class PendingDeepLink(val notifId: String?, val reminderKind: String?, val reminderItemId: String?)

    private var pendingLink: PendingDeepLink? = null

    // Bumped every time captureDeepLinkExtras() captures a new pending
    // notification id, including a warm-start tap while already sitting on
    // Dashboard. AppNavHost keys its deep-link LaunchedEffect on this (in
    // addition to the session uid) so a warm-start tap re-triggers routing
    // instead of being silently dropped — a real gap found while retiring
    // the old onWebAppReady()/dispatchPendingDeepLink() bridge, which
    // happened to paper over this case by re-checking on every WebView load.
    var pendingDeepLinkGeneration by mutableIntStateOf(0)
        private set

    // ── Native (Compose) shell integration ──────────────────────────────────
    // Set by AppNavHost so the single OnBackPressedCallback below can defer
    // to the Compose NavController's own back stack. Returns true if it
    // consumed the back press (popped something), false to fall through to
    // the double-tap-to-exit behavior.
    var onNativeBackPressed: (() -> Boolean)? = null

    fun hasPendingDeepLink(): Boolean = pendingLink != null

    fun consumePendingDeepLink(): PendingDeepLink? {
        val link = pendingLink
        pendingLink = null
        return link
    }

    // ── Back button/gesture: double-tap-to-exit state ──────────────────────
    private var backPressedOnceAt = 0L

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash surface itself can only ever be a flat color
        // (windowSplashScreenBackground is a @color, not a drawable — an
        // Android platform restriction, not a limitation of this setup) —
        // it dismisses on the very next frame instead of being held open,
        // so that unavoidable flat-orange instant is as brief as possible.
        // AppNavHost's own gradient splash (matching the real logo) takes
        // over immediately after for the actual session-restore wait.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        setContent {
            val context = LocalContext.current
            val mode by ThemeStore.modeFlow(context).collectAsState(initial = ThemeMode.LIGHT)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (mode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Status/nav bar icon contrast has to react to the resolved
            // theme too (not just MaterialTheme's colors) — light icons on
            // a dark background and vice versa, same override chosen in
            // Settings > Appearance, not just the raw OS setting.
            SideEffect {
                val insetsController = WindowInsetsControllerCompat(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
            TriangleTheme(darkTheme = darkTheme) {
                AppNavHost(activity = this)
                com.triangle.app.update.UpdatePrompt()
            }
        }

        ensureNotificationPermission()
        fetchAndCacheFcmToken()
        lifecycleScope.launch { FeatureFlags.refreshFromRemote() }
        // Only on a genuine launch — after a rotation/recreate the same (already handled)
        // intent extras would otherwise re-navigate to the notification's target.
        if (savedInstanceState == null) captureDeepLinkExtras(intent)
        registerBackHandling()
    }

    // ── Back handling: button AND gesture ──────────────────────────────────
    // Registered via OnBackPressedDispatcher (androidx.activity) instead of
    // overriding the deprecated onBackPressed() — this is the API Android's
    // Predictive Back system actually routes both the hardware/software
    // back button AND the edge-swipe gesture through. Requires
    // android:enableOnBackInvokedCallback="true" in AndroidManifest.xml for
    // the OS to actually dispatch gesture-nav through here consistently —
    // see that file's <application> tag.
    private fun registerBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val handledNatively = onNativeBackPressed?.invoke() ?: false
                if (!handledNatively) doubleTapToExit()
            }

            // Double-back-to-exit, same pattern as most Android apps — only
            // reached once the native back stack has nothing left to pop
            // (already on the home Dashboard, or Login).
            fun doubleTapToExit() {
                val now = System.currentTimeMillis()
                if (now - backPressedOnceAt < 2000) {
                    // Temporarily step aside so this same callback doesn't
                    // just re-intercept the dispatcher's default behavior,
                    // then let the system finish the exit (finishes/moves
                    // the task to back).
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                } else {
                    backPressedOnceAt = now
                    Toast.makeText(this@MainActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
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
    // AppNavHost can hand it to Firebase once a session is known, without
    // waiting on a fresh network round-trip.
    private fun fetchAndCacheFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                task.result?.let { TokenStore.save(applicationContext, it) }
            }
        }
    }

    // ── Notification-tap deep linking ─────────────────────────────────────
    private fun captureDeepLinkExtras(intent: Intent?) {
        val notifId = intent?.getStringExtra(TxpMessagingService.EXTRA_NOTIF_ID)?.takeIf { it.isNotEmpty() }
        val reminderKind = intent?.getStringExtra(com.triangle.app.reminders.ReminderNotifier.EXTRA_KIND)?.takeIf { it.isNotEmpty() }
        val reminderItemId = intent?.getStringExtra(com.triangle.app.reminders.ReminderNotifier.EXTRA_ITEM_ID)?.takeIf { it.isNotEmpty() }
        if (notifId != null || (reminderKind != null && reminderItemId != null)) {
            pendingLink = PendingDeepLink(notifId, reminderKind, reminderItemId)
            pendingDeepLinkGeneration++
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

}
