package dev.neoworks.otter.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.neoworks.otter.api.OtterApi
import dev.neoworks.otter.graphql.type.MediaType
import dev.neoworks.otter.ui.media.LIBRARY_PAGE_SIZE
import dev.neoworks.otter.ui.media.MediaRepository
import dev.neoworks.otter.ui.media.RailItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowseState(
    val genres: List<String> = emptyList(),
    /** Empty means all genres. */
    val genre: String = "",
    val items: List<RailItem> = emptyList(),
    val loading: Boolean = true,
    val done: Boolean = false,
)

/** Stored titles of one kind, most popular first, one page at a time. */
class BrowseViewModel(private val mediaType: MediaType) : ViewModel() {

    private val mutableState = MutableStateFlow(BrowseState())
    val state: StateFlow<BrowseState> = mutableState

    private var page = 0
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            OtterApi.awaitBackend()
            mutableState.update { current -> current.copy(genres = MediaRepository.genres()) }
        }
        selectGenre("")
    }

    /** A genre change resets the grid to its first page. */
    fun selectGenre(genre: String) {
        loadJob?.cancel()
        page = 0
        mutableState.update { current -> current.copy(genre = genre, items = emptyList(), loading = true, done = false) }
        loadJob = viewModelScope.launch {
            OtterApi.awaitBackend()
            val first = fetchPage(genre, 0)
            mutableState.update { current -> current.copy(items = first, loading = false, done = first.size < LIBRARY_PAGE_SIZE) }
        }
    }

    fun loadMore() {
        val current = mutableState.value
        if (current.loading || current.done) {
            return
        }
        mutableState.value = current.copy(loading = true)
        loadJob = viewModelScope.launch {
            val rows = fetchPage(current.genre, page + 1)
            page += 1
            mutableState.update { latest -> latest.copy(items = latest.items + rows, loading = false, done = rows.size < LIBRARY_PAGE_SIZE) }
        }
    }

    private suspend fun fetchPage(genre: String, pageIndex: Int): List<RailItem> {
        var genreFilter: String? = null
        if (genre.isNotEmpty()) {
            genreFilter = genre
        }
        return MediaRepository.browse(mediaType, genreFilter, LIBRARY_PAGE_SIZE, pageIndex * LIBRARY_PAGE_SIZE)
    }
}
