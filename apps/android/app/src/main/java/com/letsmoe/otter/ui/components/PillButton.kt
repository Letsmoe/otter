package com.letsmoe.otter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A rounded text button that bounces when pressed: filled (white) for the main action, framed for the secondary one. */
@Composable
fun PillButton(label: String, modifier: Modifier, enabled: Boolean, filled: Boolean, onClick: () -> Unit) {
    var fill = Color.White.copy(alpha = 0.08f)
    var frame = Color.White.copy(alpha = 0.18f)
    var labelColor = Color.White
    var fontWeight = FontWeight.Medium
    if (filled) {
        fill = Color.White
        frame = Color.White
        labelColor = Color.Black
        fontWeight = FontWeight.SemiBold
    }
    var alpha = 1f
    if (!enabled) {
        alpha = 0.5f
    }
    Row(
        modifier
            .pressable(enabled = enabled, onClick = onClick)
            .alpha(alpha)
            .background(fill, CircleShape)
            .border(1.dp, frame, CircleShape)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // One line even at large font sizes: a wrapped label doubles the pill's height.
        Text(label, fontSize = 14.sp, fontWeight = fontWeight, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
