package dev.neoworks.otter.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * Scrolling content with glass floating over it. [content] receives the modifier that marks the
 * content as the backdrop; glass placed as a sibling of that content blurs it. Glass inside the
 * backdrop itself must not use it (see [WithoutBackdrop]).
 */
@Composable
fun GlassBackdropBox(modifier: Modifier = Modifier, content: @Composable BoxScope.(backdropSource: Modifier) -> Unit) {
    val backdrop = rememberHazeState()
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        Box(modifier) { content(Modifier.hazeSource(backdrop)) }
    }
}

/** Glass in here falls back to a plain translucent fill instead of blurring its own surroundings. */
@Composable
fun WithoutBackdrop(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlassBackdrop provides null, content = content)
}
