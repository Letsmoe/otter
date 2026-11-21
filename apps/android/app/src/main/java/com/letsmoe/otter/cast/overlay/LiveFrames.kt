package com.letsmoe.otter.cast.overlay

import android.graphics.Canvas

/**
 * A picture that changes while the stream runs: [draw] paints the moment [seconds] into the screen
 * onto [canvas], the same one for as long as the screen is shown (it starts out with the session's
 * background); a source whose picture did not change may leave it as it was.
 */
interface LiveFrames {
    fun draw(canvas: Canvas, seconds: Double)
}
