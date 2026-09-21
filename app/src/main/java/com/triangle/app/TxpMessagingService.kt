package com.triangle.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random

/**
 * Receives every FCM push sent by the Phase 4 Cloud Function. Two jobs:
 *
 *  - onNewToken(): FCM calls this whenever a token is first generated, or
 *    later rotated (reinstall, data clear, token expiry). Just caches it
 *    locally — Phase 3's JS is what actually associates a token with a
 *    logged-in user in Firebase, this class has no notion of "who's logged
 *    in right now."
 *
 *  - onMessageReceived(): fires whenever a push arrives *while the app
 *    process is alive* (foreground OR backgrounded-but-not-killed). This is
 *    the one that needs to actually build and show the system notification
 *    ourselves — Android only auto-displays a "notification" payload when
 *    the app process isn't running at all. Since every message from the
 *    Cloud Function is sent as a *data* message (not a notification
 *    message — see Phase 4), onMessageReceived() is guaranteed to be the
 *    single code path for showing the notification in every app state,
 *    keeping the behavior consistent instead of split across two paths.
 */
class TxpMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        TokenStore.save(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: "TraineeXP"
        val body = data["body"] ?: message.notification?.body ?: ""
        val type = data["type"] ?: ""
        val notifId = data["notifId"] ?: ""
        val isAdmin = data["isAdmin"] == "true"

        showNotification(title, body, type, notifId, isAdmin)
    }

    private fun showNotification(title: String, body: String, type: String, notifId: String, isAdmin: Boolean) {
        val channelId = getString(R.string.default_notification_channel_id)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Channels are required on API 26+ before you can post to them at all.
        // Created here (idempotent — no-op if it already exists) rather than
        // only at app startup, so a push that arrives before the app has ever
        // been opened once still has a valid channel to post into.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId,
                    "TraineeXP Notifications",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Task, habit, post, and message notifications"
                }
                nm.createNotificationChannel(channel)
            }
        }

        // Tapping the notification launches MainActivity carrying just enough
        // to identify which notification this was — the JS side (onNotifTap,
        // already built in Phase 3/earlier work) looks up the full record and
        // does the actual routing, so native code only needs to pass the id.
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOTIF_ID, notifId)
            putExtra(EXTRA_NOTIF_IS_ADMIN, isAdmin)
            putExtra(EXTRA_NOTIF_TYPE, type)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            Random.nextInt(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setColor(getColor(R.color.notification_color))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(Random.nextInt(), notification)
    }

    companion object {
        const val EXTRA_NOTIF_ID = "notif_id"
        const val EXTRA_NOTIF_IS_ADMIN = "notif_is_admin"
        const val EXTRA_NOTIF_TYPE = "notif_type"
    }
}
