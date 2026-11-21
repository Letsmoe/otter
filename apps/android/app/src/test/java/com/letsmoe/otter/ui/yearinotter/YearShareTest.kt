package com.letsmoe.otter.ui.yearinotter

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

// One test on purpose: FileProvider keeps the first cache directory it saw in a static, and
// Robolectric gives every test its own.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class YearShareTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun share(cardNumber: Int) {
        val image = Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888)
        runBlocking { shareCardImage(context, image, 2026, cardNumber) }
    }

    @Test
    fun sharingOpensTheShareSheetWithAPngAndKeepsOnlyTheLatestImage() {
        share(3)
        val chooser = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(send)
        assertEquals(Intent.ACTION_SEND, send!!.action)
        assertEquals("image/png", send.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNotNull(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        val folder = File(context.cacheDir, "year-in-otter")
        assertTrue(File(folder, "otter-2026-3.png").length() > 0)

        share(4)
        assertEquals(listOf("otter-2026-4.png"), folder.list()!!.toList())
    }
}
