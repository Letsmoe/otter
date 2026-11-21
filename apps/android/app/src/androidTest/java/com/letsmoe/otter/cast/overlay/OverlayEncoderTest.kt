package com.letsmoe.otter.cast.overlay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.letsmoe.otter.core.BuildConfig
import com.letsmoe.otter.graphql.type.SubtitleSize
import com.letsmoe.otter.graphql.type.SubtitleStyle
import com.letsmoe.otter.ui.player.CaptionLook
import com.letsmoe.otter.ui.player.subtitleTextFraction
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val ENCODE_TIMEOUT_MILLISECONDS = 120_000L

/**
 * Encodes the clips in androidTest/assets the way a TV gets them. The outputs
 * stay in the app's files dir for a look with ffmpeg and for the backend's
 * tests (adb exec-out run-as com.letsmoe.otter cat files/<name>):
 * overlay-output.mp4 (720p, from 2 s on) and overlay-small.mp4 (the
 * backend's testdata/media3-overlay.mp4).
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class OverlayEncoderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext = InstrumentationRegistry.getInstrumentation().context

    private val plan = OverlayPlan(
        captions = listOf(
            CaptionCue(2.0, 5.0, "Where were you last night?"),
            CaptionCue(5.5, 8.0, "Upstairs. With the dog,\nlike every Sunday."),
        ),
        skips = listOf(SkipHint("Skip intro on your phone", 2.0, 8.0)),
        upNext = UpNextHint("S1:E2 · The Long Way Home", 8.0, 10.0, sampleStill()),
    )

    @Test
    fun encodesTheTitleWithTheOverlayDrawnIn() = runBlocking {
        val output = encode("overlay-source.mp4", "overlay-output.mp4", startSeconds = 2.0, videoBitrate = 8_000_000)

        assertTrue("output is ${output.length()} bytes", output.length() > 100_000)
    }

    @Test
    fun encodesASmallClipForTheBackendTests() = runBlocking {
        val output = encode("overlay-small.mp4", "overlay-small.mp4", startSeconds = 0.0, videoBitrate = 100_000)

        assertTrue("output is ${output.length()} bytes", output.length() in 1_000..200_000)
    }

    @Test
    fun theLoopbackServerStreamsARegisteredJob() {
        val source = copyAsset("overlay-small.mp4")
        val job = OverlayJob(source.toURI().toString(), "video/mp4", 0.0, 1080, plan) { _, _ -> }
        OverlayJobs.register(job)

        val head = request("HEAD", job.id)
        val missing = request("GET", "no-such-job")
        val stream = request("GET", job.id)

        assertEquals(200, head.responseCode)
        assertEquals(404, missing.responseCode)
        assertEquals(200, stream.responseCode)
        val body = stream.inputStream.use { input -> input.readBytes() }
        assertEquals("ftyp", String(body, 4, 4))
        assertTrue("streamed ${body.size} bytes", body.size > 1_000)
    }

    // One 1080p frame per caption style, for a look: files/caption-<style>.png.
    @Test
    fun drawsEveryCaptionStyle() {
        val frame = OverlayFrame("Upstairs. With the dog,\nlike every Sunday.", null, null)
        for (style in listOf(SubtitleStyle.OUTLINE, SubtitleStyle.BOX, SubtitleStyle.YELLOW)) {
            val picture = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(picture)
            canvas.drawColor(Color.rgb(200, 190, 170))
            drawFrame(canvas, frame, CaptionLook(subtitleTextFraction(SubtitleSize.MEDIUM), style), null)
            File(context.filesDir, "caption-${style.rawValue.lowercase()}.png").outputStream().use { stream ->
                picture.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }
    }

    private suspend fun encode(asset: String, outputName: String, startSeconds: Double, videoBitrate: Int): File {
        val source = copyAsset(asset)
        val output = File(context.filesDir, outputName)
        val failures = mutableListOf<Exception>()
        val job = OverlayJob(source.toURI().toString(), "video/mp4", startSeconds, 1080, plan) { _, error -> failures.add(error) }
        withTimeout(ENCODE_TIMEOUT_MILLISECONDS) {
            FileOutputStream(output).use { stream -> OverlayEncoder(context, job, videoBitrate).encode(stream) }
        }
        assertTrue(failures.isEmpty())
        val header = output.inputStream().use { input -> ByteArray(8).also { bytes -> input.read(bytes) } }
        assertEquals("ftyp", String(header, 4, 4))
        return output
    }

    private fun copyAsset(asset: String): File {
        val source = File(context.cacheDir, asset)
        testContext.assets.open(asset).use { input ->
            source.outputStream().use { stream -> input.copyTo(stream) }
        }
        return source
    }

    // The app started OverlayStreamServer when its process came up.
    private fun request(method: String, jobId: String): HttpURLConnection {
        val connection = URL("http://127.0.0.1:${BuildConfig.OVERLAY_PORT}/overlay/$jobId").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.readTimeout = ENCODE_TIMEOUT_MILLISECONDS.toInt()
        return connection
    }

    // Stands in for an episode still: a dark blue picture with a sun.
    private fun sampleStill(): Bitmap {
        val still = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(still)
        canvas.drawColor(Color.rgb(40, 70, 120))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(240, 180, 60)
        canvas.drawCircle(420f, 150f, 90f, paint)
        return still
    }
}
