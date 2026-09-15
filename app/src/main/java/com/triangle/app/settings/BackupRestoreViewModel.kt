package com.triangle.app.settings

import android.content.Context
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.firebase.database.FirebaseDatabase
import com.triangle.app.DriveAuthStore
import com.triangle.app.DriveBackupHelper
import com.triangle.app.data.SessionStore
import com.triangle.app.data.TriangleConfig
import com.triangle.app.data.fromJsonElement
import com.triangle.app.data.toJsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class BackupRestoreUiState(
    val connected: Boolean = false,
    val lastBackupTime: Long = 0L,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

private enum class PendingDriveAction { CONNECT, BACKUP, RESTORE }

/**
 * Native rewrite of MainActivity's pre-M7 Drive backup/restore block —
 * restored per the user's explicit choice to bring back Backup & Restore
 * (Settings only, unrelated to task-note photos, which use Firebase
 * Storage instead — see the M8 plan). Unlike the original, this owns its
 * OAuth flow as a self-contained Compose screen/ViewModel pair instead of
 * living in MainActivity: Activity Result APIs need a real launcher, which
 * BackupRestoreScreen provides via rememberLauncherForActivityResult and
 * feeds back in via onAuthorizationIntentResult(), so MainActivity itself
 * needs zero changes.
 *
 * Backup/restore reads and writes the WHOLE `organizations/{orgId}/data`
 * node as one opaque JSON blob (FirebaseJsonUtils' toJsonElement/
 * fromJsonElement) — the one deliberate exception in this app to every
 * other repository's narrow-sub-path-write rule, since "restore my
 * account" is inherently a full-state operation by definition, same as the
 * original WebView's own db-blob backup.
 */
class BackupRestoreViewModel(private val session: SessionStore.Session) : ViewModel() {

    private val driveScope = Scope("https://www.googleapis.com/auth/drive.file")
    private var pendingAction = PendingDriveAction.CONNECT

    private val _uiState = MutableStateFlow(BackupRestoreUiState())
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()

    private fun orgDataRef() =
        FirebaseDatabase.getInstance(TriangleConfig.FIREBASE_URL).getReference("organizations/${session.orgId}/data")

    fun refreshLocalState(context: Context) {
        _uiState.value = _uiState.value.copy(
            connected = DriveAuthStore.isConnected(context),
            lastBackupTime = DriveAuthStore.getLastBackupTime(context)
        )
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(message = null, error = null)
    }

    fun connect(context: Context, launchIntent: (IntentSenderRequest) -> Unit) {
        pendingAction = PendingDriveAction.CONNECT
        requestAuthorization(context, launchIntent)
    }

    fun disconnect(context: Context) {
        DriveAuthStore.clear(context)
        _uiState.value = _uiState.value.copy(connected = false, lastBackupTime = 0L, message = "Google Drive disconnected")
    }

    fun backupNow(context: Context, launchIntent: (IntentSenderRequest) -> Unit) {
        pendingAction = PendingDriveAction.BACKUP
        requestAuthorization(context, launchIntent)
    }

    fun restore(context: Context, launchIntent: (IntentSenderRequest) -> Unit) {
        pendingAction = PendingDriveAction.RESTORE
        requestAuthorization(context, launchIntent)
    }

    private fun requestAuthorization(context: Context, launchIntent: (IntentSenderRequest) -> Unit) {
        _uiState.value = _uiState.value.copy(busy = true, error = null, message = null)
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(driveScope)).build()
        Identity.getAuthorizationClient(context)
            .authorize(request)
            .addOnSuccessListener { authResult ->
                if (authResult.hasResolution()) {
                    val pendingIntent = authResult.pendingIntent
                    try {
                        launchIntent(IntentSenderRequest.Builder(pendingIntent!!.intentSender).build())
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(busy = false, error = "Couldn't open Google sign-in: ${e.message}")
                    }
                } else {
                    onAuthorizationGranted(context, authResult)
                }
            }
            .addOnFailureListener { e ->
                _uiState.value = _uiState.value.copy(busy = false, error = e.message ?: "Google authorization failed")
            }
    }

    /** Called from the launcher's result callback once the account-picker/consent intent finishes. */
    fun onAuthorizationIntentResult(context: Context, resultData: Intent?) {
        try {
            val authResult = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(resultData)
            onAuthorizationGranted(context, authResult)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(busy = false, error = e.message ?: "Google sign-in was cancelled")
        }
    }

    private fun onAuthorizationGranted(context: Context, authResult: AuthorizationResult) {
        val token = authResult.accessToken
        if (token.isNullOrEmpty()) {
            _uiState.value = _uiState.value.copy(busy = false, error = "No access token returned by Google")
            return
        }
        DriveAuthStore.saveAccessToken(context, token)
        DriveAuthStore.setConnected(context, true)
        _uiState.value = _uiState.value.copy(connected = true)

        when (pendingAction) {
            PendingDriveAction.CONNECT -> _uiState.value = _uiState.value.copy(busy = false, message = "Google Drive connected")
            PendingDriveAction.BACKUP -> runBackup(context, token)
            PendingDriveAction.RESTORE -> runRestore(context, token)
        }
    }

    private fun runBackup(context: Context, token: String) {
        viewModelScope.launch {
            try {
                val snapshot = orgDataRef().get().await()
                val json = withContext(Dispatchers.IO) {
                    val jsonString = snapshot.value.toJsonElement().toString()
                    DriveBackupHelper.uploadBackup(token, jsonString)
                    jsonString
                }
                val ts = System.currentTimeMillis()
                DriveAuthStore.setLastBackupTime(context, ts)
                _uiState.value = _uiState.value.copy(busy = false, lastBackupTime = ts, message = "Backup complete (${json.length} bytes)")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(busy = false, error = e.message ?: "Backup failed")
            }
        }
    }

    private fun runRestore(context: Context, token: String) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) { DriveBackupHelper.downloadLatestBackup(token) }
                if (json == null) {
                    _uiState.value = _uiState.value.copy(busy = false, error = "No backup was found in Google Drive yet")
                    return@launch
                }
                @Suppress("UNCHECKED_CAST")
                val restored = JSONObject(json).fromJsonElement() as? Map<String, Any?> ?: emptyMap()
                orgDataRef().setValue(restored).await()
                _uiState.value = _uiState.value.copy(busy = false, message = "Restore complete")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(busy = false, error = e.message ?: "Restore failed")
            }
        }
    }
}
