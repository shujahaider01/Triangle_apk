package com.triangle.app.auth

import android.app.Application
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.triangle.app.BuildConfig
import com.triangle.app.data.SessionStore
import com.triangle.app.data.UserRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class SignupStep { WELCOME, EMAIL, OTP, PASSWORD }

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val signupStep: SignupStep = SignupStep.WELCOME,
    val pendingEmail: String = "",
    val otpError: String? = null
)

sealed class AuthEvent {
    data class LoggedIn(val session: SessionStore.Session) : AuthEvent()
}

/**
 * Native reimplementation of script.js's login/signup flow (doLogin(),
 * _authSubmitEmail/_authOtpNext/_authSubmitEmailPassword,
 * onGoogleAuthClick/onGoogleAuthResult, _authRouteAfterIdentity,
 * _authCreateIndividualAccount) — see the plan's "Auth screens" section.
 * Uses the Firebase Auth SDK (wire-compatible with accounts already
 * created via the WebView's REST calls, since both hit the same Identity
 * Toolkit project) instead of raw REST, and Credential Manager instead of
 * the legacy GoogleSignInClient the WebView's own Google login still uses
 * (kept as-is — see MainActivity.kt) — this is new code with no reason to
 * build on the deprecated API.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AuthEvent>()
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    private fun setLoading(loading: Boolean) {
        _uiState.value = _uiState.value.copy(isLoading = loading, error = if (loading) null else _uiState.value.error)
    }

    private fun setError(message: String) {
        _uiState.value = _uiState.value.copy(isLoading = false, error = message)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, otpError = null)
    }

    // ── Email + password login (doLogin()) ─────────────────────────────────
    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            setError("Enter your email and password")
            return
        }
        viewModelScope.launch {
            setLoading(true)
            try {
                val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
                val user = result.user ?: error("Sign-in failed")
                routeAfterIdentity(user.uid, user.displayName ?: "", user.email ?: email)
            } catch (e: Exception) {
                setError(friendlyAuthError(e))
            }
        }
    }

    // ── Signup wizard: welcome -> email -> OTP -> password ─────────────────
    fun goToSignupWelcome() {
        _uiState.value = _uiState.value.copy(signupStep = SignupStep.WELCOME, error = null, otpError = null)
    }

    fun submitEmail(email: String) {
        if (!Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email.trim())) {
            setError("Enter a valid email address")
            return
        }
        viewModelScope.launch {
            setLoading(true)
            try {
                UserRepository.sendEmailOtp(email.trim())
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = null,
                    pendingEmail = email.trim(),
                    signupStep = SignupStep.OTP
                )
            } catch (e: Exception) {
                setError(e.message ?: "Could not send the verification code")
            }
        }
    }

    fun resendOtp() = submitEmail(_uiState.value.pendingEmail)

    /** Back link on the "Create a password" step (_authRenderWelcomeEmailPassword's
     * back -> showAuthScreen('welcomeEmailOtp')) — just moves the step back, no
     * re-verification or new code sent. */
    fun goToOtpStep() {
        _uiState.value = _uiState.value.copy(signupStep = SignupStep.OTP, error = null, otpError = null)
    }

    fun verifyOtp(code: String) {
        val email = _uiState.value.pendingEmail
        viewModelScope.launch {
            setLoading(true)
            when (val result = UserRepository.verifyEmailOtp(email, code)) {
                is UserRepository.OtpResult.Success ->
                    _uiState.value = _uiState.value.copy(isLoading = false, otpError = null, signupStep = SignupStep.PASSWORD)
                is UserRepository.OtpResult.Failure ->
                    _uiState.value = _uiState.value.copy(isLoading = false, otpError = result.message)
            }
        }
    }

    fun submitPassword(password: String) {
        if (password.length < 6) {
            setError("Password must be at least 6 characters")
            return
        }
        val email = _uiState.value.pendingEmail
        viewModelScope.launch {
            setLoading(true)
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user ?: error("Sign-up failed")
                routeAfterIdentity(user.uid, "", email)
            } catch (e: Exception) {
                setError(friendlyAuthError(e))
            }
        }
    }

    // ── Continue with Google (Credential Manager) ───────────────────────────
    fun signInWithGoogle() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            setLoading(true)
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                val response = CredentialManager.create(context).getCredential(context, request)
                val credential = response.credential
                if (credential !is CustomCredential ||
                    credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    setError("Google did not return a usable credential")
                    return@launch
                }
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
                val result = auth.signInWithCredential(firebaseCredential).await()
                val user = result.user ?: error("Google sign-in failed")
                routeAfterIdentity(
                    uid = user.uid,
                    displayName = googleCredential.displayName ?: user.displayName ?: "",
                    email = googleCredential.id
                )
            } catch (e: Exception) {
                setError(e.message ?: "Google sign-in was cancelled")
            }
        }
    }

    // ── Shared post-identity routing (_authRouteAfterIdentity) ─────────────
    private suspend fun routeAfterIdentity(uid: String, displayName: String, email: String) {
        try {
            val existing = UserRepository.fetchUserRecord(uid)
            val record = existing ?: UserRepository.createIndividualAccount(uid, displayName, email)
            val session = SessionStore.Session(
                uid = record.uid,
                name = record.name.ifBlank { displayName },
                email = record.email.ifBlank { email },
                role = record.role,
                orgId = record.orgId
            )
            SessionStore.save(getApplication(), session)
            _uiState.value = _uiState.value.copy(isLoading = false, error = null)
            _events.emit(AuthEvent.LoggedIn(session))
        } catch (e: Exception) {
            setError(e.message ?: "Could not set up your account")
        }
    }

    private fun friendlyAuthError(e: Exception): String {
        val code = (e as? FirebaseAuthException)?.errorCode
        return when (code) {
            "ERROR_INVALID_EMAIL" -> "That doesn't look like a valid email address"
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> "Incorrect email or password"
            "ERROR_USER_NOT_FOUND" -> "No account found with that email"
            "ERROR_USER_DISABLED" -> "This account has been disabled"
            "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts — try again in a moment"
            "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with that email already exists"
            "ERROR_WEAK_PASSWORD" -> "Password must be at least 6 characters"
            else -> e.localizedMessage ?: "Something went wrong — check your connection and try again"
        }
    }
}
