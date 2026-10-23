package dev.neoworks.otter.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.neoworks.otter.api.OtterApi
import dev.neoworks.otter.graphql.type.MediaType
import dev.neoworks.otter.profiles.ProfileSession
import dev.neoworks.otter.ui.media.FeaturedItem
import dev.neoworks.otter.ui.media.MediaRepository
import dev.neoworks.otter.ui.media.RailItem
import dev.neoworks.otter.ui.media.TitleActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GenrePageState(
    val loaded: Boolean = false,
    val featured: FeaturedItem? = null,
    val featuredInMyList: Boolean = false,
    val titles: List<RailItem> = emptyList(),
)

private const val GENRE_TITLE_COUNT = 80

/** One genre of movies or series: stored titles plus TMDB's popular titles of that genre. */
class GenrePageViewModel(private val mediaType: MediaType, private val genre: String) : ViewModel() {

    private val mutableState = MutableStateFlow(GenrePageState())
    val state: StateFlow<GenrePageState> = mutableState

    private val profileId: String = ProfileSession.activeProfile.value?.id.orEmpty()

    init {
        viewModelScope.launch {
            OtterApi.awaitBackend()
            load()
        }
    }

    private suspend fun load() {
        val catalogTitles = catalogTitlesOfGenre()
        val stored = MediaRepository.browse(mediaType, genre, GENRE_TITLE_COUNT, 0)
        val titles = (catalogTitles + stored).distinctBy { item -> item.id }
        val withPoster = titles.firstOrNull { item -> item.posterUrl != null }
        var featured: FeaturedItem? = null
        var inMyList = false
        if (withPoster != null) {
            featured = FeaturedItem(withPoster, listOf(genre))
            inMyList = isInMyList(withPoster.id)
        }
        mutableState.update { GenrePageState(true, featured, inMyList, titles) }
    }

    // The catalog only knows genres by TMDB id; asking for one row while excluding every other name picks this one.
    private suspend fun catalogTitlesOfGenre(): List<RailItem> {
        val otherGenres = MediaRepository.genres().filter { name -> name != genre }
        val rows = MediaRepository.catalogGenreRails(mediaType, 1, otherGenres)
        return rows.filter { row -> row.genre == genre }.flatMap { row -> row.items }
    }

    private suspend fun isInMyList(titleId: String): Boolean {
        try {
            return TitleActions.isInWatchlist(profileId, titleId)
        } catch (error: Exception) {
            return false
        }
    }

    fun toggleFeaturedInMyList() {
        val featured = mutableState.value.featured
        if (featured == null) {
            return
        }
        val inMyList = !mutableState.value.featuredInMyList
        mutableState.update { page -> page.copy(featuredInMyList = inMyList) }
        viewModelScope.launch { saveMyList(featured.item.id, inMyList) }
    }

    private suspend fun saveMyList(titleId: String, inMyList: Boolean) {
        try {
            TitleActions.setInWatchlist(profileId, titleId, inMyList)
        } catch (error: Exception) {
            mutableState.update { page -> page.copy(featuredInMyList = !inMyList) }
        }
    }
}
