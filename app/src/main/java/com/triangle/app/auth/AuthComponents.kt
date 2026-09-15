package com.triangle.app.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared building blocks for the native Login/Signup screens, ported 1:1
 * from style.css's auth-hero-mode rules (#authScreen.auth-hero-mode, .auth-*
 * classes) and script.js's _authRenderHeader/_authRenderWelcome/_authRenderLogin
 * markup — same purple hero gradient + cream sheet + copy + colors as the
 * WebView screens, so this reads as the same app, just natively built.
 *
 * Two things are deliberately simplified rather than pixel-ported, called
 * out at their own definitions below: the hero illustration (a decorative
 * SVG bar-chart+stars in script.js) and the Google "G" mark (a 4-path SVG)
 * — both recreated with plain Compose primitives in the same brand colors
 * rather than a literal path-for-path port.
 */

val AuthPurple = Color(0xFF8B5CF6)
val AuthPurpleDark = Color(0xFF7C3AED)
val AuthPurpleDarker = Color(0xFF5B21B6)
val AuthCream = Color(0xFFF3EEE4)
val AuthInk = Color(0xFF241738)
val AuthMuted = Color(0xFF6B6459)
val AuthPlaceholder = Color(0xFFA49D8D)
val AuthInputBorder = Color(0xFFE6DFCF)
val AuthGreen = Color(0xFF16A34A)
val AuthError = Color(0xFFDC2626)

private val HeroGradient = Brush.linearGradient(listOf(AuthPurple, AuthPurpleDark, AuthPurpleDarker))

/**
 * Purple hero (tabs + illustration, or — in `compact` mode — just a back
 * link — the OTP/password sub-steps use this) over a cream rounded-top
 * sheet, matching auth-hero-mode / auth-hero-compact in style.css. Weights
 * 58f/42f mirror .auth-header{min-height:58vh} vs the remaining body.
 */
@Composable
fun AuthHeroScaffold(
    activeTab: String? = null,
    onTabSelected: ((String) -> Unit)? = null,
    compact: Boolean = false,
    heroContent: (@Composable ColumnScope.() -> Unit)? = null,
    sheetContent: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize().background(AuthCream)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (compact) Modifier.height(220.dp) else Modifier.weight(58f))
                .background(HeroGradient)
                .padding(top = 28.dp, start = 20.dp, end = 20.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (activeTab != null && onTabSelected != null) {
                AuthTabs(activeTab, onTabSelected)
            }
            heroContent?.let {
                Spacer(Modifier.weight(1f))
                it()
                Spacer(Modifier.weight(1f))
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (compact) Modifier.weight(1f) else Modifier.weight(42f))
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(AuthCream)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = sheetContent
        )
    }
}

@Composable
private fun AuthTabs(active: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .padding(top = 24.dp)
            .fillMaxWidth(0.78f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .padding(4.dp)
    ) {
        listOf("signup" to "Sign Up", "login" to "Login").forEach { (key, label) ->
            val isActive = key == active
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isActive) Color.White else Color.Transparent)
                    .clickable { onSelect(key) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) AuthPurple else Color.White.copy(alpha = 0.75f)
                )
            }
        }
    }
}

/**
 * Simplified stand-in for script.js's _AUTH_HERO_ILLUSTRATION_SVG (an
 * upward bar chart + graduation cap + twinkling stars) — same "Habits /
 * Tasks / Rewards" motif and brand colors (#F472B6/#60A5FA/#F5B324), built
 * from plain rounded boxes instead of a hand-ported multi-path SVG.
 */
@Composable
fun AuthHeroIllustration() {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        HeroBar(56.dp, Color(0xFFF472B6), "Habits")
        HeroBar(84.dp, Color(0xFF60A5FA), "Tasks")
        HeroBar(108.dp, Color(0xFFF5B324), "Rewards", showCap = true)
    }
}

@Composable
private fun HeroBar(barHeight: Dp, color: Color, label: String, showCap: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (showCap) {
            Text("🎓", fontSize = 18.sp)
            Spacer(Modifier.height(2.dp))
        }
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .width(30.dp)
                .height(barHeight)
                .clip(RoundedCornerShape(6.dp))
                .background(color)
        )
    }
}

enum class AuthBtnVariant { GREEN, OUTLINE }

/** Matches .auth-btn / .auth-btn--green in style.css. */
@Composable
fun AuthButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AuthBtnVariant = AuthBtnVariant.OUTLINE,
    enabled: Boolean = true,
    loading: Boolean = false,
    leading: (@Composable () -> Unit)? = null
) {
    val bg = if (variant == AuthBtnVariant.GREEN) AuthGreen else Color.White
    val textColor = if (variant == AuthBtnVariant.GREEN) Color.White else AuthInk
    val borderColor = if (variant == AuthBtnVariant.GREEN) AuthGreen else AuthInputBorder
    val isEnabled = enabled && !loading
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg.copy(alpha = if (isEnabled) 1f else 0.55f))
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(enabled = isEnabled) { onClick() }
            .padding(vertical = 13.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), color = textColor, strokeWidth = 2.dp)
        } else {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(10.dp))
            }
            Text(text, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = textColor)
        }
    }
}

/**
 * Simplified stand-in for script.js's _GOOGLE_G_SVG (Google's real 4-color
 * "G" mark, a 4-path SVG) — a single-color approximation in Google's own
 * blue rather than a hand-ported multi-path vector.
 */
@Composable
fun GoogleGIcon(size: Dp = 18.dp) {
    Text("G", fontSize = (size.value * 0.95f).sp, fontWeight = FontWeight.Black, color = Color(0xFF4285F4))
}

/** Matches .auth-input (+ leading icon / password toggle variants). */
@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = AuthPlaceholder) },
        leadingIcon = leadingIcon?.let { icon -> { Icon(icon, null, tint = AuthPlaceholder) } },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = AuthPlaceholder)
                }
            }
        } else null,
        visualTransformation = if (isPassword && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            errorContainerColor = Color.White,
            focusedBorderColor = AuthPurple,
            unfocusedBorderColor = AuthInputBorder,
            errorBorderColor = AuthError,
            focusedTextColor = AuthInk,
            unfocusedTextColor = AuthInk,
            cursorColor = AuthPurple
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/** Matches .error-msg. */
@Composable
fun AuthErrorMessage(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AuthError.copy(alpha = 0.08f))
            .border(1.dp, AuthError.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.ErrorOutline, null, tint = AuthError, modifier = Modifier.size(15.dp))
        Text(message, fontSize = 13.sp, color = AuthError)
    }
}

/** Matches .auth-divider ("or" between the primary action and Google). */
@Composable
fun AuthDivider() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(AuthInputBorder))
        Text("  OR  ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AuthPlaceholder)
        Box(Modifier.weight(1f).height(1.dp).background(AuthInputBorder))
    }
}

/** Matches .auth-otp-row / .auth-otp-box — 6 individual digit boxes. */
@Composable
fun OtpBoxRow(digits: List<String>, onDigitChange: (index: Int, value: String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        digits.forEachIndexed { i, digit ->
            OutlinedTextField(
                value = digit,
                onValueChange = { new -> onDigitChange(i, new.filter { it.isDigit() }.take(1)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AuthInk,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = AuthPurple,
                    unfocusedBorderColor = AuthInputBorder
                ),
                modifier = Modifier.width(44.dp).height(56.dp)
            )
        }
    }
}

/** Matches .auth-link-row--back ("‹ Back" above the OTP/password sub-steps). */
@Composable
fun AuthBackLink(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = AuthMuted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text("Back", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = AuthMuted)
    }
}
