package com.triangle.app.circle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ConnectSuccessGreen = Color(0xFF22C55E)

/** Second step of the Email connect option — see CircleViewModel.sendEmailInvite()/emailInvite. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectEmailScreen(viewModel: CircleViewModel, onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    val state by viewModel.emailInvite.collectAsState()

    LaunchedEffect(Unit) { viewModel.resetEmailInvite() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect by Email", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            when (val s = state) {
                is EmailInviteUiState.SentToExistingUser, is EmailInviteUiState.EmailInviteSent -> {
                    EmailInviteSuccess(
                        message = if (s is EmailInviteUiState.SentToExistingUser)
                            "They're already on Triangle — we sent them a connection request and an email letting them know."
                        else
                            "They're not on Triangle yet — we emailed them an invite. You'll connect automatically as soon as they sign up with this email."
                    )
                }
                else -> {
                    Text(
                        "Enter their email address. If they already use Triangle, we'll send a connection request — otherwise we'll email them an invite.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = { Text("name@example.com") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
                        isError = s is EmailInviteUiState.Error,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (s is EmailInviteUiState.Error) {
                        Spacer(Modifier.height(6.dp))
                        Text(s.message, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.sendEmailInvite(email) },
                        enabled = email.isNotBlank() && s !is EmailInviteUiState.Sending,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (s is EmailInviteUiState.Sending) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("Send Invite")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmailInviteSuccess(message: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp).background(ConnectSuccessGreen.copy(alpha = 0.1f), RoundedCornerShape(16.dp)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ConnectSuccessGreen, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(10.dp))
        Text("Invite sent!", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
