package dev.neoworks.otter.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.neoworks.otter.api.OtterApi
import dev.neoworks.otter.graphql.type.CatalogSort
import dev.neoworks.otter.graphql.type.CatalogTheme
import dev.neoworks.otter.graphql.type.MediaType
import dev.neoworks.otter.profiles.ProfileSession
import dev.neoworks.otter.ui.components.ActorItem
import dev.neoworks.otter.ui.media.FeaturedItem
import dev.neoworks.otter.ui.media.GenreRail
import dev.neoworks.otter.ui.media.MediaRepository
import dev.neoworks.otter.ui.media.MoreWithRail
import dev.neoworks.otter.ui.media.RailItem
import dev.neoworks.otter.ui.media.TitleActions
import dev.neoworks.otter.ui.media.onlyKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TitlesPageState(
    val featured: FeaturedItem? = null,
    val featuredInMyList: Boolean = false,
    val continueWatching: List<RailItem> = emptyList(),
    val myList: List<RailItem> = emptyList(),
    val newAndComingSoon: List<RailItem> = emptyList(),
    val recommended: List<RailItem> = emptyList(),
    val popular: List<RailItem> = emptyList(),
    val trending: List<RailItem> = emptyList(),
    val topRated: List<RailItem> = emptyList(),
    val moreWith: MoreWithRail? = null,
    val actors: List<ActorItem> = emptyList(),
    val genreRails: List<GenreRail> = emptyList(),
    val themes: Map<CatalogTheme, List<RailItem>> = emptyMap(),
    val becauseRows: List<GenreRail> = emptyList(),
    val watchAgain: List<RailItem> = emptyList(),
)

private const val GENRE_RAIL_TARGET = 8

// Rails with fewer titles look broken; they're replaced by catalog rails.
private const val MIN_RAIL_SIZE = 5

private val movieThemes = listOf(CatalogTheme.HIDDEN_GEMS, CatalogTheme.BLOCKBUSTERS, CatalogTheme.FEEL_GOOD)
private val seriesThemes = listOf(CatalogTheme.HIDDEN_GEMS, CatalogTheme.BINGEWORTHY, CatalogTheme.FEEL_GOOD)

/** One Home category (movies or TV shows); every row loads on its own so fast rows don't wait for slow ones. */
class TitlesPageViewModel(private val mediaType: MediaType) : ViewModel() {

    private val mutableState = MutableStateFlow(TitlesPageState())
    val state: StateFlow<TitlesPageState> = mutableState

    private val profileId: String = ProfileSession.activeProfile.value?.id.orEmpty()

    init {
        viewModelScope.launch {
            OtterApi.awaitBackend()
            loadAll()
        }
    }

    private fun loadAll() {
        loadPersonalRows()
        loadRow({ MediaRepository.newAndComingSoon(mediaType) }) { items -> copy(newAndComingSoon = items) }
        loadRow({ MediaRepository.trending(mediaType) }) { items -> copy(trending = items) }
        viewModelScope.launch { loadRecommended() }
        loadRow({ MediaRepository.moreWith(profileId, mediaType) }) { row -> copy(moreWith = row) }
        loadRow({ MediaRepository.watchedActors(profileId, mediaType) }) { actors -> copy(actors = actors) }
        loadRow({ genreRailsFilledFromCatalog() }) { rails -> copy(genreRails = rails) }
        loadRow({ MediaRepository.watchAgain(profileId, mediaType) }) { items -> copy(watchAgain = items) }
        loadRow({ MediaRepository.becauseRows(profileId, mediaType) }) { rows -> copy(becauseRows = rows) }
        loadThemes()
    }

    private fun loadThemes() {
        var themeList = movieThemes
        if (mediaType == MediaType.SERIES) {
            themeList = seriesThemes
        }
        for (theme in themeList) {
            loadRow({ MediaRepository.theme(mediaType, theme) }) { items -> copy(themes = themes + (theme to items)) }
        }
    }

    /** Rows the profile changes while using the app; reloaded when the page is shown again. */
    fun loadPersonalRows() {
        loadRow({ MediaRepository.continueWatching(profileId).onlyKind(mediaType) }) { items -> copy(continueWatching = items) }
        loadRow({ MediaRepository.watchlist(profileId).onlyKind(mediaType) }) { items -> copy(myList = items) }
        viewModelScope.launch { loadFeatured() }
    }

    // A new profile has no recommendations yet; TMDB's popular and top-rated lists stand in.
    private suspend fun loadRecommended() {
        val recommended = MediaRepository.recommended(profileId, mediaType)
        mutableState.update { rows -> rows.copy(recommended = recommended) }
        if (recommended.isNotEmpty()) {
            return
        }
        loadRow({ MediaRepository.catalog(mediaType, CatalogSort.POPULAR) }) { items -> copy(popular = items) }
        loadRow({ MediaRepository.catalog(mediaType, CatalogSort.TOP_RATED) }) { items -> copy(topRated = items) }
    }

    // The profile's own genre rails first, then popular TMDB genres up to GENRE_RAIL_TARGET.
    private suspend fun genreRailsFilledFromCatalog(): List<GenreRail> {
        val ownRails = MediaRepository.genreRails(profileId, mediaType).filter { rail -> rail.items.size >= MIN_RAIL_SIZE }
        val missing = GENRE_RAIL_TARGET - ownRails.size
        if (missing <= 0) {
            return ownRails
        }
        val ownGenres = ownRails.map { rail -> rail.genre }
        return ownRails + MediaRepository.catalogGenreRails(mediaType, missing, ownGenres)
    }

    /** Merges each row into the state as soon as it arrives. */
    private fun <T> loadRow(load: suspend () -> T, merge: TitlesPageState.(T) -> TitlesPageState) {
        viewModelScope.launch {
            val value = load()
            mutableState.update { rows -> rows.merge(value) }
        }
    }

    private suspend fun loadFeatured() {
        val featured = MediaRepository.featured(profileId, mediaType)
        if (featured == null) {
            return
        }
        val inMyList = isInMyList(featured.item.id)
        mutableState.update { rows -> rows.copy(featured = featured, featuredInMyList = inMyList) }
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
        mutableState.update { rows -> rows.copy(featuredInMyList = inMyList) }
        viewModelScope.launch { saveMyList(featured.item.id, inMyList) }
    }

    private suspend fun saveMyList(titleId: String, inMyList: Boolean) {
        try {
            TitleActions.setInWatchlist(profileId, titleId, inMyList)
        } catch (error: Exception) {
            mutableState.update { rows -> rows.copy(featuredInMyList = !inMyList) }
        }
    }
}
