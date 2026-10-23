package dev.neoworks.otter.ui.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.neoworks.otter.api.OtterApi
import dev.neoworks.otter.api.backdropUrl
import dev.neoworks.otter.api.dataOrThrow
import dev.neoworks.otter.api.tmdbImageUrl
import dev.neoworks.otter.ui.components.ActorItem
import dev.neoworks.otter.graphql.TitlePreviewQuery
import dev.neoworks.otter.profiles.ProfileSession
import dev.neoworks.otter.ui.media.TitleActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PreviewDetail(
    val title: String,
    val facts: List<String>,
    val overview: String,
    val backdropUrl: String?,
    val trailerKey: String?,
    val inMyList: Boolean,
    val cast: List<ActorItem>,
)

/** Loads one title's trailer and summary for the long-press preview. */
class TitlePreviewViewModel(private val titleId: String) : ViewModel() {

    private val mutableDetail = MutableStateFlow<PreviewDetail?>(null)
    val detail: StateFlow<PreviewDetail?> = mutableDetail

    private val profileId: String = ProfileSession.activeProfile.value?.id.orEmpty()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            val title = OtterApi.client.query(TitlePreviewQuery(titleId, profileId)).execute().dataOrThrow().title
            if (title == null) {
                return
            }
            mutableDetail.value = PreviewDetail(
                title = title.title,
                facts = factsOf(title.year, title.genres),
                overview = title.overview.orEmpty(),
                backdropUrl = backdropUrl(title.backdropPath),
                trailerKey = title.trailerKey,
                inMyList = title.inWatchlist,
                cast = castOf(title.credits),
            )
        } catch (error: Exception) {
            mutableDetail.value = null
        }
    }

    fun toggleMyList() {
        val current = mutableDetail.value
        if (current == null) {
            return
        }
        val inMyList = !current.inMyList
        mutableDetail.update { detail -> detail?.copy(inMyList = inMyList) }
        viewModelScope.launch { saveMyList(inMyList) }
    }

    private suspend fun saveMyList(inMyList: Boolean) {
        try {
            TitleActions.setInWatchlist(profileId, titleId, inMyList)
        } catch (error: Exception) {
            mutableDetail.update { detail -> detail?.copy(inMyList = !inMyList) }
        }
    }
}

private const val MAX_GENRES = 3
private const val MAX_CAST = 12

private fun castOf(credits: List<TitlePreviewQuery.Credit>): List<ActorItem> =
    credits
        .filter { credit -> credit.role == "cast" }
        .sortedBy { credit -> credit.order }
        .take(MAX_CAST)
        .map { credit -> ActorItem(credit.person.id, credit.person.name, tmdbImageUrl(credit.person.profilePath, "w185"), credit.character) }

private fun factsOf(year: Int?, genres: List<String>): List<String> {
    val facts = mutableListOf<String>()
    if (year != null) {
        facts.add(year.toString())
    }
    facts.addAll(genres.take(MAX_GENRES))
    return facts
}
