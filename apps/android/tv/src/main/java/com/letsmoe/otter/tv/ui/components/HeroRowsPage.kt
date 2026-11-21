package com.letsmoe.otter.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Rows below the first scroll to just under the top bar.
private val ROWS_TOP_LINE = 56.dp
val ROW_SPACING = 12.dp
// The backdrop moves at this share of the scroll speed: the parallax.
private const val PARALLAX_FACTOR = 0.5f
// Focus is in the hero; rows count from 0.
private const val HERO_FOCUSED = -1

/** One row of a [HeroRowsPage]; the key keeps its place and focus when rows load or reorder. */
class PageRow(val key: String, val content: @Composable () -> Unit)

/**
 * A page like a TV streaming app's home or title page: a hero with one row under it on the first
 * screen. Focusing a later row scrolls the page so that row sits under the top bar and the rows
 * fill the screen; the backdrop follows at half speed and fades out. Back on the first row or the
 * hero, the hero returns.
 *
 * [firstRowSpace] is the height left under the hero for the first row.
 */
@Composable
fun HeroRowsPage(
    firstRowSpace: Dp,
    rows: List<PageRow>,
    backdrop: @Composable (Modifier) -> Unit,
    hero: @Composable () -> Unit,
) {
    val listState = rememberLazyListState()
    var focusedRow by remember { mutableIntStateOf(HERO_FOCUSED) }
    ScrollFocusedRowIntoPlace(listState, focusedRow)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = maxHeight - firstRowSpace
        backdrop(Modifier.parallax(listState, heroHeight))
        ManualFocusScroll {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // Room for the last row to scroll up to the top line too.
                contentPadding = PaddingValues(bottom = maxHeight),
                verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
            ) {
                item(key = "hero") {
                    Box(Modifier.fillMaxWidth().height(heroHeight).reportFocus(HERO_FOCUSED) { focusedRow = it }) {
                        hero()
                    }
                }
                rows.forEachIndexed { index, row ->
                    item(key = row.key) {
                        Box(Modifier.reportFocus(index) { focusedRow = it }) {
                            row.content()
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.reportFocus(row: Int, onFocused: (Int) -> Unit): Modifier {
    return onFocusChanged { state ->
        if (state.hasFocus) {
            onFocused(row)
        }
    }
}

@Composable
private fun ScrollFocusedRowIntoPlace(listState: LazyListState, focusedRow: Int) {
    val topLine = with(LocalDensity.current) { ROWS_TOP_LINE.roundToPx() }
    LaunchedEffect(focusedRow) {
        if (focusedRow <= 0) {
            listState.animateScrollToItem(0)
            return@LaunchedEffect
        }
        // Item 0 is the hero, so row n is item n + 1; a negative offset leaves room above it.
        listState.animateScrollToItem(focusedRow + 1, -topLine)
    }
}

private fun Modifier.parallax(listState: LazyListState, heroHeight: Dp): Modifier {
    return graphicsLayer {
        val heroPixels = heroHeight.toPx()
        val scrolled = heroScrollOf(listState, heroPixels)
        translationY = -scrolled * PARALLAX_FACTOR
        alpha = 1f - (scrolled / heroPixels).coerceIn(0f, 1f)
    }
}

// Past the hero the exact distance doesn't matter: the backdrop is gone by then.
private fun heroScrollOf(listState: LazyListState, heroPixels: Float): Float {
    if (listState.firstVisibleItemIndex > 0) {
        return heroPixels
    }
    return listState.firstVisibleItemScrollOffset.toFloat()
}
