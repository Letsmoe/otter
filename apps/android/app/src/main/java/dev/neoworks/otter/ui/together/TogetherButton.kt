package dev.neoworks.otter.ui.together

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.UsersThree
import dev.neoworks.otter.together.TogetherSession
import dev.neoworks.otter.together.isOpen
import dev.neoworks.otter.ui.components.frostedGlass

/** Header shortcut to Watch Together; a green dot shows while a room is open. Mirrors TogetherButton.svelte. */
@Composable
fun TogetherButton(modifier: Modifier = Modifier, glass: Boolean = false, iconSize: Dp = 22.dp) {
    val room by TogetherSession.room.collectAsStateWithLifecycle()
    var shape = modifier.size(40.dp).clip(CircleShape)
    if (glass) {
        shape = modifier.size(40.dp).frostedGlass(CircleShape)
    }
    Box(shape.clickable { TogetherSession.openSheet() }, contentAlignment = Alignment.Center) {
        Icon(PhosphorIcons.Regular.UsersThree, contentDescription = "Watch Together", modifier = Modifier.size(iconSize))
        if (room.isOpen()) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-6).dp, y = 6.dp)
                    .size(8.dp)
                    .border(2.dp, Color.Black, CircleShape)
                    .background(Color(0xFF4ADE80), CircleShape),
            )
        }
    }
}
