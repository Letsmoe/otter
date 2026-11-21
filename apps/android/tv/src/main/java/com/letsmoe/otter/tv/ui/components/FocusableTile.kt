package com.letsmoe.otter.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.theme.TvType

/**
 * A focusable surface for the D-pad: grows and gets a white ring while focused.
 * Every clickable thing on the TV goes through this, so focus looks the same everywhere.
 */
@Composable
fun FocusableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    focusedScale: Float = 1.08f,
    containerColor: Color = TvColors.subtleFill,
    focusedContainerColor: Color = Color.White.copy(alpha = 0.16f),
    ringWidth: Dp = 2.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            contentColor = TvColors.baseContent,
            focusedContainerColor = focusedContainerColor,
            focusedContentColor = TvColors.baseContent,
            pressedContainerColor = focusedContainerColor,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(ringWidth, Color.White), inset = 1.dp, shape = shape),
        ),
    ) {
        content()
    }
}

/** A pill button with an optional icon; [primary] is white on black for the main action of a screen. */
@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    var containerColor = TvColors.subtleFill
    var contentColor = TvColors.baseContent
    if (primary) {
        containerColor = Color.White.copy(alpha = 0.9f)
        contentColor = Color.Black
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            contentColor = contentColor,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
            pressedContainerColor = Color.White,
            pressedContentColor = Color.Black,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
            }
            Text(label, fontSize = TvType.detail, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** A message in the middle of the screen with a way to try again. */
@Composable
fun FailedNotice(title: String, message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, fontSize = TvType.screenTitle, fontWeight = FontWeight.SemiBold, color = TvColors.baseContent)
        if (message.isNotEmpty()) {
            Text(message, fontSize = TvType.body, color = TvColors.secondaryContent)
        }
        Box(Modifier.padding(top = 6.dp)) {
            PillButton("Try again", onClick = onRetry, primary = true)
        }
    }
}
