package com.letsmoe.otter.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp

/** Height of the fixed hero area at the top; the rows fill the band below it. */
val BACKDROP_HERO_HEIGHT = 300.dp
// Focus is in the hero; rows count from 0.
private const val HERO_FOCUSED = -1

/**
 * A page like a TV launcher's home: a full-screen [backdrop] that stays put, the [hero] fixed in
 * the top area, and the rows in the band below it. The focused row always sits at the top of
 * the band; rows above it slide out of view.
 */
@Composable
fun BackdropRowsPage(rows: List<PageRow>, backdrop: @Composable () -> Unit, hero: @Composable () -> Unit) {
    val listState = rememberLazyListState()
    var focusedRow by remember { mutableIntStateOf(HERO_FOCUSED) }
    KeepFocusedRowOnTop(listState, focusedRow)
    Box(Modifier.fillMaxSize()) {
        backdrop()
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(BACKDROP_HERO_HEIGHT).reportFocus(HERO_FOCUSED) { focusedRow = it }) {
                hero()
            }
            RowsBand(listState, rows, onRowFocused = { row -> focusedRow = row }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun RowsBand(listState: LazyListState, rows: List<PageRow>, onRowFocused: (Int) -> Unit, modifier: Modifier) {
    ManualFocusScroll {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxWidth(),
            // Room for the last row to scroll up to the top of the band too.
            contentPadding = PaddingValues(bottom = 540.dp),
            verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
        ) {
            rows.forEachIndexed { index, row ->
                item(key = row.key) {
                    Box(Modifier.reportFocus(index, onRowFocused)) {
                        row.content()
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
private fun KeepFocusedRowOnTop(listState: LazyListState, focusedRow: Int) {
    LaunchedEffect(focusedRow) {
        if (focusedRow <= 0) {
            listState.animateScrollToItem(0)
            return@LaunchedEffect
        }
        listState.animateScrollToItem(focusedRow)
    }
}
