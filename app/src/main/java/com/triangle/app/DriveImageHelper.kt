package com.triangle.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Uploads feed/reward/rich-text/task-photo images straight to Google Drive
 * using the SAME OAuth connection Backup & Restore already established
 * (the drive.file access token in DriveAuthStore) — plain Drive v3 REST via
 * HttpURLConnection, no separate Apps Script proxy.
 *
 * This REPLACES the earlier Apps Script Web App approach (ImageUploadHelper
 * + apps_script_code.gs). That approach tied every uploaded image to
 * whichever fixed Google account happened to deploy the script — completely
 * disconnected from whichever account an admin actually connects via
 * Backup & Restore's "Sign In With Google". With two different admins (or
 * even the same admin reinstalling/redeploying), images could silently end
 * up owned by an account nobody on the team actually has access to. Routing
 * through the already-connected drive.file token instead means images
 * always land in the SAME Drive account that's visibly connected in
 * Settings > Backup & Restore — whichever admin/device connected it.
 *
 * Folder layout is created (if missing) under that account's Drive root:
 *   TraineeXP Images / avatars|feed|rewards|richtext|misc
 */
object DriveImageHelper {
    private const val ROOT_FOLDER_NAME = "TraineeXP Images"
    private val SUBFOLDERS = setOf("avatars", "feed", "rewards", "richtext")
    private const val FOLDER_MIME = "application/vnd.google-apps.folder"

    @Throws(Exception::class)
    fun uploadImage(accessToken: String, base64Image: String, filename: String, folder: String): String {
        var b64 = base64Image
        val commaIdx = b64.indexOf(",")
        if (b64.startsWith("data:") && commaIdx != -1) b64 = b64.substring(commaIdx + 1)
        val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)

        val rootId = findOrCreateFolder(accessToken, ROOT_FOLDER_NAME, null)
        val safeSubfolder = if (SUBFOLDERS.contains(folder)) folder else "misc"
        val subfolderId = findOrCreateFolder(accessToken, safeSubfolder, rootId)

        val fileId = uploadFile(accessToken, bytes, filename, subfolderId)
        makePubliclyViewable(accessToken, fileId)
        return "https://lh3.googleusercontent.com/d/$fileId"
    }

    // Finds a folder by exact name under the given parent (or Drive root if
    // parentId is null), creating it if it doesn't exist yet. drive.file
    // scope only sees files/folders this app created itself, which is
    // exactly what we want — no risk of colliding with an unrelated folder
    // the admin already had.
    @Throws(Exception::class)
    private fun findOrCreateFolder(accessToken: String, name: String, parentId: String?): String {
        val parentClause = if (parentId != null) " and '$parentId' in parents" else " and 'root' in parents"
        val query = "name = '${name.replace("'", "\\'")}' and mimeType = '$FOLDER_MIME' and trashed = false$parentClause"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id,name)&pageSize=1")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            val code = conn.responseCode
            if (code !in 200..299) throw Exception("Drive folder lookup failed (HTTP $code): ${readStream(conn.errorStream)}")
            val json = JSONObject(readStream(conn.inputStream))
            val files: JSONArray = json.optJSONArray("files") ?: JSONArray()
            if (files.length() > 0) return files.getJSONObject(0).getString("id")
        } finally {
            conn.disconnect()
        }

        // Not found — create it.
        val metadata = JSONObject().apply {
            put("name", name)
            put("mimeType", FOLDER_MIME)
            if (parentId != null) put("parents", JSONArray().put(parentId))
        }
        val createUrl = URL("https://www.googleapis.com/drive/v3/files?fields=id")
        val createConn = createUrl.openConnection() as HttpURLConnection
        try {
            createConn.requestMethod = "POST"
            createConn.doOutput = true
            createConn.setRequestProperty("Authorization", "Bearer $accessToken")
            createConn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            val body = metadata.toString().toByteArray(StandardCharsets.UTF_8)
            createConn.setFixedLengthStreamingMode(body.size)
            createConn.outputStream.use { it.write(body) }
            val code = createConn.responseCode
            if (code !in 200..299) throw Exception("Drive folder create failed (HTTP $code): ${readStream(createConn.errorStream)}")
            return JSONObject(readStream(createConn.inputStream)).getString("id")
        } finally {
            createConn.disconnect()
        }
    }

    @Throws(Exception::class)
    private fun uploadFile(accessToken: String, bytes: ByteArray, filename: String, parentId: String): String {
        val boundary = "txp_image_boundary_" + System.currentTimeMillis()
        val metadata = JSONObject().apply {
            put("name", filename)
            put("parents", JSONArray().put(parentId))
        }

        val head = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${metadata}\r\n--$boundary\r\nContent-Type: image/jpeg\r\n\r\n"
        val tail = "\r\n--$boundary--"
        val headBytes = head.toByteArray(StandardCharsets.UTF_8)
        val tailBytes = tail.toByteArray(StandardCharsets.UTF_8)

        val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            val total = headBytes.size + bytes.size + tailBytes.size
            conn.setFixedLengthStreamingMode(total)
            conn.outputStream.use {
                it.write(headBytes)
                it.write(bytes)
                it.write(tailBytes)
            }
            val code = conn.responseCode
            if (code !in 200..299) throw Exception("Drive image upload failed (HTTP $code): ${readStream(conn.errorStream)}")
            return JSONObject(readStream(conn.inputStream)).getString("id")
        } finally {
            conn.disconnect()
        }
    }

    @Throws(Exception::class)
    private fun makePubliclyViewable(accessToken: String, fileId: String) {
        val body = JSONObject().apply {
            put("role", "reader")
            put("type", "anyone")
        }.toString().toByteArray(StandardCharsets.UTF_8)

        val url = URL("https://www.googleapis.com/drive/v3/files/$fileId/permissions")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            if (code !in 200..299) throw Exception("Drive sharing update failed (HTTP $code): ${readStream(conn.errorStream)}")
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
