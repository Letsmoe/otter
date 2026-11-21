package com.letsmoe.otter.tv.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.theme.TvType

/** A pill that can be picked: seasons, search types and filters, profile languages. */
@Composable
fun SelectableChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var container = TvColors.subtleFill
    var textColor = TvColors.secondaryContent
    if (selected) {
        container = Color.White.copy(alpha = 0.24f)
        textColor = TvColors.baseContent
    }
    FocusableSurface(onClick = onClick, shape = CircleShape, containerColor = container, modifier = modifier) {
        Text(
            label,
            fontSize = TvType.detail,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}
