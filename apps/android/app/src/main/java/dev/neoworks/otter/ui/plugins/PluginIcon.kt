package dev.neoworks.otter.ui.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Plugs

/** A plugin's icon: an image URL, an emoji, or a plug as fallback. Mirrors PluginIcon.svelte. */
@Composable
fun PluginIcon(icon: String?, large: Boolean = false) {
    var boxSize: Dp = 40.dp
    var corner: Dp = 12.dp
    var emojiSize = 18
    if (large) {
        boxSize = 56.dp
        corner = 16.dp
        emojiSize = 24
    }
    Box(
        Modifier.size(boxSize).clip(RoundedCornerShape(corner)).background(Color.White.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null && isUrl(icon)) {
            AsyncImage(icon, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
        } else if (!icon.isNullOrEmpty()) {
            Text(icon, fontSize = emojiSize.sp)
        } else {
            Icon(PhosphorIcons.Regular.Plugs, contentDescription = null, modifier = Modifier.size(20.dp).alpha(0.6f))
        }
    }
}

private fun isUrl(value: String): Boolean {
    return value.startsWith("http://") || value.startsWith("https://") || value.startsWith("/")
}
