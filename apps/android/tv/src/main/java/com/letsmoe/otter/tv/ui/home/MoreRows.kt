package com.letsmoe.otter.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollographql.apollo.api.Optional
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.graphql.DiscoverGenresQuery
import com.letsmoe.otter.graphql.DiscoverQuery
import com.letsmoe.otter.graphql.type.DiscoverFilter
import com.letsmoe.otter.graphql.type.DiscoverSort
import com.letsmoe.otter.graphql.type.MediaType
import com.letsmoe.otter.ui.home.PosterRow
import com.letsmoe.otter.ui.home.RailStyle
import com.letsmoe.otter.ui.media.RailItem
import com.letsmoe.otter.ui.media.toRailItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

// Rows asked for at once when the page nears its end.
private const val ROWS_PER_LOAD = 3
// Rows with fewer titles aren't worth a row.
private const val MIN_ROW_SIZE = 5

private val decades = listOf(2020, 2010, 2000, 1990, 1980)

/** One row home can add at its end: a title and the catalog query behind it. */
private class MoreRowSpec(val key: String, val title: String, val filter: DiscoverFilter)

/**
 * Rows added below home's own as focus nears the end, for as long as there are some: popular
 * titles of genres home doesn't show yet, the best of each decade, then each genre's top rated
 * and newest titles.
 */
class MoreRowsViewModel(private val mediaType: MediaType) : ViewModel() {

    private val mutableRows = MutableStateFlow<List<PosterRow>>(emptyList())
    val rows: StateFlow<List<PosterRow>> = mutableRows

    private var specs: List<MoreRowSpec>? = null
    private var nextSpec = 0
    private var loading = false

    /** The next few rows; [shownGenres] are genres home already has a row for. */
    fun loadMore(shownGenres: List<String>) {
        if (loading) {
            return
        }
        loading = true
        viewModelScope.launch {
            try {
                appendNextRows(shownGenres)
            } finally {
                loading = false
            }
        }
    }

    private suspend fun appendNextRows(shownGenres: List<String>) {
        val allSpecs = specsFor(shownGenres)
        val batch = allSpecs.drop(nextSpec).take(ROWS_PER_LOAD)
        if (batch.isEmpty()) {
            return
        }
        nextSpec += batch.size
        val loaded = coroutineScope { batch.map { spec -> async { loadRow(spec) } }.awaitAll() }
        mutableRows.value = mutableRows.value + loaded.filterNotNull()
    }

    private suspend fun specsFor(shownGenres: List<String>): List<MoreRowSpec> {
        val known = specs
        if (known != null) {
            return known
        }
        val built = buildSpecs(loadGenres(), shownGenres)
        specs = built
        return built
    }

    private suspend fun loadGenres(): List<String> {
        OtterApi.awaitBackend()
        try {
            return OtterApi.client.query(DiscoverGenresQuery(mediaType)).execute().dataOrThrow().discoverGenres
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return emptyList()
        }
    }

    private fun buildSpecs(genres: List<String>, shownGenres: List<String>): List<MoreRowSpec> {
        val plural = pluralOf(mediaType)
        val newGenres = genres.filter { genre -> genre !in shownGenres }
        val popular = newGenres.map { genre -> MoreRowSpec("more-popular-$genre", "$genre $plural", discoverFilter(genre = genre)) }
        val bestOfDecades = decades.map { decade ->
            MoreRowSpec("more-decade-$decade", "Best of the ${decade}s", discoverFilter(sort = DiscoverSort.TOP_RATED, minYear = decade, maxYear = decade + 9))
        }
        val topRated = genres.map { genre -> MoreRowSpec("more-top-$genre", "Top Rated $genre", discoverFilter(genre = genre, sort = DiscoverSort.TOP_RATED)) }
        val newest = genres.map { genre -> MoreRowSpec("more-new-$genre", "New in $genre", discoverFilter(genre = genre, sort = DiscoverSort.NEWEST)) }
        return popular + bestOfDecades + topRated + newest
    }

    // A row that fails or comes back too short is left out.
    private suspend fun loadRow(spec: MoreRowSpec): PosterRow? {
        try {
            val query = DiscoverQuery(mediaType = mediaType, query = Optional.absent(), filter = Optional.present(spec.filter), page = 1)
            val page = OtterApi.client.query(query).execute().dataOrThrow().discover
            val items: List<RailItem> = page.items.map { title -> title.titleCard.toRailItem() }
            if (items.size < MIN_ROW_SIZE) {
                return null
            }
            return PosterRow(spec.key, spec.title, items, RailStyle.REGULAR)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return null
        }
    }
}

private fun pluralOf(mediaType: MediaType): String {
    if (mediaType == MediaType.SERIES) {
        return "Shows"
    }
    return "Movies"
}

private fun discoverFilter(
    genre: String? = null,
    sort: DiscoverSort = DiscoverSort.POPULAR,
    minYear: Int? = null,
    maxYear: Int? = null,
): DiscoverFilter {
    return DiscoverFilter(
        genre = Optional.presentIfNotNull(genre),
        minYear = Optional.presentIfNotNull(minYear),
        maxYear = Optional.presentIfNotNull(maxYear),
        sort = Optional.present(sort),
    )
}
