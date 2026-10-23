package dev.neoworks.otter.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/** A sheet opened somewhere in the app, drawn by [SheetHostOutlet]. */
class SheetEntry(
    val onDismiss: () -> Unit,
    val fullHeight: Boolean,
    val content: @Composable ColumnScope.() -> Unit,
)

/**
 * Collects the open glass sheets so they render above the tab bar but inside the app's window,
 * where they can blur the page (a separate dialog window can't see it).
 */
@Stable
class SheetHostState {
    val entries = mutableStateListOf<SheetEntry>()
}

val LocalSheetHost = staticCompositionLocalOf<SheetHostState?> { null }

/** Hands [content] to the host while this is in composition, keeping the caller's ViewModel and lifecycle owners. */
@Composable
internal fun RegisterSheet(host: SheetHostState, onDismiss: () -> Unit, fullHeight: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val currentDismiss by rememberUpdatedState(onDismiss)
    val currentContent by rememberUpdatedState(content)
    val viewModelStoreOwner = LocalViewModelStoreOwner.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val entry = remember {
        SheetEntry(onDismiss = { currentDismiss() }, fullHeight = fullHeight) {
            if (viewModelStoreOwner == null) {
                currentContent(this)
                return@SheetEntry
            }
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides viewModelStoreOwner,
                LocalLifecycleOwner provides lifecycleOwner,
            ) {
                currentContent(this)
            }
        }
    }
    DisposableEffect(host) {
        host.entries.add(entry)
        onDispose { host.entries.remove(entry) }
    }
}

/** Draws the open sheets; place it above everything the sheets should cover. */
@Composable
fun SheetHostOutlet(host: SheetHostState) {
    for (entry in host.entries) {
        GlassSheetLayer(entry)
    }
}
