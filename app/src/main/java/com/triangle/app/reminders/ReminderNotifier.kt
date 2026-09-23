package com.triangle.app.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.triangle.app.MainActivity
import com.triangle.app.R

/**
 * Builds and posts the local notification a fired reminder alarm shows —
 * same NotificationCompat.Builder pattern as TxpMessagingService's FCM push
 * path, but under its own channel (so a user can mute reminders separately
 * from push notifications in system settings) and with a STABLE notification
 * id (derived from the item id) rather than TxpMessagingService's
 * Random.nextInt(), so re-firing the same task/habit's reminder replaces the
 * existing notification instead of stacking duplicates.
 */
object ReminderNotifier {
    private const val CHANNEL_ID = "triangle_reminders"

    const val EXTRA_KIND = "reminder_kind"
    const val EXTRA_ITEM_ID = "reminder_item_id"

    fun show(context: Context, kind: String, itemId: String, title: String, body: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Task and habit reminders"
                }
                nm.createNotificationChannel(channel)
            }
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_KIND, kind)
            putExtra(EXTRA_ITEM_ID, itemId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            itemId.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setColor(context.getColor(R.color.notification_color))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(itemId.hashCode(), notification)
    }
}
