package com.letsmoe.otter.tv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollographql.apollo.api.Optional
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.graphql.DiscoverGenresQuery
import com.letsmoe.otter.graphql.DiscoverQuery
import com.letsmoe.otter.graphql.type.MediaType
import com.letsmoe.otter.ui.media.RailItem
import com.letsmoe.otter.ui.media.toRailItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

// Typing waits this long before searching, so every letter doesn't start a search.
private const val QUERY_DEBOUNCE_MILLISECONDS = 400L
// A filtered search page can come back short; this many titles fill the grid's first screens.
private const val FILLED_COUNT = 18
// Pages read in a row to reach FILLED_COUNT before waiting for the grid to ask again.
private const val MAX_PAGES_PER_FILL = 4

/** The search page: what's typed, which category and filters, and the results loaded so far. */
data class TvSearchState(
    val query: String = "",
    val mediaType: MediaType = MediaType.MOVIE,
    val filters: SearchFilters = SearchFilters(),
    val genres: List<String> = emptyList(),
    val items: List<RailItem> = emptyList(),
    val loading: Boolean = false,
    val hasMore: Boolean = true,
    val failed: Boolean = false,
) {
    val hasQuery: Boolean get() = query.isNotBlank()
}

/**
 * Movies or shows matching the query, or without one the catalog, narrowed by the filters, a page
 * at a time: any change starts over, [loadMore] appends the next page.
 */
class TvSearchViewModel : ViewModel() {

    private val mutableState = MutableStateFlow(TvSearchState())
    val state: StateFlow<TvSearchState> = mutableState

    private var nextPage = 1
    private var loadJob: Job? = null

    init {
        loadGenres()
        restart(delayMilliseconds = 0)
    }

    fun onQueryChange(query: String) {
        mutableState.update { current -> current.copy(query = query) }
        restart(QUERY_DEBOUNCE_MILLISECONDS)
    }

    /** Genres differ between movies and shows, so the genre filter starts over too. */
    fun selectMediaType(mediaType: MediaType) {
        if (mediaType == mutableState.value.mediaType) {
            return
        }
        mutableState.update { current ->
            current.copy(mediaType = mediaType, filters = current.filters.copy(genre = ""), genres = emptyList())
        }
        loadGenres()
        restart(delayMilliseconds = 0)
    }

    fun changeFilters(change: (SearchFilters) -> SearchFilters) {
        mutableState.update { current -> current.copy(filters = change(current.filters)) }
        restart(delayMilliseconds = 0)
    }

    /** The grid nears its end: the next page, unless one is loading or there is none. */
    fun loadMore() {
        val current = mutableState.value
        if (current.loading || !current.hasMore || current.failed) {
            return
        }
        loadJob = viewModelScope.launch { loadPages() }
    }

    private fun restart(delayMilliseconds: Long) {
        loadJob?.cancel()
        nextPage = 1
        mutableState.update { current -> current.copy(items = emptyList(), hasMore = true, loading = true, failed = false) }
        loadJob = viewModelScope.launch {
            delay(delayMilliseconds)
            loadPages()
        }
    }

    // Pages until FILLED_COUNT new titles arrived, so a filtered search whose pages come back short still fills the grid.
    private suspend fun loadPages() {
        mutableState.update { current -> current.copy(loading = true) }
        val target = mutableState.value.items.size + FILLED_COUNT
        var pagesRead = 0
        while (pagesRead < MAX_PAGES_PER_FILL && loadPage()) {
            pagesRead++
            val current = mutableState.value
            if (current.items.size >= target || !current.hasMore) {
                break
            }
        }
        mutableState.update { current -> current.copy(loading = false) }
    }

    /** Reads [nextPage]; false when it failed. */
    private suspend fun loadPage(): Boolean {
        OtterApi.awaitBackend()
        val current = mutableState.value
        try {
            val page = OtterApi.client.query(discoverQueryFor(current, nextPage)).execute().dataOrThrow().discover
            val found = page.items.map { title -> title.titleCard.toRailItem() }
            nextPage++
            mutableState.update { latest -> latest.copy(items = withoutRepeats(latest.items, found), hasMore = page.hasMore) }
            return true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            mutableState.update { latest -> latest.copy(failed = true) }
            return false
        }
    }

    private fun loadGenres() {
        val mediaType = mutableState.value.mediaType
        viewModelScope.launch {
            OtterApi.awaitBackend()
            try {
                val genres = OtterApi.client.query(DiscoverGenresQuery(mediaType)).execute().dataOrThrow().discoverGenres
                mutableState.update { current -> current.copy(genres = genres) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // Without the list the genre filter only offers "Any genre".
            }
        }
    }
}

private fun discoverQueryFor(state: TvSearchState, page: Int): DiscoverQuery {
    var query: String? = null
    if (state.hasQuery) {
        query = state.query.trim()
    }
    return DiscoverQuery(
        mediaType = state.mediaType,
        query = Optional.presentIfNotNull(query),
        filter = Optional.present(state.filters.toDiscoverFilter(state.mediaType)),
        page = page,
    )
}

// TMDB pages can overlap while its order shifts; a title shows once.
private fun withoutRepeats(shown: List<RailItem>, found: List<RailItem>): List<RailItem> {
    val shownIds = shown.map { item -> item.id }.toHashSet()
    return shown + found.filter { item -> shownIds.add(item.id) }
}
