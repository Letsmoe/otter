package com.letsmoe.otter.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.letsmoe.otter.ui.components.pressable
import com.letsmoe.otter.ui.media.RailItem
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

/**
 * Tap opens the title; a long press buzzes and opens its preview. Squishes under the finger, so
 * put it first in the chain (before clip and background).
 */
@Composable
fun Modifier.openOrPreview(item: RailItem, onClick: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    return pressable(
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            TitlePreview.show(item)
        },
        onClick = onClick,
    )
}
