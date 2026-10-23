package dev.neoworks.otter.ui.title

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowLeft
import com.adamglin.phosphoricons.regular.MagnifyingGlass
import dev.neoworks.otter.ui.components.frostedGlass

/**
 * Round glass back, Watch Together and search buttons floating over a title page's banner.
 * [togetherButton] is the slot for the Watch Together button (glass, 40dp, like the others).
 */
@Composable
fun FloatingNav(onBack: () -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier, togetherButton: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        GlassIconButton(PhosphorIcons.Regular.ArrowLeft, "Back", onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            togetherButton()
            GlassIconButton(PhosphorIcons.Regular.MagnifyingGlass, "Search", onSearch)
        }
    }
}

@Composable
fun GlassIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).frostedGlass(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
    }
}
