package dev.neoworks.otter.ui.title

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.SpeakerHigh
import com.adamglin.phosphoricons.regular.SpeakerSlash
import dev.neoworks.otter.ui.components.frostedGlass
import dev.neoworks.otter.ui.theme.OtterColors
import kotlin.math.roundToInt

// Share of the scroll distance the media lags behind the page.
private const val PARALLAX_FACTOR = 0.45f

/** Whether the trailer's first frame has replaced the backdrop image yet, and whether it's muted. */
@Stable
class TrailerState(val trailerKey: String?) {
    var visible by mutableStateOf(false)
    var muted by mutableStateOf(true)
}

@Composable
fun rememberTrailerState(trailerKey: String?): TrailerState {
    return remember(trailerKey) { TrailerState(trailerKey) }
}

/**
 * The banner's media: backdrop image, faded out for a muted looping trailer once its first frame renders.
 * Drawn behind the page's list, outside the glass blur source. Follows the list's first item, drifting slower than it.
 */
@Composable
fun TitleBackdropMedia(imageUrl: String?, trailer: TrailerState, scroll: () -> BannerScroll, modifier: Modifier = Modifier) {
    var targetImageAlpha = 1f
    if (trailer.visible) {
        targetImageAlpha = 0f
    }
    val imageAlpha by animateFloatAsState(targetImageAlpha, tween(700), label = "backdropAlpha")
    // The frame scrolls with the page and clips; only the picture inside it lags behind.
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .offset { frameOffset(scroll()) }
            .clipToBounds()
            .background(Color.Black),
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = pictureLag(scroll()) }) {
            BackdropPicture(imageUrl, imageAlpha, trailer)
        }
    }
}

@Composable
private fun BackdropPicture(imageUrl: String?, imageAlpha: Float, trailer: TrailerState) {
    Box(Modifier.fillMaxSize()) {
        if (trailer.trailerKey != null) {
            TrailerVideo(
                trailer.trailerKey,
                looping = true,
                muted = trailer.muted,
                modifier = Modifier.fillMaxSize(),
                onReady = { trailer.visible = true },
            )
        }
        if (imageUrl != null) {
            AsyncImage(imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(imageAlpha))
        }
    }
}

/** How far the list has scrolled its banner item; [bannerGone] once it left the screen. */
class BannerScroll(val offsetPixels: Float, val bannerGone: Boolean)

private fun frameOffset(scroll: BannerScroll): IntOffset {
    if (scroll.bannerGone) {
        // Kept composed (so the trailer doesn't reload) but far off screen.
        return IntOffset(0, -100_000)
    }
    return IntOffset(0, -scroll.offsetPixels.roundToInt())
}

private fun pictureLag(scroll: BannerScroll): Float = scroll.offsetPixels * PARALLAX_FACTOR

/** The banner's place in the page's list: transparent over the media, with the fade and the mute button. */
@Composable
fun TitleBackdropOverlay(trailer: TrailerState, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(1f / 3f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, OtterColors.base100))),
        )
        if (trailer.trailerKey != null && trailer.visible) {
            MuteButton(trailer.muted, onToggle = { trailer.muted = !trailer.muted }, Modifier.align(Alignment.BottomEnd).padding(12.dp))
        }
    }
}

@Composable
private fun MuteButton(muted: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    var icon = PhosphorIcons.Regular.SpeakerHigh
    var label = "Mute trailer"
    if (muted) {
        icon = PhosphorIcons.Regular.SpeakerSlash
        label = "Unmute trailer"
    }
    Box(modifier.size(36.dp).frostedGlass(CircleShape).clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
    }
}
