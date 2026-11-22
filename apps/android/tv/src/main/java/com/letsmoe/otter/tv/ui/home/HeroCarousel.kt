package com.letsmoe.otter.tv.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.letsmoe.otter.graphql.type.MediaType
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.ui.home.TitlesPageState
import com.letsmoe.otter.ui.media.TitleActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The featured title, then this many of the top trending ones.
private const val TRENDING_IN_CAROUSEL = 4
private const val ROTATE_MILLISECONDS = 8_000L

/**
 * The titles the hero cycles through while focus is on its buttons: the featured title, then the
 * top trending ones. Keeps which of them are on My List and how they're rated, asked once per title.
 */
class HeroCarousel(
    val titles: List<HeroTitle>,
    private val scope: CoroutineScope,
    private val inMyList: MutableMap<String, Boolean>,
    // A title without a rating maps to null; a missing key means not asked yet.
    private val ratings: MutableMap<String, Double?>,
    private val indexState: MutableIntState,
) {
    val index: Int get() = indexState.intValue.coerceIn(0, maxOf(titles.size - 1, 0))

    val current: HeroTitle? get() = titles.getOrNull(index)

    fun next() {
        if (titles.size > 1) {
            indexState.intValue = (index + 1) % titles.size
        }
    }

    fun previous() {
        if (titles.size > 1) {
            indexState.intValue = (index - 1 + titles.size) % titles.size
        }
    }

    fun isInMyList(titleId: String): Boolean {
        return inMyList[titleId] == true
    }

    /** Flips the current title on My List; undone when the backend refuses. */
    fun toggleMyList() {
        val title = current ?: return
        val titleId = title.item.id
        val wanted = !isInMyList(titleId)
        inMyList[titleId] = wanted
        scope.launch {
            try {
                TitleActions.setInWatchlist(profileId(), titleId, wanted)
            } catch (error: Exception) {
                inMyList[titleId] = !wanted
            }
        }
    }

    suspend fun loadMyListState(titleId: String) {
        if (inMyList.containsKey(titleId)) {
            return
        }
        try {
            inMyList[titleId] = TitleActions.isInWatchlist(profileId(), titleId)
        } catch (error: Exception) {
            inMyList[titleId] = false
        }
    }

    fun ratingOf(titleId: String): Double? {
        return ratings[titleId]
    }

    /** Rates the current title; choosing its current rating again clears it. Undone when the backend refuses. */
    fun rate(rating: Double) {
        val title = current ?: return
        val titleId = title.item.id
        val previous = ratingOf(titleId)
        var wanted: Double? = rating
        if (previous == rating) {
            wanted = null
        }
        ratings[titleId] = wanted
        scope.launch {
            try {
                saveRating(titleId, wanted)
            } catch (error: Exception) {
                ratings[titleId] = previous
            }
        }
    }

    suspend fun loadRating(titleId: String) {
        if (ratings.containsKey(titleId)) {
            return
        }
        try {
            ratings[titleId] = TitleActions.ratingOf(profileId(), titleId)
        } catch (error: Exception) {
            ratings[titleId] = null
        }
    }
}

private suspend fun saveRating(titleId: String, rating: Double?) {
    if (rating == null) {
        TitleActions.clearRating(profileId(), titleId)
        return
    }
    TitleActions.setRating(profileId(), titleId, rating)
}

private fun profileId(): String {
    return ProfileSession.activeProfile.value?.id.orEmpty()
}

@Composable
fun rememberHeroCarousel(mediaType: MediaType, state: TitlesPageState): HeroCarousel {
    val scope = rememberCoroutineScope()
    val inMyList = remember(mediaType) { mutableStateMapOf<String, Boolean>() }
    val ratings = remember(mediaType) { mutableStateMapOf<String, Double?>() }
    val indexState = remember(mediaType) { mutableIntStateOf(0) }
    val carousel = HeroCarousel(carouselTitles(state), scope, inMyList, ratings, indexState)
    val current = carousel.current
    LaunchedEffect(current?.item?.id) {
        if (current != null) {
            carousel.loadMyListState(current.item.id)
            carousel.loadRating(current.item.id)
        }
    }
    LaunchedEffect(indexState.intValue, carousel.titles.size) {
        delay(ROTATE_MILLISECONDS)
        carousel.next()
    }
    return carousel
}

private fun carouselTitles(state: TitlesPageState): List<HeroTitle> {
    val featured = state.featured ?: return emptyList()
    val titles = mutableListOf(HeroTitle(featured.item, featured.genres))
    val trending = state.trending.filter { item -> item.id != featured.item.id && item.backdropUrl != null }
    for (item in trending.take(TRENDING_IN_CAROUSEL)) {
        titles.add(HeroTitle(item))
    }
    return titles
}

/** Left on the first hero button goes to the previous title, right on the last to the next. */
fun carouselKeys(event: KeyEvent, direction: Key, move: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown || event.key != direction) {
        return false
    }
    move()
    return true
}
