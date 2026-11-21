package com.letsmoe.otter.ui.simple

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.ui.media.MediaRepository
import com.letsmoe.otter.ui.media.RailItem
import com.letsmoe.otter.ui.search.combineFilters
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val SEARCH_PAUSE_MILLISECONDS = 500L

data class SimpleTitlesState(val items: List<RailItem> = emptyList(), val loaded: Boolean = false)

/** Titles of one profile list (continue watching, My List), reloaded whenever the page is shown. */
class SimpleTitlesViewModel(private val fetch: suspend (profileId: String) -> List<RailItem>) : ViewModel() {

    private val mutableState = MutableStateFlow(SimpleTitlesState())
    val state: StateFlow<SimpleTitlesState> = mutableState

    fun load() {
        val profileId = ProfileSession.activeProfile.value?.id ?: return
        viewModelScope.launch {
            OtterApi.awaitBackend()
            mutableState.value = SimpleTitlesState(fetch(profileId), loaded = true)
        }
    }
}

/** [finished] is true once the results belong to the current query. */
data class SimpleSearchState(val query: String = "", val results: List<RailItem> = emptyList(), val finished: Boolean = false)

class SimpleSearchViewModel : ViewModel() {

    private val mutableState = MutableStateFlow(SimpleSearchState())
    val state: StateFlow<SimpleSearchState> = mutableState

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        mutableState.update { current -> current.copy(query = query, finished = false) }
        search(SEARCH_PAUSE_MILLISECONDS)
    }

    fun searchNow() {
        search(0)
    }

    /** A newer search cancels the older one, so late answers never replace fresh results. */
    private fun search(delayMilliseconds: Long) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(delayMilliseconds)
            runSearch(mutableState.value.query)
        }
    }

    private suspend fun runSearch(query: String) {
        if (query.isBlank()) {
            mutableState.update { current -> current.copy(results = emptyList(), finished = true) }
            return
        }
        OtterApi.awaitBackend()
        val found = MediaRepository.search(query.trim(), null, combineFilters(emptyList(), emptyMap()))
        mutableState.update { current -> current.copy(results = found, finished = true) }
    }
}
