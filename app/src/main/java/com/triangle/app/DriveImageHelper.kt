package com.triangle.app

import android.graphics.Bitmap
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Google Drive v3 REST upload for user-taken photos (profile avatar, task
 * notes, habit notes) — the replacement for Firebase Storage, which this
 * project's Firebase console never had Storage enabled for (every upload
 * there just threw). Reuses the same drive.file OAuth scope/token as
 * Settings > Backup & Restore (see DriveBackupHelper/DriveAuthStore) rather
 * than adding a second Google integration.
 *
 * drive.file uploads are private to the uploading account by default, and
 * there's no way to load a private Drive file into an image-loading library
 * (Coil) without hand-rolling an authenticated HTTP client — so every
 * upload here is immediately given a "anyone with the link can view"
 * permission right after upload. This is the same trade-off any app makes
 * when it hotlinks Drive-hosted images: the file is unlisted (nobody finds
 * it without the exact link/id) but not access-controlled the way Firebase
 * Storage rules could be. Acceptable for avatar/note photos in a personal
 * productivity app; would need revisiting for anything actually sensitive.
 */
object DriveImageHelper {
    private fun compressJpeg(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { stream ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        stream.toByteArray()
    }

    /** Compresses to JPEG, uploads to Drive, makes it link-viewable, and returns a directly-loadable image URL. */
    @Throws(Exception::class)
    fun uploadImage(accessToken: String, fileName: String, bitmap: Bitmap): String =
        uploadJpeg(accessToken, fileName, compressJpeg(bitmap))

    /** Same as [uploadImage] for callers that already have the JPEG bytes (e.g. to record their size first). */
    @Throws(Exception::class)
    fun uploadJpeg(accessToken: String, fileName: String, jpegBytes: ByteArray): String {
        val fileId = uploadBytes(accessToken, fileName, "image/jpeg", jpegBytes)
        makePubliclyViewable(accessToken, fileId)
        return "https://drive.google.com/uc?export=view&id=$fileId"
    }

    /** Uploads any file (photo, video, PDF) and makes it link-viewable; returns the same `uc?export=view` style URL as [uploadJpeg]. */
    @Throws(Exception::class)
    fun uploadFile(accessToken: String, fileName: String, mimeType: String, bytes: ByteArray): String {
        val fileId = uploadBytes(accessToken, fileName, mimeType, bytes)
        makePubliclyViewable(accessToken, fileId)
        return "https://drive.google.com/uc?export=view&id=$fileId"
    }

    fun toJpegBytes(bitmap: Bitmap): ByteArray = compressJpeg(bitmap)

    // Binary-safe multipart upload (unlike DriveBackupHelper.uploadBackup's
    // String-concatenation body, which is fine for JSON text but would
    // corrupt arbitrary JPEG bytes) — builds the request body as raw bytes
    // via ByteArrayOutputStream instead.
    private fun uploadBytes(accessToken: String, fileName: String, mimeType: String, fileBytes: ByteArray): String {
        val boundary = "txp_img_boundary_" + System.currentTimeMillis()
        val metadata = JSONObject().apply { put("name", fileName) }

        val body = ByteArrayOutputStream()
        fun writeText(s: String) = body.write(s.toByteArray(StandardCharsets.UTF_8))
        writeText("--$boundary\r\n")
        writeText("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        writeText(metadata.toString())
        writeText("\r\n--$boundary\r\n")
        writeText("Content-Type: $mimeType\r\n\r\n")
        body.write(fileBytes)
        writeText("\r\n--$boundary--")
        val bodyBytes = body.toByteArray()

        val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            conn.setFixedLengthStreamingMode(bodyBytes.size)
            conn.outputStream.use { it.write(bodyBytes) }

            val code = conn.responseCode
            if (code !in 200..299) {
                throw Exception("Drive image upload failed (HTTP $code): ${readStream(conn.errorStream)}")
            }
            return JSONObject(readStream(conn.inputStream)).getString("id")
        } finally {
            conn.disconnect()
        }
    }

    private fun makePubliclyViewable(accessToken: String, fileId: String) {
        val url = URL("https://www.googleapis.com/drive/v3/files/$fileId/permissions")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            val body = JSONObject().apply { put("role", "reader"); put("type", "anyone") }.toString().toByteArray(StandardCharsets.UTF_8)
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }

            val code = conn.responseCode
            if (code !in 200..299) {
                throw Exception("Couldn't make the photo viewable (HTTP $code): ${readStream(conn.errorStream)}")
            }
            readStream(conn.inputStream)
        } finally {
            conn.disconnect()
        }
    }

    private fun readStream(stream: InputStream?): String {
        if (stream == null) return ""
        val reader = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) sb.append(line)
        reader.close()
        return sb.toString()
    }
}

