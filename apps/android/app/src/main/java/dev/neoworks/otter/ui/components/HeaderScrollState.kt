package dev.neoworks.otter.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

// Scroll distance before the header reacts, so small jitters don't toggle it.
private val scrollThreshold = 8.dp
private val alwaysShownAbove = 48.dp

/** Hides a floating header while scrolling down and brings it back when scrolling up. */
class HeaderScrollState(
    private val isScrolledPastTop: () -> Boolean,
    private val thresholdPixels: Float,
) : NestedScrollConnection {

    var visible by mutableStateOf(true)
        private set

    private var travelled = 0f

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        travelled += available.y
        if (abs(travelled) < thresholdPixels) {
            return Offset.Zero
        }
        visible = travelled > 0 || !isScrolledPastTop()
        travelled = 0f
        return Offset.Zero
    }
}

@Composable
fun rememberHeaderScrollState(listState: LazyListState): HeaderScrollState {
    val density = LocalDensity.current
    return remember(listState) {
        val pastTopOffset = with(density) { alwaysShownAbove.toPx() }
        HeaderScrollState(
            isScrolledPastTop = { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > pastTopOffset },
            thresholdPixels = with(density) { scrollThreshold.toPx() },
        )
    }
}
