package dev.neoworks.otter.ui.title

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.neoworks.otter.api.OtterApi
import dev.neoworks.otter.api.dataOrThrow
import dev.neoworks.otter.graphql.TitleDetailQuery
import dev.neoworks.otter.graphql.type.MediaType
import dev.neoworks.otter.profiles.ProfileSession
import dev.neoworks.otter.toasts.ToastKind
import dev.neoworks.otter.toasts.Toasts
import dev.neoworks.otter.ui.LoadState
import dev.neoworks.otter.ui.media.TitleActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RailsState(val rails: List<TitleRail> = emptyList(), val loaded: Boolean = false) {
    val hasItems: Boolean get() = rails.any { rail -> rail.items.isNotEmpty() }
}

/** What both the movie and the series page share: the detail, My List, the rating, "more like this" and a movie's sources. */
class TitleViewModel(private val titleId: String) : ViewModel() {

    private val mutableDetail = MutableStateFlow<LoadState<TitleDetail>>(LoadState.Loading)
    val detail: StateFlow<LoadState<TitleDetail>> = mutableDetail

    private val mutableRails = MutableStateFlow(RailsState())
    val rails: StateFlow<RailsState> = mutableRails

    private val streamFollower = StreamFollower(viewModelScope)
    val streams: StateFlow<StreamList> = streamFollower.streams

    private val profileId: String = ProfileSession.activeProfile.value?.id.orEmpty()

    init {
        load()
    }

    fun load() {
        mutableDetail.value = LoadState.Loading
        viewModelScope.launch {
            OtterApi.awaitBackend()
            try {
                showDetail(fetchDetail())
            } catch (error: Exception) {
                mutableDetail.value = LoadState.Failed(error.message.orEmpty())
            }
        }
    }

    private suspend fun fetchDetail(): TitleDetail? {
        val data = OtterApi.client.query(TitleDetailQuery(titleId, profileId)).execute().dataOrThrow()
        return data.title?.toDetail()
    }

    private suspend fun showDetail(detail: TitleDetail?) {
        if (detail == null) {
            mutableDetail.value = LoadState.Failed("Title not found")
            return
        }
        mutableDetail.value = LoadState.Loaded(detail)
        if (detail.mediaType == MediaType.MOVIE) {
            streamFollower.follow(titleId)
        }
        mutableRails.value = RailsState(loadTitleRails(detail), loaded = true)
    }

    fun searchAgain() = streamFollower.searchAgain()

    fun toggleWatchlist() {
        val detail = loadedDetail()
        if (detail == null) {
            return
        }
        val inWatchlist = !detail.inWatchlist
        updateDetail { current -> current.copy(inWatchlist = inWatchlist) }
        viewModelScope.launch { saveWatchlist(inWatchlist) }
    }

    private suspend fun saveWatchlist(inWatchlist: Boolean) {
        try {
            TitleActions.setInWatchlist(profileId, titleId, inWatchlist)
        } catch (error: Exception) {
            updateDetail { current -> current.copy(inWatchlist = !inWatchlist) }
            Toasts.push("Could not update watchlist", ToastKind.ERROR)
        }
    }

    /** Choosing the current rating again clears it. */
    fun rate(rating: Double) {
        val detail = loadedDetail()
        if (detail == null) {
            return
        }
        var newRating: Double? = rating
        if (detail.rating == rating) {
            newRating = null
        }
        updateDetail { current -> current.copy(rating = newRating) }
        viewModelScope.launch { saveRating(newRating, detail.rating) }
    }

    private suspend fun saveRating(rating: Double?, previous: Double?) {
        try {
            if (rating == null) {
                TitleActions.clearRating(profileId, titleId)
                return
            }
            TitleActions.setRating(profileId, titleId, rating)
        } catch (error: Exception) {
            updateDetail { current -> current.copy(rating = previous) }
        }
    }

    private fun loadedDetail(): TitleDetail? {
        val state = mutableDetail.value
        if (state is LoadState.Loaded) {
            return state.value
        }
        return null
    }

    private fun updateDetail(change: (TitleDetail) -> TitleDetail) {
        mutableDetail.update { state ->
            when (state) {
                is LoadState.Loaded -> LoadState.Loaded(change(state.value))
                else -> state
            }
        }
    }

    override fun onCleared() {
        streamFollower.stop()
    }
}
