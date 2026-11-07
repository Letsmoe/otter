package com.letsmoe.otter.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.WifiSlash
import com.letsmoe.otter.network.ConnectivityStore

/** A small pill above the tab bar while the phone has no internet. */
@Composable
fun OfflineBar(modifier: Modifier = Modifier) {
    val online by ConnectivityStore.online.collectAsStateWithLifecycle()
    AnimatedVisibility(
        visible = !online,
        modifier = modifier,
        enter = slideInVertically { height -> height } + fadeIn(),
        exit = slideOutVertically { height -> height } + fadeOut(),
    ) {
        Row(
            Modifier
                .padding(bottom = 8.dp)
                .frostedGlass(RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(PhosphorIcons.Regular.WifiSlash, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("You're offline", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text("Downloads still play", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
        }
    }
}
