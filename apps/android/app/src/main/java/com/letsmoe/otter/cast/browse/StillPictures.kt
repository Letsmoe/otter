package com.letsmoe.otter.cast.browse

import android.content.Context
import android.graphics.Bitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private const val JPEG_QUALITY = 90

/** The backdrop at [url], covering a 16:9 picture [height] lines tall; null when it can't be loaded. */
suspend fun loadBackdropBitmap(context: Context, url: String, height: Int): Bitmap? {
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(Size(height * 16 / 9, height))
        .scale(Scale.FILL)
        .allowHardware(false)
        .build()
    val result = SingletonImageLoader.get(context).execute(request)
    if (result !is SuccessResult) {
        return null
    }
    return result.image.toBitmap()
}

/** Saves [picture] as a JPEG in the cache, for the encoder to read. */
suspend fun writeStillPicture(context: Context, picture: Bitmap, name: String): File = withContext(Dispatchers.IO) {
    val file = File(context.cacheDir, "$name-${UUID.randomUUID()}.jpg")
    file.outputStream().use { stream -> picture.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream) }
    file
}
