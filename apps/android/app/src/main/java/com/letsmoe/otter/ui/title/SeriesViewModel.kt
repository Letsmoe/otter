package com.letsmoe.otter.ui.title

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.filler.FillerSkipSettings
import com.letsmoe.otter.filler.isFiller
import com.letsmoe.otter.graphql.SeriesEpisodesQuery
import com.letsmoe.otter.profiles.ProfileSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EpisodesState(
    val episodes: List<SeasonEpisode> = emptyList(),
    val loaded: Boolean = false,
    val shownSeason: Int? = null,
    val upNext: UpNext? = null,
    /** Filler episodes are jumped over for this series (see [SeasonEpisode.filler]). */
    val skipFiller: Boolean = false,
) {
    val fillerCount: Int
        get() = episodes.count { episode -> episode.seasonNumber > 0 && episode.isFiller() }
}

/** Episodes, the season switch and Play of the up-next episode for one series page. */
class SeriesViewModel(
    private val seriesId: String,
    private val linkedEpisode: LinkedEpisode,
) : ViewModel() {

    private val mutableState = MutableStateFlow(EpisodesState())
    val state: StateFlow<EpisodesState> = mutableState

    private val profileId: String = ProfileSession.activeProfile.value?.id.orEmpty()

    init {
        loadEpisodes()
    }

    /** Reloaded when the page is shown again, so Resume and the progress bars are current. */
    fun loadEpisodes() {
        viewModelScope.launch {
            OtterApi.awaitBackend()
            try {
                val data = OtterApi.client.query(SeriesEpisodesQuery(seriesId, profileId)).execute().dataOrThrow()
                showEpisodes(data.title?.toEpisodes().orEmpty())
            } catch (error: Exception) {
                mutableState.update { current -> current.copy(loaded = true) }
            }
        }
    }

    private fun showEpisodes(episodes: List<SeasonEpisode>) {
        val skipFiller = FillerSkipSettings.skips(profileId, seriesId)
        val upNext = linkedOrUpNext(episodes, skipFiller)
        mutableState.update { current ->
            var season = current.shownSeason
            if (season == null) {
                season = initialSeason(episodes, upNext)
            }
            EpisodesState(episodes, loaded = true, shownSeason = season, upNext = upNext, skipFiller = skipFiller)
        }
    }

    /** Skips filler episodes of this series (or not) from now on: Play, the next episode and autoplay pass them over. */
    fun setSkipFiller(skipFiller: Boolean) {
        FillerSkipSettings.set(profileId, seriesId, skipFiller)
        mutableState.update { current ->
            current.copy(skipFiller = skipFiller, upNext = linkedOrUpNext(current.episodes, skipFiller))
        }
    }

    /** A deep link (Continue Watching, a shared link) picks the episode; otherwise the profile's progress does. */
    private fun linkedOrUpNext(episodes: List<SeasonEpisode>, skipFiller: Boolean): UpNext? {
        val linked = findLinkedEpisode(episodes, linkedEpisode)
        if (linked != null) {
            return UpNext(linked, resume = linked.progress != null)
        }
        return upNextEpisode(episodes, skipFiller)
    }

    private fun initialSeason(episodes: List<SeasonEpisode>, upNext: UpNext?): Int? {
        if (episodes.any { episode -> episode.seasonNumber == linkedEpisode.seasonNumber }) {
            return linkedEpisode.seasonNumber
        }
        if (upNext != null) {
            return upNext.episode.seasonNumber
        }
        return episodes.firstOrNull()?.seasonNumber
    }

    fun selectSeason(seasonNumber: Int) {
        mutableState.update { current -> current.copy(shownSeason = seasonNumber) }
    }
}

/** The episode [link] names, by id or else by season and episode number; null when it names none. */
fun findLinkedEpisode(episodes: List<SeasonEpisode>, link: LinkedEpisode): SeasonEpisode? {
    if (link.episodeId != null) {
        return episodes.firstOrNull { episode -> episode.id == link.episodeId }
    }
    if (link.episodeNumber == null) {
        return null
    }
    return episodes.firstOrNull { episode ->
        episode.seasonNumber == link.seasonNumber && episode.episodeNumber == link.episodeNumber
    }
}
