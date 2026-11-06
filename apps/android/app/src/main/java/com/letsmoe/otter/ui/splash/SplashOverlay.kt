package com.letsmoe.otter.ui.splash

import android.graphics.Paint
import android.graphics.PathMeasure
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.letsmoe.otter.R
import com.letsmoe.otter.api.OtterApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min

// Wordmark geometry in otter_wordmark.xml units.
private const val WORDMARK_WIDTH = 1343f
private const val WORDMARK_HEIGHT = 316f
private const val LETTER_O_WIDTH = 334f
private const val FIRST_LETTER_GAP_END = 336f
private const val PEN_WIDTH = 7f

// The system splash draws the icon without background at 288dp; the "O" fills 56.8 of its 108 units.
private val systemSplashLetterWidth = 151.dp
private val finalWordmarkMaxWidth = 280.dp
private const val FINAL_WORDMARK_SCREEN_SHARE = 0.62f

// Each letter after the "O" is outlined by a pen, then filled; the next one starts while it's still drawing.
private const val LETTER_STAGGER_MILLISECONDS = 210f
private const val LETTER_OUTLINE_MILLISECONDS = 620f
private const val LETTER_FILL_DELAY_SHARE = 0.55f
private const val LETTER_FILL_MILLISECONDS = 280f
private const val DRAWN_LETTERS = 4
private const val DRAWING_MILLISECONDS =
    LETTER_STAGGER_MILLISECONDS * (DRAWN_LETTERS - 1) + LETTER_OUTLINE_MILLISECONDS * LETTER_FILL_DELAY_SHARE + LETTER_FILL_MILLISECONDS

private const val HOLD_BEFORE_DRAWING_MILLISECONDS = 120L
private const val HOLD_AFTER_DRAWING_MILLISECONDS = 300L
private const val BACKEND_WAIT_MILLISECONDS = 3000L
private const val FADE_OUT_MILLISECONDS = 300

/** The splash only plays on a cold start, not when the activity is recreated. */
object SplashPlayback {
    private var finished = false

    /** The app underneath is composed once the letters are drawn, so it can't make the pen stutter. */
    var appMayCompose by mutableStateOf(false)
        private set

    fun shouldPlay(): Boolean = !finished

    fun drawingDone() {
        appMayCompose = true
    }

    fun finish() {
        finished = true
        appMayCompose = true
    }
}

/**
 * Takes over from the system splash: after the "O", a pen writes out "TTER" letter by letter while
 * the wordmark shrinks and slides left, so the finished word ends up centered.
 */
@Composable
fun SplashOverlay() {
    var visible by remember { mutableStateOf(SplashPlayback.shouldPlay()) }
    if (!visible) {
        return
    }
    val wordmarkVector = ImageVector.vectorResource(R.drawable.otter_wordmark)
    val letters = remember(wordmarkVector) { wordmarkLetters(wordmarkVector) }
    val pen = remember { WordmarkPen() }
    val elapsed = remember { Animatable(0f) }
    val opacity = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        playSplash(elapsed, opacity)
        SplashPlayback.finish()
        visible = false
    }
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = opacity.value }
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        drawWordmark(letters, pen, elapsed.value)
    }
}

private suspend fun playSplash(elapsed: Animatable<Float, *>, opacity: Animatable<Float, *>) {
    delay(HOLD_BEFORE_DRAWING_MILLISECONDS)
    elapsed.animateTo(DRAWING_MILLISECONDS, tween(DRAWING_MILLISECONDS.toInt(), easing = LinearEasing))
    SplashPlayback.drawingDone()
    delay(HOLD_AFTER_DRAWING_MILLISECONDS)
    // The app underneath shouldn't appear before the backend can fill it.
    withTimeoutOrNull(BACKEND_WAIT_MILLISECONDS) { OtterApi.awaitBackend() }
    opacity.animateTo(0f, tween(FADE_OUT_MILLISECONDS))
}

/** One letter of the wordmark: its filled shape and its outline split into contours for the pen. */
private class WordmarkLetter(val shape: android.graphics.Path, val contours: List<PathMeasure>)

private fun wordmarkLetters(wordmark: ImageVector): List<WordmarkLetter> {
    val letters = mutableListOf<WordmarkLetter>()
    for (index in 0 until wordmark.root.size) {
        val node = wordmark.root[index]
        if (node is VectorPath) {
            val shape = PathParser().addPathNodes(node.pathData).toPath().asAndroidPath()
            letters.add(WordmarkLetter(shape, contoursOf(shape)))
        }
    }
    return letters.sortedBy { letter -> leftEdge(letter.shape) }
}

private fun contoursOf(shape: android.graphics.Path): List<PathMeasure> {
    val contours = mutableListOf<PathMeasure>()
    val measure = PathMeasure(shape, true)
    do {
        val contour = android.graphics.Path()
        measure.getSegment(0f, measure.length, contour, true)
        contours.add(PathMeasure(contour, false))
    } while (measure.nextContour())
    return contours
}

private fun leftEdge(shape: android.graphics.Path): Float {
    val bounds = RectF()
    shape.computeBounds(bounds, true)
    return bounds.left
}

/** Paints and a scratch path reused every frame. */
private class WordmarkPen {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = PEN_WIDTH
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val segment = android.graphics.Path()
}

private fun DrawScope.drawWordmark(letters: List<WordmarkLetter>, pen: WordmarkPen, elapsed: Float) {
    val camera = FastOutSlowInEasing.transform((elapsed / DRAWING_MILLISECONDS).coerceIn(0f, 1f))
    val startPixelsPerUnit = systemSplashLetterWidth.toPx() / LETTER_O_WIDTH
    val finalWidth = min(size.width * FINAL_WORDMARK_SCREEN_SHARE, finalWordmarkMaxWidth.toPx())
    val pixelsPerUnit = startPixelsPerUnit + (finalWidth / WORDMARK_WIDTH - startPixelsPerUnit) * camera

    // Centering the written part (not the whole word) is what slides the wordmark left as it grows.
    val writtenUnits = FIRST_LETTER_GAP_END + (WORDMARK_WIDTH - FIRST_LETTER_GAP_END) * camera
    val left = (size.width - writtenUnits * pixelsPerUnit) / 2f
    val top = (size.height - WORDMARK_HEIGHT * pixelsPerUnit) / 2f
    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas
        nativeCanvas.save()
        nativeCanvas.translate(left, top)
        nativeCanvas.scale(pixelsPerUnit, pixelsPerUnit)
        letters.forEachIndexed { index, letter -> drawLetter(nativeCanvas, letter, index, pen, elapsed) }
        nativeCanvas.restore()
    }
}

private fun drawLetter(canvas: android.graphics.Canvas, letter: WordmarkLetter, index: Int, pen: WordmarkPen, elapsed: Float) {
    // The "O" is already on screen from the system splash.
    if (index == 0) {
        pen.fill.alpha = 255
        canvas.drawPath(letter.shape, pen.fill)
        return
    }
    val letterStart = LETTER_STAGGER_MILLISECONDS * (index - 1)
    val outlineProgress = FastOutSlowInEasing.transform(((elapsed - letterStart) / LETTER_OUTLINE_MILLISECONDS).coerceIn(0f, 1f))
    if (outlineProgress <= 0f) {
        return
    }
    drawOutline(canvas, letter, pen, outlineProgress)
    val fillStart = letterStart + LETTER_OUTLINE_MILLISECONDS * LETTER_FILL_DELAY_SHARE
    val fillProgress = ((elapsed - fillStart) / LETTER_FILL_MILLISECONDS).coerceIn(0f, 1f)
    if (fillProgress <= 0f) {
        return
    }
    pen.fill.alpha = (fillProgress * 255).toInt()
    canvas.drawPath(letter.shape, pen.fill)
}

// Every contour of a letter is traced at once, so outer edge and counter (the R's bowl) finish together.
private fun drawOutline(canvas: android.graphics.Canvas, letter: WordmarkLetter, pen: WordmarkPen, progress: Float) {
    for (contour in letter.contours) {
        pen.segment.reset()
        contour.getSegment(0f, contour.length * progress, pen.segment, true)
        canvas.drawPath(pen.segment, pen.outline)
    }
}
