package dev.neoworks.otter.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.X

private val panelShape = RoundedCornerShape(24.dp)
private val maxSidePanelWidth = 384.dp

/**
 * Floating glass panel over the player: on the right (speed, sources, together) or centred and
 * large (audio and subtitles, episodes), with a close button. Mirrors PlayerSheet.svelte.
 */
@Composable
fun PlayerSheetHost(sheet: PlayerSheetKind?, onClose: () -> Unit, content: @Composable (PlayerSheetKind) -> Unit) {
    // Keeps the last sheet around so its content stays while the panel leaves.
    var lastSheet by remember { mutableStateOf(sheet) }
    if (sheet != null) {
        lastSheet = sheet
    }
    val shown = lastSheet ?: return
    Box(Modifier.fillMaxSize()) {
        if (sheet != null) {
            Box(Modifier.fillMaxSize().clickableWithoutRipple(onClick = onClose))
        }
        SheetPanel(visible = sheet != null, side = shown.isSide, onClose = onClose) { content(shown) }
    }
}

@Composable
private fun SheetPanel(visible: Boolean, side: Boolean, onClose: () -> Unit, content: @Composable () -> Unit) {
    if (side) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
            val panelWidth = minOf(maxSidePanelWidth, maxWidth / 2)
            AnimatedVisibility(
                visible,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally(tween(340)) { width -> width },
                exit = slideOutHorizontally(tween(240)) { width -> width },
            ) {
                PanelBody(Modifier.width(panelWidth).fillMaxHeight(), onClose, content)
            }
        }
        return
    }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 32.dp, vertical = 16.dp)) {
        AnimatedVisibility(
            visible,
            enter = scaleIn(tween(280), initialScale = 0.94f) + fadeIn(tween(280)),
            exit = scaleOut(tween(240), targetScale = 0.94f) + fadeOut(tween(240)),
        ) {
            PanelBody(Modifier.fillMaxSize(), onClose, content)
        }
    }
}

@Composable
private fun PanelBody(modifier: Modifier, onClose: () -> Unit, content: @Composable () -> Unit) {
    Box(modifier.playerGlass(panelShape).clickableWithoutRipple {}) {
        Column(Modifier.fillMaxSize()) { content() }
        GlassIconButton(PhosphorIcons.Regular.X, "Close", 40.dp, onClose, Modifier.align(Alignment.TopEnd).padding(16.dp))
    }
}
