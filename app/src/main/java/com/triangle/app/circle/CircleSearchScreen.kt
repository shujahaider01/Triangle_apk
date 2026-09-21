package com.triangle.app.circle

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Send-a-connection-request-by-username screen. Deliberately NOT a global
 * people search: this app can't show anyone's profile who isn't already a
 * connection, so letting someone browse/search the whole user directory
 * would be a dead end at best and a username-enumeration tool at worst.
 * Instead it's the same shape as the old WebView's "Choose Message
 * Recipient" dialog — type an @username, send, done — and the outcome is
 * always the same generic toast (see CircleViewModel.sendConnectionRequestByUsername's
 * doc) whether or not that username exists, is already connected, or is
 * your own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleSearchScreen(viewModel: CircleViewModel, onBack: () -> Unit) {
    var username by remember { mutableStateOf("") }
    val requestState by viewModel.usernameRequest.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add by Username", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Text(
                "Enter their @username to send a connection request.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                placeholder = { Text("Recipient's @username") },
                leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    viewModel.sendConnectionRequestByUsername(username) {
                        Toast.makeText(context, "Your connection request has been sent", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                },
                enabled = username.isNotBlank() && requestState == UsernameRequestState.Idle,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (requestState == UsernameRequestState.Sending) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Send Request")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onBack,
                enabled = requestState == UsernameRequestState.Idle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }
}
