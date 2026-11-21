package com.letsmoe.otter.ui.player

// How often the watcher looks at the links.
private const val CHECK_INTERVAL_MILLISECONDS = 1000L
// Playback must have run this long without a rebuffer before it is trusted to carry a sharper link.
private const val STABLE_MILLISECONDS = 3 * 60_000L
// The measured throughput must be this many times what the sharper link needs.
private const val BANDWIDTH_HEADROOM = 2.0
// A pause between two lines of captions at least this long counts as a quiet moment.
private const val QUIET_MILLISECONDS = 1500L
// After a switch the next one waits this long, so a link that turns out worse can't flip-flop.
private const val SWITCH_COOLDOWN_MILLISECONDS = 5 * 60_000L

/** A link as the better-link watcher sees it. [height] is the video height of its quality, e.g. 1080. */
data class LinkInfo(val id: String, val height: Int?, val language: String?, val playable: Boolean?, val qualityLabel: String)

/** Everything the watcher needs to know about the playback right now. */
data class PlaybackSnapshot(
    val paused: Boolean,
    val casting: Boolean,
    /** The viewer picked the playing link themselves: it is theirs to change. */
    val viewerChoseLink: Boolean,
    val current: LinkInfo?,
    val links: List<LinkInfo>,
    /** What the player measures it can download, in bits per second. */
    val measuredBitsPerSecond: Long,
    val captionVisible: Boolean,
)

/** Roughly what a video of this height needs to stream without stalling, in bits per second. */
fun bitsPerSecondNeeded(height: Int): Long {
    return when {
        height <= 480 -> 1_200_000L
        height <= 720 -> 2_500_000L
        height <= 1080 -> 5_000_000L
        height <= 1440 -> 9_000_000L
        else -> 15_000_000L
    }
}

/** Whether [measuredBitsPerSecond] is comfortably above what a link of [height] needs. */
fun bandwidthSuffices(measuredBitsPerSecond: Long, height: Int): Boolean {
    return measuredBitsPerSecond >= bitsPerSecondNeeded(height) * BANDWIDTH_HEADROOM
}

/**
 * The sharpest link that is higher than the playing one, in the same language, known to deliver
 * video, and not tried before; null when there is none (or the playing link's height is unknown).
 */
fun pickBetterLink(current: LinkInfo, links: List<LinkInfo>, tried: Set<String>): LinkInfo? {
    val currentHeight = current.height ?: return null
    return links
        .filter { link -> link.id != current.id && link.id !in tried }
        .filter { link -> link.playable == true && link.language == current.language }
        .filter { link -> (link.height ?: 0) > currentHeight }
        .maxByOrNull { link -> link.height ?: 0 }
}

/**
 * Looks for a sharper link while a title plays and asks for the switch once it is safe: playback
 * has been stable for a few minutes, the bandwidth is far above what the link needs, and the
 * moment is quiet (paused, or no caption on screen). While casting it never switches, since the
 * TV would reload its stream; it only says so once per title.
 */
class BetterLinkWatcher(
    private val clock: () -> Long,
    private val onSwitch: (LinkInfo) -> Unit,
    private val onCastingSkipped: (LinkInfo) -> Unit,
) {
    private var playbackStartedAt: Long? = null
    private var lastRebufferAt: Long? = null
    private var quietSince: Long? = null
    private var lastSwitchAt: Long? = null
    private var lastCheckAt = 0L
    private var castingNoted = false
    private val tried = mutableSetOf<String>()

    /** A new title: nothing is known about it yet. */
    fun reset() {
        playbackStartedAt = null
        lastRebufferAt = null
        quietSince = null
        lastSwitchAt = null
        castingNoted = false
        tried.clear()
    }

    /** The playing link got ready for the first time. */
    fun onPlaybackStarted() {
        playbackStartedAt = clock()
        lastRebufferAt = null
    }

    fun onRebuffer() {
        lastRebufferAt = clock()
    }

    /** Looks at [snapshot] (built only when it is time to look); at most once a second. */
    fun check(snapshot: () -> PlaybackSnapshot) {
        val now = clock()
        if (now - lastCheckAt < CHECK_INTERVAL_MILLISECONDS) {
            return
        }
        lastCheckAt = now
        val current = snapshot()
        val better = betterLinkFor(current) ?: return
        if (current.casting) {
            noteCastingSkipped(better)
            return
        }
        if (readyToSwitch(now, current, better)) {
            tried.add(better.id)
            lastSwitchAt = now
            onSwitch(better)
        }
    }

    private fun betterLinkFor(snapshot: PlaybackSnapshot): LinkInfo? {
        val current = snapshot.current
        if (snapshot.viewerChoseLink || current == null) {
            return null
        }
        return pickBetterLink(current, snapshot.links, tried)
    }

    private fun noteCastingSkipped(better: LinkInfo) {
        if (castingNoted) {
            return
        }
        castingNoted = true
        onCastingSkipped(better)
    }

    private fun readyToSwitch(now: Long, snapshot: PlaybackSnapshot, better: LinkInfo): Boolean {
        trackQuiet(now, snapshot)
        val height = better.height ?: return false
        return isStable(now) &&
            isOutOfCooldown(now) &&
            bandwidthSuffices(snapshot.measuredBitsPerSecond, height) &&
            isQuiet(now, snapshot)
    }

    private fun isStable(now: Long): Boolean {
        val started = playbackStartedAt ?: return false
        val lastTrouble = lastRebufferAt ?: started
        return now - maxOf(started, lastTrouble) >= STABLE_MILLISECONDS
    }

    private fun isOutOfCooldown(now: Long): Boolean {
        val last = lastSwitchAt ?: return true
        return now - last >= SWITCH_COOLDOWN_MILLISECONDS
    }

    private fun trackQuiet(now: Long, snapshot: PlaybackSnapshot) {
        if (snapshot.captionVisible) {
            quietSince = null
            return
        }
        if (quietSince == null) {
            quietSince = now
        }
    }

    private fun isQuiet(now: Long, snapshot: PlaybackSnapshot): Boolean {
        if (snapshot.paused) {
            return true
        }
        val since = quietSince ?: return false
        return now - since >= QUIET_MILLISECONDS
    }
}
