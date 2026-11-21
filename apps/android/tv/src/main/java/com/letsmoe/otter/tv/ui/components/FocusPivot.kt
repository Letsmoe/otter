package com.letsmoe.otter.tv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * Scrolls lists so the focused child starts [pivot] from the list's start edge, the way TV
 * launchers keep focus on one line. Applies to the lazy lists inside [content].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusPivot(pivot: Dp, content: @Composable () -> Unit) {
    val pivotPixels = with(LocalDensity.current) { pivot.toPx() }
    val spec = remember(pivotPixels) { PivotBringIntoViewSpec(pivotPixels) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/**
 * Lists inside [content] don't scroll by themselves when focus moves; the screen scrolls them
 * (the home moves whole rows into place). Nested lists can set their own [FocusPivot].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ManualFocusScroll(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides NoScrollBringIntoViewSpec, content = content)
}

private object NoScrollBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        return 0f
    }
}

private class PivotBringIntoViewSpec(private val pivotPixels: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        return offset - pivotPixels
    }
}
