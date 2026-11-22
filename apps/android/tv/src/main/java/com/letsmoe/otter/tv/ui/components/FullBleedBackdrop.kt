package com.letsmoe.otter.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * A backdrop across the whole screen, shaded on the left for the title's text and at the bottom
 * for the first row, so both stay readable over any picture.
 */
@Composable
fun FullBleedBackdrop(imageUrl: String?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .crossfade(400)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(leftShade))
        Box(Modifier.fillMaxSize().background(bottomShade))
    }
}

private val leftShade = Brush.horizontalGradient(
    0f to Color.Black.copy(alpha = 0.85f),
    0.3f to Color.Black.copy(alpha = 0.55f),
    0.6f to Color.Transparent,
)

private val bottomShade = Brush.verticalGradient(
    0.4f to Color.Transparent,
    0.68f to Color.Black.copy(alpha = 0.7f),
    0.85f to Color.Black,
)
