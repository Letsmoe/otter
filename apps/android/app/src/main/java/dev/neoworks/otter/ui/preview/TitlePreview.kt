package dev.neoworks.otter.ui.preview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.neoworks.otter.ui.media.RailItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The title whose preview (trailer and summary) is open, set by long-pressing a poster anywhere. */
object TitlePreview {
    private val mutableItem = MutableStateFlow<RailItem?>(null)
    val item: StateFlow<RailItem?> = mutableItem

    fun show(item: RailItem) {
        mutableItem.value = item
    }

    fun close() {
        mutableItem.value = null
    }
}

/** Tap opens the title; a long press buzzes and opens its preview. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.openOrPreview(item: RailItem, onClick: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    return combinedClickable(
        onClick = onClick,
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            TitlePreview.show(item)
        },
    )
}
