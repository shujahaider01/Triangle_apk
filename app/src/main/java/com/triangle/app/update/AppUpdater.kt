package com.triangle.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.delay
import androidx.core.content.FileProvider
import com.google.firebase.database.FirebaseDatabase
import com.triangle.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-hosted update info, read from `appUpdate` in this environment's own
 * Realtime Database (so dev/qa/prod each announce their own builds):
 * { latestVersionCode, versionName, apkUrl, changelog }. Updates are always mandatory.
 */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val changelog: String,
    val force: Boolean,
)

object AppUpdater {

    // The first read right after launch can fail with "Client is offline" before
    // the database connection is established, so retry a few times.
    suspend fun checkForUpdate(): UpdateInfo? {
        repeat(4) { attempt ->
            runCatching {
                val snap = FirebaseDatabase.getInstance().getReference("appUpdate").get().await()
                val latest = (snap.child("latestVersionCode").value as? Number)?.toLong()
                val url = (snap.child("apkUrl").value as? String)?.takeIf { it.startsWith("https://") }
                Log.i(TAG, "attempt=$attempt exists=${snap.exists()} latest=$latest installed=${BuildConfig.VERSION_CODE} url=$url")
                if (latest == null || url == null || latest <= BuildConfig.VERSION_CODE) return null
                return UpdateInfo(
                    versionCode = latest,
                    versionName = snap.child("versionName").value?.toString() ?: latest.toString(),
                    apkUrl = url,
                    changelog = (snap.child("changelog").value as? String).orEmpty(),
                    force = true,
                )
            }.onFailure { Log.w(TAG, "attempt=$attempt failed: ${it.message}") }
            delay(3_000)
        }
        return null
    }

    private const val TAG = "AppUpdater"

    /** Downloads the APK into cache, reporting 0f..1f (or -1f if size unknown). */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "update-${info.versionCode}.apk")
            val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            try {
                check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
                val total = conn.contentLengthLong
                var done = 0L
                conn.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            onProgress(if (total > 0) done.toFloat() / total else -1f)
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            file
        }

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
