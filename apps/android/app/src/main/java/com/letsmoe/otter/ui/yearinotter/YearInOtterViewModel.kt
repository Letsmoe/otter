package com.letsmoe.otter.ui.yearinotter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.graphql.YearInOtterQuery
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.ui.LoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException

private const val SECONDS_PER_MINUTE = 60

/** Loads the cards of the active profile's year. */
class YearInOtterViewModel(private val year: Int) : ViewModel() {

    private val mutableCards = MutableStateFlow<LoadState<List<YearCard>>>(LoadState.Loading)
    val cards: StateFlow<LoadState<List<YearCard>>> = mutableCards

    init {
        load()
    }

    fun load() {
        mutableCards.value = LoadState.Loading
        viewModelScope.launch { fetch() }
    }

    private suspend fun fetch() {
        OtterApi.awaitBackend()
        val profile = ProfileSession.activeProfile.value
        try {
            val data = OtterApi.client.query(YearInOtterQuery(profile?.id.orEmpty(), year, phoneOffsetMinutes())).execute().dataOrThrow()
            mutableCards.value = LoadState.Loaded(yearCards(data.yearInReview.toReview(), profile?.name.orEmpty()))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            mutableCards.value = LoadState.Failed(error.message.orEmpty())
        }
    }

    // The phone's current offset from UTC, so nights fall where the profile lived them.
    private fun phoneOffsetMinutes(): Int =
        ZoneId.systemDefault().rules.getOffset(Instant.now()).totalSeconds / SECONDS_PER_MINUTE
}
