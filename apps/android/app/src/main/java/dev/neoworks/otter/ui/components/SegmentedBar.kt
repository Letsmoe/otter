package dev.neoworks.otter.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.neoworks.otter.ui.theme.OtterColors

private val barPadding = 4.dp
private val optionHeight = 36.dp

/** Glass bar with a sliding pill, styled like the tab bar. */
@Composable
fun <Value> SegmentedBar(
    options: List<Pair<Value, String>>,
    selected: Value,
    onSelect: (Value) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = options.indexOfFirst { option -> option.first == selected }.coerceAtLeast(0)
    BoxWithConstraints(modifier.fillMaxWidth().frostedGlass(CircleShape).padding(barPadding)) {
        val optionWidth = maxWidth / options.size
        val pillOffset by animateDpAsState(
            targetValue = optionWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow),
            label = "segmentPill",
        )
        Box(
            Modifier
                .offset(x = pillOffset)
                .width(optionWidth)
                .height(optionHeight)
                .clip(CircleShape)
                .background(OtterColors.baseContent.copy(alpha = 0.1f)),
        )
        Row {
            for (option in options) {
                SegmentLabel(option.second, option.first == selected, Modifier.width(optionWidth)) { onSelect(option.first) }
            }
        }
    }
}

@Composable
private fun SegmentLabel(label: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var color = OtterColors.baseContent.copy(alpha = 0.6f)
    var fontWeight = FontWeight.Normal
    if (isSelected) {
        color = OtterColors.baseContent
        fontWeight = FontWeight.SemiBold
    }
    Box(
        modifier = modifier
            .height(optionHeight)
            .clip(CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, fontSize = 13.sp, fontWeight = fontWeight, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}
