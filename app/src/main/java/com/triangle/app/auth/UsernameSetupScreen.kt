package com.triangle.app.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.triangle.app.data.SessionStore
import com.triangle.app.data.UsernameRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Mandatory "choose your username" step — AppNavHost routes here instead of
 * Dashboard for any account whose Session.username is still null (new
 * signups, and existing pre-this-feature accounts the first time they open
 * the app). Usernames are how people find and connect with each other in
 * the Connect->Assign->Complete->Earn model, so this can't be skipped or
 * deferred the way profile-photo upload can.
 */
@Composable
fun UsernameSetupScreen(
    session: SessionStore.Session,
    onUsernameSet: (SessionStore.Session) -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var available by remember { mutableStateOf<Boolean?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Debounced live availability check as the user types.
    LaunchedEffect(username) {
        available = null
        if (UsernameRepository.formatError(username) != null) return@LaunchedEffect
        checking = true
        delay(400)
        available = runCatching { UsernameRepository.isAvailable(username) }.getOrNull()
        checking = false
    }

    fun save() {
        if (saving) return
        val formatError = UsernameRepository.formatError(username)
        if (formatError != null) {
            error = formatError
            return
        }
        saving = true
        error = null
        scope.launch {
            when (val result = UsernameRepository.claim(session.uid, username)) {
                is UsernameRepository.ClaimResult.Success -> {
                    val updated = session.copy(username = UsernameRepository.normalize(username))
                    SessionStore.save(context, updated)
                    saving = false
                    onUsernameSet(updated)
                }
                is UsernameRepository.ClaimResult.Taken -> {
                    saving = false
                    available = false
                    error = "That username is already taken"
                }
                is UsernameRepository.ClaimResult.Error -> {
                    saving = false
                    error = result.message
                }
            }
        }
    }

    AuthHeroScaffold(
        compact = true,
        heroContent = {
            Text("Almost there!", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(
                "Pick a username — it's how people find and connect with you",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    ) {
        AuthTextField(
            value = username,
            onValueChange = { username = it.take(20); error = null },
            placeholder = "username",
            leadingIcon = Icons.Default.AlternateEmail,
            keyboardType = KeyboardType.Text,
            isError = error != null || available == false
        )
        Spacer(Modifier.height(8.dp))
        val hint = when {
            error != null -> error
            username.isBlank() -> null
            checking -> "Checking availability…"
            available == true -> "@${UsernameRepository.normalize(username)} is available"
            available == false -> "That username is already taken"
            else -> null
        }
        hint?.let {
            Text(it, fontSize = 12.5.sp, color = if (error != null || available == false) AuthError else AuthMuted)
            Spacer(Modifier.height(8.dp))
        }

        AuthButton(
            text = "Continue",
            onClick = { save() },
            variant = AuthBtnVariant.GREEN,
            enabled = !saving && !checking && available == true,
            loading = saving
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = {
            scope.launch {
                SessionStore.clear(context)
                FirebaseAuth.getInstance().signOut()
                onSignedOut()
            }
        }) {
            Text("Sign out", color = AuthMuted, fontSize = 13.sp)
        }
    }
}
