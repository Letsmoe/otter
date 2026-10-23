package dev.neoworks.otter.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.neoworks.otter.api.OtterApi
import dev.neoworks.otter.graphql.type.MediaType
import dev.neoworks.otter.ui.media.MediaRepository
import dev.neoworks.otter.ui.media.RailItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DEBOUNCE_MILLISECONDS = 300L

enum class SearchType(val label: String, val mediaType: MediaType?) {
    ALL("All", null),
    MOVIES("Movies", MediaType.MOVIE),
    SERIES("TV Shows", MediaType.SERIES),
}

data class RelatedRow(val title: String, val items: List<RailItem>)

data class SearchState(
    val query: String = "",
    val type: SearchType = SearchType.ALL,
    val chosenFilters: Map<String, String> = emptyMap(),
    val results: List<RailItem> = emptyList(),
    val searching: Boolean = false,
    val trending: List<RailItem> = emptyList(),
    /** "More Like X" rows for the best movie and the best show of the results. */
    val relatedRows: List<RelatedRow> = emptyList(),
) {
    val hasQuery: Boolean get() = query.isNotBlank()

    val filterGroups: List<FilterGroup>
        get() = when (type) {
            SearchType.MOVIES -> listOf(ratingFilter, lengthFilter)
            SearchType.SERIES -> listOf(ratingFilter, seasonsFilter)
            SearchType.ALL -> emptyList()
        }
}

class SearchViewModel : ViewModel() {

    private val mutableState = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = mutableState

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            OtterApi.awaitBackend()
            val trending = MediaRepository.trending(null)
            mutableState.update { current -> current.copy(trending = trending) }
        }
    }

    fun onQueryChange(query: String) {
        mutableState.update { current -> current.copy(query = query) }
        search(DEBOUNCE_MILLISECONDS)
    }

    fun clearQuery() {
        mutableState.update { current -> current.copy(query = "") }
        search(0)
    }

    fun selectType(type: SearchType) {
        mutableState.update { current -> current.copy(type = type, chosenFilters = emptyMap()) }
        search(0)
    }

    fun selectFilter(group: FilterGroup, value: String) {
        mutableState.update { current -> current.copy(chosenFilters = current.chosenFilters + (group.key to value)) }
        search(0)
    }

    /** A newer search cancels the older one, so late answers never overwrite fresh results. */
    private fun search(delayMilliseconds: Long) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(delayMilliseconds)
            runSearch(mutableState.value)
        }
    }

    private suspend fun runSearch(current: SearchState) {
        if (!current.hasQuery) {
            mutableState.update { state -> state.copy(results = emptyList(), relatedRows = emptyList(), searching = false) }
            return
        }
        mutableState.update { state -> state.copy(searching = true) }
        val filter = combineFilters(current.filterGroups, current.chosenFilters)
        val found = MediaRepository.search(current.query, current.type.mediaType, filter)
        mutableState.update { state -> state.copy(results = found, searching = false, relatedRows = emptyList()) }
        loadRelatedRows(found)
    }

    private suspend fun loadRelatedRows(found: List<RailItem>) {
        val seeds = listOfNotNull(
            found.firstOrNull { item -> item.mediaType == MediaType.MOVIE },
            found.firstOrNull { item -> item.mediaType == MediaType.SERIES },
        )
        val rows = coroutineScope {
            seeds.map { seed -> async { RelatedRow("More Like ${seed.title}", MediaRepository.related(seed.id)) } }.map { row -> row.await() }
        }
        mutableState.update { state -> state.copy(relatedRows = rows) }
    }
}
