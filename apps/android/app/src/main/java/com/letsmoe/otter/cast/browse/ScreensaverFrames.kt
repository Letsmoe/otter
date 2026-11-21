package com.letsmoe.otter.cast.browse

import android.graphics.Bitmap
import android.graphics.Canvas
import com.letsmoe.otter.cast.overlay.LiveFrames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

// Steps of the crossfade: about one per frame, so it is smooth but a frame is not drawn twice.
private const val FADE_STEPS = 25

/** What was drawn last, so an unchanged picture is not drawn (and uploaded) again. */
private data class DrawnMoment(val slide: Int, val fadeStep: Int, val hasCurrent: Boolean, val hasNext: Boolean)

/**
 * The idle TV's picture over time: [slides] one after the other with a slow crossfade
 * (see [screensaverMomentAt]). Backdrops come from [loadBackdrop] in the background, the current
 * and the next one only, so a long list does not fill the phone's memory; a slide whose backdrop is
 * not there yet shows dark, a fade into one that is not there yet waits for it.
 */
class ScreensaverFrames(
    private val slides: List<ScreensaverSlide>,
    private val scope: CoroutineScope,
    private val loadBackdrop: suspend (String) -> Bitmap?,
) : LiveFrames {

    private val backdrops = ConcurrentHashMap<Int, Bitmap>()
    private val requested = ConcurrentHashMap.newKeySet<Int>()
    private var drawn: DrawnMoment? = null
    private var drawnCanvas: Canvas? = null

    override fun draw(canvas: Canvas, seconds: Double) {
        val moment = screensaverMomentAt(seconds, slides.size)
        keepOnly(moment)
        val current = layerOf(moment.slide)
        var next: ScreensaverLayer? = null
        if (moment.fade > 0f) {
            next = layerOf(moment.next)
        }
        val fadeStep = (moment.fade * FADE_STEPS).toInt()
        val key = DrawnMoment(moment.slide, fadeStep, current?.backdrop != null, next?.backdrop != null)
        // A restarted session job paints onto a fresh, empty canvas.
        if (key == drawn && canvas === drawnCanvas) {
            return
        }
        drawScreensaverFrame(canvas, current, next, fadeStep.toFloat() / FADE_STEPS)
        drawn = key
        drawnCanvas = canvas
    }

    private fun layerOf(index: Int): ScreensaverLayer? {
        if (slides.isEmpty()) {
            return null
        }
        request(index)
        return ScreensaverLayer(slides[index], backdrops[index])
    }

    private fun request(index: Int) {
        if (!requested.add(index)) {
            return
        }
        scope.launch {
            val backdrop = loadBackdrop(slides[index].backdropUrl)
            if (backdrop != null) {
                backdrops[index] = backdrop
            }
        }
    }

    // Loads the current and the next backdrop, and lets go of the ones that are over.
    private fun keepOnly(moment: ScreensaverMoment) {
        request(moment.slide)
        request(moment.next)
        val wanted = setOf(moment.slide, moment.next)
        backdrops.keys.removeAll { index -> index !in wanted }
        requested.removeAll { index -> index !in wanted }
    }
}
