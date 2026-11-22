package com.letsmoe.otter.tv.ui.title

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.theme.TvSpacing
import com.letsmoe.otter.tv.theme.TvType
import com.letsmoe.otter.tv.ui.components.PreviewTrailer

/** A title's YouTube video full screen, letterboxed; Back leaves, and so does the video's end. */
@Composable
fun VideoScreen(videoKey: String, onFinished: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PreviewTrailer(
            videoKey,
            onReady = {},
            onEnded = onFinished,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

/** A title's scene stills full screen, from [startIndex]; left and right move through them, Back leaves. */
@Composable
fun ScenesScreen(imageUrls: List<String>, startIndex: Int) {
    var index by remember { mutableIntStateOf(startIndex) }
    val keys = remember { FocusRequester() }
    LaunchedEffect(Unit) { keys.requestFocus() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(keys)
            .onKeyEvent { event -> moveThroughScenes(event, imageUrls.size, index) { next -> index = next } }
            .focusable(),
    ) {
        AsyncImage(
            model = imageUrls.getOrNull(index),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            "${index + 1} / ${imageUrls.size}",
            fontSize = TvType.detail,
            color = TvColors.secondaryContent,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = TvSpacing.screenHorizontal, vertical = TvSpacing.screenVertical)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

// Left and right step through the stills and stop at either end.
private fun moveThroughScenes(event: KeyEvent, count: Int, index: Int, onMove: (Int) -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) {
        return false
    }
    if (event.key == Key.DirectionRight && index < count - 1) {
        onMove(index + 1)
        return true
    }
    if (event.key == Key.DirectionLeft && index > 0) {
        onMove(index - 1)
        return true
    }
    return false
}
