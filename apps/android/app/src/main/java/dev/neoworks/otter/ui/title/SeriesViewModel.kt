package dev.neoworks.otter.ui.title

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.neoworks.otter.api.OtterApi
import dev.neoworks.otter.api.dataOrThrow
import dev.neoworks.otter.graphql.SeriesEpisodesQuery
import dev.neoworks.otter.profiles.ProfileSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EpisodesState(
    val episodes: List<SeasonEpisode> = emptyList(),
    val loaded: Boolean = false,
    val shownSeason: Int? = null,
    val upNext: UpNext? = null,
)

/** Episodes, the season switch and Play of the up-next episode for one series page. */
class SeriesViewModel(
    private val seriesId: String,
    private val linkedSeasonNumber: Int?,
    private val linkedEpisodeId: String?,
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
        val upNext = linkedOrUpNext(episodes)
        mutableState.update { current ->
            var season = current.shownSeason
            if (season == null) {
                season = initialSeason(episodes, upNext)
            }
            EpisodesState(episodes, loaded = true, shownSeason = season, upNext = upNext)
        }
    }

    /** A deep link (Continue Watching) picks the episode; otherwise the profile's progress does. */
    private fun linkedOrUpNext(episodes: List<SeasonEpisode>): UpNext? {
        val linked = episodes.firstOrNull { episode -> episode.id == linkedEpisodeId }
        if (linked != null) {
            return UpNext(linked, resume = linked.progress != null)
        }
        return upNextEpisode(episodes)
    }

    private fun initialSeason(episodes: List<SeasonEpisode>, upNext: UpNext?): Int? {
        if (episodes.any { episode -> episode.seasonNumber == linkedSeasonNumber }) {
            return linkedSeasonNumber
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
