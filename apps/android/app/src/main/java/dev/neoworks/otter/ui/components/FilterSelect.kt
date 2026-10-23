package dev.neoworks.otter.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.CaretDown
import com.adamglin.phosphoricons.regular.Check
import dev.neoworks.otter.ui.theme.OtterColors

/**
 * Glass pill showing "Label: choice" that opens a glass dropdown. Filters dim while on their
 * default (the first option); a plain chooser (`showLabel = false`) always reads as set.
 */
@Composable
fun <Value> FilterSelect(
    label: String,
    options: List<Pair<Value, String>>,
    selected: Value,
    onSelect: (Value) -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { option -> option.first == selected }?.second
    val isActive = !showLabel || selected != options.first().first
    var textColor = OtterColors.baseContent.copy(alpha = 0.6f)
    var fontWeight = FontWeight.Normal
    if (isActive) {
        textColor = OtterColors.baseContent
        fontWeight = FontWeight.SemiBold
    }
    var text = selectedLabel.orEmpty()
    if (showLabel) {
        text = "$label: $text"
    }
    Box(modifier) {
        Row(
            Modifier
                .height(36.dp)
                .frostedGlass(CircleShape)
                .clickable { open = true }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, color = textColor, fontSize = 13.sp, fontWeight = fontWeight)
            Icon(PhosphorIcons.Regular.CaretDown, null, tint = textColor, modifier = Modifier.padding(start = 6.dp).size(14.dp))
        }
        FilterMenu(open, { open = false }, options, selected) { value ->
            open = false
            onSelect(value)
        }
    }
}

@Composable
private fun <Value> FilterMenu(
    open: Boolean,
    onDismiss: () -> Unit,
    options: List<Pair<Value, String>>,
    selected: Value,
    onSelect: (Value) -> Unit,
) {
    DropdownMenu(
        expanded = open,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = GlassColor,
        border = BorderStroke(1.dp, OtterColors.baseContent.copy(alpha = 0.1f)),
    ) {
        for (option in options) {
            val isSelected = option.first == selected
            DropdownMenuItem(
                text = { Text(option.second, fontWeight = menuWeight(isSelected)) },
                trailingIcon = { if (isSelected) Icon(PhosphorIcons.Regular.Check, null, Modifier.size(16.dp)) },
                onClick = { onSelect(option.first) },
            )
        }
    }
}

private fun menuWeight(isSelected: Boolean): FontWeight {
    if (isSelected) {
        return FontWeight.SemiBold
    }
    return FontWeight.Normal
}
