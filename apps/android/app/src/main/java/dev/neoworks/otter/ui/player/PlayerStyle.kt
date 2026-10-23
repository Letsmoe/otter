package dev.neoworks.otter.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.neoworks.otter.ui.theme.OtterColors

internal val playerGlassFill = OtterColors.base200.copy(alpha = 0.78f)
internal val playerRimColor = Color.White.copy(alpha = 0.12f)

/** The player's glass: video can't be blurred behind it, so it is a translucent fill with the web app's rim. */
internal fun Modifier.playerGlass(shape: Shape): Modifier {
    return this.clip(shape).background(playerGlassFill).border(1.dp, playerRimColor, shape)
}

/** A tap target without a ripple, for areas the player listens on. */
@Composable
internal fun Modifier.clickableWithoutRipple(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    return this.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick)
}

/** Round glass button with an icon. */
@Composable
internal fun GlassIconButton(icon: ImageVector, description: String, size: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(size).playerGlass(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(size * 0.5f))
    }
}
