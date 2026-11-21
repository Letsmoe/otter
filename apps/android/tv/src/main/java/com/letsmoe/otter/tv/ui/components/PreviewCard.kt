package com.letsmoe.otter.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.theme.TvSpacing
import com.letsmoe.otter.tv.theme.TvType
import com.letsmoe.otter.ui.media.RailItem
import kotlinx.coroutines.delay

// Focus rests this long on a card before it widens into a preview.
private const val PREVIEW_DELAY_MILLISECONDS = 1_500L
private const val PREVIEW_ASPECT = 16f / 9f
private val PREVIEW_LOGO_MAX_WIDTH = 190.dp
private val PREVIEW_LOGO_MAX_HEIGHT = 60.dp
private val PREVIEW_INFO_WIDTH = 560.dp

/** Which card of a row is previewing, so the row can show that title's facts and description below. */
class RowPreview {
    var item by mutableStateOf<RailItem?>(null)
        private set

    fun start(previewed: RailItem) {
        item = previewed
    }

    /** Ends [previewed]'s preview; a later card's preview that already started stays. */
    fun end(previewed: RailItem) {
        if (item == previewed) {
            item = null
        }
    }
}

@Composable
fun rememberRowPreview(): RowPreview {
    return remember { RowPreview() }
}

/**
 * A title card that, once focus rests on it, widens to 16:9 at the same height and plays the
 * trailer with the title's logo over it. [resting] is the card's usual inside. [rowPreview] hears
 * about the preview when the row shows the title's info below it.
 */
@Composable
fun PreviewCard(
    item: RailItem,
    restingWidth: Dp,
    restingHeight: Dp,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    rowPreview: RowPreview? = null,
    resting: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val previewing = rememberPreviewing(focused, canPreview(item))
    ReportPreview(item, previewing, rowPreview)
    val width by animateDpAsState(cardWidth(restingWidth, restingHeight, previewing), label = "previewCardWidth")
    FocusableSurface(
        onClick = onClick,
        shape = CARD_SHAPE,
        focusedScale = focusedScaleFor(previewing),
        modifier = Modifier
            .width(width)
            .height(restingHeight)
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

@Composable
private fun ReportPreview(item: RailItem, previewing: Boolean, rowPreview: RowPreview?) {
    if (rowPreview == null) {
        return
    }
    LaunchedEffect(item, previewing) {
        if (previewing) {
            rowPreview.start(item)
        } else {
            rowPreview.end(item)
        }
    }
    DisposableEffect(item) {
        onDispose { rowPreview.end(item) }
    }
}

private fun cardWidth(restingWidth: Dp, restingHeight: Dp, previewing: Boolean): Dp {
    if (!previewing) {
        return restingWidth
    }
    return maxOf(restingWidth, restingHeight * PREVIEW_ASPECT)
}

// A widened card growing like a poster would cover its neighbours.
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

/** The previewed title's facts and description under a row; slides open while a card previews. */
@Composable
fun PreviewInfo(item: RailItem?) {
    // Kept through the closing animation, after the preview already ended.
    val lastItem = remember { mutableStateOf<RailItem?>(null) }
    if (item != null) {
        lastItem.value = item
    }
    AnimatedVisibility(
        visible = item != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        val shown = lastItem.value ?: return@AnimatedVisibility
        PreviewInfoText(shown)
    }
}

@Composable
private fun PreviewInfoText(item: RailItem) {
    val extras by rememberTitleExtras(item.id)
    Column(
        modifier = Modifier
            .padding(start = TvSpacing.screenHorizontal, top = 2.dp, bottom = 6.dp)
            .width(PREVIEW_INFO_WIDTH),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(previewFacts(item, extras), fontSize = TvType.body, color = TvColors.secondaryContent, maxLines = 1)
        val overview = extras?.overview
        if (overview != null) {
            Text(
                overview,
                fontSize = TvType.body,
                lineHeight = 19.sp,
                color = Color.White.copy(alpha = 0.82f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// "Comedy · Crime  ·  2026  ·  1h 57m", or the season count for a series.
private fun previewFacts(item: RailItem, extras: TitleExtras?): String {
    val parts = mutableListOf<String>()
    if (extras != null && extras.genres.isNotEmpty()) {
        parts.add(extras.genres.take(2).joinToString(" · "))
    }
    val year = item.year
    if (year != null) {
        parts.add(year.toString())
    }
    val length = extras?.lengthLabel(item.mediaType)
    if (length != null) {
        parts.add(length)
    }
    return parts.joinToString("  ·  ")
}
