package com.triangle.app.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triangle.app.data.HabitPalette
import com.triangle.app.ui.SvgPathIcon

/**
 * Matches script.js's create-task/create-habit color+icon row
 * (.ct-icon-color-row) — two preview chips that toggle a swatch strip /
 * icon grid below. Shared by CreateEditTaskScreen and CreateEditHabitScreen.
 */
@Composable
fun IconColorPicker(
    selectedColor: String,
    selectedIconKey: String,
    onColorSelected: (String) -> Unit,
    onIconSelected: (key: String, svg: String) -> Unit
) {
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    var showColors by remember { mutableStateOf(false) }
    var showIcons by remember { mutableStateOf(false) }
    val selectedSvg = HabitPalette.ICONS[selectedIconKey] ?: HabitPalette.ICONS.getValue(HabitPalette.DEFAULT_ICON_KEY)
    val selectedTint = runCatching { Color(android.graphics.Color.parseColor(selectedColor)) }.getOrDefault(Color.Gray)

    androidx.compose.foundation.layout.Column {
        // Two equal columns: a white rounded chip with its label to the right.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clickable { focus.clearFocus(); showColors = !showColors; if (showColors) showIcons = false },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(formCardColor()),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(selectedTint))
                }
                Spacer(Modifier.width(14.dp))
                Text("Color", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Row(
                Modifier.weight(1f).clickable { focus.clearFocus(); showIcons = !showIcons; if (showIcons) showColors = false },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(formCardColor()),
                    contentAlignment = Alignment.Center
                ) {
                    SvgPathIcon(selectedSvg, tint = MaterialTheme.colorScheme.onSurface, size = 26.dp)
                }
                Spacer(Modifier.width(14.dp))
                Text("Icon", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        if (showColors) {
            Spacer(Modifier.height(12.dp))
            FormCard {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(HabitPalette.COLORS) { hex ->
                        val color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)
                        val selected = hex.equals(selectedColor, ignoreCase = true)
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { onColorSelected(hex) }
                        )
                    }
                }
            }
        }

        if (showIcons) {
            Spacer(Modifier.height(12.dp))
            FormCard {
                // 3 full rows plus half of the 4th so it is obvious the grid scrolls (cell size follows the available width).
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cell = (maxWidth - 8.dp * 6) / 7
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().height(cell * 3.5f + 8.dp * 3)
                    ) {
                        items(HabitPalette.ICONS.entries.toList()) { (key, svg) ->
                            val selected = key == selectedIconKey
                            Box(
                                Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) selectedTint else Color.Transparent)
                                    .clickable { onIconSelected(key, svg) },
                                contentAlignment = Alignment.Center
                            ) {
                                SvgPathIcon(svg, tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, size = 20.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}
