package com.triangle.app.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.triangle.app.data.SessionStore

/**
 * Native port of script.js's 4-step signup wizard: welcome (Google / email
 * accordion) -> OTP -> set password (_authRenderWelcome ->
 * _authRenderWelcomeEmailOtp -> _authRenderWelcomeEmailPassword). Same
 * purple hero + cream sheet as Login on the welcome step; the OTP/password
 * sub-steps switch to a compact hero with no tabs/illustration, matching
 * .auth-hero-compact — see AuthHeroScaffold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    onLoggedIn: (SessionStore.Session) -> Unit,
    onGoToLogin: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is AuthEvent.LoggedIn) onLoggedIn(event.session)
        }
    }

    when (state.signupStep) {
        SignupStep.PASSWORD -> AuthHeroScaffold(compact = true) {
            PasswordStep(viewModel, state)
        }
        SignupStep.OTP -> AuthHeroScaffold(compact = true) {
            OtpStep(viewModel, state)
        }
        else -> AuthHeroScaffold(
            activeTab = "signup",
            onTabSelected = { tab -> if (tab == "login") onGoToLogin() },
            heroContent = { AuthHeroIllustration() }
        ) {
            WelcomeStep(viewModel, state)
        }
    }
}

@Composable
private fun WelcomeStep(viewModel: AuthViewModel, state: AuthUiState) {
    var emailPanelOpen by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }

    Text(
        buildAnnotatedTitle(),
        fontSize = 22.sp,
        fontWeight = FontWeight.Black,
        color = AuthInk
    )
    Spacer(Modifier.height(4.dp))
    Text("Learn. Grow. Achieve.", fontSize = 14.sp, color = AuthMuted)
    Spacer(Modifier.height(24.dp))

    AuthButton(
        text = "Continue with Google",
        onClick = { viewModel.signInWithGoogle() },
        enabled = !state.isLoading,
        leading = { GoogleGIcon() }
    )
    AuthButton(
        text = "Continue with email",
        onClick = { emailPanelOpen = !emailPanelOpen },
        enabled = !state.isLoading,
        leading = { Icon(Icons.Default.Email, null, tint = AuthInk, modifier = Modifier.size(18.dp)) }
    )

    AnimatedVisibility(visible = emailPanelOpen) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth()) {
                AuthTextField(
                    value = email,
                    onValueChange = { email = it; viewModel.clearError() },
                    placeholder = "your@email.com",
                    keyboardType = KeyboardType.Email
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 5.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AuthPurple)
                        .clickable(enabled = !state.isLoading) { viewModel.submitEmail(email) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Continue", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(15.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("We'll send you a code to sign in", fontSize = 12.sp, color = AuthMuted)
        }
    }

    state.error?.let {
        Spacer(Modifier.height(10.dp))
        AuthErrorMessage(it, modifier = Modifier.fillMaxWidth())
    }
}

private fun buildAnnotatedTitle() = buildAnnotatedString {
    append("Welcome to ")
    withStyle(SpanStyle(color = AuthPurple)) { append("Triangle") }
}

@Composable
private fun OtpStep(viewModel: AuthViewModel, state: AuthUiState) {
    var digits by remember { mutableStateOf(List(6) { "" }) }
    val code = digits.joinToString("")

    AuthBackLink(onClick = { viewModel.goToSignupWelcome() })
    AuthIconCircle(Icons.Default.Email)
    Spacer(Modifier.height(2.dp))
    Text("Enter your code", fontSize = 22.sp, fontWeight = FontWeight.Black, color = AuthInk)
    Spacer(Modifier.height(4.dp))
    Text(
        "We sent a 6-digit code to ${state.pendingEmail}",
        fontSize = 14.sp,
        color = AuthMuted,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(18.dp))

    OtpBoxRow(digits) { i, value ->
        digits = digits.toMutableList().also { it[i] = value }
    }

    state.otpError?.let {
        Spacer(Modifier.height(12.dp))
        AuthErrorMessage(it, modifier = Modifier.fillMaxWidth())
    }

    Spacer(Modifier.height(14.dp))
    TextButton(onClick = { viewModel.resendOtp() }, enabled = !state.isLoading) {
        Text("Resend code", color = AuthPurple, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
    }

    if (code.length == 6) {
        Spacer(Modifier.height(6.dp))
        AuthButton(
            text = "Next",
            onClick = { viewModel.verifyOtp(code) },
            variant = AuthBtnVariant.GREEN,
            enabled = !state.isLoading,
            loading = state.isLoading
        )
    }
}

@Composable
private fun PasswordStep(viewModel: AuthViewModel, state: AuthUiState) {
    var password by remember { mutableStateOf("") }

    AuthBackLink(onClick = { viewModel.goToOtpStep() })
    Text("Create a password", fontSize = 22.sp, fontWeight = FontWeight.Black, color = AuthInk)
    Spacer(Modifier.height(4.dp))
    Text(
        "Almost there — set a password for ${state.pendingEmail}",
        fontSize = 14.sp,
        color = AuthMuted,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(24.dp))

    AuthTextField(
        value = password,
        onValueChange = { password = it; viewModel.clearError() },
        placeholder = "Password (min. 6 characters)",
        leadingIcon = Icons.Default.Lock,
        isPassword = true,
        keyboardType = KeyboardType.Password
    )

    state.error?.let {
        Spacer(Modifier.height(10.dp))
        AuthErrorMessage(it, modifier = Modifier.fillMaxWidth())
    }

    Spacer(Modifier.height(16.dp))
    AuthButton(
        text = "Set Password",
        onClick = { viewModel.submitPassword(password) },
        variant = AuthBtnVariant.GREEN,
        enabled = !state.isLoading,
        loading = state.isLoading
    )
}

@Composable
private fun AuthIconCircle(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        Modifier.size(76.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(0xFFF1EBFE)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = AuthPurple, modifier = Modifier.size(34.dp))
    }
}
