package com.triangle.app.data

import android.graphics.Bitmap
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

/**
 * Firebase Storage upload for note photos. Requires Storage to actually be
 * enabled on the `triangle-apk` Firebase project (Console > Build > Storage
 * > Get started) — it wasn't as of this feature landing, so these uploads
 * throw a real exception from the SDK until that's done, surfaced to the UI
 * rather than silently failing. Serves Task notes (uploadNotePhoto) and
 * Habit Detail's photo timeline (uploadHabitPhoto) — both otherwise
 * identical (compress, upload, return a download URL), just scoped under
 * different Storage paths. Profile's avatar upload moved to Google Drive
 * instead (see DriveImageHelper/ProfileViewModel.requestPhotoUpload) once
 * Storage turned out to still be disabled; the same move could be made here
 * later if these two also need to work without Storage enabled.
 */
object TaskNoteRepository {
    private fun storage() = FirebaseStorage.getInstance()

    private fun compressJpeg(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { stream ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        stream.toByteArray()
    }

    /** Compresses to JPEG and uploads to taskNotePhotos/{orgId}/{uid}/{taskId}/{timestamp}.jpg — returns the download URL. */
    suspend fun uploadNotePhoto(orgId: String, uid: String, taskId: String, bitmap: Bitmap): String {
        val path = "taskNotePhotos/$orgId/$uid/$taskId/${System.currentTimeMillis()}.jpg"
        val ref = storage().reference.child(path)
        ref.putBytes(compressJpeg(bitmap)).await()
        return ref.downloadUrl.await().toString()
    }

    /** Same as uploadNotePhoto but for Habit Detail's photo timeline — habitNotePhotos/{orgId}/{uid}/{habitId}/{timestamp}.jpg. */
    suspend fun uploadHabitPhoto(orgId: String, uid: String, habitId: String, bitmap: Bitmap): String {
        val path = "habitNotePhotos/$orgId/$uid/$habitId/${System.currentTimeMillis()}.jpg"
        val ref = storage().reference.child(path)
        ref.putBytes(compressJpeg(bitmap)).await()
        return ref.downloadUrl.await().toString()
    }
}
