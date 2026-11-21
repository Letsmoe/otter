package com.letsmoe.otter.cast.overlay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaExtractor
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.letsmoe.otter.BuildConfig
import com.letsmoe.otter.cast.browse.BrowsePreview
import com.letsmoe.otter.cast.browse.drawBrowseFrame
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
private const val STILL_SECONDS = 3

/**
 * Encodes the TV's browse picture the way a TV gets it. The output stays in the
 * app's files dir for a look with ffprobe (adb exec-out run-as com.letsmoe.otter
 * cat files/still-output.mp4), as does the picture (still-picture.png).
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class StillEncoderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun holdsThePictureAsVideoWithSilentSound() = runBlocking {
        val output = File(context.filesDir, "still-output.mp4")
        val failures = mutableListOf<Exception>()
        val job = StillJob(pictureFile(), STILL_SECONDS) { error -> failures.add(error) }

        withTimeout(ENCODE_TIMEOUT_MILLISECONDS) {
            FileOutputStream(output).use { stream -> StillEncoder(context, job).encode(stream) }
        }

        assertTrue(failures.isEmpty())
        val header = output.inputStream().use { input -> ByteArray(8).also { bytes -> input.read(bytes) } }
        assertEquals("ftyp", String(header, 4, 4))
        val mimeTypes = trackMimeTypes(output)
        assertTrue("tracks: $mimeTypes", mimeTypes.any { type -> type == "video/avc" })
        assertTrue("tracks: $mimeTypes", mimeTypes.any { type -> type.startsWith("audio/") })
        assertTrue("duration ${durationSeconds(output)}", durationSeconds(output) in 2.0..4.5)
    }

    @Test
    fun theLoopbackServerStreamsARegisteredPicture() {
        val job = StillJob(pictureFile(), STILL_SECONDS) { _ -> }
        StillJobs.register(job)

        val head = request("HEAD", job.id)
        val stream = request("GET", job.id)

        assertEquals(200, head.responseCode)
        assertEquals(200, stream.responseCode)
        val body = stream.inputStream.use { input -> input.readBytes() }
        assertEquals("ftyp", String(body, 4, 4))
        assertTrue("streamed ${body.size} bytes", body.size > 5_000)
    }

    // The production drawing code on a plain backdrop; files/still-picture.png shows it.
    private fun pictureFile(): File {
        val preview = BrowsePreview("movie:157336", "Interstellar", listOf("2014", "2h 49m", "★ 8.5", "Adventure · Drama"), "Explorers use a wormhole to travel beyond our galaxy.", null)
        val picture = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val backdrop = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        Canvas(backdrop).drawColor(Color.rgb(60, 90, 130))
        drawBrowseFrame(Canvas(picture), preview, backdrop)
        File(context.filesDir, "still-picture.png").outputStream().use { stream -> picture.compress(Bitmap.CompressFormat.PNG, 100, stream) }
        val file = File(context.cacheDir, "still-test-picture.jpg")
        file.outputStream().use { stream -> picture.compress(Bitmap.CompressFormat.JPEG, 90, stream) }
        return file
    }

    private fun trackMimeTypes(file: File): List<String> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            return (0 until extractor.trackCount).map { index -> extractor.getTrackFormat(index).getString("mime").orEmpty() }
        } finally {
            extractor.release()
        }
    }

    // Fragmented MP4 has no total length: it ends where the last sample of the video begins.
    private fun durationSeconds(file: File): Double {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            selectVideoTrack(extractor)
            var lastSampleUs = 0L
            while (extractor.sampleTime >= 0) {
                lastSampleUs = extractor.sampleTime
                extractor.advance()
            }
            return lastSampleUs / 1_000_000.0
        } finally {
            extractor.release()
        }
    }

    private fun selectVideoTrack(extractor: MediaExtractor) {
        for (index in 0 until extractor.trackCount) {
            if (extractor.getTrackFormat(index).getString("mime") == "video/avc") {
                extractor.selectTrack(index)
            }
        }
    }

    // The app started OverlayStreamServer when its process came up.
    private fun request(method: String, jobId: String): HttpURLConnection {
        val connection = URL("http://127.0.0.1:${BuildConfig.OVERLAY_PORT}/overlay/$jobId").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.readTimeout = ENCODE_TIMEOUT_MILLISECONDS.toInt()
        return connection
    }
}
