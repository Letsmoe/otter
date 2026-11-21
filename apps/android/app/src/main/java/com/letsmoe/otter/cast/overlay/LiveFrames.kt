package com.letsmoe.otter.cast.overlay

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay

/**
 * A picture that changes while the stream runs: [draw] paints the moment [seconds] into the stream
 * onto [canvas], always the same one; a source whose picture did not change may leave it as it was.
 */
interface LiveFrames {
    fun draw(canvas: Canvas, seconds: Double)
}

/**
 * Draws a [LiveFrames] source over every frame of a still stream, so the picture the TV watches
 * changes without the TV loading anything new. The bitmap is covered entirely by the source.
 */
@UnstableApi
class LiveFrameOverlay(private val source: LiveFrames) : BitmapOverlay() {

    private var canvasBitmap: Bitmap? = null
    private var canvas: Canvas? = null
    private var firstPresentationTimeUs: Long? = null

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        val bitmap = Bitmap.createBitmap(videoSize.width, videoSize.height, Bitmap.Config.ARGB_8888)
        canvasBitmap = bitmap
        canvas = Canvas(bitmap)
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val bitmap = checkNotNull(canvasBitmap) { "configure() comes first" }
        val first = firstPresentationTimeUs ?: presentationTimeUs.also { firstPresentationTimeUs = it }
        source.draw(checkNotNull(canvas), (presentationTimeUs - first) / 1_000_000.0)
        return bitmap
    }
}
