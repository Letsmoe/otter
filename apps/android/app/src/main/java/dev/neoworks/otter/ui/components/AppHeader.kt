package dev.neoworks.otter.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.MagnifyingGlass

private val headerFade = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.9f),
    0.5f to Color.Black.copy(alpha = 0.7f),
    1f to Color.Transparent,
)

/**
 * Floating header: the logo, optional [trailingActions] (Watch Together) and search on a fade to
 * black, with optional [content] such as a category switch below. Slides away when not [visible].
 */
@Composable
fun AppHeader(
    visible: Boolean,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    trailingActions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(tween(320)) { height -> -height } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(320)) { height -> -height } + fadeOut(tween(220)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(headerFade)
                .statusBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeaderTopRow(onSearch, trailingActions)
            content()
        }
    }
}

@Composable
private fun HeaderTopRow(onSearch: () -> Unit, trailingActions: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("OTTER", fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 3.2.sp, modifier = Modifier.padding(start = 8.dp))
        Box(Modifier.weight(1f))
        trailingActions()
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onSearch), contentAlignment = Alignment.Center) {
            Icon(PhosphorIcons.Regular.MagnifyingGlass, contentDescription = "Search", modifier = Modifier.size(22.dp))
        }
    }
}
