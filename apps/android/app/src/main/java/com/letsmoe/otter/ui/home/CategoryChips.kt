package com.letsmoe.otter.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.CaretDown
import com.adamglin.phosphoricons.regular.X
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.letsmoe.otter.ui.components.frostedGlass
import com.letsmoe.otter.ui.components.pressable

private val chipShape = CircleShape

/**
 * Pill chips: Movies, TV Shows and a Categories chip that opens the genre sheet. With a genre
 * picked the last chip shows its name and an X to clear it. Padding sits inside the scroll so
 * chips and their glass shadow reach the screen edge unclipped.
 */
@Composable
fun CategoryChips(
    selected: HomeCategory,
    selectedGenre: String?,
    onSelect: (HomeCategory) -> Unit,
    onOpenCategories: () -> Unit,
    onClearGenre: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (category in HomeCategory.entries) {
            CategoryChip(category.label, category == selected, null, { onSelect(category) })
        }
        if (selectedGenre == null) {
            CategoryChip("Categories", false, PhosphorIcons.Regular.CaretDown, onOpenCategories)
            return@Row
        }
        CategoryChip(selectedGenre, true, PhosphorIcons.Regular.X, onClearGenre)
    }
}

@Composable
private fun CategoryChip(label: String, isSelected: Boolean, trailingIcon: ImageVector?, onClick: () -> Unit) {
    val style = chipStyle(isSelected)
    Row(
        Modifier
            .pressable(onClick = onClick)
            .height(36.dp)
            .frostedGlass(chipShape)
            .clip(chipShape)
            .background(style.highlight)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = style.textColor, fontSize = 15.sp, fontWeight = style.fontWeight)
        if (trailingIcon != null) {
            Icon(trailingIcon, contentDescription = null, tint = style.textColor, modifier = Modifier.size(14.dp))
        }
    }
}

private class ChipStyle(val highlight: Color, val textColor: Color, val fontWeight: FontWeight)

@Composable
private fun chipStyle(isSelected: Boolean): ChipStyle {
    var targetHighlight = Color.Transparent
    var targetText = Color.White.copy(alpha = 0.75f)
    var fontWeight = FontWeight.Medium
    if (isSelected) {
        targetHighlight = Color.White.copy(alpha = 0.16f)
        targetText = Color.White
        fontWeight = FontWeight.SemiBold
    }
    val highlight by animateColorAsState(targetHighlight, tween(220), label = "chipHighlight")
    val textColor by animateColorAsState(targetText, tween(220), label = "chipText")
    return ChipStyle(highlight, textColor, fontWeight)
}
