package com.triangle.app.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.CategoryRepository
import com.triangle.app.data.HabitPalette
import com.triangle.app.data.SessionStore
import com.triangle.app.data.TaskCategory
import com.triangle.app.tasks.FormTextField
import com.triangle.app.tasks.IconColorPicker
import com.triangle.app.tasks.formPageColor
import com.triangle.app.ui.SvgPathIcon
import com.triangle.app.ui.theme.TriangleBrandPurple
import com.triangle.app.util.capFirst
import kotlinx.coroutines.launch

/**
 * Settings > Categories: the categories offered for Solo tasks and habits. Tap a category to edit or delete it;
 * "Add Category" opens a sheet with a name, a colour and an icon.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(session: SessionStore.Session, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val categories by CategoryRepository.categoriesFlow(session.orgId).collectAsState(initial = CategoryRepository.DEFAULTS)
    // null = sheet closed; a TaskCategory with a blank name = adding; otherwise editing that one.
    var editing by remember { mutableStateOf<TaskCategory?>(null) }

    Box(Modifier.fillMaxSize().background(formPageColor())) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ShapesLogo()
                Spacer(Modifier.height(10.dp))
                Text("Categories", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(44.dp))
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                categories.forEach { c -> CategoryChip(c) { editing = c } }
            }
            Spacer(Modifier.height(120.dp))
        }

        Button(
            onClick = { editing = TaskCategory(id = "", name = "", color = HabitPalette.DEFAULT_COLOR, iconKey = HabitPalette.DEFAULT_ICON_KEY) },
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = TriangleBrandPurple, contentColor = Color.White),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 26.dp, vertical = 16.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 28.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add Category", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }

    editing?.let { current ->
        val isNew = current.id.isEmpty()
        var name by remember(current.id) { mutableStateOf(current.name) }
        var color by remember(current.id) { mutableStateOf(current.color) }
        var iconKey by remember(current.id) { mutableStateOf(current.iconKey) }
        val duplicate = categories.any { it.id != current.id && it.name.equals(name.trim(), ignoreCase = true) }

        ModalBottomSheet(onDismissRequest = { editing = null }, containerColor = formPageColor()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    if (isNew) "Add New" else "Edit Category",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                FormTextField(
                    value = name,
                    onValueChange = { name = it.take(30).capFirst() },
                    placeholder = "Enter Category Name",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
                if (duplicate) Text("A category with this name already exists", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                IconColorPicker(
                    selectedColor = color,
                    selectedIconKey = iconKey,
                    onColorSelected = { color = it },
                    onIconSelected = { key, _ -> iconKey = key }
                )
                Button(
                    onClick = {
                        val saved = current.copy(id = if (isNew) "cat-${System.currentTimeMillis()}" else current.id, name = name.trim(), color = color, iconKey = iconKey)
                        val next = if (isNew) categories + saved else categories.map { if (it.id == current.id) saved else it }
                        scope.launch { runCatching { CategoryRepository.save(session.orgId, next) } }
                        editing = null
                    },
                    enabled = name.isNotBlank() && !duplicate,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = TriangleBrandPurple, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) { Text(if (isNew) "Add New" else "Save", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                if (!isNew) {
                    TextButton(
                        onClick = {
                            scope.launch { runCatching { CategoryRepository.save(session.orgId, categories.filter { it.id != current.id }) } }
                            editing = null
                        },
                        enabled = categories.size > 1, // keep at least one to choose from
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (categories.size > 1) "Delete category" else "At least one category is needed",
                            color = if (categories.size > 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** One category pill: tinted in its own colour with a matching border, its icon and its name. */
@Composable
private fun CategoryChip(c: TaskCategory, onClick: () -> Unit) {
    val tint = runCatching { Color(android.graphics.Color.parseColor(c.color)) }.getOrDefault(TriangleBrandPurple)
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .clip(shape)
            .background(tint.copy(alpha = 0.22f))
            .border(1.5.dp, tint.copy(alpha = 0.45f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SvgPathIcon(HabitPalette.ICONS[c.iconKey] ?: HabitPalette.ICONS.getValue(HabitPalette.DEFAULT_ICON_KEY), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f), size = 22.dp)
        Spacer(Modifier.width(12.dp))
        Text(c.name, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** The small triangle / square / circle mark above the title. */
@Composable
private fun ShapesLogo() {
    val gold = Color(0xFFFFD60A)
    Canvas(Modifier.size(width = 56.dp, height = 56.dp)) {
        val w = size.width
        val h = size.height
        // Triangle on top.
        val tri = Path().apply {
            moveTo(w * 0.5f, 0f); lineTo(w * 0.78f, h * 0.45f); lineTo(w * 0.22f, h * 0.45f); close()
        }
        drawPath(tri, gold)
        // Square bottom-left, circle bottom-right.
        drawRect(gold, topLeft = Offset(0f, h * 0.55f), size = androidx.compose.ui.geometry.Size(w * 0.46f, h * 0.45f))
        drawCircle(gold, radius = h * 0.225f, center = Offset(w * 0.77f, h * 0.775f))
    }
}
