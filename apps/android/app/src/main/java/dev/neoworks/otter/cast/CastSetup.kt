package dev.neoworks.otter.cast

import android.content.Context
import android.util.Log
import androidx.media3.cast.Cast
import androidx.media3.cast.CastParams
import com.google.android.gms.cast.CastMediaControlIntent

/** Prepares the Cast SDK once per process; devices without Google Play services simply can't cast. */
object CastSetup {

    private const val TAG = "OtterCast"

    fun initialize(context: Context) {
        // Google's Default Media Receiver plays HLS and MP4 from a URL, which is all the stream proxy serves.
        val params = CastParams.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .build()
        try {
            Cast.getSingletonInstance(context).initialize(params)
        } catch (error: Exception) {
            Log.w(TAG, "Cast unavailable", error)
        }
    }

    /** The TV's name while casting, e.g. "Living Room TV". */
    fun connectedDeviceName(context: Context): String? {
        return try {
            Cast.getSingletonInstance(context).currentCastSession?.castDevice?.friendlyName
        } catch (error: Exception) {
            null
        }
    }

    /** Stops the TV's playback and disconnects from it. */
    fun endSession(context: Context) {
        try {
            Cast.getSingletonInstance(context).endCurrentSession(true)
        } catch (error: Exception) {
            Log.w(TAG, "ending cast session failed", error)
        }
    }
}
