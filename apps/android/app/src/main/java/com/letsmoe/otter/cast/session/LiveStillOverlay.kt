package com.letsmoe.otter.cast.session

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import java.io.File

/**
 * The whole picture of a still job: the screen [job] shows right now. That changes while the job
 * runs ([TelevisionSession.show] swaps it in), so going from the idle screen to a browsed title
 * needs no new encoder. The picture is drawn again only when the screen changed, or for the
 * screensaver, whose backdrops move; Media3 uploads it to the GPU only then.
 */
@UnstableApi
class LiveStillOverlay(private val context: Context, private val job: SessionJob) : BitmapOverlay() {

    private var canvasBitmap: Bitmap? = null
    private var canvas: Canvas? = null
    private var drawnScreen: SessionScreen? = null
    private var screenStartMicroseconds = 0L

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        val bitmap = Bitmap.createBitmap(videoSize.width, videoSize.height, Bitmap.Config.ARGB_8888)
        canvasBitmap = bitmap
        canvas = Canvas(bitmap)
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val bitmap = checkNotNull(canvasBitmap) { "configure() comes first" }
        val screen = job.screen
        if (screen !== drawnScreen) {
            drawnScreen = screen
            screenStartMicroseconds = presentationTimeUs
            drawNewScreen(checkNotNull(canvas), screen)
            return bitmap
        }
        if (screen is SessionScreen.Screensaver) {
            val seconds = (presentationTimeUs - screenStartMicroseconds) / 1_000_000.0
            screen.frames.draw(checkNotNull(canvas), seconds)
        }
        return bitmap
    }

    private fun drawNewScreen(canvas: Canvas, screen: SessionScreen) {
        if (screen is SessionScreen.Picture) {
            drawPicture(canvas, screen.imageFile)
            return
        }
        // The screensaver paints over the whole picture, starting from an empty one.
        drawStillScreen(canvas, context, screen)
        if (screen is SessionScreen.Screensaver) {
            screen.frames.draw(canvas, 0.0)
        }
    }

    // The phone drew the picture at the session's size already; it is only scaled when that changed.
    private fun drawPicture(canvas: Canvas, imageFile: File) {
        val picture = BitmapFactory.decodeFile(imageFile.path)
        if (picture == null) {
            drawStillScreen(canvas, context, SessionScreen.Idle)
            return
        }
        canvas.drawBitmap(picture, null, Rect(0, 0, canvas.width, canvas.height), null)
        picture.recycle()
    }
}
