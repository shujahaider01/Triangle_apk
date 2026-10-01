package com.triangle.app.dm

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.triangle.app.DriveImageHelper
import com.triangle.app.tasks.decodePickedUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A photo (re-encoded JPEG) or video, read from the device and ready to upload; videos carry a JPEG poster frame. */
class PreparedMedia(val bytes: ByteArray, val mime: String, val thumb: ByteArray? = null, val durationMs: Long? = null)

private const val MAX_VIDEO_BYTES = 25L * 1024 * 1024
private const val MAX_SIDE = 1600

private fun downscaled(bmp: Bitmap): Bitmap {
    val longest = maxOf(bmp.width, bmp.height)
    if (longest <= MAX_SIDE) return bmp
    val f = MAX_SIDE.toFloat() / longest
    return Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt(), (bmp.height * f).toInt(), true)
}

fun prepareBitmap(bmp: Bitmap): PreparedMedia = PreparedMedia(DriveImageHelper.toJpegBytes(downscaled(bmp)), "image/jpeg")

/** Reads a gallery pick. Null when it can't be read or a video is over the size limit ([tooBig] tells which). */
suspend fun prepareUri(context: Context, uri: Uri, onTooBig: () -> Unit): PreparedMedia? = withContext(Dispatchers.IO) {
    val mime = context.contentResolver.getType(uri) ?: return@withContext null
    when {
        mime.startsWith("image/") -> decodePickedUri(context, uri)?.let { prepareBitmap(it) }
        mime.startsWith("video/") -> {
            val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            if (size > MAX_VIDEO_BYTES) { onTooBig(); return@withContext null }
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            val thumb = runCatching {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(context, uri)
                    r.getFrameAtTime(0)?.let { DriveImageHelper.toJpegBytes(downscaled(it)) }
                } finally { r.release() }
            }.getOrNull()
            PreparedMedia(bytes, mime, thumb)
        }
        else -> null
    }
}

/** Downloads a chat photo/video and saves it to the phone's gallery (Pictures/Triangle or Movies/Triangle). Returns true on success. */
suspend fun saveMediaToGallery(context: Context, url: String, isVideo: Boolean): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val id = Regex("[?&]id=([^&]+)").find(url)?.groupValues?.get(1)
        val src = if (isVideo && id != null) "https://drive.usercontent.google.com/download?id=$id&export=download&confirm=t" else url
        val conn = java.net.URL(src).openConnection() as java.net.HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15000; conn.readTimeout = 30000
        if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
        val name = "Triangle_${System.currentTimeMillis()}.${if (isVideo) "mp4" else "jpg"}"
        val mime = if (isVideo) "video/mp4" else "image/jpeg"
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, (if (isVideo) "Movies" else "Pictures") + "/Triangle")
            }
        }
        val collection = if (isVideo) android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI else android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val out = context.contentResolver.insert(collection, values) ?: error("Couldn't create the file")
        context.contentResolver.openOutputStream(out)?.use { os -> conn.inputStream.use { it.copyTo(os) } } ?: error("Couldn't write the file")
        conn.disconnect()
        true
    }.getOrDefault(false)
}

/** Records a voice message to an AAC .m4a file in the cache. */
class VoiceRecorder(private val context: Context) {
    private var recorder: android.media.MediaRecorder? = null
    private var file: java.io.File? = null
    private var startedAt = 0L

    fun start(): Boolean {
        val f = java.io.File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        val r = if (android.os.Build.VERSION.SDK_INT >= 31) android.media.MediaRecorder(context) else @Suppress("DEPRECATION") android.media.MediaRecorder()
        return try {
            r.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(64000)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r; file = f; startedAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            runCatching { r.release() }
            f.delete()
            false
        }
    }

    /** 0..1 loudness for the live waveform. */
    fun level(): Float = (runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0) / 12000f).coerceIn(0f, 1f)

    /** Stops and returns the recording (bytes + length), or null if it failed. Pass discard = true to throw it away. */
    fun stop(discard: Boolean): PreparedMedia? {
        val r = recorder ?: return null
        val f = file
        recorder = null; file = null
        val duration = System.currentTimeMillis() - startedAt
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        if (discard || !ok || f == null) { f?.delete(); return null }
        val bytes = runCatching { f.readBytes() }.getOrNull()
        f.delete()
        return bytes?.let { PreparedMedia(it, "audio/mp4", durationMs = duration) }
    }
}

/** Downloads a Drive-hosted chat file into the cache once (reused afterwards) and returns it, or null on failure. */
suspend fun cachedChatFile(context: Context, url: String, ext: String): java.io.File? = withContext(Dispatchers.IO) {
    val id = Regex("[?&]id=([^&]+)").find(url)?.groupValues?.get(1) ?: return@withContext null
    val target = java.io.File(java.io.File(context.cacheDir, "chat_files").apply { mkdirs() }, "$id.$ext")
    if (target.exists() && target.length() > 0) return@withContext target
    runCatching {
        val conn = java.net.URL("https://drive.usercontent.google.com/download?id=$id&export=download&confirm=t").openConnection() as java.net.HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15000; conn.readTimeout = 30000
        if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
        val tmp = java.io.File(target.path + ".part")
        conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        conn.disconnect()
        if (tmp.renameTo(target)) target else null
    }.getOrNull()
}
