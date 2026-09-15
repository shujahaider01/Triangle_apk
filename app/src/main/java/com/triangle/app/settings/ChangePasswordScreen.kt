package com.triangle.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.UserRepository
import kotlinx.coroutines.launch

/**
 * Native replacement for doChangePassword() (script.js:6168-6191) — uses
 * real Firebase Auth reauthenticate+updatePassword via
 * UserRepository.changePassword(), not the source's insecure SHA-256-in-RTDB
 * scheme. See the Milestone 6 plan's Architectural Decision #2.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(onDone: () -> Unit, onBack: () -> Unit) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun save() {
        error = null
        if (currentPassword.isBlank() || newPassword.isBlank()) {
            error = "Fill in both password fields"
            return
        }
        if (newPassword.length < 6) {
            error = "New password must be at least 6 characters"
            return
        }
        if (newPassword != confirmPassword) {
            error = "New passwords don't match"
            return
        }
        saving = true
        scope.launch {
            val result = UserRepository.changePassword(currentPassword, newPassword)
            saving = false
            result.fold(
                onSuccess = { onDone() },
                onFailure = { e -> error = e.message ?: "Could not change your password" }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Change Password") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = currentPassword,
                onValueChange = { currentPassword = it },
                label = { Text("Current password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("New password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm new password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            if (error != null) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            Button(
                onClick = { save() },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(if (saving) "Saving..." else "Save")
            }
        }
    }
}
