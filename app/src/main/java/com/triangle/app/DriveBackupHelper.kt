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
 * Minimal Google Drive v3 REST client for backup/restore — no Drive SDK
 * dependency, just HttpURLConnection, since the only two operations needed
 * are "upload a JSON snapshot" and "fetch the most recent one back."
 *
 * Uses the drive.file scope (see MainActivity.DRIVE_SCOPE), which means
 * this app can only ever see/manage files IT created — never the rest of
 * the signed-in admin's Drive. All backups are named with BACKUP_PREFIX so
 * they're easy to find, sort by recency, and clean up.
 */
object DriveBackupHelper {
    private const val BACKUP_PREFIX = "TraineeXP_Backup_"
    private const val MAX_BACKUPS_TO_KEEP = 5

    @Throws(Exception::class)
    fun uploadBackup(accessToken: String, jsonData: String) {
        val fileName = BACKUP_PREFIX + System.currentTimeMillis() + ".json"
        val boundary = "txp_backup_boundary_" + System.currentTimeMillis()
        val metadata = JSONObject().apply { put("name", fileName) }

        val body = StringBuilder()
        body.append("--").append(boundary).append("\r\n")
        body.append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        body.append(metadata.toString()).append("\r\n")
        body.append("--").append(boundary).append("\r\n")
        body.append("Content-Type: application/json\r\n\r\n")
        body.append(jsonData).append("\r\n")
        body.append("--").append(boundary).append("--")

        val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")

            val bodyBytes = body.toString().toByteArray(StandardCharsets.UTF_8)
            conn.setFixedLengthStreamingMode(bodyBytes.size)
            conn.outputStream.use { it.write(bodyBytes) }

            val code = conn.responseCode
            if (code !in 200..299) {
                throw Exception("Drive upload failed (HTTP $code): ${readStream(conn.errorStream)}")
            }
            readStream(conn.inputStream) // drain the response; content unused
        } finally {
            conn.disconnect()
        }

        pruneOldBackups(accessToken)
    }

    @Throws(Exception::class)
    fun downloadLatestBackup(accessToken: String): String? {
        val files = listBackups(accessToken)
        if (files.isEmpty()) return null
        val latestId = files[0].first

        val url = URL("https://www.googleapis.com/drive/v3/files/$latestId?alt=media")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $accessToken")

            val code = conn.responseCode
            if (code !in 200..299) {
                throw Exception("Drive download failed (HTTP $code): ${readStream(conn.errorStream)}")
            }
            return readStream(conn.inputStream)
        } finally {
            conn.disconnect()
        }
    }

    // Returns (fileId, name) pairs, newest first.
    @Throws(Exception::class)
    private fun listBackups(accessToken: String): List<Pair<String, String>> {
        val query = "name contains '$BACKUP_PREFIX' and trashed = false"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = URL(
            "https://www.googleapis.com/drive/v3/files?q=$encodedQuery" +
                "&orderBy=createdTime desc&fields=files(id,name)&pageSize=50"
        )
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $accessToken")

            val code = conn.responseCode
            if (code !in 200..299) {
                throw Exception("Drive list failed (HTTP $code): ${readStream(conn.errorStream)}")
            }
            val json = JSONObject(readStream(conn.inputStream))
            val filesArr: JSONArray = json.optJSONArray("files") ?: JSONArray()
            val result = mutableListOf<Pair<String, String>>()
            for (i in 0 until filesArr.length()) {
                val f = filesArr.getJSONObject(i)
                result.add(Pair(f.getString("id"), f.getString("name")))
            }
            return result
        } finally {
            conn.disconnect()
        }
    }

    // Best-effort cleanup so Drive doesn't accumulate unlimited backup
    // files — keeps the newest MAX_BACKUPS_TO_KEEP, deletes the rest.
    // Any failure here never breaks the backup that was just uploaded.
    private fun pruneOldBackups(accessToken: String) {
        try {
            val files = listBackups(accessToken)
            if (files.size <= MAX_BACKUPS_TO_KEEP) return
            files.drop(MAX_BACKUPS_TO_KEEP).forEach { (id, _) ->
                try {
                    val url = URL("https://www.googleapis.com/drive/v3/files/$id")
                    val conn = url.openConnection() as HttpURLConnection
                    try {
                        conn.requestMethod = "DELETE"
                        conn.setRequestProperty("Authorization", "Bearer $accessToken")
                        conn.responseCode // trigger the request
                    } finally {
                        conn.disconnect()
                    }
                } catch (e: Exception) { /* ignore single-file cleanup failures */ }
            }
        } catch (e: Exception) { /* cleanup is best-effort only */ }
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
