package com.triangle.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.triangle.app.data.RankWatcher
import com.triangle.app.data.SessionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
        val rawTitle = data["title"] ?: message.notification?.title ?: "TraineeXP"
        val rawBody = data["body"] ?: message.notification?.body ?: ""
        val type = data["type"] ?: ""
        // Announcements now live in the Inbox: the phone notification is just "New Inbox: “Title”".
        val title = when (type) {
            "announcement" -> "New Inbox: “${rawTitle.trim()}”"
            "poll" -> "New Poll: “${rawTitle.trim()}”"
            else -> rawTitle
        }
        val body = if (type == "announcement" || type == "poll") "" else rawBody
        val notifId = data["notifId"] ?: ""
        val isAdmin = data["isAdmin"] == "true"

        showNotification(title, body, type, notifId, isAdmin)

        // A newly assigned task/habit: arm its reminder now, without waiting for the Tasks screen to be opened.
        if (type == "task_assigned" || type == "habit_assigned") {
            val appContext = applicationContext
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val s = SessionStore.sessionFlow(appContext).first() ?: return@launch
                runCatching { com.triangle.app.data.TaskRepository.materializeRepeatingTasks(s.orgId) }
                runCatching { com.triangle.app.reminders.ReminderScheduler.rescheduleAll(appContext, s.orgId) }
            }
        }

        // A push is a chance to re-check the leaderboard position in the background (throttled inside RankWatcher).
        if (type != "rank_change") {
            val appContext = applicationContext
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val s = SessionStore.sessionFlow(appContext).first() ?: return@launch
                RankWatcher.check(appContext, s)
            }
        }
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

        val systemId = Random.nextInt()
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_triangle)
            .setColor(getColor(R.color.notification_purple))
            .setLargeIcon(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_notification_large)?.toBitmap())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        // A completed assigned task can be rated straight from the notification. Android notifications can't
        // show a star widget, so the expanded notification carries two boxed one-tap buttons
        // ("Excellent 5 ★", "Good 4 ★"), and a "Review…" action opens the full form (any rating + comment).
        if (type == "task_completed" && notifId.isNotEmpty()) {
            fun ratePending(stars: Int): PendingIntent {
                val i = Intent(this, ReviewQuickRateReceiver::class.java).apply {
                    action = "com.triangle.app.REVIEW_RATE:$notifId:$stars"
                    putExtra(ReviewQuickRateReceiver.EXTRA_NOTIF_ID, notifId)
                    putExtra(ReviewQuickRateReceiver.EXTRA_STARS, stars)
                    putExtra(ReviewQuickRateReceiver.EXTRA_SYSTEM_ID, systemId)
                }
                return PendingIntent.getBroadcast(
                    this, ("rate:$notifId:$stars").hashCode(), i,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            val views = android.widget.RemoteViews(packageName, R.layout.notification_review).apply {
                setTextViewText(R.id.review_title, title)
                setTextViewText(R.id.review_body, body)
                setOnClickPendingIntent(R.id.review_rate5, ratePending(5))
                setOnClickPendingIntent(R.id.review_rate4, ratePending(4))
            }
            builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
            builder.setCustomBigContentView(views)
            builder.addAction(NotificationCompat.Action.Builder(0, "Review…", pendingIntent).build())
        }

        // Chat messages can be answered straight from the notification shade.
        if (type == "chat_message" && notifId.isNotEmpty()) {
            val replyIntent = Intent(this, DmReplyReceiver::class.java).apply {
                action = "com.triangle.app.DM_REPLY:$notifId"
                putExtra(DmReplyReceiver.EXTRA_NOTIF_ID, notifId)
                putExtra(DmReplyReceiver.EXTRA_SYSTEM_ID, systemId)
            }
            // Must be mutable so the system can attach the typed reply to it.
            val replyPending = PendingIntent.getBroadcast(
                this, systemId, replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            )
            val remoteInput = androidx.core.app.RemoteInput.Builder(DmReplyReceiver.KEY_REPLY).setLabel("Reply").build()
            builder.addAction(
                NotificationCompat.Action.Builder(0, "Reply", replyPending)
                    .addRemoteInput(remoteInput)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                    .build()
            )
        }

        nm.notify(systemId, builder.build())
    }

    companion object {
        const val EXTRA_NOTIF_ID = "notif_id"
        const val EXTRA_NOTIF_IS_ADMIN = "notif_is_admin"
        const val EXTRA_NOTIF_TYPE = "notif_type"
    }
}
