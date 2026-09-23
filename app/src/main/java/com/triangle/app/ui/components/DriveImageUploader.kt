package com.triangle.app.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.triangle.app.DriveImageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Uploads a photo to the user's Google Drive (see DriveImageHelper for why Drive, not Firebase
 * Storage) and returns a link-viewable image URL. Handles the drive.file authorization — Google's
 * consent screen is only shown when access hasn't been granted yet — so callers just `upload()`.
 * Throws if the user declines Drive access or the upload fails.
 */
class DriveImageUploader internal constructor(
    private val authorize: suspend () -> String?
) {
    suspend fun upload(bitmap: Bitmap, fileName: String): String {
        val token = authorize()
        if (token.isNullOrEmpty()) throw IllegalStateException("Google Drive access is needed to upload photos")
        return withContext(Dispatchers.IO) { DriveImageHelper.uploadImage(token, fileName, bitmap) }
    }
}

private class PendingToken { var resume: ((String?) -> Unit)? = null }

@Composable
fun rememberDriveImageUploader(): DriveImageUploader {
    val context = LocalContext.current
    val pending = remember { PendingToken() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val token = runCatching {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data).accessToken
        }.getOrNull()
        pending.resume?.invoke(token)
        pending.resume = null
    }
    return remember(context) {
        DriveImageUploader {
            suspendCancellableCoroutine { cont ->
                fun finish(token: String?) {
                    pending.resume = null
                    if (cont.isActive) cont.resume(token)
                }
                pending.resume = ::finish
                Identity.getAuthorizationClient(context)
                    .authorize(AuthorizationRequest.builder().setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.file"))).build())
                    .addOnSuccessListener { auth ->
                        if (auth.hasResolution()) {
                            try {
                                launcher.launch(IntentSenderRequest.Builder(auth.pendingIntent!!.intentSender).build())
                            } catch (e: Exception) {
                                finish(null)
                            }
                        } else {
                            finish(auth.accessToken)
                        }
                    }
                    .addOnFailureListener { finish(null) }
            }
        }
    }
}
