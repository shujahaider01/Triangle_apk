package com.triangle.app.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.triangle.app.data.SessionStore

/**
 * Native port of script.js's _authRenderLogin() — same purple hero + cream
 * sheet as Sign Up (see AuthHeroScaffold), no title/subtitle on this step
 * (matches the original's own comment: the hero above already establishes
 * "this is Triangle").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoggedIn: (SessionStore.Session) -> Unit,
    onGoToSignup: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is AuthEvent.LoggedIn) onLoggedIn(event.session)
        }
    }

    AuthHeroScaffold(
        activeTab = "login",
        onTabSelected = { tab -> if (tab == "signup") onGoToSignup() },
        heroContent = { AuthHeroIllustration() }
    ) {
        AuthTextField(
            value = username,
            onValueChange = { username = it; viewModel.clearError() },
            placeholder = "Username or email",
            leadingIcon = Icons.Default.Person,
            keyboardType = KeyboardType.Email
        )
        Spacer(Modifier.height(10.dp))
        AuthTextField(
            value = password,
            onValueChange = { password = it; viewModel.clearError() },
            placeholder = "Password",
            leadingIcon = Icons.Default.Lock,
            isPassword = true,
            keyboardType = KeyboardType.Password
        )
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
            TextButton(onClick = { /* password reset coming soon, matches WebView */ }) {
                Text("Forgot Password?", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = AuthPlaceholder)
            }
        }
        Spacer(Modifier.height(4.dp))

        state.error?.let {
            AuthErrorMessage(it, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }

        AuthButton(
            text = "Login",
            onClick = { viewModel.login(username, password) },
            variant = AuthBtnVariant.GREEN,
            enabled = !state.isLoading,
            loading = state.isLoading
        )
        Spacer(Modifier.height(12.dp))
        AuthDivider()
        Spacer(Modifier.height(12.dp))
        AuthButton(
            text = "Continue with Google",
            onClick = { viewModel.signInWithGoogle() },
            enabled = !state.isLoading,
            leading = { GoogleGIcon() }
        )

        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Don't have an account? ", fontSize = 13.5.sp, color = AuthMuted)
            TextButton(onClick = onGoToSignup) { Text("Sign Up", fontWeight = FontWeight.Bold, color = AuthPurple) }
        }
    }
}
