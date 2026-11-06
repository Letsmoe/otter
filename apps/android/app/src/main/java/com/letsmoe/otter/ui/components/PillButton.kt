package com.letsmoe.otter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A rounded text button: filled for the main action, plain for the secondary one. */
@Composable
fun PillButton(label: String, modifier: Modifier, enabled: Boolean, filled: Boolean, onClick: () -> Unit) {
    var fill = Color.Transparent
    var labelColor = Color.White.copy(alpha = 0.7f)
    var fontWeight = FontWeight.Normal
    if (filled) {
        fill = Color.White.copy(alpha = 0.1f)
        labelColor = Color.White
        fontWeight = FontWeight.SemiBold
    }
    var alpha = 1f
    if (!enabled) {
        alpha = 0.5f
    }
    Row(
        modifier.alpha(alpha).background(fill, CircleShape).clickable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = fontWeight, color = labelColor)
    }
}
