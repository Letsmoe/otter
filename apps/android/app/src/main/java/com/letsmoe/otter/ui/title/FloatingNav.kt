package com.letsmoe.otter.ui.title

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.letsmoe.otter.ui.components.wiggleOnChange
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowLeft
import com.adamglin.phosphoricons.regular.MagnifyingGlass
import com.letsmoe.otter.ui.components.frostedGlass
import com.letsmoe.otter.ui.components.pressable

/**
 * Round glass back, Play on TV, Watch Together and search buttons floating over a title page's banner.
 * [headerButtons] is the slot for Play on TV and Watch Together (glass, 40dp, like the others).
 */
@Composable
fun FloatingNav(onBack: () -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier, headerButtons: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        GlassIconButton(PhosphorIcons.Regular.ArrowLeft, "Back", onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            headerButtons()
            GlassIconButton(PhosphorIcons.Regular.MagnifyingGlass, "Search", onSearch)
        }
    }
}

@Composable
fun GlassIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    var tapCount by remember { mutableIntStateOf(0) }
    val tap = {
        tapCount += 1
        onClick()
    }
    Box(Modifier.pressable(onClick = tap).size(40.dp).frostedGlass(CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, modifier = Modifier.wiggleOnChange(tapCount).size(20.dp))
    }
}
