package com.letsmoe.otter.cast.session

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import java.io.File

// Sizes are in pixels of a 1080-line picture and scale with the real one.
private const val REFERENCE_HEIGHT = 1080f
private const val BRAND_SIZE = 132f
private const val STATUS_SIZE = 40f
private const val TITLE_SIZE = 64f
private const val STATUS_GAP = 36f
private const val STATUS_DOT_RADIUS = 9f
private const val TITLE_MAX_WIDTH_FRACTION = 0.7f
private const val SPINNER_SIZE = 112f
private const val SPINNER_STROKE = 9f
private const val SPINNER_SWEEP_DEGREES = 100f
private const val SPINNER_TURN_SECONDS = 1.1
// Where the spinner's center sits, from the top, as a fraction of the picture's height.
private const val SPINNER_CENTER_FRACTION = 0.56f
private const val LOADING_TITLE_FRACTION = 0.42f
// The app's theme (ui/theme/Theme.kt): base200, baseContent, secondaryContent, secondary and accent.
private val BACKGROUND = Color.rgb(0x0B, 0x0B, 0x0D)
private val TEXT = Color.rgb(0xFA, 0xFA, 0xFA)
private val SECONDARY_TEXT = Color.rgb(0xA1, 0xA1, 0xAA)
private val TRACK = Color.rgb(0x1C, 0x1C, 0x1F)
private val ACCENT = Color.rgb(0xA7, 0x8B, 0xFA)

/** The picture size of a session, 16:9 at [height] lines; both even, as encoders want them. */
fun sessionPictureSize(height: Int): Size {
    val evenHeight = height - height % 2
    val width = evenHeight * 16 / 9
    return Size(width - width % 2, evenHeight)
}

/** Draws [screen]'s still picture at [size] into a PNG in the cache and returns it. */
fun renderScreenPicture(context: Context, screen: SessionScreen, size: Size, name: String): File {
    val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(BACKGROUND)
    drawScreen(canvas, screen)
    val file = File(context.cacheDir, "tv-screen-$name.png")
    file.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
    bitmap.recycle()
    return file
}

// The screensaver paints all of its picture live; titles and the phone's pictures aren't drawn here.
private fun drawScreen(canvas: Canvas, screen: SessionScreen) {
    when (screen) {
        is SessionScreen.Loading -> drawLoading(canvas, screen.title)
        is SessionScreen.Idle -> drawIdle(canvas)
        is SessionScreen.Screensaver, is SessionScreen.Picture, is SessionScreen.Title -> Unit
    }
}

// "Otter" large in the middle, "● Connected" under it.
private fun drawIdle(canvas: Canvas) {
    val scale = canvas.height / REFERENCE_HEIGHT
    val brand = textPaint(BRAND_SIZE * scale, TEXT, Typeface.BOLD)
    val centerX = canvas.width / 2f
    val brandBaseline = canvas.height / 2f - brand.descent()
    canvas.drawText("Otter", centerX, brandBaseline, brand)
    val status = textPaint(STATUS_SIZE * scale, SECONDARY_TEXT, Typeface.NORMAL)
    val statusBaseline = brandBaseline + brand.descent() + STATUS_GAP * scale - status.ascent()
    val label = "Connected"
    canvas.drawText(label, centerX + STATUS_DOT_RADIUS * scale * 1.5f, statusBaseline, status)
    val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    dot.color = ACCENT
    val dotX = centerX - status.measureText(label) / 2f - STATUS_DOT_RADIUS * scale
    val dotY = statusBaseline + (status.ascent() + status.descent()) / 2f
    canvas.drawCircle(dotX, dotY, STATUS_DOT_RADIUS * scale, dot)
}

// The title above where the spinner turns; just the spinner when the title isn't known yet.
private fun drawLoading(canvas: Canvas, title: String?) {
    if (title.isNullOrBlank()) {
        return
    }
    val scale = canvas.height / REFERENCE_HEIGHT
    val paint = textPaint(TITLE_SIZE * scale, TEXT, Typeface.BOLD)
    val shown = TextUtils.ellipsize(title, paint, canvas.width * TITLE_MAX_WIDTH_FRACTION, TextUtils.TruncateAt.END).toString()
    canvas.drawText(shown, canvas.width / 2f, canvas.height * LOADING_TITLE_FRACTION, paint)
}

private fun textPaint(textSize: Float, color: Int, style: Int): TextPaint {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    paint.textSize = textSize
    paint.color = color
    paint.typeface = Typeface.create(Typeface.SANS_SERIF, style)
    paint.textAlign = Paint.Align.CENTER
    return paint
}

/**
 * The loading screen's spinner: a small bitmap drawn anew for every frame from
 * its presentation time, so it turns in the TV's picture.
 */
@UnstableApi
class SpinnerOverlay : BitmapOverlay() {

    private var bitmap: Bitmap? = null
    private var scale = 1f

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        scale = videoSize.height / REFERENCE_HEIGHT
        val side = (SPINNER_SIZE * scale).toInt().coerceAtLeast(2)
        bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val spinner = checkNotNull(bitmap) { "configure() comes first" }
        drawSpinner(Canvas(spinner), spinnerAngle(presentationTimeUs), SPINNER_STROKE * scale)
        return spinner
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        // Anchors are -1 (bottom) to 1 (top).
        val anchorY = 1f - 2f * SPINNER_CENTER_FRACTION
        return StaticOverlaySettings.Builder().setBackgroundFrameAnchor(0f, anchorY).build()
    }
}

private fun spinnerAngle(presentationTimeUs: Long): Float {
    val turns = presentationTimeUs / 1_000_000.0 / SPINNER_TURN_SECONDS
    return ((turns % 1.0) * 360.0).toFloat()
}

// A dim ring with an accent-coloured arc at [startAngle].
private fun drawSpinner(canvas: Canvas, startAngle: Float, stroke: Float) {
    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = stroke
    paint.strokeCap = Paint.Cap.ROUND
    val inset = stroke
    val ring = RectF(inset, inset, canvas.width - inset, canvas.height - inset)
    paint.color = TRACK
    canvas.drawOval(ring, paint)
    paint.color = ACCENT
    canvas.drawArc(ring, startAngle, SPINNER_SWEEP_DEGREES, false, paint)
}
