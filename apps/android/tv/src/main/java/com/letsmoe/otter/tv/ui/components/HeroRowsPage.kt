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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Rows below the first scroll to just under the top bar.
private val ROWS_TOP_LINE = 56.dp
val ROW_SPACING = 12.dp
// The backdrop moves at this share of the scroll speed: the parallax.
private const val PARALLAX_FACTOR = 0.5f
private const val GROW_SETTLE_MILLISECONDS = 250L
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
    onNearEnd: () -> Unit = {},
    hero: @Composable () -> Unit,
) {
    val listState = rememberLazyListState()
    var focusedRow by remember { mutableIntStateOf(HERO_FOCUSED) }
    var focusedRowResizes by remember { mutableIntStateOf(0) }
    ScrollFocusedRowIntoPlace(listState, focusedRow)
    KeepGrownRowOnScreen(listState, focusedRow, focusedRowResizes)
    AskForRowsNearEnd(focusedRow, rows.size, onNearEnd)
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
                        val rowModifier = Modifier
                            .reportFocus(index) { focusedRow = it }
                            .onSizeChanged {
                                if (index == focusedRow) {
                                    focusedRowResizes++
                                }
                            }
                        Box(rowModifier) {
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
        if (focusedRow == HERO_FOCUSED || (focusedRow == 0 && firstRowFitsUnderHero(listState))) {
            listState.animateScrollToItem(0)
            return@LaunchedEffect
        }
        // Item 0 is the hero, so row n is item n + 1; a negative offset leaves room above it.
        listState.animateScrollToItem(focusedRow + 1, -topLine)
    }
}

// Focus this many rows from the last one asks for more, so they're there before focus gets there.
private const val ROWS_AHEAD = 3

/** Tells [onNearEnd] once focus is within [ROWS_AHEAD] rows of the end, and again each time rows were added. */
@Composable
private fun AskForRowsNearEnd(focusedRow: Int, rowCount: Int, onNearEnd: () -> Unit) {
    LaunchedEffect(focusedRow, rowCount) {
        if (rowCount > 0 && focusedRow >= rowCount - ROWS_AHEAD) {
            onNearEnd()
        }
    }
}

/**
 * A card growing into a preview makes its row taller; when the row then runs off the bottom of
 * the screen, it moves up under the top bar like a focused later row. Shrinking back moves nothing.
 */
@Composable
private fun KeepGrownRowOnScreen(listState: LazyListState, focusedRow: Int, resizes: Int) {
    val topLine = with(LocalDensity.current) { ROWS_TOP_LINE.roundToPx() }
    LaunchedEffect(resizes) {
        // The card grows in an animation; act once it has settled, not on every frame of it.
        delay(GROW_SETTLE_MILLISECONDS)
        if (focusedRow == HERO_FOCUSED || !rowRunsOffScreen(listState, focusedRow + 1)) {
            return@LaunchedEffect
        }
        listState.animateScrollToItem(focusedRow + 1, -topLine)
    }
}

private fun rowRunsOffScreen(listState: LazyListState, itemIndex: Int): Boolean {
    val layout = listState.layoutInfo
    val row = layout.visibleItemsInfo.firstOrNull { item -> item.index == itemIndex } ?: return false
    return row.offset + row.size > layout.viewportEndOffset
}

// A page whose first screen shows only the hero (a movie) scrolls its first row up like any other.
private fun firstRowFitsUnderHero(listState: LazyListState): Boolean {
    if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
        return false
    }
    val layout = listState.layoutInfo
    val firstRow = layout.visibleItemsInfo.firstOrNull { item -> item.index == 1 } ?: return false
    return firstRow.offset + firstRow.size <= layout.viewportEndOffset
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
