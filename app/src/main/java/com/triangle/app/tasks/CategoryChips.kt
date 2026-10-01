package com.triangle.app.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.CategoryRepository
import com.triangle.app.data.HabitPalette
import com.triangle.app.data.TaskCategory
import com.triangle.app.ui.SvgPathIcon

/**
 * The user's own categories (Settings > Categories) as selectable chips, each with its icon and colour.
 * If [selected] isn't one of them (a new item still on the old default, or a category deleted since), a new
 * item jumps to the first category and an existing item keeps its old name as an extra chip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryChips(orgId: String, selected: String, isNew: Boolean, onSelect: (String) -> Unit) {
    val categories by CategoryRepository.categoriesFlow(orgId).collectAsState(initial = CategoryRepository.DEFAULTS)
    LaunchedEffect(categories, selected, isNew) {
        if (isNew && categories.none { it.name == selected }) categories.firstOrNull()?.let { onSelect(it.name) }
    }
    val shown = if (categories.none { it.name == selected } && !isNew && selected.isNotBlank())
        categories + TaskCategory("legacy-$selected", selected, HabitPalette.DEFAULT_COLOR, HabitPalette.DEFAULT_ICON_KEY)
    else categories

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEach { c ->
            val tint = runCatching { Color(android.graphics.Color.parseColor(c.color)) }.getOrDefault(MaterialTheme.colorScheme.primary)
            val active = c.name == selected
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) tint else tint.copy(alpha = 0.16f))
                    .clickable { onSelect(c.name) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SvgPathIcon(
                    HabitPalette.ICONS[c.iconKey] ?: HabitPalette.ICONS.getValue(HabitPalette.DEFAULT_ICON_KEY),
                    tint = if (active) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    size = 16.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(c.name, fontSize = 14.sp, color = if (active) Color.White else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
