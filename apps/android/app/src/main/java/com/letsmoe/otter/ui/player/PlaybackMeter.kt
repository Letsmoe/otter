package com.letsmoe.otter.ui.player

// A play shorter than this says little about a link, unless it stalled.
private const val MIN_REPORTED_PLAY_MILLISECONDS = 15_000L

// Buffering this soon after a seek is part of the seek.
private const val SEEK_GRACE_MILLISECONDS = 4_000L

/** What one play of a link measured; sent to the backend once the link is left. */
data class LinkMeasurement(
    val streamId: String,
    val widthPixels: Int,
    val heightPixels: Int,
    val bitrateBps: Int,
    val stalls: Int,
    val stallMillis: Int,
    val firstFrameMillis: Int,
    val playedMillis: Int,
)

/**
 * Watches one link while it plays: how long until the first picture, how often and how long it
 * stopped to buffer afterwards (seeks and pauses don't count), what the picture measured and how
 * long it really played. Feed it the player's events; [finish] hands back the measurement.
 */
class PlaybackMeter(private val clock: () -> Long) {

    private var streamId: String? = null
    private var loadedAtMillis = 0L
    private var firstFrameAfterMillis: Long? = null
    private var widthPixels = 0
    private var heightPixels = 0
    private var stalls = 0
    private var stallMillis = 0L
    private var stallStartedAtMillis: Long? = null
    private var playingSinceMillis: Long? = null
    private var playedMillis = 0L
    private var lastSeekAtMillis: Long? = null

    /** The link was handed to the player. */
    fun start(newStreamId: String) {
        reset()
        streamId = newStreamId
        loadedAtMillis = clock()
    }

    fun firstFrame() {
        if (streamId == null || firstFrameAfterMillis != null) {
            return
        }
        firstFrameAfterMillis = clock() - loadedAtMillis
    }

    fun videoSize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            widthPixels = width
            heightPixels = height
        }
    }

    /** Buffering right after a seek is the viewer's doing, not the link's. */
    fun seeked() {
        lastSeekAtMillis = clock()
    }

    /** [wantsToPlay]: the player is not paused, so buffering keeps the picture waiting. */
    fun buffering(buffering: Boolean, wantsToPlay: Boolean) {
        if (streamId == null) {
            return
        }
        if (buffering) {
            startStall(wantsToPlay)
            return
        }
        endStall()
    }

    private fun startStall(wantsToPlay: Boolean) {
        val counts = firstFrameAfterMillis != null && wantsToPlay && !justSeeked()
        if (counts && stallStartedAtMillis == null) {
            stallStartedAtMillis = clock()
        }
    }

    private fun justSeeked(): Boolean {
        val seekedAt = lastSeekAtMillis ?: return false
        return clock() - seekedAt < SEEK_GRACE_MILLISECONDS
    }

    private fun endStall() {
        val startedAt = stallStartedAtMillis ?: return
        stalls += 1
        stallMillis += clock() - startedAt
        stallStartedAtMillis = null
    }

    fun playing(isPlaying: Boolean) {
        if (streamId == null) {
            return
        }
        if (isPlaying && playingSinceMillis == null) {
            playingSinceMillis = clock()
        }
        if (!isPlaying) {
            stopPlayedClock()
        }
    }

    private fun stopPlayedClock() {
        val since = playingSinceMillis ?: return
        playedMillis += clock() - since
        playingSinceMillis = null
    }

    /** Ends the play; null when nothing worth reporting happened. The meter is empty afterwards. */
    fun finish(bitrateBps: Int): LinkMeasurement? {
        val finishedStreamId = streamId
        val firstFrame = firstFrameAfterMillis
        endStall()
        stopPlayedClock()
        val measurement = measurementOf(finishedStreamId, firstFrame, bitrateBps)
        reset()
        return measurement
    }

    private fun measurementOf(finishedStreamId: String?, firstFrame: Long?, bitrateBps: Int): LinkMeasurement? {
        if (finishedStreamId == null || firstFrame == null) {
            return null
        }
        if (playedMillis < MIN_REPORTED_PLAY_MILLISECONDS && stalls == 0) {
            return null
        }
        return LinkMeasurement(
            streamId = finishedStreamId,
            widthPixels = widthPixels,
            heightPixels = heightPixels,
            bitrateBps = bitrateBps,
            stalls = stalls,
            stallMillis = stallMillis.toInt(),
            firstFrameMillis = firstFrame.toInt(),
            playedMillis = playedMillis.toInt(),
        )
    }

    private fun reset() {
        streamId = null
        firstFrameAfterMillis = null
        widthPixels = 0
        heightPixels = 0
        stalls = 0
        stallMillis = 0
        stallStartedAtMillis = null
        playingSinceMillis = null
        playedMillis = 0
        lastSeekAtMillis = null
    }
}
