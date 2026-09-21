package com.triangle.app.data

import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Faithful port of script.js's _enqueuePush() (script.js:971-984) — writes a
 * push request to the SAME root `pushQueue` path the WebView already uses.
 * A GitHub Actions cron (push-worker/drain.js, already deployed — see
 * commit 3609d2a/0fd78c5) drains this queue every 5 minutes and sends real
 * FCM messages via firebase-admin. Nothing new needs to run server-side for
 * native-originated pushes to work — this just needs to write the same
 * shape to the same place.
 */
object PushQueue {
    private val db get() = FirebaseDatabase.getInstance()

    suspend fun enqueue(targetType: String, targetId: String, notifId: String, type: String, title: String, body: String) {
        db.getReference("pushQueue").push().setValue(
            mapOf(
                "targetType" to targetType,
                "targetId" to targetId,
                "notifId" to notifId,
                "isAdmin" to (targetType == "admin"),
                "type" to type,
                "title" to title,
                "body" to body,
                "createdAt" to System.currentTimeMillis()
            )
        ).await()
    }
}
