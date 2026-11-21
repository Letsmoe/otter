package com.letsmoe.otter.cast.browse

import com.letsmoe.otter.ui.media.RailItem
import kotlin.math.floor

/** How long each backdrop stays, and how much of that the slow crossfade into the next takes. */
const val SLIDE_SECONDS = 20.0
const val FADE_SECONDS = 2.5

private const val MAX_SLIDES = 12

/** One backdrop of the screensaver with the title in the corner. */
data class ScreensaverSlide(val titleId: String, val title: String, val year: Int?, val backdropUrl: String)

/**
 * What the picture shows at one moment: slide [slide], and while [fade] (0 to 1) is above 0
 * the slide [next] fading in over it.
 */
data class ScreensaverMoment(val slide: Int, val next: Int, val fade: Float)

/** The moment at [seconds] of the stream, going round the [slideCount] slides again and again. */
fun screensaverMomentAt(seconds: Double, slideCount: Int): ScreensaverMoment {
    if (slideCount <= 0) {
        return ScreensaverMoment(0, 0, 0f)
    }
    val position = floor(seconds / SLIDE_SECONDS).toInt()
    val slide = position % slideCount
    val next = (slide + 1) % slideCount
    val secondsIntoSlide = seconds - position * SLIDE_SECONDS
    val fadeStart = SLIDE_SECONDS - FADE_SECONDS
    if (slideCount == 1 || secondsIntoSlide < fadeStart) {
        return ScreensaverMoment(slide, next, 0f)
    }
    val progress = ((secondsIntoSlide - fadeStart) / FADE_SECONDS).toFloat()
    return ScreensaverMoment(slide, next, progress.coerceIn(0f, 1f))
}

/**
 * The slides for an idle TV: what the profile is watching first, then My List, each title once and
 * only with a backdrop (a poster would not fill the screen).
 */
fun screensaverSlidesOf(continueWatching: List<RailItem>, watchlist: List<RailItem>): List<ScreensaverSlide> {
    val slides = mutableListOf<ScreensaverSlide>()
    val seen = mutableSetOf<String>()
    for (item in continueWatching + watchlist) {
        val backdropUrl = televisionBackdropUrl(item.backdropUrl)
        if (backdropUrl == null || !seen.add(item.id)) {
            continue
        }
        slides.add(ScreensaverSlide(item.id, item.title, item.year, backdropUrl))
    }
    return slides.take(MAX_SLIDES)
}
