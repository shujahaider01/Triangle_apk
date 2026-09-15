package com.triangle.app.data

import android.graphics.Bitmap
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

/**
 * Firebase Storage upload for task-note photos — decoupled from Google
 * Drive on purpose (the user's explicit choice: Drive OAuth is scoped to
 * Settings > Backup & Restore only, so a photo note works whether or not
 * Drive is ever connected). Requires Storage to actually be enabled on the
 * `triangle-apk` Firebase project (Console > Build > Storage > Get
 * started) — it wasn't as of this feature landing; `uploadNotePhoto` will
 * throw a real exception from the SDK until that's done, surfaced to the
 * UI rather than silently failing.
 */
object TaskNoteRepository {
    private fun storage() = FirebaseStorage.getInstance()

    /** Compresses to JPEG and uploads to taskNotePhotos/{orgId}/{uid}/{taskId}/{timestamp}.jpg — returns the download URL. */
    suspend fun uploadNotePhoto(orgId: String, uid: String, taskId: String, bitmap: Bitmap): String {
        val bytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            stream.toByteArray()
        }
        val path = "taskNotePhotos/$orgId/$uid/$taskId/${System.currentTimeMillis()}.jpg"
        val ref = storage().reference.child(path)
        ref.putBytes(bytes).await()
        return ref.downloadUrl.await().toString()
    }
}
