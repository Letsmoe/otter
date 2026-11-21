package com.letsmoe.otter.ui.yearinotter

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val SHARE_FOLDER = "year-in-otter"
private const val PNG_QUALITY = 100

/** Writes the card image to the cache and opens Android's share sheet with it. */
suspend fun shareCardImage(context: Context, bitmap: Bitmap, year: Int, cardNumber: Int) {
    val file = withContext(Dispatchers.IO) { writeCardImage(context, bitmap, year, cardNumber) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "My $year in Otter")
        clipData = ClipData.newRawUri("", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share your $year in Otter").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun writeCardImage(context: Context, bitmap: Bitmap, year: Int, cardNumber: Int): File {
    val folder = File(context.cacheDir, SHARE_FOLDER)
    folder.mkdirs()
    // Earlier shares are no longer needed once the next card is made.
    folder.listFiles()?.forEach { old -> old.delete() }
    val file = File(folder, "otter-$year-$cardNumber.png")
    file.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, stream) }
    return file
}
