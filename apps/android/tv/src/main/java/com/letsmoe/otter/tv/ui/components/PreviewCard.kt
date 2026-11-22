package com.letsmoe.otter.tv.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.theme.TvType
import com.letsmoe.otter.ui.media.RailItem
import kotlinx.coroutines.delay

// Focus rests this long on a card before it grows into a preview.
private const val PREVIEW_DELAY_MILLISECONDS = 1_500L
private const val PREVIEW_ASPECT = 16f / 9f
// A landscape card grows to this many times its size in both directions.
private const val LANDSCAPE_PREVIEW_SCALE = 2f
private val PREVIEW_LOGO_MAX_WIDTH = 190.dp
private val PREVIEW_LOGO_MAX_HEIGHT = 60.dp
private const val PREVIEW_OVERVIEW_MAX_LINES = 3

/**
 * A title card that, once focus rests on it, grows into a preview playing the trailer with the
 * title's logo over it: a landscape card to twice its size, a poster to 16:9 at its height.
 * [resting] is the card's usual inside; [below] goes under the card and knows whether it previews.
 */
@Composable
fun PreviewCard(
    item: RailItem,
    restingWidth: Dp,
    restingHeight: Dp,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    below: @Composable (previewing: Boolean) -> Unit = {},
    resting: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val previewing = rememberPreviewing(focused, canPreview(item))
    val size = cardSize(restingWidth, restingHeight, previewing)
    val width by animateDpAsState(size.width, label = "previewCardWidth")
    val height by animateDpAsState(size.height, label = "previewCardHeight")
    Column {
        FocusableSurface(
            onClick = onClick,
            shape = CARD_SHAPE,
            focusedScale = focusedScaleFor(previewing),
            modifier = Modifier
                .width(width)
                .height(height)
                .onFocusChanged { state ->
                    focused = state.isFocused
                    if (state.isFocused) {
                        onFocus()
                    }
                },
        ) {
            Crossfade(previewing, label = "previewCardContent") { showPreview ->
                if (showPreview) {
                    PreviewContent(item)
                } else {
                    resting()
                }
            }
        }
        below(previewing)
    }
}

/**
 * A 16:9 [PreviewCard] of [width] with the title and a second line under it; while it previews,
 * the title's facts and description instead.
 */
@Composable
fun CaptionedLandscapeCard(item: RailItem, width: Dp, onClick: () -> Unit) {
    PreviewCard(
        item,
        restingWidth = width,
        restingHeight = width * 9f / 16f,
        onFocus = {},
        onClick = onClick,
        below = { previewing ->
            if (previewing) {
                PreviewDetails(item, width * LANDSCAPE_PREVIEW_SCALE)
            } else {
                CardCaption(item, width)
            }
        },
    ) {
        LandscapeCardContent(item)
    }
}

// Continue watching opens an episode or resumes; a trailer there would be in the way.
private fun canPreview(item: RailItem): Boolean {
    return item.episodeId == null && item.progress == null
}

@Composable
private fun rememberPreviewing(focused: Boolean, allowed: Boolean): Boolean {
    var previewing by remember { mutableStateOf(false) }
    LaunchedEffect(focused, allowed) {
        previewing = false
        if (!focused || !allowed) {
            return@LaunchedEffect
        }
        delay(PREVIEW_DELAY_MILLISECONDS)
        previewing = true
    }
    return previewing
}

private class CardSize(val width: Dp, val height: Dp)

private fun cardSize(restingWidth: Dp, restingHeight: Dp, previewing: Boolean): CardSize {
    if (!previewing) {
        return CardSize(restingWidth, restingHeight)
    }
    if (restingWidth > restingHeight) {
        return CardSize(restingWidth * LANDSCAPE_PREVIEW_SCALE, restingHeight * LANDSCAPE_PREVIEW_SCALE)
    }
    return CardSize(restingHeight * PREVIEW_ASPECT, restingHeight)
}

// A grown card scaling up like a resting one would cover its neighbours.
private fun focusedScaleFor(previewing: Boolean): Float {
    if (previewing) {
        return 1.03f
    }
    return 1.08f
}

/** The backdrop, then the trailer over it once its first frame is there, and the logo at the bottom left. */
@Composable
private fun PreviewContent(item: RailItem) {
    val extras by rememberTitleExtras(item.id)
    var trailerShowing by remember(item.id) { mutableStateOf(false) }
    var trailerEnded by remember(item.id) { mutableStateOf(false) }
    val trailerAlpha by animateFloatAsState(alphaFor(trailerShowing), label = "trailerAlpha")
    Box(Modifier.fillMaxSize()) {
        AsyncImage(model = previewImage(item), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        val trailerKey = extras?.trailerKey
        if (trailerKey != null && !trailerEnded) {
            PreviewTrailer(
                trailerKey,
                onReady = { trailerShowing = true },
                onEnded = {
                    trailerShowing = false
                    trailerEnded = true
                },
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = trailerAlpha },
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))))
        TitleName(
            item.id,
            item.title,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            logoMaxWidth = PREVIEW_LOGO_MAX_WIDTH,
            logoMaxHeight = PREVIEW_LOGO_MAX_HEIGHT,
        )
    }
}

private fun alphaFor(visible: Boolean): Float {
    if (visible) {
        return 1f
    }
    return 0f
}

private fun previewImage(item: RailItem): String? {
    val backdrop = item.backdropUrl
    if (backdrop != null) {
        return backdrop
    }
    return item.posterUrl
}

/** Under a previewing card: the title, its facts line and the description. */
@Composable
fun PreviewDetails(item: RailItem, width: Dp) {
    val extras by rememberTitleExtras(item.id)
    Column(
        modifier = Modifier.width(width).padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(item.title, fontSize = TvType.body, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(previewFacts(item, extras), fontSize = TvType.caption, fontWeight = FontWeight.SemiBold, color = TvColors.secondaryContent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val overview = extras?.overview
        if (overview != null) {
            Text(
                overview,
                fontSize = TvType.detail,
                lineHeight = 16.sp,
                color = Color.White.copy(alpha = 0.82f),
                maxLines = PREVIEW_OVERVIEW_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// "Comedy, Crime  ·  2026  ·  1h 57m  ·  ★ 7.4", with the season count for a series.
private fun previewFacts(item: RailItem, extras: TitleExtras?): String {
    val parts = mutableListOf<String>()
    if (extras != null && extras.genres.isNotEmpty()) {
        parts.add(extras.genres.take(2).joinToString(", "))
    }
    val year = item.year
    if (year != null) {
        parts.add(year.toString())
    }
    if (extras != null) {
        parts.addAll(listOfNotNull(extras.lengthLabel(item.mediaType), extras.ratingLabel()))
    }
    return parts.joinToString("  ·  ")
}
