package com.triangle.app.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.ui.theme.TriangleBrandPurple
import com.triangle.app.ui.theme.triangleDarkTheme

/**
 * Shared look for the Create/Edit Task and Habit forms: a light-gray page,
 * white rounded cards with a thin tinted border for every section, outlined
 * placeholder-style text fields, and outlined switches. Every section of
 * those forms is one [FormCard] so they read as separate, clean blocks.
 */
@Composable
fun formPageColor(): Color = if (triangleDarkTheme()) Color(0xFF101014) else Color(0xFFF6F6F8)

@Composable
fun formCardColor(): Color = if (triangleDarkTheme()) Color(0xFF1B1B22) else Color.White

@Composable
fun formCardBorder(): Color = if (triangleDarkTheme()) Color(0xFF2E2E3A) else Color(0xFFE2E2EE)

private val FormShape = RoundedCornerShape(16.dp)

/** One white, rounded, thinly-bordered section card. */
@Composable
fun FormCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = FormShape,
        color = formCardColor(),
        border = BorderStroke(1.dp, formCardBorder())
    ) {
        Column(
            Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            content = content
        )
    }
}

/** Bold section heading used inside a [FormCard]. */
@Composable
fun FormCardTitle(text: String) {
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
}

/** Outlined, placeholder-style text field (no floating label), dark border, rounded. */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)) },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = if (singleLine) keyboardOptions.copy(imeAction = androidx.compose.ui.text.input.ImeAction.Done) else keyboardOptions,
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focus.clearFocus() }),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = formCardColor(),
            unfocusedContainerColor = formCardColor(),
            focusedBorderColor = MaterialTheme.colorScheme.onSurface,
            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/** Outlined switch: dark outline + dark thumb when off, brand-tinted when on. */
@Composable
fun FormSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
            uncheckedTrackColor = formPageColor(),
            uncheckedBorderColor = MaterialTheme.colorScheme.onSurface,
            checkedThumbColor = Color.White,
            checkedTrackColor = TriangleBrandPurple,
            checkedBorderColor = TriangleBrandPurple
        )
    )
}

/** A card with a title and a switch; [content] (the details) is shown only while the switch is on. */
@Composable
fun FormToggleCard(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    showSwitch: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    FormCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FormCardTitle(title)
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (showSwitch) FormSwitch(checked, onCheckedChange)
        }
        if (checked && content != null) {
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** Vertical gap between form sections. */
val FormSectionSpacing = Arrangement.spacedBy(20.dp)

/** Filled, rounded Save button for the top bar of the Create/Edit forms. */
@Composable
fun FormSaveButton(saving: Boolean, enabled: Boolean, onClick: () -> Unit, label: String = "Save") {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled && !saving,
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = TriangleBrandPurple,
            contentColor = Color.White
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        modifier = Modifier.padding(end = 12.dp)
    ) {
        if (saving) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White
            )
            Spacer(Modifier.width(8.dp))
            Text("Saving…", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        } else {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Scrolling for a form page that also drops text focus (and so the keyboard)
 * when the user taps empty space or starts scrolling.
 */
@Composable
fun Modifier.formScroll(): Modifier {
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val state = androidx.compose.foundation.rememberScrollState()
    androidx.compose.runtime.LaunchedEffect(state) {
        androidx.compose.runtime.snapshotFlow { state.isScrollInProgress }.collect { if (it) focus.clearFocus() }
    }
    return this
        .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } }
        .verticalScroll(state)
}
