package com.letsmoe.otter.ui.player

import android.content.Context
import androidx.core.content.edit

// Plugins label a link "VOE — Deutsch": the hoster, then the language.
private const val LABEL_SEPARATOR = " — "
// A link that played this long (or a quarter of a short episode) without an error counts as working.
private const val PLAYED_SECONDS = 300.0
private const val PLAYED_FRACTION = 0.25

private fun playedSecondsOf(durationSeconds: Double): Double {
    return minOf(PLAYED_SECONDS, durationSeconds * PLAYED_FRACTION)
}

/** The hoster a link's label names ("VOE — Deutsch" gives "voe"); null for links without one. */
fun hosterOf(stream: SourceStream): String? {
    val label = stream.label ?: return null
    val hoster = label.substringBefore(LABEL_SEPARATOR).trim().lowercase()
    if (hoster.isEmpty()) {
        return null
    }
    return hoster
}

/** [links] split into the links of [hoster] and the others, each group kept in order and left out when empty. */
fun preferredFirst(links: List<SourceStream>, hoster: String?): List<List<SourceStream>> {
    if (hoster == null) {
        return listOf(links)
    }
    val (preferred, others) = links.partition { stream -> hosterOf(stream) == hoster }
    return listOf(preferred, others).filter { group -> group.isNotEmpty() }
}

/** Where the hoster that last played a series is kept. */
interface PreferredHosterStorage {
    fun read(seriesKey: String): String?

    fun write(seriesKey: String, hoster: String)
}

/** Saved on this device only, like the other player preferences. */
class PreferencesPreferredHosterStorage(context: Context) : PreferredHosterStorage {

    private val preferences = context.getSharedPreferences("preferred-hosters", Context.MODE_PRIVATE)

    override fun read(seriesKey: String): String? = preferences.getString(seriesKey, null)

    override fun write(seriesKey: String, hoster: String) {
        preferences.edit { putString(seriesKey, hoster) }
    }
}

/**
 * Remembers per profile and series the hoster whose link played an episode (half of it, without an
 * error), so the next episodes try that hoster's link first. Movies have no memory.
 */
class PreferredHoster(private val storage: PreferredHosterStorage) {

    private val seriesOfEpisode = mutableMapOf<String, String>()
    private var profileId: String? = null
    private var seriesKey: String? = null
    private var playingHoster: String? = null
    private var startedAtSeconds: Double? = null
    private var troubled = false
    private var remembered = false

    /** The hoster to try first for the playing episode; null when none is known. */
    var hoster: String? = null
        private set

    /** The episodes of a series, so a later one is known to belong to it before its own details arrive. */
    fun learnEpisodes(seriesId: String, episodeIds: List<String>) {
        episodeIds.forEach { episodeId -> seriesOfEpisode[episodeId] = seriesId }
    }

    /** A new title starts for [profileId]: the series is known if one of its episodes played before. */
    fun open(profileId: String?, mediaId: String) {
        this.profileId = profileId
        sourceDetached()
        seriesKey = null
        hoster = null
        val seriesId = seriesOfEpisode[mediaId] ?: return
        useSeries(seriesId)
    }

    /** The episode's series, once its details arrived. */
    fun confirmSeries(seriesId: String?) {
        if (seriesId == null) {
            return
        }
        useSeries(seriesId)
    }

    private fun useSeries(seriesId: String) {
        val owner = profileId ?: return
        val key = "$owner|$seriesId"
        if (key == seriesKey) {
            return
        }
        seriesKey = key
        hoster = storage.read(key)
    }

    /** A link of [linkHoster] starts playing; it earns the memory when it gets through without trouble. */
    fun sourceAttached(linkHoster: String?) {
        playingHoster = linkHoster
        startedAtSeconds = null
        troubled = false
        remembered = false
    }

    /** The playing link failed. */
    fun sourceFailed() {
        troubled = true
    }

    private fun sourceDetached() {
        playingHoster = null
        remembered = false
    }

    /** Called with every position update. */
    fun follow(positionSeconds: Double, durationSeconds: Double) {
        val key = seriesKey ?: return
        val linkHoster = playingHoster ?: return
        if (troubled || remembered || durationSeconds <= 0) {
            return
        }
        val start = startedAtSeconds ?: positionSeconds
        startedAtSeconds = start
        if (positionSeconds - start < playedSecondsOf(durationSeconds)) {
            return
        }
        remembered = true
        hoster = linkHoster
        storage.write(key, linkHoster)
    }
}
