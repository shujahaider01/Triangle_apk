package com.triangle.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.triangle.app.data.NotificationRepository
import com.triangle.app.data.ReviewRepository
import com.triangle.app.data.SessionStore
import com.triangle.app.data.models.Review
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * One-tap star buttons on a "task completed" notification: reviews the person
 * who completed the task without opening the app, using the same
 * ReviewRepository.submit() as the review form (so the same rules apply),
 * then dismisses the notification.
 */
class ReviewQuickRateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getStringExtra(EXTRA_NOTIF_ID) ?: return
        val stars = intent.getIntExtra(EXTRA_STARS, 0).coerceIn(1, Review.MAX_STARS)
        val systemId = intent.getIntExtra(EXTRA_SYSTEM_ID, 0)

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            var message: String? = null
            try {
                val session = SessionStore.sessionFlow(appContext).first()
                    ?: return@launch run { message = "Open Triangle and sign in to review" }
                val notif = NotificationRepository.getNotification(session.orgId, session.uid, notifId)
                val taskId = notif?.itemId
                val recipient = notif?.otherUserId
                if (taskId == null || recipient == null) {
                    message = "Couldn't find that task"
                    return@launch
                }
                val title = runCatching { ReviewRepository.loadTask(recipient, taskId)?.title }.getOrNull().orEmpty()
                val result = ReviewRepository.submit(
                    Review(
                        taskId = taskId,
                        recipientUid = recipient,
                        reviewerUid = session.uid,
                        reviewerName = session.name,
                        reviewerPhotoUrl = session.photoUrl,
                        taskTitle = title,
                        stars = stars
                    )
                )
                when (result) {
                    is ReviewRepository.Result.Sent -> {
                        message = "Review sent: $stars/${Review.MAX_STARS}"
                        runCatching { NotificationRepository.markRead(session.orgId, session.uid, notifId) }
                        (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(systemId)
                    }
                    is ReviewRepository.Result.AlreadyReviewed -> {
                        message = "You already reviewed this task"
                        (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(systemId)
                    }
                    is ReviewRepository.Result.NotAllowed -> message = "You can only review tasks you assigned"
                    is ReviewRepository.Result.Failed -> message = "Couldn't send the review"
                }
            } finally {
                message?.let { m -> Handler(Looper.getMainLooper()).post { Toast.makeText(appContext, m, Toast.LENGTH_SHORT).show() } }
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_NOTIF_ID = "review_notif_id"
        const val EXTRA_STARS = "review_stars"
        const val EXTRA_SYSTEM_ID = "review_system_id"
    }
}
